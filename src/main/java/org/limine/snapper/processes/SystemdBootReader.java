package org.limine.snapper.processes;

import org.limine.snapper.formats.json1.*;
import org.limine.snapper.objects.Config;
import org.limine.snapper.objects.TreeNode;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Reads systemd-boot Type #1 entries and adapts their data for snapshot history. */
public class SystemdBootReader {
    public final boolean isSnapshotsInsideOsNode = true;
    public final TreeNode snapshotsNode;
    public final String entriesPath;
    public final TreeNode treeRoot;
    public final TreeNode targetOS;
    private final Config config;
    private final List<TreeNode> bootEntries = new ArrayList<>();

    public SystemdBootReader(Config config) { this(config, false); }

    public SystemdBootReader(Config config, boolean askUserIfOSnotFound) {
        this.config = config;
        this.entriesPath = Path.of(config.espPath(), Config.BOOT_ENTRIES_DIRECTORY).toString();
        this.treeRoot = new TreeNode("root", 0, null);
        String osName = Objects.requireNonNullElse(Config.TARGET_OS_NAME, Utility.getLinuxDistroName());
        Config.TARGET_OS_NAME = osName;
        this.targetOS = new TreeNode("/" + osName, 1, "root");
        this.treeRoot.addNode(targetOS);
        this.snapshotsNode = new TreeNode("//Snapshots", 2, targetOS.name);
        targetOS.addNode(snapshotsNode);
        loadEntries();
        if (bootEntries.isEmpty() && askUserIfOSnotFound) {
            Utility.warnMessage("No systemd-boot entry matched TARGET_OS_NAME", osName);
            String selectedTitle = Utility.userInput("Enter a title prefix matching your systemd-boot entry: ").trim();
            if (!selectedTitle.isEmpty()) {
                Config.TARGET_OS_NAME = selectedTitle;
                targetOS.name = "/" + selectedTitle;
                targetOS.cleanName = selectedTitle;
                loadEntries();
            }
        }
    }

    private void loadEntries() {
        Path entries = Path.of(config.espPath(), "loader", "entries");
        if (!Files.isDirectory(entries)) return;
        try (var paths = Files.list(entries)) {
            paths.filter(p -> p.getFileName().toString().endsWith(".conf")).sorted().forEach(path -> {
                try {
                    Map<String, List<String>> values = new LinkedHashMap<>();
                    for (String line : Files.readAllLines(path)) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                        int split = trimmed.indexOf(' ');
                        if (split < 1) continue;
                        values.computeIfAbsent(trimmed.substring(0, split), ignored -> new ArrayList<>())
                                .add(trimmed.substring(split + 1).trim());
                    }
                    String machineId = first(values, "machine-id");
                    String fileName = path.getFileName().toString();
                    String title = Objects.requireNonNullElse(first(values, "title"), fileName);
                    if (fileName.startsWith(config.machineID() + "-lss-snapshot-")
                            || isReservedEntry(title) || isEntryExcluded(title)) return;
                    boolean machineMatch = config.machineID().equals(machineId) || fileName.startsWith(config.machineID() + "-");
                    boolean titleMatch = Config.TARGET_OS_NAME != null
                            && title.toLowerCase(Locale.ROOT).startsWith(Config.TARGET_OS_NAME.toLowerCase(Locale.ROOT));
                    if (!machineMatch && !titleMatch) return;
                    TreeNode node = new TreeNode("/" + title, 2, targetOS.name);
                    String linux = first(values, "linux");
                    String efi = first(values, "efi");
                    String image = linux != null ? linux : efi;
                    if (linux != null) node.addConfigLine("linux: " + linux);
                    else if (efi != null) node.addConfigLine("efi: " + efi);
                    for (String initrd : values.getOrDefault("initrd", List.of())) node.addConfigLine("initrd: " + initrd);
                    String options = first(values, "options");
                    if (options != null) node.addConfigLine("options: " + options);
                    if (image != null) {
                        targetOS.addNode(node);
                        bootEntries.add(node);
                    }
                } catch (IOException e) {
                    Utility.warnMessage("Cannot read BLS entry", path.toString() + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            Utility.warnMessage("Cannot read systemd-boot entries", e.getMessage());
        }
    }

    private static String first(Map<String, List<String>> values, String key) {
        List<String> found = values.get(key);
        return found == null || found.isEmpty() ? null : found.getFirst();
    }

    public boolean isTargetOsNotFound() { return bootEntries.isEmpty(); }

    public SnapshotEntry createCurrentBootEntry(SnapperEntry snapper) {
        return new SnapshotEntry(snapper, createKernelEntries(snapper.snapshotID()), null);
    }

    public SnapshotEntry createCurrentBootEntry() {
        return createCurrentBootEntry(new SnapperEntry(0, null, null, null));
    }

    private List<KernelEntry> createKernelEntries(int snapshotId) {
        List<KernelEntry> result = new ArrayList<>();
        for (TreeNode entry : bootEntries) {
            KernelReader reader = new KernelReader(config);
            KernelEntry kernel = reader.createKernelEntry(entry, "", snapshotId, entry.configLines);
            if (reader.isBootConfigValid()) result.add(kernel);
        }
        return result;
    }


    public boolean isReservedEntry(String name) { return isSnapshotLikeEntry(name) || isBackupLikeEntry(name); }
    public boolean isSnapshotLikeEntry(String name) { return name != null && name.toLowerCase(Locale.ROOT).contains("snapshot"); }
    public boolean isBackupLikeEntry(String name) { return name != null && name.toLowerCase(Locale.ROOT).startsWith("backup"); }
    public boolean isEntryExcluded(String name) {
        for (String pattern : Config.EXCLUDE_SNAPSHOT_ENTRIES) if ("*".equals(pattern) || Utility.wildcardMatch(pattern, name)) return true;
        return false;
    }
}
