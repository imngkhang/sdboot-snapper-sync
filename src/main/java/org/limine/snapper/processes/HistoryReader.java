package org.limine.snapper.processes;

import org.limine.snapper.objects.Messages;
import org.limine.snapper.formats.json1.ImageDetail;
import org.limine.snapper.formats.json1.Manifest;
import org.limine.snapper.formats.json1.SnapperEntry;
import org.limine.snapper.formats.json1.SnapshotEntry;
import org.limine.snapper.formats.snapper.SnapperProperty;
import org.limine.snapper.objects.Config;
import org.limine.snapper.objects.ConsoleColor;
import org.limine.snapper.objects.Hash;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.function.Function;

public class HistoryReader {

    public static boolean isManifestChanged = false;

    public static Manifest load(String filePath) {
        String backup = Utility.buildPath(Config.LSS_STATE_DIR, Config.MANIFEST_FILE);
        String oldBackup = filePath + Config.BACKUP_SUFFIX;
        JsonMapper jsonMapper = JsonMapper.builder().enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();
        // Helper: load single file
        Function<String, Manifest> loadSingle = (path) -> {
            try {
                return jsonMapper.readValue(new File(path), Manifest.class);
            } catch (Exception e) {
                Utility.errorMessage(Messages.get("error.broken-manifest-detected"), e.getMessage());
                return null;
            }
        };
        // 1. Primary first
        boolean primaryExists = Utility.isFilePresent(filePath);
        if (primaryExists) {
            Manifest m = loadSingle.apply(filePath);
            if (m != null) {
                return m;
            }
        }
        // 2. Missing primary but backups exist?
        boolean backupExists = Utility.isFilePresent(backup);
        boolean oldBackupExists = Utility.isFilePresent(oldBackup);
        if (!primaryExists && (backupExists || oldBackupExists)) {
            Utility.errorMessage(Messages.get("error.primary-manifest-missing"), filePath);
        }
        // 3. Load backup
        if (backupExists) {
            Utility.errorMessage(Messages.get("error.loading-manifest-from-backup"), backup);
            Manifest m = loadSingle.apply(backup);
            if (m != null) {
                isManifestChanged = true;
                return m;
            }
        }
        // 4. Load old backup
        if (oldBackupExists) {
            Utility.errorMessage(Messages.get("error.loading-manifest-from-old-backup"), oldBackup);
            Manifest m = loadSingle.apply(oldBackup);
            if (m != null) {
                isManifestChanged = true;
                return m;
            }
        }
        // 5. Init (no error) when nothing exists
        if (!primaryExists && !backupExists && !oldBackupExists) {
            Utility.ensurePathExists(filePath);
            Manifest manifest = new Manifest();
            manifest.setVersion(Config.MANIFEST_VERSION);
            manifest.setLastSnapshotID(0);
            manifest.setUuid(null);
            manifest.setLastLocalTime(Config.DEFAULT_TIMESTAMP);
            manifest.setLastUTCTime(Config.DEFAULT_TIMESTAMP);
            return manifest;
        }
        // 6. All manifests broken
        Utility.exitWithError(Messages.get("error.all-existing-manifest-files-are"));
        return null;
    }

    public static LinkedHashSet<String> displaySnapshotList(Manifest manifest) {
        System.out.println();
        List<String[]> data = new ArrayList<>();
        LinkedHashSet<String> IDs = new LinkedHashSet<>();
        String headerID = Messages.get("label.table-id");
        String headerDate = Messages.get("label.table-date");
        String headerDescription = Messages.get("label.table-description");
        int columnWidth1 = headerID.length(), columnWidth2 = headerDate.length(), columnWidth3 = headerDescription.length();
        for (SnapshotEntry snapshot : manifest.getSnapshotEntries()) {
            SnapperEntry entry = snapshot.snapperEntry();
            String id = String.valueOf(entry.snapshotID());
            IDs.add(id);
            if (columnWidth1 < id.length()) {
                columnWidth1 = id.length();
            }
            if (columnWidth2 < entry.localTime().length()) {
                columnWidth2 = entry.localTime().length();
            }
            String description = entry.properties().get(SnapperProperty.DESCRIPTION.replace);
            if (description != null) {
                if (columnWidth3 < description.length()) {
                    columnWidth3 = description.length();
                }
            } else {
                description = "";
            }
            String[] line = new String[]{id, entry.localTime(), description};
            data.add(line);
        }
        // print a snapshots-table
        printTable(data, columnWidth1, columnWidth2, columnWidth3, headerID, headerDate, headerDescription);
        return IDs;
    }

