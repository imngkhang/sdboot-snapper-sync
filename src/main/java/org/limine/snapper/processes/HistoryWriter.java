package org.limine.snapper.processes;

import org.limine.snapper.objects.Messages;
import org.limine.snapper.formats.json1.ImageDetail;
import org.limine.snapper.formats.json1.Manifest;
import org.limine.snapper.formats.json1.SnapshotEntry;
import org.limine.snapper.objects.Config;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;

public class HistoryWriter {

    /**
     * Save manifest.
     */
    public static void save(Manifest manifest, String filePath) {
        updateManifest(manifest);
        JsonMapper jsonMapper = new JsonMapper();
        // Asynchronous manifest backup (atomic on Linux FS, e.g., Btrfs)
        if (!Config.RESTORE_MODE && !Utility.areYouInSnapshot()) {
            Thread.startVirtualThread(() -> {
                try {
                    Utility.ensureDirectoryExists(Config.LSS_STATE_DIR);
                    Path backupTmp = Path.of(Config.LSS_STATE_DIR, Config.MANIFEST_FILE + ".tmp");
                    Path backupTarget = Path.of(Config.LSS_STATE_DIR, Config.MANIFEST_FILE);
                    jsonMapper.writerWithDefaultPrettyPrinter().writeValue(backupTmp.toFile(), manifest);
                    Files.move(backupTmp, backupTarget, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException ignored) {
                }
            });
        }
        // Write main manifest on FAT32 (atomic)
        Utility.ensurePathExists(filePath);
        Path tmpPath = Path.of(filePath + ".tmp");
        Path targetPath = Path.of(filePath);
        try {
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(tmpPath.toFile(), manifest);
            Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            Utility.infoMessage(Messages.get("info.saved"), filePath);
        } catch (IOException e) {
            Utility.errorMessage(Messages.get("error.failed-save-manifest"), e.getMessage());
        }
    }

    public static void backup(String manifestPath) {
        if (Utility.isFilePresent(manifestPath)) {
            // Auto time-based backup. Check if an older backup exists.
            String backupPath = manifestPath + Config.BACKUP_SUFFIX;
            boolean isBackupNeeded = true;
            if (Utility.isFilePresent(backupPath)) {
                isBackupNeeded = Utility.isFileOlderThan(backupPath, Config.CONFIG_BACKUP_THRESHOLD);
            }
            if (isBackupNeeded) {
                Path src = Path.of(manifestPath);
                Path dest = Path.of(backupPath);
                try {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    Utility.errorMessage(Messages.get("error.failed-backup-manifest"), e.getMessage());
                }
            }
        }
    }

    public static boolean updateManifest(Manifest manifest) {
        boolean isManifestChanged = false;
        if (!Config.MANIFEST_VERSION.equals(manifest.getVersion())) {
            manifest.setVersion(Config.MANIFEST_VERSION);
            isManifestChanged = true;
        }
        if (Config.FS_UUID != null && !Config.FS_UUID.equals(manifest.getUuid())) {
            // FS_UUID is changed based on the user-defined config.
            manifest.setUuid(Config.FS_UUID);
            isManifestChanged = true;
        }
        if (Config.RESTORE_MODE) {
            return isManifestChanged;
        }
        if (manifest.getUuid() == null || manifest.getUuid().isBlank() || !Utility.isPathPresent("/dev/disk/by-uuid/" + manifest.getUuid())) {
            if (Utility.areYouInBtrfs()) {
                manifest.setUuid(Utility.getFsUuid());
                isManifestChanged = true;
            }
        }
        String subvolumePath = manifest.getProperties().get("SUBVOLUME_PATH");
        if (Config.ROOT_SUBVOLUME_PATH != null && !Config.ROOT_SUBVOLUME_PATH.equals(subvolumePath)) {
            manifest.addProperty("SUBVOLUME_PATH", Config.ROOT_SUBVOLUME_PATH);
            isManifestChanged = true;
            //TODO auto update all cmdlines in the manifest
        }
        String snapshotsPath = manifest.getProperties().get("SNAPSHOTS_PATH");
        if (Config.ROOT_SNAPSHOTS_PATH != null && !Config.ROOT_SNAPSHOTS_PATH.equals(snapshotsPath)) {
            manifest.addProperty("SNAPSHOTS_PATH", Config.ROOT_SNAPSHOTS_PATH);
            isManifestChanged = true;
            //TODO auto update all cmdlines in the manifest
        }
        return isManifestChanged;
    }

    public static boolean cleanupUnusedFiles(Manifest manifest, Config config) {
        boolean unusedFilesFound = false;
        HashSet<String> neededFiles = new HashSet<>();
        for (SnapshotEntry entry : manifest.getSnapshotEntries()) {
            for (ImageDetail image : entry.allImages()) {
                neededFiles.add(image.fileHashName());
            }
        }
        String dirPath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER);
        List<String> directory = Utility.collectFilesFromDirectory(dirPath);
        for (String fileName : directory) {
            if ((Config.MANIFEST_FILE + Config.BACKUP_SUFFIX).startsWith(fileName)) {
                continue;
            }
            // Check if the file is unused
            if (!neededFiles.contains(fileName)) {
                Utility.deleteFile(Utility.buildPath(dirPath, fileName));
                unusedFilesFound = true;
            }
        }
        return unusedFilesFound;
    }
}
