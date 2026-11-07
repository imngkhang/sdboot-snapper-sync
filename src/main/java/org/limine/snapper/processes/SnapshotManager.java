package org.limine.snapper.processes;

import org.limine.snapper.formats.json1.ImageDetail;
import org.limine.snapper.formats.json1.Manifest;
import org.limine.snapper.formats.json1.SnapperEntry;
import org.limine.snapper.formats.json1.SnapshotEntry;
import org.limine.snapper.formats.snapper.SnapperProperty;
import org.limine.snapper.objects.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class SnapshotManager {

    private final Config config;
    private boolean isUpdateNeeded;
    private boolean isSnapshotNew;

    public SnapshotManager(Config config) {
        this.config = config;
        isUpdateNeeded = false;
        isSnapshotNew = false;
    }

    /**
     * Check whether the last Snapper entry is the newest and is not yet in the boot entries.
     * If so, add it.
     * Clean outdated snapshot entries if they are not present in the Snapper list.
     */
    public void sync(Manifest manifest, SnapperReader snapper) {

        String lastUTCTime = manifest.getLastUTCTime();
        if (Utility.isTimeFormatNotValid(lastUTCTime)) {
            Utility.exitWithError(Messages.get("error.invalid-utc-time-format"), lastUTCTime);
            return;
        }

        // Delete outdated snapshot-entries from the manifest if they have already been deleted from the Snapper list.
        // Update snapshot entries if their description has been changed in the Snapper list.
        this.update(manifest, snapper.getEntries());

        SnapperEntry lastSnapperEntry = snapper.lastEntry();
        if (lastSnapperEntry == null) {
            isSnapshotNew = false;
            return;
        }

        int result = Utility.compareTimestamps(lastUTCTime, lastSnapperEntry.utcTime());
        if (result < 0) {
            // lastUTCTime is before lastSnapperEntry.utcTime
            isSnapshotNew = true;
        } else if (result > 0) {
            // lastUTCTime is after lastSnapperEntry.utcTime
            isSnapshotNew = false;
        }
        if (result == 0 || !isSnapshotNew) {
            // lastUTCTime is equal to lastSnapperEntry.utcTime, then check both IDs
            isSnapshotNew = manifest.getLastSnapshotID() < lastSnapperEntry.snapshotID();
        }

        // Snapshot type filter
        String lastSnapshotType = lastSnapperEntry.properties().get(SnapperProperty.TYPE.replace);
        if (lastSnapshotType != null && Config.EXCLUDE_SNAPSHOT_TYPES.contains(lastSnapshotType)) {
            isSnapshotNew = false;
        }

        if (Config.MAX_SNAPSHOT_ENTRIES == Config.AUTO_SNAPSHOT_LIMIT) {
            // Auto-prune older snapshot boot entries according to LIMIT_USAGE_PERCENT.
            autoPruneSnapshotEntries(manifest, Utility.calculatePartitionUsage(Config.LIMIT_USAGE_PERCENT, config.espPath(), 1));
        } else {
            // Prune snapshot entries according to MAX_SNAPSHOT_ENTRIES.
            pruneSnapshotEntries(manifest, isSnapshotNew, snapper.getEntries().size());
        }
    }

    public PartitionUsage autoPruneSnapshotEntries(Manifest manifest, PartitionUsage bootUsage) {
        if (bootUsage == null) {
            bootUsage = Utility.calculatePartitionUsage(Config.LIMIT_USAGE_PERCENT, config.espPath(), 1);
        }
        if (bootUsage != null && bootUsage.exceedsLimit() && HistoryWriter.cleanupUnusedFiles(manifest, config)) {
            bootUsage = Utility.calculatePartitionUsage(Config.LIMIT_USAGE_PERCENT, config.espPath(), bootUsage.deltaBytes());
        }
        if (bootUsage != null && bootUsage.exceedsLimit()) {
            if (manifest.getSnapshotEntries().isEmpty()) {
                Utility.errorMessage(Messages.get("error.boot-usage-limit-reached"), Messages.get("error.no-snapshot-entries-but-limit_usage_percent", Config.LIMIT_USAGE_PERCENT));
                return bootUsage;
            } else {
                Utility.warnMessage(Messages.get("error.boot-usage-limit-reached"), Messages.get("warn.auto-pruning-older-snapshot-entries", Config.LIMIT_USAGE_PERCENT));
            }
        }
        while (bootUsage != null && !manifest.getSnapshotEntries().isEmpty() && bootUsage.exceedsLimit()) {
            HashSet<ImageDetail> imageFiles = manifest.getSnapshotEntries().getFirst().allImages();
            manifest.getSnapshotEntries().removeFirst();
            deleteImageFiles(manifest.getSnapshotEntries(), imageFiles);
            isUpdateNeeded = true;
            bootUsage = Utility.calculatePartitionUsage(Config.LIMIT_USAGE_PERCENT, config.espPath(), bootUsage.deltaBytes());
        }
        return bootUsage;
    }

    private void pruneSnapshotEntries(Manifest manifest, boolean isSnapshotNew, int total) {
        if (Config.MAX_SNAPSHOT_ENTRIES < 0) {
            return;
        }
        boolean warningVisible = Config.MAX_SNAPSHOT_ENTRIES < total;
        if (warningVisible) {
            Utility.warnMessage(Messages.get("warn.snapshot-limit-mismatch"), Messages.get("warn.snapper-snapshots-exceed-configured-max_snapshot_entries", total, Config.MAX_SNAPSHOT_ENTRIES));
            Utility.infoMessage(Messages.get("info.to-remove-warning-increase-max_snapshot_entries"));
        }
        int snapshotCount = manifest.getSnapshotEntries().size() + (isSnapshotNew ? 1 : 0);
        int deleteCount = snapshotCount - Config.MAX_SNAPSHOT_ENTRIES;
        if (deleteCount <= 0) {
            return;
        }
        if (warningVisible) {
            Utility.warnMessage(Messages.get("warn.snapshot-limit-reached"), Messages.get("warn.auto-pruning-older-snapshot-entries-2", Config.MAX_SNAPSHOT_ENTRIES));
        }
        HashSet<ImageDetail> outdatedImageFiles = new HashSet<>();
        while (deleteCount > 0) {
            SnapshotEntry limineSnapshot = manifest.getSnapshotEntries().getFirst();
            outdatedImageFiles.addAll(limineSnapshot.allImages());
            manifest.getSnapshotEntries().removeFirst();
            deleteCount--;
        }
        this.isUpdateNeeded = true;
        this.deleteImageFiles(manifest.getSnapshotEntries(), outdatedImageFiles);
    }

    private void update(Manifest manifest, HashMap<String, SnapperEntry> snapperList) {
        Iterator<SnapshotEntry> iterator = manifest.getSnapshotEntries().iterator();
        HashSet<ImageDetail> outdatedImageFiles = new HashSet<>();
        boolean shouldFilesBeDeleted = false;
        while (iterator.hasNext()) {
            SnapshotEntry limineSnapshot = iterator.next();
            SnapperEntry snapperEntry = snapperList.get(limineSnapshot.snapperEntry().timeId());
            if (snapperEntry == null) {
                outdatedImageFiles.addAll(limineSnapshot.allImages());
                iterator.remove();
                this.isUpdateNeeded = true;
                shouldFilesBeDeleted = true;
            } else {
                // Update the boot snapshot if its Snapper description has changed.
                String oldDescription = limineSnapshot.snapperEntry().properties().get(SnapperProperty.DESCRIPTION.replace);
                String newDescription = snapperEntry.properties().get(SnapperProperty.DESCRIPTION.replace);
                if (!Objects.equals(oldDescription, newDescription)) {
                    limineSnapshot.snapperEntry().properties().put(SnapperProperty.DESCRIPTION.replace, newDescription);
                    this.isUpdateNeeded = true;
                }
            }
        }
        if (shouldFilesBeDeleted) {
            this.deleteImageFiles(manifest.getSnapshotEntries(), outdatedImageFiles);
        }
    }

    public HashSet<FileDetail> remove(Manifest manifest, HashSet<Integer> ids) {
        HashSet<ImageDetail> outdatedFiles = new HashSet<>();
        Iterator<SnapshotEntry> iterator = manifest.getSnapshotEntries().iterator();

        while (iterator.hasNext()) {
            SnapshotEntry snapshotEntry = iterator.next();
            Integer id = snapshotEntry.snapperEntry().snapshotID();
            if (id != null && ids.contains(id)) {
                outdatedFiles.addAll(snapshotEntry.allImages());
                iterator.remove();
            }
        }
        if (outdatedFiles.isEmpty()) {
            return new HashSet<>();
        } else {
            // The manifest should be updated after this change!
            isUpdateNeeded = true;
            return deleteImageFiles(manifest.getSnapshotEntries(), outdatedFiles);
        }
    }

    private HashSet<FileDetail> deleteImageFiles(List<SnapshotEntry> limineSnapshots, HashSet<ImageDetail> outdatedImageFiles) {
        HashSet<FileDetail> deletedFiles = new LinkedHashSet<>();
        // Collect all files from all current snapshots after you have deleted some snapshots.
        HashSet<String> allImageFiles = this.getCurrentImageFiles(limineSnapshots);
        // Deletion of outdated files if they are not used by all snapshots.
        String basePath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER) + File.separator;
        for (ImageDetail imageDetail : outdatedImageFiles) {
            if (!allImageFiles.contains(imageDetail.fileHashName())) {
                // Run a command to delete /<esp>/<machine-id>/limine_history/<some file>
                String targetHashFile = Utility.buildPath(basePath, imageDetail.fileHashName());
                FileDetail fileDetail = FileDetail.create(imageDetail.fileHashName(), basePath);
                if (fileDetail != null) {
                    deletedFiles.add(fileDetail);
                }
                Utility.deleteFile(targetHashFile);
            }
        }
        return deletedFiles;
    }

    private HashSet<String> getCurrentImageFiles(List<SnapshotEntry> limineSnapshots) {
        HashSet<String> allImageFiles = new HashSet<>();
        for (SnapshotEntry snapshotEntry : limineSnapshots) {
            for (ImageDetail imageDetail : snapshotEntry.allImages()) {
                allImageFiles.add(imageDetail.fileHashName());
            }
        }
        return allImageFiles;
    }

    /**
     * Copy files to the history folder with a deduplication function.
     * The manifest adds the last snapshot.
     */
    public void historyAddSnapshot(Manifest manifest, SnapshotEntry lastSnapshotEntry) {
        if (lastSnapshotEntry.kernelEntries().isEmpty()) {
            if (!Config.RESTORE_MODE) {
                // The current snapshot entry has no kernel entry, so it cannot be added to the boot menu.
                Utility.errorMessage(Messages.get("error.cannot-create-snapshot-boot-entry"), Messages.get("error.no-valid-kernel-entry-found"));
            }
            return;
        }
        // Copy or move any image files to the boot history directory.
        for (ImageDetail imageDetail : lastSnapshotEntry.allImages()) {
            if (Config.RESTORE_MODE && imageDetail.fileHashName().endsWith(Config.NO_FILE_NAME)) {
                // There is no image file. No need to back up this file during the restore.
                continue;
            }
            String sourceFilePath = Utility.buildPath(config.espPath(), imageDetail.dirPath(), imageDetail.fileName());
            String targetFilePath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER, imageDetail.fileHashName());
            if (Utility.isFilePresent(targetFilePath)) {
                this.repairIfCorrupted(sourceFilePath, targetFilePath);
                continue;
            }
            isUpdateNeeded = true;
            if (Config.RESTORE_MODE) {
                // Move the image file to the boot history directory
                Utility.moveFile(sourceFilePath, targetFilePath);
            } else {
                // Copy the image file to the boot history directory
                Utility.copyFile(sourceFilePath, targetFilePath);
            }
        }
        manifest.getSnapshotEntries().add(lastSnapshotEntry);
        updateManifest(manifest, lastSnapshotEntry.snapperEntry());
    }

    public void updateManifest(Manifest manifest, SnapperEntry lastSnapperEntry) {
        manifest.setLastLocalTime(lastSnapperEntry.localTime());
        manifest.setLastUTCTime(lastSnapperEntry.utcTime());
        manifest.setLastSnapshotID(lastSnapperEntry.snapshotID());
    }

    private SnapshotEntry getSnapshotEntry(Manifest manifest, int snapshotID) {
        SnapshotEntry snapshotEntry = null;
        for (SnapshotEntry entry : manifest.getSnapshotEntries()) {
            if (entry.snapperEntry().snapshotID() == snapshotID) {
                snapshotEntry = entry;
                break;
            }
        }
        return snapshotEntry;
    }

    /**
     * User is prompted to confirm the snapshot ID or select a different snapshot ID for restoring.
     */
    private SnapshotEntry confirmOrSelectSnapshot(Manifest manifest) {
        int snapshotID = Utility.getSnapshotIdFromKernelCmdline();
        // Check the snapshot ID if it is present in the manifest.
        SnapshotEntry snapshotEntry = getSnapshotEntry(manifest, snapshotID);
        if (snapshotEntry == null) {
            snapshotID = -1;
        } else {
            System.out.println(ConsoleColor.GREEN_BOLD + Messages.get("text.snapshot-id") + ConsoleColor.RESET + snapshotID);
            System.out.println(ConsoleColor.GREEN_BOLD + Messages.get("text.date") + ConsoleColor.RESET + snapshotEntry.snapperEntry().localTime());
            System.out.println(ConsoleColor.GREEN_BOLD + Messages.get("text.description") + ConsoleColor.RESET + snapshotEntry.snapperEntry().properties().get(SnapperProperty.DESCRIPTION.replace));
            System.out.println(ConsoleColor.GREEN_BOLD + Messages.get("text.restore-method") + ConsoleColor.RESET + Config.RESTORE_METHOD.name().toLowerCase());
            if (!Config.EXCLUDE_SNAPSHOT_ENTRIES.isEmpty()) {
                System.out.println(ConsoleColor.GREEN_BOLD + Messages.get("text.excluded-entries") + ConsoleColor.RESET + Config.EXCLUDE_SNAPSHOT_ENTRIES);
            }
        }

        if (snapshotID > 0) {
            // Ask a user to confirm to restore this snapshot?
            boolean isConfirmed = Utility.askUser(Messages.get("prompt.restore-snapshot-method-r-restore", snapshotID, Config.RESTORE_METHOD.name().toLowerCase()), Messages.get("prompt.choice-r-l-c"), Messages.get("prompt.restore"), Messages.get("prompt.list"), Messages.get("prompt.cancel"));

            if (!isConfirmed) {
                snapshotID = selectSnapshotByUser(manifest);
            }
        } else {
            // Ask the user to select which snapshot ID for restore.
            snapshotID = selectSnapshotByUser(manifest);
        }
        snapshotEntry = getSnapshotEntry(manifest, snapshotID);
        if (snapshotEntry == null) {
            String errMessage = Messages.get("text.no-snapshot-is-found", snapshotID);
            Utility.errorMessage(errMessage);
            return null;
        }
        return snapshotEntry;
    }

    /**
     * Check custom selected snapshot ID for restoring.
     */
    private SnapshotEntry checkCustomSnapshotID(Manifest manifest, String customID) {
        int snapshotID;
        try {
            snapshotID = Integer.parseInt(customID);
        } catch (NumberFormatException e) {
            Utility.errorMessage(Messages.get("error.invalid-id-must-be-numeric", customID));
            return null;
        }
        // Check the snapshot ID if it is present in the manifest.
        SnapshotEntry snapshotEntry = getSnapshotEntry(manifest, snapshotID);

        if (snapshotEntry == null) {
            Utility.errorMessage(Messages.get("error.snapshot-id-not-found-limine", customID));
            return null;
        } else {
            return snapshotEntry;
        }
    }


    public RestoreContext preRestoreKernels(Manifest manifest, SystemdBootReader limine, String customID) {
        SnapshotEntry snapshotEntry = checkCustomSnapshotID(manifest, customID);
        if (snapshotEntry == null) {
            return null;
        } else {
            SnapshotEntry targetEntry = limine.createCurrentBootEntry();
            boolean isBackupNeeded = false;
            // Predict if there is enough partition space when adding new files?
            PartitionUsage usage = predictSpaceUsage(targetEntry, snapshotEntry, isBackupNeeded);
            if (usage.exceedsLimit()) {
                Utility.errorMessage(Messages.get("error.stopping-restore"), Messages.get("error.not-enough-space-boot-partition", usage.usedPercent(), usage.newPercent(), usage.deltaMiB()));
                return null;
            }
            verifySnapshotFiles(snapshotEntry, true, List.of());
            return new RestoreContext(snapshotEntry, targetEntry, "", isBackupNeeded, true);
        }
    }

    public RestoreContext preRestore(Manifest manifest, SystemdBootReader limine) {
        SnapshotEntry snapshotEntry = confirmOrSelectSnapshot(manifest);
        if (snapshotEntry == null) {
            return null;
        }
        SnapshotEntry targetEntry = limine.createCurrentBootEntry();
        boolean isBackupNeeded = true;
        // Predict if there is enough partition space when adding new files?
        PartitionUsage usage = predictSpaceUsage(targetEntry, snapshotEntry, isBackupNeeded);
        if (usage.exceedsLimit()) {
            Utility.errorMessage(Messages.get("error.stopping-restore"), Messages.get("error.not-enough-space-boot-partition", usage.usedPercent(), usage.newPercent(), usage.deltaMiB()));
            return null;
        }

        if (!verifySnapshotFiles(snapshotEntry, false, manifest.getSnapshotEntries())) {
            if (Utility.askUser(Messages.get("prompt.select-snapshot-action"),
                    Messages.get("prompt.choice-s-r-c"),
                    Messages.get("prompt.select"), Messages.get("prompt.restore"), Messages.get("prompt.cancel"))
            ) {
                return preRestore(manifest, limine);
            }
        }

        if (Config.USE_OPENSUSE_LAYOUT) {
            // Checking if OpenSUSE-style layout is valid and compatible with snapper rollback
            return new RestoreContext(snapshotEntry, targetEntry, "", isBackupNeeded, Utility.checkOpenSuseLayout());
        } else {
            if (RestoreMethod.RSYNC == Config.RESTORE_METHOD) {
                if (!Utility.isCommandPresent("rsync")) {
                    String errMessage = Messages.get("text.command-rsync-is-not-installed");
                    Utility.errorMessage(errMessage);
                    return null;
                }
            }
            String subvolumePath = Utility.removeSurroundingSlashes(Config.ROOT_SUBVOLUME_PATH);
            if (subvolumePath == null) {
                Utility.errorMessage(Messages.get("error.bug-no-subvolume-path"));
                return null;
            }
            if (RestoreMethod.REPLACE == Config.RESTORE_METHOD && subvolumePath.isEmpty()) {
                String errMessage = Messages.get("error.no-root-subvolume-detected");
                Utility.errorMessage(errMessage);
                return null;
            }

            String backupDescription = Utility.userInput(Messages.get("prompt.backup-subvolume-description", subvolumePath));
            if (backupDescription.isBlank()) {
                backupDescription = Messages.get("text.backup-before-restore", snapshotEntry.snapperEntry().snapshotID());
            }
            return new RestoreContext(snapshotEntry, targetEntry, backupDescription, isBackupNeeded, true);
        }
    }

    /**
     * Restoration process
     */
    public SnapshotEntry restore(Manifest manifest, SystemdBootReader limine, RestoreContext restoreContext) {
        int snapshotID = restoreContext.snapshotEntry().snapperEntry().snapshotID();

        String subvolumePath = Utility.removeSurroundingSlashes(Config.ROOT_SUBVOLUME_PATH);
        String snapshotsPath = Utility.removeSurroundingSlashes(Config.ROOT_SNAPSHOTS_PATH);

        String mountDir = "/run/lss/";
        String timestamp = Utility.getCurrentIsoFormattedTime();
        String backupSubvolumePath = mountDir + subvolumePath + "_backup_" + timestamp;
        String newSubvolumePath = mountDir + subvolumePath + "_lss_" + timestamp;
        String fullSubvolumePath = mountDir + subvolumePath;
        String fullSnapshotsPath = mountDir + snapshotsPath;

        if (subvolumePath == null) {
            Utility.errorMessage(Messages.get("error.bug-no-subvolume-path"));
            return null;
        }
        if (snapshotsPath == null) {
            Utility.errorMessage(Messages.get("error.bug-no-snapshots-path"));
            return null;
        }
        String fsUuid;
        if (Config.FS_UUID != null) {
            fsUuid = Config.FS_UUID;
        } else {
            fsUuid = manifest.getUuid();
        }
        if (fsUuid == null || fsUuid.isBlank()) {
            Utility.errorMessage(Messages.get("error.no-fs_uuid-is-set-please", Config.DEFAULT_CONFIG_FILE_PATH, Config.TMP_CONFIG_FILE_PATH));
            return null;
        }
        if (!Utility.isPathPresent("/dev/disk/by-uuid/" + fsUuid)) {
            Utility.errorMessage(Messages.get("error.invalid-fs_uuid"), Messages.get("error.does-not-exist-dev-disk", fsUuid));
            return null;
        }

        // mount /run/lss
        List<String> commands = List.of(
                "mount -t btrfs -o subvol=/ /dev/disk/by-uuid/" + fsUuid + " " + mountDir
        );

        // Time to start mount.
        Utility.infoMessage(Messages.get("info.mounting-btrfs"));
        if (!Utility.ensureDirectoryExists(mountDir)) {
            return null;
        }
        if (!Utility.runCommands(commands, true, true, true, true)) {
            Utility.errorMessage(Messages.get("error.mount-failed"), mountDir);
            return null;
        }
        if (!Utility.areYouInBtrfs(mountDir)) {
            Utility.errorMessage(Messages.get("error.invalid-fs_uuid"), Messages.get("error.is-not-btrfs", fsUuid));
            return null;
        }
        if (!Utility.isDirectoryPresent(fullSubvolumePath)) {
            Utility.errorMessage(Messages.get("error.invalid-root_subvolume_path"), Messages.get("error.not-found", subvolumePath, mountDir));
            return null;
        }
        if (!Utility.isDirectoryPresent(fullSnapshotsPath)) {
            Utility.errorMessage(Messages.get("error.invalid-root_snapshots_path"), Messages.get("error.not-found", snapshotsPath, mountDir));
            return null;
        }
        // Find all child subvolumes of the parent subvolume. This is important because rsync should exclude them.
        // For Btrfs restore, they should be moved back to the parent subvolume after the restore.
        List<String> childSubvolumePaths = new ArrayList<>();
        // Get all child subvolume-paths
        String btrfsCommand = "btrfs subvolume list -o '" + fullSubvolumePath + "'";
        Output subvolumePathsOutput = Utility.getCommandOutput(btrfsCommand, true, null);
        if (subvolumePathsOutput.isSuccess()) {
            for (String fullLine : subvolumePathsOutput.text()) {
                String path = "";
                int index = fullLine.indexOf(subvolumePath + File.separator);
                if (index != -1) {
                    path = fullLine.substring(index + subvolumePath.length() + 1);
                }
                if (!path.isEmpty()) {
                    childSubvolumePaths.add(path);
                } else if (!fullLine.isBlank()) {
                    String errMessage = Messages.get("text.the-output-format-has-changed", btrfsCommand);
                    Utility.errorMessage(errMessage);
                    return null;
                }
            }
        }

        boolean isSnapshot = Utility.isDirectoryPresent(Utility.buildPath(fullSnapshotsPath, snapshotID + "/snapshot/"));
        if (!isSnapshot) {
            Utility.errorMessage(Messages.get("error.unsupported-btrfs-layout-snapper-managed"));
            return null;
        }

        String fullSnapshotIDPath = Utility.buildPath(fullSnapshotsPath, snapshotID + "/snapshot");
        if (Config.RESTORE_METHOD == RestoreMethod.RSYNC) {
            // Rsync instead Btrfs restore
            StringBuilder excludes = new StringBuilder();
            if (!childSubvolumePaths.isEmpty()) {
                excludes.append(" --exclude={");
                for (int i = 0; i < childSubvolumePaths.size(); i++) {
                    excludes.append("'/").append(childSubvolumePaths.get(i)).append("'");
                    if (i < childSubvolumePaths.size() - 1) {
                        excludes.append(",");
                    }
                }
                excludes.append("}");
            }
            String rsyncCmdline = Config.RSYNC_CMDLINE + excludes + " '" + fullSnapshotIDPath + "/' '" + fullSubvolumePath + "/'";
            commands = List.of(
                    // Bugfix: Do not use the "-r" read-only option as it denies moving the snapshot to the Snapper list.
                    "btrfs subvolume snapshot '" + fullSubvolumePath + "' '" + backupSubvolumePath + "'",
                    rsyncCmdline
            );
            // Check if the subvolume is read-only
            if (isSubvolumeReadOnly(fullSubvolumePath)) {
                Utility.warnMessage(Messages.get("warn.warning"), Messages.get("warn.the-subvolume-is-read-only", subvolumePath));
                return null;
            }
        } else {
            // Restore the snapshot in any (default or custom) Snapper layout.
            commands = List.of(
                    "btrfs subvolume snapshot '" + fullSnapshotIDPath + "' '" + newSubvolumePath + "'",
                    "mv '" + fullSubvolumePath + "' '" + backupSubvolumePath + "'",
                    "mv '" + newSubvolumePath + "' '" + fullSubvolumePath + "'"
            );

            // Set the subvolume to writable before restoration.
            if (isSubvolumeReadOnly(fullSubvolumePath)) {
                Utility.warnMessage(Messages.get("warn.warning"), Messages.get("warn.the-subvolume-is-read-only-2", subvolumePath));
                Utility.setSubvolumeProperty(fullSubvolumePath, false);
            }
        }

        // Time to start to restore.
        Utility.infoMessage(Messages.get("info.restoring-snapshot", snapshotID));
        if (!Utility.runCommands(commands, true, true, true, true)) {
            if (RestoreMethod.REPLACE == Config.RESTORE_METHOD) {
                // The restore fails, then "backup" subvolume should be moved back to the correct location.
                if (Utility.isDirectoryPresent(backupSubvolumePath)) {
                    List<String> revert;
                    if (Utility.isDirectoryPresent(fullSubvolumePath)) {
                        revert = List.of(
                                "mv '" + fullSubvolumePath + "' '" + newSubvolumePath + "'",
                                "mv '" + backupSubvolumePath + "' '" + fullSubvolumePath + "'"
                        );
                    } else {
                        revert = List.of(
                                "mv '" + backupSubvolumePath + "' '" + fullSubvolumePath + "'"
                        );
                    }
                    Utility.runCommands(revert, true, true, true, true);
                }
            }
            String errMessage = Messages.get("text.failed-restore");
            Utility.errorMessage(errMessage);
            return null;
        }

        if (RestoreMethod.REPLACE == Config.RESTORE_METHOD) {
            if (Config.SET_SNAPSHOT_AS_DEFAULT) {
                String setDefaultSubvolumeCommand = "btrfs subvolume set-default '" + fullSubvolumePath + "'";
                Utility.runCommand(setDefaultSubvolumeCommand, true, true, true);
            }
            // If the parent backup-subvolume has multiple child subvolumes,
            // they should be moved back to the right subvolume.
            List<String> movedChildSubvolumeCommands = new ArrayList<>();
            for (String childSubvolumePath : childSubvolumePaths) {
                String sourceDirPath = Utility.buildPath(backupSubvolumePath, childSubvolumePath);
                String extractDirectoryPath = Utility.extractParentDirectoryPath(childSubvolumePath);
                String targetDirPath = Utility.buildPath(fullSubvolumePath, extractDirectoryPath) + File.separator;
                movedChildSubvolumeCommands.add("mv '" + sourceDirPath + "' '" + targetDirPath + "'");
            }

            if (!movedChildSubvolumeCommands.isEmpty()) {
                // Time to move all child subvolumes back.
                Utility.infoMessage(Messages.get("info.moving-child-subvolumes-parent-subvolume", subvolumePath));
                if (!Utility.runCommands(movedChildSubvolumeCommands, false, true, true, true)) {
                    String errMessage = Messages.get("text.failed-moved-child-subvolumes");
                    Utility.errorMessage(errMessage);
                }
            }
        }
        // Move the backup subvolume to the Snapper list, move its kernel files to the boot history, and then update the manifest.
        if (restoreContext.isBackupNeeded()) {
            Utility.infoMessage(Messages.get("info.adding-entry-to-snapper-list", restoreContext.backupDescription()));
            int latestSnapshotID = Utility.findLatestSnapperNumber(fullSnapshotsPath);
            if (latestSnapshotID < 1) {
                Utility.errorMessage(Messages.get("error.no-snapshot-found-snapper-list"));
            } else {
                if (Utility.areYouInSnapshot()) {
                    // You are in the snapshot without the conflict with the Snapper.
                    latestSnapshotID = latestSnapshotID + 1;
                } else {
                    // You are not in the snapshot.
                    // If the tool and the Snapper create the same ID at the same time, this leads to a conflict.
                    // Solution: Another higher ID is created, probably to avoid the conflict with the Snapper.
                    latestSnapshotID = latestSnapshotID + 2;
                }

                String newSnapshotIDPath = Utility.buildPath(fullSnapshotsPath, String.valueOf(latestSnapshotID));
                Utility.ensureDirectoryExists(newSnapshotIDPath);

                List<String> moveSubvolumeToSnapper = List.of(
                        "mv '" + backupSubvolumePath + "' '" + newSnapshotIDPath + "/snapshot'"
                );
                if (Utility.runCommands(moveSubvolumeToSnapper, false, true, true, true)) {
                    Utility.setSubvolumeProperty("'" + newSnapshotIDPath + "/snapshot'", !Config.SNAPSHOT_WRITABLE);

                    // Create a new info.xml for this snapshot in the Snapper list.
                    String utcTime = Utility.getCurrentUTCTime();
                    SnapperWriter.writeInfoXml(latestSnapshotID, utcTime, restoreContext.backupDescription(), newSnapshotIDPath + "/info.xml");

                    // Create a new backup (snapshot) entry with the "broken" kernel versions
                    String localTime = Utility.convertUTCtoLocal(utcTime);
                    SnapperEntry snapperEntry = new SnapperEntry(latestSnapshotID, utcTime, localTime, null);
                    snapperEntry.properties().put(SnapperProperty.DESCRIPTION.replace, restoreContext.backupDescription());
                    SnapshotEntry backupEntry = limine.createCurrentBootEntry(snapperEntry);

                    // Move the "broken" kernel files to the boot history directory.
                    // snapshots.json (the manifest) adds the backup entry.
                    this.historyAddSnapshot(manifest, backupEntry);

                } else {
                    String errMessage = Messages.get("error.failed-to-move-backup-snapshot");
                    Utility.errorMessage(errMessage);
                }
            }
        }
        // Time to copy the matching kernel-files of this snapshot to their right place.
        Utility.infoMessage(Messages.get("info.restoring-matching-kernel-versions"));
        return restoreKernels(restoreContext);
    }

    public SnapshotEntry restoreKernels(RestoreContext restoreContext) {
        SnapshotEntry targetEntry = restoreContext.targetEntry();
        SnapshotEntry snapshotEntry = restoreContext.snapshotEntry();
        // Phase 1: determine and delete unused kernel files
        Set<ImageDetail> unusedImages = new HashSet<>(targetEntry.allImages());
        unusedImages.removeAll(snapshotEntry.allImages());
        for (ImageDetail image : unusedImages) {
            if (image.fileHashName().endsWith(Config.NO_FILE_NAME)) {
                continue;
            }
            String sourceFilePath = Utility.buildPath(config.espPath(), image.dirPath(), image.fileName());
            if (Utility.isFilePresent(sourceFilePath)) {
                String backupFilePath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER, image.fileHashName());
                repairIfCorrupted(sourceFilePath, backupFilePath);
                Utility.deleteFile(sourceFilePath);
            }
        }
        // Phase 2: restore kernel files
        for (ImageDetail image : snapshotEntry.allImages()) {
            if (image.fileHashName().endsWith(Config.NO_FILE_NAME)) {
                continue;
            }
            String sourceFilePath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER, image.fileHashName());
            String targetFilePath = Utility.buildPath(config.espPath(), image.dirPath(), image.fileName());
            Utility.ensurePathExists(targetFilePath);
            boolean shouldReplace = !Utility.isFilePresent(targetFilePath) || !image.fileHashName().endsWith(Utility.calculateHash(targetFilePath));
            if (shouldReplace) {
                Utility.copyFile(sourceFilePath, targetFilePath);
            }
        }
        // Phase 3: Replace current kernel entries with the restored snapshot entries.
        targetEntry.kernelEntries().clear();
        targetEntry.kernelEntries().addAll(snapshotEntry.kernelEntries());
        return targetEntry;
    }

    private void repairIfCorrupted(String sourceFilePath, String targetFilePath) {
        if (Utility.isFilePresent(targetFilePath)) {
            // Check if the target file in any of the snapshots is corrupted.
            String hashCode = Utility.calculateHash(targetFilePath);
            if (!targetFilePath.endsWith(hashCode)) {
                // The snapshot file is corrupted.
                // Replace the corrupted file with a healthy one.
                Utility.copyFile(sourceFilePath, targetFilePath);
                Utility.errorMessage(Messages.get("error.corrupted-file-detected-repaired"), targetFilePath);
            }
        }
    }

    private int selectSnapshotByUser(Manifest manifest) {
        LinkedHashSet<String> IDs = HistoryReader.displaySnapshotList(manifest);
        boolean unicode = Utility.isUnicodeSupported();
        String upOrDown = unicode ? "↑/↓" : "Up/Down";
        int selectedID;
        while (true) {
            String input = Utility.userSelect(Messages.get("prompt.select-snapshot-restore-method-select", Config.RESTORE_METHOD.name().toLowerCase(), upOrDown), Messages.get("prompt.choice"), new ArrayList<>(IDs));

            if (IDs.contains(input.trim())) {
                selectedID = Integer.parseInt(input.trim());
                break;
            } else {
                System.err.println(ConsoleColor.RED + Messages.get("text.invalid-snapshot-id") + ConsoleColor.RESET + input);
                System.err.println(Messages.get("text.please-try-again"));
            }
        }
        return selectedID;
    }

    private boolean verifySnapshotFiles(SnapshotEntry snapshotEntry, boolean showError, List<SnapshotEntry> snapshotEntryList) {
        boolean allFilesValid = true;
        LinkedHashSet<SnapshotEntry> affectedSnapshots = new LinkedHashSet<>();
        for (ImageDetail image : snapshotEntry.allImages()) {
            String hashedFilePath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER, image.fileHashName());
            boolean fileOk = true;
            // Check if the file exists
            if (!Utility.isFilePresent(hashedFilePath)) {
                String message = Messages.get("text.missing-file-detected");
                if (showError) {
                    Utility.errorMessage(message, hashedFilePath);
                } else {
                    Utility.warnMessage(message, hashedFilePath);
                }
                fileOk = false;
            }
            // Check if the file is corrupted
            if (Hash.hasCorruption(hashedFilePath)) {
                String message = Messages.get("text.corrupted-file-detected");
                if (showError) {
                    Utility.errorMessage(message, hashedFilePath);
                } else {
                    Utility.warnMessage(message, hashedFilePath);
                }
                fileOk = false;
            }
            if (!fileOk) {
                allFilesValid = false;
                // Collect snapshots affected by this missing or corrupted file
                for (SnapshotEntry otherSnapshot : snapshotEntryList) {
                    for (ImageDetail otherImage : otherSnapshot.allImages()) {
                        if (Objects.equals(otherImage.fileHashName(), image.fileHashName())) {
                            affectedSnapshots.add(otherSnapshot);
                        }
                    }
                }
            }
        }
        if (!affectedSnapshots.isEmpty()) {
            // Convert affectedSnapshots to comma-separated list of IDs
            String ids = affectedSnapshots.stream()
                    .map(s -> String.valueOf(s.snapperEntry().snapshotID()))
                    .collect(Collectors.joining(", "));
            Utility.warnMessage(Messages.get("warn.snapshots-affected"), ids);
        }
        return allFilesValid;
    }

    public PartitionUsage predictSpaceUsage(SnapshotEntry currentEntry) {
        HashSet<String> addedFilePaths = new HashSet<>();
        for (ImageDetail imageDetail : currentEntry.allImages()) {
            String snapshotFilePath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER, imageDetail.fileHashName());
            if (!Utility.isFilePresent(snapshotFilePath)) {
                String filePath = Utility.buildPath(config.espPath(), imageDetail.dirPath(), imageDetail.fileName());
                addedFilePaths.add(filePath);
            }
        }
        return Utility.checkPartitionUsage(Config.LIMIT_USAGE_PERCENT, config.espPath(), addedFilePaths, new HashSet<>());
    }

    private PartitionUsage predictSpaceUsage(SnapshotEntry currentEntry, SnapshotEntry snapshotEntry, boolean isBackupNeeded) {
        HashSet<String> addedFilePaths = new HashSet<>();
        HashSet<String> deletedFilePaths = new HashSet<>();
        for (ImageDetail imageDetail : currentEntry.allImages()) {
            String filePath = Utility.buildPath(config.espPath(), imageDetail.dirPath(), imageDetail.fileName());
            if (isBackupNeeded) {
                String snapshotFilePath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER, imageDetail.fileHashName());
                if (Utility.isFilePresent(filePath) && Utility.isFilePresent(snapshotFilePath)) {
                    deletedFilePaths.add(filePath);
                }
            } else {
                if (Utility.isFilePresent(filePath)) {
                    deletedFilePaths.add(filePath);
                }
            }
        }
        for (ImageDetail imageDetail : snapshotEntry.allImages()) {
            String snapshotFilePath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER, imageDetail.fileHashName());
            if (Utility.isFilePresent(snapshotFilePath)) {
                addedFilePaths.add(snapshotFilePath);
            }
        }
        return Utility.checkPartitionUsage(100.0, config.espPath(), addedFilePaths, deletedFilePaths);
    }

    private boolean isSubvolumeReadOnly(String subvolumePath) {
        Output output = Utility.getCommandOutput("btrfs property get '" + subvolumePath + "' ro", true, null);
        if (output.isSuccess() && !output.text().isEmpty()) {
            return output.text().getFirst().contains("true");
        } else {
            return false;
        }
    }

    public boolean checkUpdate() {
        return isUpdateNeeded || isSnapshotNew;
    }

    public boolean isSnapshotNew() {
        return isSnapshotNew;
    }
}