    public static void displayInfo(Manifest manifest, Config config) {
        System.out.println();
        // Display a detail:
        printFormattedInfo(Messages.get("label.version"), Config.VERSION, true, ConsoleColor.GREEN_BOLD);
        printFormattedInfo(Messages.get("label.manifest-version"), manifest.getVersion(), true, ConsoleColor.GREEN_BOLD);
        String uuid = manifest.getUuid();
        if (uuid == null || uuid.isBlank()) {
            if (manifest.getSnapshotEntries().isEmpty()) {
                printFormattedInfo(Messages.get("label.fs-uuid"), "-", true, ConsoleColor.GREEN_BOLD);
            } else {
                printFormattedInfo(Messages.get("label.fs-uuid"), Messages.get("label.missing-uuid"), true, ConsoleColor.RED_BOLD);
            }
        } else {
            printFormattedInfo(Messages.get("label.fs-uuid"), manifest.getUuid(), true, ConsoleColor.GREEN_BOLD);
        }
        if (!Config.DEFAULT_TIMESTAMP.equals(manifest.getLastLocalTime())) {
            printFormattedInfo(Messages.get("label.last-snapshot"), Messages.get("label.id-date", manifest.getLastSnapshotID(), manifest.getLastLocalTime()), true, ConsoleColor.GREEN_BOLD);
        }
        if (!Config.EXCLUDE_SNAPSHOT_TYPES.isEmpty()) {
            printFormattedInfo(Messages.get("label.excluded-types"), Config.EXCLUDE_SNAPSHOT_TYPES.toString(), true, ConsoleColor.GREEN_BOLD);
        }
        if (!Config.EXCLUDE_SNAPSHOT_ENTRIES.isEmpty()) {
            printFormattedInfo(Messages.get("label.excluded-entries"), Config.EXCLUDE_SNAPSHOT_ENTRIES.toString(), true, ConsoleColor.GREEN_BOLD);
        }
        String snapshotsInfo = "" + manifest.getSnapshotEntries().size();
        if (0 <= Config.MAX_SNAPSHOT_ENTRIES && Config.MAX_SNAPSHOT_ENTRIES <= 10000) {
            snapshotsInfo += Messages.get("text.max", Config.MAX_SNAPSHOT_ENTRIES);
        } else if (Config.MAX_SNAPSHOT_ENTRIES == Config.AUTO_SNAPSHOT_LIMIT) {
            snapshotsInfo += Messages.get("text.max-auto");
        }
        if (manifest.getSnapshotEntries().size() < Config.MAX_SNAPSHOT_ENTRIES || Config.MAX_SNAPSHOT_ENTRIES == Config.AUTO_SNAPSHOT_LIMIT) {
            printFormattedInfo(Messages.get("label.snapshot-entries"), snapshotsInfo, true, ConsoleColor.GREEN_BOLD);
        } else {
            printFormattedInfo(Messages.get("label.snapshot-entries"), snapshotsInfo, true, ConsoleColor.YELLOW_BOLD);
        }
        double usagePercent = Utility.getUsagePercent(config.espPath());
        String usageInfo = Messages.get("text.of-max", usagePercent, Utility.getTotalSpace(config.espPath()), Config.LIMIT_USAGE_PERCENT);
        if (usagePercent + 10.0 < Config.LIMIT_USAGE_PERCENT) {
            printFormattedInfo(Messages.get("label.boot-usage"), usageInfo, true, ConsoleColor.GREEN_BOLD);
        } else if (usagePercent + 5.0 > Config.LIMIT_USAGE_PERCENT) {
            printFormattedInfo(Messages.get("label.boot-usage"), usageInfo, true, ConsoleColor.RED_BOLD);
        } else {
            printFormattedInfo(Messages.get("label.boot-usage"), usageInfo, true, ConsoleColor.YELLOW_BOLD);
        }
        verifyFiles(manifest, config);
        System.out.println();
    }

