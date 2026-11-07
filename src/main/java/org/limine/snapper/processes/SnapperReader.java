package org.limine.snapper.processes;

import org.limine.snapper.formats.json1.SnapperEntry;
import org.limine.snapper.formats.snapper.SnapperProperty;
import org.limine.snapper.objects.Config;
import org.limine.snapper.objects.Messages;
import org.limine.snapper.objects.Output;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class SnapperReader {

    private static final JsonMapper OBJECT_MAPPER = new JsonMapper();
    private final LinkedHashMap<String, SnapperEntry> snapperEntries;
    private final Integer addedID;
    private SnapperEntry addedEntry;

    public SnapperReader(String addedID) {
        this.addedID = parseId(addedID);
        this.snapperEntries = new LinkedHashMap<>();
        this.process();
        if (addedEntry == null && !snapperEntries.isEmpty()) {
            addedEntry = snapperEntries.lastEntry().getValue();
        }
    }

    private Integer parseId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void process() {
        readList();
        // If no snapper entries are found, verify /.snapshots setup.
        // Important: This prevents accidental deletion of all snapshot entries due to user error.
        if (!validateSnapshots()) {
            return;
        }
        if (Config.USE_OPENSUSE_LAYOUT) {
            cleanupOpenSuseWritableCopyEntry();
        }
    }

    private void readList() {
        snapperEntries.clear();
        Output output = Utility.getCommandOutput(Config.SNAPPER_LIST_CMDLINE, true, null);
        if (!output.isSuccess()) {
            return;
        }
        if (output.text() == null || output.text().isEmpty()) {
            Utility.exitWithError(Messages.get("text.no-snapper-list-output"));
            return;
        }
        String text = String.join("\n", output.text());
        // Ignore everything before the actual JSON output.
        int jsonStart = text.indexOf('{');
        if (jsonStart < 0) {
            Utility.exitWithError(Messages.get("text.mismatch-snapper-list-format-output"), text);
            return;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(text.substring(jsonStart));
            JsonNode snapshots = root.properties().iterator().next().getValue();
            for (JsonNode snapshot : snapshots) {
                SnapperEntry entry = createSnapperEntry(snapshot);
                if (entry == null) {
                    continue;
                }
                snapperEntries.put(entry.timeId(), entry);
                if (addedID != null && addedID.equals(entry.snapshotID())) {
                    addedEntry = entry;
                }
            }
        } catch (Exception e) {
            Utility.exitWithError(Messages.get("text.mismatch-snapper-list-format-output"), text);
        }
    }

    private SnapperEntry createSnapperEntry(JsonNode snapshot) {
        JsonNode numberNode = snapshot.get("number");
        JsonNode dateNode = snapshot.get("date");
        if (numberNode == null || dateNode == null) {
            Utility.exitWithError(Messages.get("text.mismatch-snapper-number-date-format-output"));
            return null;
        }
        int snapshotID = numberNode.asInt();
        if (snapshotID == 0) {
            return null;
        }
        String utcTime = dateNode.asString();
        if (Utility.isTimeFormatNotValid(utcTime)) {
            Utility.exitWithError(
                    Messages.get("error.stopping-sync"),
                    Messages.get("error.because-snapper-time-format-has", utcTime)
            );
            return null;
        }
        Map<String, String> properties = new HashMap<>();
        for (SnapperProperty property : SnapperProperty.values()) {
            JsonNode value = snapshot.get(property.replace);
            if (value != null && !value.isNull()) {
                properties.put(property.replace, normalize(value.asString()));
            }
        }
        String localTime = Utility.convertUTCtoLocal(utcTime);
        return new SnapperEntry(snapshotID, utcTime, localTime, properties);
    }

    private String normalize(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            builder.append(c == '\n' || c == '\r' ? ' ' : c);
        }
        return builder.toString();
    }

    private int countNumericFoldersWithSnapshot() {
        Path path = Paths.get("/.snapshots");
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, Files::isDirectory)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (name.isEmpty()) continue;
                boolean numeric = name.chars().allMatch(Character::isDigit);
                if (!numeric) continue;
                if (Files.isDirectory(entry.resolve("snapshot"))) {
                    count++;
                }
            }
        } catch (IOException ignored) {
        }
        return count;
    }

    private boolean validateSnapshots() {
        if (!snapperEntries.isEmpty()) {
            return true;
        }
        if (!Utility.isDirectoryPresent("/.snapshots")) {
            Utility.exitWithError(Messages.get("error.snapshots-directory-is-missing"));
            return false;
        }
        if (!Utility.runCommand("btrfs subvolume show /.snapshots", false, false, false)) {
            Utility.exitWithError(Messages.get("error.snapshots-is-not-a-subvolume"));
            return false;
        }
        if (countNumericFoldersWithSnapshot() > 0) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }
            readList();
            if (snapperEntries.isEmpty() && countNumericFoldersWithSnapshot() > 0) {
                Utility.exitWithError(Messages.get("error.snapper-returned-an-empty-list"));
            }
        }
        Utility.warnMessage(Messages.get("warn.no-snapshots-available"));
        return false;
    }

    private void cleanupOpenSuseWritableCopyEntry() {
        SnapperEntry entry = snapperEntries.lastEntry().getValue();
        if (entry == null) {
            return;
        }
        // If the last snapper ID contains the description "writable copy of #<ID>", then it should be ignored.
        String description = entry.properties().get(SnapperProperty.DESCRIPTION.replace);
        if (description != null && description.startsWith("writable copy of #")) {
            snapperEntries.remove(snapperEntries.lastEntry().getKey());
            if (Objects.equals(addedEntry, entry)) {
                addedEntry = snapperEntries.lastEntry().getValue();
            }
        }
    }

    public HashMap<String, SnapperEntry> getEntries() {
        return snapperEntries;
    }

    public SnapperEntry lastEntry() {
        if (snapperEntries.isEmpty()) {
            return null;
        }
        return addedEntry;
    }
}
