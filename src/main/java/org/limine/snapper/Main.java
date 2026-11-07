package org.limine.snapper;

import org.limine.snapper.objects.Messages;
import org.limine.snapper.formats.json1.Manifest;
import org.limine.snapper.formats.json1.SnapshotEntry;
import org.limine.snapper.objects.Config;
import org.limine.snapper.objects.FileDetail;
import org.limine.snapper.objects.PartitionUsage;
import org.limine.snapper.objects.RestoreContext;
import org.limine.snapper.processes.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class Main {

    static void main(String[] args) throws InterruptedException {
        if (args.length == 0) {
            update(null);
            return;
        }

        String cmd = "--update";
        LinkedHashSet<String> params = new LinkedHashSet<>();

        List<String> list = Arrays.asList(args);
        Iterator<String> it = list.iterator();

        while (it.hasNext()) {
            String a = it.next();
            switch (a) {
                case "--info":
                case "--list":
                case "--restore":
                    cmd = a;
                    break;
                case "--add":
                case "--restore-kernels":
                    cmd = a;
                    if (it.hasNext()) {
                        String next = it.next();
                        if (!next.startsWith("--")) {
                            params.add(next);
                        }
                    }
                    break;
                case "--remove":
                    cmd = a;
                    while (it.hasNext()) {
                        String next = it.next();
                        if (!next.startsWith("--")) {
                            params.add(next);
                        } else {
                            break;
                        }
                    }
                    break;
                default:
                    // ignore unknown options
                    break;
            }
        }
        handleCommand(cmd, params);
    }

    private static void handleCommand(String cmd, LinkedHashSet<String> params) throws InterruptedException {
        switch (cmd) {
            case "--remove":
                if (!params.isEmpty()) {
                    removeEntries(params);
                } else {
                    Utility.warnMessage(Messages.get("warn.please-give-ids-like"));
                }
                break;
            case "--restore-kernels":
                if (!params.isEmpty()) {
                    restoreKernels(params.getFirst());
                } else {
                    Utility.exitWithError(Messages.get("error.restore-kernels-requires-snapshot-id"));
                }
                break;
            case "--restore":
                restore();
                break;
            case "--info":
                info();
                break;
            case "--list":
                list();
                break;
            case "--add":
            default:
                String addedID = null;
                if (!params.isEmpty()) {
                    addedID = params.getFirst();
                }
                update(addedID);
                break;
        }
    }

    private static void info() {
        // Load keywords and values from multiple config files
        Config config = new ConfigReader().readConfig();

        // Read /<esp>/<machine-id>/limine_history/snapshots.json
        String manifestPath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER, Config.MANIFEST_FILE);
        Manifest manifest = HistoryReader.load(manifestPath);
        HistoryReader.displayInfo(manifest, config);
    }

    private static void list() {
        // Load keywords and values from multiple config files
        Config config = new ConfigReader().readConfig();

        // Read /<esp>/<machine-id>/limine_history/snapshots.json
        String manifestPath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER, Config.MANIFEST_FILE);
        Manifest manifest = HistoryReader.load(manifestPath);
        if (manifest != null) {
            HistoryReader.displaySnapshotList(manifest);
        }
    }

    private static void removeEntries(HashSet<String> params) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        final int maxIds = 1000;
        for (String param : params) {
            // Check global max limit before anything
            if (ids.size() >= maxIds) {
                Utility.warnMessage(Messages.get("warn.too-many-ids-max", maxIds));
                return;
            }
            try {
                if (param.contains("..") || param.contains("-")) {
                    String sep = param.contains("..") ? ".." : "-";
                    String[] parts = param.split(Pattern.quote(sep));
                    if (parts.length == 2) {
                        int start = Integer.parseInt(parts[0]);
                        int end = Integer.parseInt(parts[1]);
                        int min = Math.min(start, end);
                        int max = Math.max(start, end);
                        int rangeSize = max - min + 1;

                        // Prevent exploding range
                        if (rangeSize > maxIds) {
                            Utility.warnMessage(Messages.get("warn.range-too-large-max-ids", maxIds));
                            return;
                        }
                        for (int i = min; i <= max; i++) {
                            ids.add(i);
                        }
                    }
                } else {
                    ids.add(Integer.parseInt(param));
                }
            } catch (NumberFormatException e) {
                //Utility.warnMessage(Messages.get("warn.invalid-number-range", param));
            }
        }

        ConfigReader configReader = new ConfigReader();
        configReader.loadQuietMode();
        Config config = configReader.readConfig();
        // Read /<esp>/<machine-id>/limine_history/snapshots.json
        String manifestPath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER, Config.MANIFEST_FILE);
        Manifest manifest = HistoryReader.load(manifestPath);
        if (manifest == null) {
            return;
        }
        // If the manifest version is old, it will be upgraded automatically
        boolean isManifestChanged = FormatConverter.upgradeManifest(manifest) || HistoryReader.isManifestChanged;

        int snapshotCountBefore = manifest.getSnapshotEntries().size();
        SnapshotManager manager = new SnapshotManager(config);
        HashSet<FileDetail> deletedFileDetails = manager.remove(manifest, ids);
        int snapshotCountAfter = manifest.getSnapshotEntries().size();

        if (deletedFileDetails.isEmpty()) {
            if (snapshotCountBefore == snapshotCountAfter) {
                Utility.infoMessage(Messages.get("info.no-snapshot-boot-entries-found"));
            } else {
                Utility.infoMessage(Messages.get("info.no-kernel-files-removed-still"));
            }
        } else {
            long bytes = 0L;
            for (FileDetail file : deletedFileDetails) {
                bytes += file.bytes();
            }
            int removedCount = deletedFileDetails.size();
            String fileInfo = Messages.plural("info.kernel-files-removed", removedCount, removedCount);
            Utility.infoMessage(Messages.get("info.freed-boot-partition", FileDetail.toMiB(bytes), fileInfo));
        }

        if (isManifestChanged || manager.checkUpdate() || HistoryWriter.updateManifest(manifest)) {
            HistoryWriter.backup(manifestPath);
            HistoryWriter.save(manifest, manifestPath);
            new BlsWriter(config).save(manifest);
        }
    }

    private static void update(String addedID) throws InterruptedException {
        if (!Utility.areYouInBtrfs()) {
            Utility.warnMessage(Messages.get("warn.you-are-not-btrfs-tool"));
            return;
        }

        // Load keywords and values from multiple config files
        ConfigReader configReader = new ConfigReader();
        configReader.loadQuietMode();
        Config config = configReader.readConfig();
        configReader.validateAndSetSubvolPaths();
        // Run SnapperReader to read a Snapper list
        configReader.readSnapperConfig();

        // If you boot into any snapshot, do not update anything!
        if (Utility.areYouInSnapshot() && !Config.USE_OPENSUSE_LAYOUT) {
            Utility.warnMessage(Messages.get("warn.you-are-snapshot-tool-does"));
            return;
        }

        // On a dedicated EFI mount, systemd copies installed kernels and creates
        // their BLS entries. The /boot LSS path keeps its snapshot workflow below.
        Utility.installKernelsOnEfiEsp(config.espPath());

        String manifestPath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER, Config.MANIFEST_FILE);
        AtomicReference<Manifest> manifestRef = new AtomicReference<>();
        AtomicReference<SnapperReader> snapperRef = new AtomicReference<>();
        AtomicReference<SystemdBootReader> bootEntryReaderRef = new AtomicReference<>();
        // Read /<esp>/<machine-id>/limine_history/snapshots.json
        Thread historyThread = new Thread(() -> manifestRef.set(HistoryReader.load(manifestPath)));
        Thread snapperThread = new Thread(() -> snapperRef.set(new SnapperReader(addedID)));
        Thread bootEntryReaderThread = new Thread(() -> bootEntryReaderRef.set(new SystemdBootReader(config)));

        snapperThread.start();
        bootEntryReaderThread.start();
        historyThread.start();

        bootEntryReaderThread.join();
        snapperThread.join();
        historyThread.join();

        SnapperReader snapper = snapperRef.get();
        SystemdBootReader bootEntries = bootEntryReaderRef.get();
        Manifest manifest = manifestRef.get();
        if (manifest == null) {
            return;
        }
        // If the manifest is old, convert it to a new version.
        boolean isManifestChanged = FormatConverter.upgradeManifest(manifest) || HistoryReader.isManifestChanged;

        SnapshotManager manager = new SnapshotManager(config);
        manager.sync(manifest, snapper);

        boolean updateBootEntries = manager.checkUpdate();
        if (updateBootEntries) {
            // Check that at least one matching systemd-boot entry exists.
            if (bootEntries.isTargetOsNotFound()) {
                String errMessage = Messages.get("text.please-set-target_os_name", Config.DEFAULT_CONFIG_FILE_PATH);
                if (Config.TARGET_OS_NAME != null) {
                    errMessage = Messages.get("text.target-os-name-was-not", Config.TARGET_OS_NAME, Config.DEFAULT_CONFIG_FILE_PATH);
                }
                Utility.errorMessage(errMessage);
                return;
            }

            // Check if your OS entry has at least one kernel node.
            if (bootEntries.targetOS.nodes.isEmpty() || (bootEntries.targetOS.nodes.size() < 2 && bootEntries.isSnapshotsInsideOsNode)) {
                String errMessage = Messages.get("text.your-os-entry-has-no", Utility.buildPath(config.espPath(), Config.BOOT_ENTRIES_DIRECTORY));
                Utility.errorMessage(errMessage);
                return;
            }

            if (manager.isSnapshotNew()) {
                SnapshotEntry newSnapshotEntry = bootEntries.createCurrentBootEntry(snapper.lastEntry());
                // Check the size of your boot partition if the usage limit is exceeded.
                PartitionUsage usage = manager.predictSpaceUsage(newSnapshotEntry);
                if (usage.exceedsLimit() && Config.MAX_SNAPSHOT_ENTRIES == Config.AUTO_SNAPSHOT_LIMIT) {
                    // Auto-prune older snapshot boot entries according to LIMIT_USAGE_PERCENT.
                    usage = manager.autoPruneSnapshotEntries(manifest, usage);
                }
                if (usage.exceedsLimit()) {
                    if (usage.percent(usage.usedBytes()) >= Config.LIMIT_USAGE_PERCENT) {
                        Utility.errorMessage(Messages.get("error.stopping-boot-entry-creation"), Messages.get("error.boot-partition-usage-exceeds", usage.usedPercent(), Config.LIMIT_USAGE_PERCENT));
                    } else if (usage.increaseAtLeastMiB(50)) {
                        Utility.errorMessage(Messages.get("error.stopping-boot-entry-creation"), Messages.get("error.boot-partition-usage-from-will", usage.usedPercent(), usage.newPercent(), usage.deltaMiB(), Config.LIMIT_USAGE_PERCENT));
                    } else {
                        Utility.errorMessage(Messages.get("error.stopping-boot-entry-creation"), Messages.get("error.boot-partition-usage-from-will-2", usage.usedPercent(), usage.newPercent(), Config.LIMIT_USAGE_PERCENT));
                    }
                } else {
                    if (Config.SNAPSHOT_WRITABLE) {
                        String makeSnapshotWritable = "btrfs property set '/.snapshots/" + newSnapshotEntry.snapperEntry().snapshotID() + "/snapshot' ro " + false;
                        Utility.runCommand(makeSnapshotWritable, false, false, true);
                    }
                    // The manifest adds the new snapshot.
                    manager.historyAddSnapshot(manifest, newSnapshotEntry);
                }
            }
        }
        if (updateBootEntries || isManifestChanged || HistoryWriter.updateManifest(manifest)) {
            historyThread = new Thread(() -> {
                HistoryWriter.backup(manifestPath);
                HistoryWriter.save(manifest, manifestPath);
            });
            historyThread.start();
        }
        bootEntryReaderThread = new Thread(() -> {
            new BlsWriter(config).save(manifest);
        });
        bootEntryReaderThread.start();
        bootEntryReaderThread.join();
        historyThread.join();
    }

    private static void restoreKernels(String snapshotID) {
        Config.ENABLE_TMP_CONFIG = Utility.areYouInBtrfs() && Utility.areYouInSnapshot();
        // Load keywords and values from multiple config files
        Config config = new ConfigReader().readConfig();
        Utility.installKernelsOnEfiEsp(config.espPath());

        // Read /<esp>/<machine-id>/limine_history/snapshots.json
        String manifestPath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER, Config.MANIFEST_FILE);
        Manifest manifest = HistoryReader.load(manifestPath);
        // Autoload subvolume-path and snapshot-path from the manifest if they are not configured.
        HistoryReader.loadSubvolPaths(manifest);

        // Check that matching systemd-boot entries exist.
        SystemdBootReader bootEntries = new SystemdBootReader(config, false);
        if (bootEntries.isTargetOsNotFound()) {
            String errMessage = Messages.get("text.please-set-target_os_name", Config.DEFAULT_CONFIG_FILE_PATH);
            if (Config.TARGET_OS_NAME != null) {
                errMessage = Messages.get("text.target-os-name-was-not", Config.TARGET_OS_NAME, Config.DEFAULT_CONFIG_FILE_PATH);
            }
            Utility.exitWithError(errMessage, false);
            return;
        }

        Config.RESTORE_MODE = true;

        // Select snapshot ID for the restore.
        SnapshotManager manager = new SnapshotManager(config);
        RestoreContext restoreContext = manager.preRestoreKernels(manifest, bootEntries, snapshotID);
        if (restoreContext == null || !restoreContext.isValid()) {
            Config.RESTORE_MODE = false;
            Utility.exitWithError(Messages.get("error.the-restore-is-invalid"), false);
            return;
        }

        // It's time to start restoring a snapshot.
        // Copy the selected snapshot entry from snapshots.json and write it to the target OS node.
        SnapshotEntry restoredEntry = manager.restoreKernels(restoreContext);
        if (restoredEntry == null) {
            Config.RESTORE_MODE = false;
            Utility.exitWithError(Messages.get("error.the-restore-failed"), false);
        } else {
            HistoryWriter.backup(manifestPath);
            // Overwrite /<esp>/<machine-id>/limine_history/snapshots.json
            HistoryWriter.save(manifest, manifestPath);

            new BlsWriter(config).save(manifest);
        }
    }

    private static void restore() {
        Config.RESTORE_MODE = true;
        Config.ENABLE_TMP_CONFIG = Utility.areYouInBtrfs() && Utility.areYouInSnapshot();
        // Load keywords and values from multiple config files
        Config config = new ConfigReader().readConfig();
        Utility.installKernelsOnEfiEsp(config.espPath());

        // Read /<esp>/<machine-id>/limine_history/snapshots.json
        String manifestPath = Utility.buildPath(config.espPath(), config.machineID(), Config.HISTORY_FOLDER, Config.MANIFEST_FILE);
        Manifest manifest = HistoryReader.load(manifestPath);
        // Autoload subvolume-path and snapshot-path from the manifest if they are not configured.
        HistoryReader.loadSubvolPaths(manifest);

        // Check that matching systemd-boot entries exist.
        SystemdBootReader bootEntries = new SystemdBootReader(config, true);
        if (bootEntries.isTargetOsNotFound()) {
            String errMessage = Messages.get("text.please-set-target_os_name", Config.DEFAULT_CONFIG_FILE_PATH);
            if (Config.TARGET_OS_NAME != null) {
                errMessage = Messages.get("text.target-os-name-was-not", Config.TARGET_OS_NAME, Config.DEFAULT_CONFIG_FILE_PATH);
            }
            Utility.errorMessage(errMessage);
            return;
        }

        // Select snapshot ID for the restore.
        SnapshotManager manager = new SnapshotManager(config);
        RestoreContext restoreContext = manager.preRestore(manifest, bootEntries);
        if (restoreContext == null || !restoreContext.isValid()) {
            Utility.exitWithError(Messages.get("error.the-restore-is-invalid"), false);
            return;
        }

        if (Config.USE_OPENSUSE_LAYOUT) {
            Utility.deleteFile(Config.RESTORE_LOCK_FILE, false);
            String snapperRollbackCmdline = "snapper rollback " + restoreContext.snapshotEntry().snapperEntry().snapshotID();
            if (!Utility.runCommand(snapperRollbackCmdline, true, true, true)) {
                Utility.exitWithError(Messages.get("error.snapper-rollback-failed"), true);
                return;
            }
        } else {
            // It's time to start restoring a snapshot.
            // Copy the selected snapshot entry from snapshots.json and write it to the target OS node.
            SnapshotEntry restoredEntry = manager.restore(manifest, bootEntries, restoreContext);
            if (restoredEntry == null) {
                Utility.exitWithError(Messages.get("error.the-restore-failed"), true);
                return;
            } else {
                HistoryWriter.backup(manifestPath);
                // Overwrite /<esp>/<machine-id>/limine_history/snapshots.json
                HistoryWriter.save(manifest, manifestPath);

                new BlsWriter(config).save(manifest);
            }
        }
        if (Utility.isFilePresent(Config.RESTORE_LOCK_FILE)) {
            try {
                Files.writeString(Path.of(Config.RESTORE_LOCK_FILE), "restored");
            } catch (IOException _) {
            }
        }
    }
}