    public static void verifyFiles(Manifest manifest, Config config) {
        System.out.print(ConsoleColor.YELLOW + Messages.get("text.verifying-files") + ConsoleColor.RESET);
        List<String> unusedFiles = new ArrayList<>();
        List<String> missingFiles = new ArrayList<>();
        List<String> nonHashableFiles = new ArrayList<>();
        List<String> filesToVerify = new ArrayList<>();

        LinkedHashSet<String> neededFiles = new LinkedHashSet<>();
        for (SnapshotEntry entry : manifest.getSnapshotEntries()) {
            for (ImageDetail image : entry.allImages()) {
                neededFiles.add(image.fileHashName());
            }
        }

        String dirPath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER);
        List<String> directory = Utility.collectFilesFromDirectory(dirPath);
        var allFiles = new ArrayList<String>();
        var allFilesSizeBytes = 0L;

        for (String fileName : directory) {
            if ((Config.MANIFEST_FILE + Config.BACKUP_SUFFIX).startsWith(fileName)) {
                continue;
            }

            // Check if the file is unnecessary
            if (!neededFiles.contains(fileName)) {
                unusedFiles.add(fileName);
            }

            // Count all files and sum sizes
            allFiles.add(fileName);
            allFilesSizeBytes += new File(dirPath, fileName).length();
        }

        for (String file : neededFiles) {
            if (!directory.contains(file)) {
                missingFiles.add(file);
            } else if (Hash.isFileHashable(file)) {
                filesToVerify.add(file);
            } else {
                nonHashableFiles.add(file);
            }
        }

        // Multithreaded corruption check
        List<String> corruptedFiles = filesToVerify
                .parallelStream()
                .filter(file -> Hash.hasCorruption(Utility.buildPath(dirPath, file)))
                .toList();

        //Check which snapshots are affected by corrupted files
        var snapshotsWithCorruptedFiles = new LinkedHashSet<String>();

        //Check which snapshots are affected by missing files
        var snapshotsWithMissingFiles = new LinkedHashSet<String>();

        //Check which snapshots are affected by non-hashable files
        var snapshotsWithNonHashableFiles = new LinkedHashSet<String>();

        for (var snapshot : manifest.getSnapshotEntries()) {
            String snapshotId = String.valueOf(snapshot.snapperEntry().snapshotID());
            for (var image : snapshot.allImages()) {
                String fileWithHash = image.fileHashName();
                if (corruptedFiles.contains(fileWithHash)) {
                    snapshotsWithCorruptedFiles.add(snapshotId);
                }
                if (missingFiles.contains(fileWithHash)) {
                    snapshotsWithMissingFiles.add(snapshotId);
                }
                if (nonHashableFiles.contains(fileWithHash)) {
                    snapshotsWithNonHashableFiles.add(snapshotId);
                }
            }
        }

        // erase the current terminal line
        System.out.print("\r\u001B[K");
        printFormattedInfo(Messages.get("label.snapshot-files"), allFiles.size() + " (" + String.format(Locale.ROOT, "%.1f MiB", allFilesSizeBytes / (1024.0 * 1024.0)) + ")", true, ConsoleColor.GREEN_BOLD);

        if (unusedFiles.isEmpty()) {
            printFormattedInfo(Messages.get("label.unused-files"), "0", true, ConsoleColor.GREEN_BOLD);
        } else {
            printFormattedInfo(Messages.get("label.unused-files"), String.valueOf(unusedFiles.size()), true, ConsoleColor.YELLOW_BOLD);
            for (String file : unusedFiles) {
                printFormattedInfo("", file, false, ConsoleColor.YELLOW);
            }
        }

        if (missingFiles.isEmpty()) {
            printFormattedInfo(Messages.get("label.missing-files"), "0", true, ConsoleColor.GREEN_BOLD);
        } else {
            printFormattedInfo(Messages.get("label.missing-files"), String.valueOf(missingFiles.size()), true, ConsoleColor.RED_BOLD);
            for (String file : missingFiles) {
                printFormattedInfo("", file, false, ConsoleColor.RED);
            }
            printFormattedInfo(Messages.get("label.affected-snapshots"), String.join(", ", snapshotsWithMissingFiles), true, ConsoleColor.RED_BOLD);
        }

        if (corruptedFiles.isEmpty()) {
            printFormattedInfo(Messages.get("label.corrupted-files"), "0", true, ConsoleColor.GREEN_BOLD);
        } else {
            printFormattedInfo(Messages.get("label.corrupted-files"), String.valueOf(corruptedFiles.size()), true, ConsoleColor.RED_BOLD);
            for (String file : corruptedFiles) {
                printFormattedInfo("", file, false, ConsoleColor.RED);
            }
            printFormattedInfo(Messages.get("label.affected-snapshots"), String.join(", ", snapshotsWithCorruptedFiles), true, ConsoleColor.RED_BOLD);
        }

        if (!nonHashableFiles.isEmpty()) {
            printFormattedInfo(Messages.get("label.non-hashable-files"), String.valueOf(nonHashableFiles.size()), true, ConsoleColor.YELLOW_BOLD);
            for (String file : nonHashableFiles) {
                printFormattedInfo("", file, false, ConsoleColor.RED);
            }
            printFormattedInfo(Messages.get("label.affected-snapshots"), String.join(", ", snapshotsWithNonHashableFiles), true, ConsoleColor.YELLOW_BOLD);
        }

        if (!unusedFiles.isEmpty()) {
            System.out.println();
            boolean confirm = Utility.yesOrNo(Messages.get("prompt.cleanup-unused-files", unusedFiles.size()), false);
            if (confirm) {
                boolean allSuccess = true;
                for (String file : unusedFiles) {
                    String path = Utility.buildPath(dirPath, file);
                    if (!new File(path).delete()) {
                        allSuccess = false;
                        System.out.println(Messages.get("text.failed-delete", path));
                    }
                }
                if (allSuccess) {
                    System.out.println(Messages.get("text.cleaned-up"));
                }
            }
        }
    }

    private static void printFormattedInfo(String label, String value, boolean showColon, ConsoleColor color) {
        int labelWidth = 18; // Adjust this to the desired width
        String formattedLabel = String.format("%-" + labelWidth + "s", label);
        if (showColon) {
            System.out.println(color + formattedLabel + ": " + ConsoleColor.RESET + value);
        } else {
            System.out.println(color + formattedLabel + "  " + ConsoleColor.RESET + value);
        }
    }

    /**
     * Prints a snapper table with a nice format for the output in the terminal.
     */
    public static void printTable(List<String[]> data, int columnWidth1, int columnWidth2, int columnWidth3,
                                  String headerID, String headerDate, String headerDescription) {

        boolean unicode = Utility.isUnicodeSupported();
        String colSep = unicode ? "│" : "|";
        String rowColSep = unicode ? "┼" : "|";
        String rowSep = unicode ? "─" : "-";

        // Here you can define the width of the columns
        int[] columnWidths = {columnWidth1, columnWidth2, columnWidth3};

        // Print header
        String header = String.format(" %1$-" + columnWidths[0] + "s " + colSep + " %2$-" + columnWidths[1] + "s " + colSep + " %3$-" + columnWidths[2] + "s ", headerID, headerDate, headerDescription);
        String rowSeparator = rowSep.repeat(columnWidths[0] + 2) + rowColSep + rowSep.repeat(columnWidths[1] + 2) + rowColSep + rowSep.repeat(columnWidths[2] + 2);

        System.out.println(header);
        System.out.println(rowSeparator);

        // Print lines
        for (String[] row : data) {
            System.out.printf(" %1$-" + columnWidths[0] + "s " + colSep + " %2$-" + columnWidths[1] + "s " + colSep + " %3$-" + columnWidths[2] + "s %n", row[0], row[1], row[2]);
        }
    }

    public static void loadSubvolPaths(Manifest manifest) {
        // Set ROOT_SUBVOLUME_PATH if it is empty
        if (Config.ROOT_SUBVOLUME_PATH == null || Config.ROOT_SUBVOLUME_PATH.isBlank()) {
            String subvolumePath = manifest.getProperties().getOrDefault("SUBVOLUME_PATH", "");
            if (subvolumePath.isBlank() && !Config.USE_OPENSUSE_LAYOUT) {
                Utility.exitWithError(Messages.get("error.no-root_subvolume_path-please-set-root_subvolume_path", Config.DEFAULT_CONFIG_FILE_PATH));
            }
            Config.ROOT_SUBVOLUME_PATH = subvolumePath;
        }
        // Set ROOT_SNAPSHOTS_PATH if it is empty
        if (Config.ROOT_SNAPSHOTS_PATH == null || Config.ROOT_SNAPSHOTS_PATH.isBlank()) {
            String snapshotsPath = manifest.getProperties().getOrDefault("SNAPSHOTS_PATH", "");
            if (snapshotsPath.isBlank() && !Config.USE_OPENSUSE_LAYOUT) {
                Utility.exitWithError(Messages.get("error.no-root_snapshots_path-please-set-root_snapshots_path", Config.DEFAULT_CONFIG_FILE_PATH));
            }
            Config.ROOT_SNAPSHOTS_PATH = snapshotsPath;
        }
    }

}
