package org.limine.snapper.processes;

import org.limine.snapper.formats.limine8.Limine;
import org.limine.snapper.objects.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.limine.snapper.processes.Utility.getLinuxDistroName;

public class ConfigReader {

    private final Map<String, String> configs = new HashMap<>();

    public ConfigReader() {
        loadConfigFile("/etc/limine-snapper-sync.conf");
        loadConfigFile(Config.DEFAULT_CONFIG_FILE_PATH);

        Output c = Utility.getCommandOutput("tput colors", false, "");
        if (c.isSuccess() && !c.text().isEmpty()) {
            try {
                Config.ENABLE_COLOR = Integer.parseInt(c.text().getFirst().trim()) >= 8;
            } catch (NumberFormatException ignored) {
                Config.ENABLE_COLOR = false;
            }
        }

        if (Config.ENABLE_TMP_CONFIG && Utility.isFilePresent(Config.TMP_CONFIG_FILE_PATH)) {
            if (Utility.isRootOwnedFile(Config.TMP_CONFIG_FILE_PATH)) {
                loadConfigFile(Config.TMP_CONFIG_FILE_PATH);
            } else {
                Utility.warnMessage(Messages.get("warn.the-temporary-config-file-is", Config.TMP_CONFIG_FILE_PATH));
            }
        }
    }

    private List<String> getKernelParams(String input) {
        List<String> params = new ArrayList<>();
        if (input == null || input.isEmpty()) {
            return params;
        }
        String[] parts = input.split("\\s+");
        for (String part : parts) {
            if (!part.isEmpty()) {
                params.add(part);
            }
        }
        return params;
    }

    private void loadConfigFile(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) return;

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) continue;

                int eqIndex = line.indexOf('=');
                if (eqIndex == -1) continue;

                String key = line.substring(0, eqIndex).trim();
                String value = Utility.removeSurrounding(line.substring(eqIndex + 1).trim(), "\""); // remove surrounding quotes;

                switch (key) {
                    case "SNAPSHOT_KERNEL_PARAMETERS+":
                        Config.KERNEL_PARAMETERS_TO_ADD.addAll(getKernelParams(value));
                        break;
                    case "SNAPSHOT_KERNEL_PARAMETERS-":
                        Config.KERNEL_PARAMETERS_TO_REMOVE.addAll(getKernelParams(value));
                        break;
                    case "SNAPSHOT_KERNEL_PARAMETERS":
                        Config.KERNEL_PARAMETERS_TO_REPLACE.addAll(getKernelParams(value));
                    default:
                        configs.put(key, value);
                        break;
                }
            }
        } catch (IOException e) {
            Utility.exitWithError(Messages.get("error.failed-load-config-file"), Messages.get("error.due", filePath, e.getMessage()));
        }
    }

    public Config readConfig() {
        String targetOs = configs.get("TARGET_OS_NAME");
        if (targetOs == null || targetOs.isBlank()) {
            Config.TARGET_OS_NAME = getLinuxDistroName();
        } else {
            Config.TARGET_OS_NAME = Limine.cleanName(targetOs);
        }

        String maxSnapshotEntries = configs.get("MAX_SNAPSHOT_ENTRIES");
        if (maxSnapshotEntries == null || maxSnapshotEntries.isBlank() || "auto".equalsIgnoreCase(maxSnapshotEntries)) {
            Config.MAX_SNAPSHOT_ENTRIES = Config.AUTO_SNAPSHOT_LIMIT;
        } else {
            try {
                int number = Integer.parseInt(maxSnapshotEntries);
                if (number >= 0) {
                    Config.MAX_SNAPSHOT_ENTRIES = number;
                } else {
                    Config.MAX_SNAPSHOT_ENTRIES = Config.AUTO_SNAPSHOT_LIMIT;
                }
            } catch (NumberFormatException ignored) {
                Utility.errorMessage(Messages.get("error.invalid-value-max_snapshot_entries-must-be"));
            }
        }

        String espPath = configs.get("ESP_PATH");
        if (espPath == null || espPath.isBlank()) {
            espPath = Utility.findLimineBootPartition(Config.LIMINE_CONFIG_FILE);
        }
        if (espPath == null) {
            Utility.exitWithError(Messages.get("error.fat32-boot-partition-with-not", Config.LIMINE_CONFIG_FILE, Config.DEFAULT_CONFIG_FILE_PATH));
            return null;
        }
        // Does $ESP_PATH/limine.conf exist?
        String limineConfigPath = Utility.buildPath(espPath, Config.LIMINE_CONFIG_FILE);
        if (!Utility.isFilePresent(limineConfigPath)) {
            Utility.exitWithError(Messages.get("error.file-not-found"), limineConfigPath);
            return null;
        }

        String rootSubvolumePath = configs.get("ROOT_SUBVOLUME_PATH");
        if (rootSubvolumePath != null && !rootSubvolumePath.isBlank()) {
            Config.ROOT_SUBVOLUME_PATH = Utility.buildPath(rootSubvolumePath);
        }
        String rootSnapshotsPath = configs.get("ROOT_SNAPSHOTS_PATH");
        if (rootSnapshotsPath != null && !rootSnapshotsPath.isBlank()) {
            Config.ROOT_SNAPSHOTS_PATH = Utility.buildPath(rootSnapshotsPath);
        }

        if (!Utility.isCommandPresent("snapper")) {
            String errMessage = Messages.get("text.snapper-is-not-installed");
            Utility.exitWithError(errMessage);
            return null;
        }

        String snapshotWritable = configs.get("SNAPSHOT_WRITABLE");
        if (snapshotWritable != null) {
            Config.SNAPSHOT_WRITABLE = "yes".equalsIgnoreCase(snapshotWritable);
        }

        String setSnapshotAsDefault = configs.get("SET_SNAPSHOT_AS_DEFAULT");
        if (setSnapshotAsDefault != null) {
            Config.SET_SNAPSHOT_AS_DEFAULT = "yes".equalsIgnoreCase(setSnapshotAsDefault);
        }

        String excludeSnapshotTypes = configs.get("EXCLUDE_SNAPSHOT_TYPES");
        if (excludeSnapshotTypes != null) {
            for (String type : excludeSnapshotTypes.split(",")) {
                String trimmed = type.trim();
                if (!trimmed.isEmpty()) {
                    Config.EXCLUDE_SNAPSHOT_TYPES.add(trimmed);
                }
            }
        }

        String excludeEntries = configs.get("EXCLUDE_SNAPSHOT_ENTRIES");
        if (excludeEntries != null) {
            for (String type : excludeEntries.split(",")) {
                String trimmed = type.trim();
                if (!trimmed.isEmpty()) {
                    Config.EXCLUDE_SNAPSHOT_ENTRIES.add(trimmed);
                }
            }
        }

        String restoreMethod = configs.get("RESTORE_METHOD");
        if (restoreMethod != null) {
            switch (restoreMethod) {
                case "snapper", "opensuse":
                    Config.RESTORE_METHOD = RestoreMethod.SNAPPER;
                    break;
                case "rsync":
                    if (Utility.isCommandPresent("rsync")) {
                        Config.RESTORE_METHOD = RestoreMethod.RSYNC;
                    } else {
                        String errMessage = Messages.get("text.restore_method-rsync-can-not-be");
                        Utility.errorMessage(errMessage);
                        Config.RESTORE_METHOD = RestoreMethod.REPLACE;
                    }
                    break;
                case "replace":
                    Config.RESTORE_METHOD = RestoreMethod.REPLACE;
                    break;
                default:
                    String errMessage = Messages.get("text.invalid-option-restore_method-available-options", restoreMethod);
                    Utility.errorMessage(errMessage);
                    Config.RESTORE_METHOD = RestoreMethod.REPLACE;
                    break;
            }
        } else {
            String enableRsync = configs.get("ENABLE_RSYNC_ASK");
            if ("yes".equalsIgnoreCase(enableRsync) && Utility.isCommandPresent("rsync")) {
                Config.RESTORE_METHOD = RestoreMethod.RSYNC;
                Utility.warnMessage(Messages.get("warn.config-enable_rsync_ask-yes-is-deprecated"));
            } else {
                Config.RESTORE_METHOD = RestoreMethod.REPLACE;
            }
        }

        Config.USE_OPENSUSE_LAYOUT = Config.RESTORE_METHOD == RestoreMethod.SNAPPER;

        String spaceNumber = configs.get("SPACE_NUMBER");
        if (spaceNumber != null) {
            try {
                int number = Integer.parseInt(spaceNumber);
                StringBuilder spaces = new StringBuilder();
                while (number > 0) {
                    spaces.append(" ");
                    number--;
                }
                Config.SPACES = spaces.toString();
            } catch (NumberFormatException ignored) {
                String errMessage = Messages.get("text.invalid-value-space_number-must-be");
                Utility.errorMessage(errMessage);
            }
        }

        String limitUsagePercent = configs.get("LIMIT_USAGE_PERCENT");
        if (limitUsagePercent != null) {
            try {
                double percent = Double.parseDouble(limitUsagePercent);
                if (0.0 < percent && percent < 100.0) {
                    Config.LIMIT_USAGE_PERCENT = percent;
                }
            } catch (NumberFormatException e) {
                String errMessage = Messages.get("text.invalid-value-limit_usage_percent-should-be");
                Utility.errorMessage(errMessage);
            }
        }

        String fsUuid = configs.get("FS_UUID");
        if (fsUuid == null || fsUuid.isBlank()) {
            fsUuid = configs.get("UUID");
        }
        if (fsUuid != null && !fsUuid.isBlank()) {
            if (Utility.isPathPresent("/dev/disk/by-uuid/" + fsUuid)) {
                Config.FS_UUID = fsUuid;
            } else {
                Utility.errorMessage(Messages.get("error.invalid-fs_uuid"), Messages.get("error.does-not-exist-dev-disk", fsUuid));
            }
        }

        String snapshotFormatChoice = configs.get("SNAPSHOT_FORMAT_CHOICE");
        if (snapshotFormatChoice != null) {
            Config.SNAPSHOT_FORMAT_CHOICE = snapshotFormatChoice;
        }

        String hashName = configs.get("HASH_FUNCTION");
        if (hashName != null) {
            Hash hashFunction = Hash.getHashFunction(hashName);
            if (hashFunction != null && Utility.isCommandPresent(hashFunction.command)) {
                Config.HASH_FUNCTION = hashFunction;
            } else {
                String errMessage = Messages.get("text.the-hash-function-was-not", hashName);
                Utility.errorMessage(errMessage);
            }
        }

        // The default hash function must be available if no other hash function is selected or found.
        if (Config.HASH_FUNCTION == Hash.SHA256 && !Utility.isCommandPresent(Hash.SHA256.command)) {
            Utility.exitWithError(Messages.get("error.the-default-hash-utility-is", Hash.SHA256.command));
            return null;
        }

        String configBackupThreshold = configs.get("CONFIG_BACKUP_THRESHOLD");
        if (configBackupThreshold != null) {
            try {
                Config.CONFIG_BACKUP_THRESHOLD = Integer.parseInt(configBackupThreshold);
            } catch (NumberFormatException e) {
                Utility.errorMessage(Messages.get("error.config_backup_threshold-is-invalid-should-be", configBackupThreshold));
            }
        }

        String commands = configs.get("COMMANDS_BEFORE_SAVE");
        if (commands != null) {
            Config.COMMANDS_BEFORE_SAVE = commands;
        }

        commands = configs.get("COMMANDS_AFTER_SAVE");
        if (commands != null) {
            Config.COMMANDS_AFTER_SAVE = commands;
        }

        String machineID = Utility.readMachineID();
        Config.LSS_STATE_DIR = "/var/cache/boot/" + machineID + "/lss/";
        return new Config(machineID, espPath);
    }

    public void loadQuietMode() {
        Config.QUIET = "yes".equalsIgnoreCase(configs.get("QUIET_MODE"));
    }

    public void readSnapperConfig() {
        String snapperConfigName = configs.get("SNAPPER_CONFIG_NAME");
        if (snapperConfigName != null) {
            if (!snapperConfigName.isBlank()) {
                Config.SNAPPER_CONFIG_NAME = snapperConfigName;
            } else {
                Config.SNAPPER_CONFIG_NAME = null;
            }
        }
        boolean disableDbus = Utility.areYouInChroot();
        if (Config.SNAPPER_CONFIG_NAME == null) {
            Config.SNAPPER_CONFIG_NAME = Utility.snapperConfigName(disableDbus);
        }
        if (Config.SNAPPER_CONFIG_NAME == null && Utility.areYouInBtrfs()) {
            String warnMessage = Messages.get("warn.creating-snapper-config-for-root");
            Utility.warnMessage(warnMessage);
            // Automatically create a Snapper configuration and root snapshot if not present.
            Config.SNAPPER_CONFIG_NAME = initSnapperConfig();
        }
        if (Config.SNAPPER_CONFIG_NAME != null) {
            String dbusOption = disableDbus ? " --no-dbus" : "";
            Config.SNAPPER_LIST_CMDLINE = "snapper --utc --jsonout"
                    + dbusOption
                    + " -c " + Config.SNAPPER_CONFIG_NAME
                    + " list --columns number,date,type,description";
        } else {
            String errMessage;
            if (Utility.areYouInBtrfs()) {
                errMessage = Messages.get("text.no-snapper-config-found-please", Config.DEFAULT_CONFIG_FILE_PATH);
            } else {
                errMessage = Messages.get("text.the-partition-is-not-using");
            }
            Utility.exitWithError(errMessage);
        }
    }

    /**
     * Automatically create a Snapper configuration and root snapshot.
     */
    private String initSnapperConfig() {
        List<String> commands = List.of(
                "snapper -c root create-config /",
                "snapper -c root create --description 'limine-snapper-sync' --cleanup-algorithm number --read-only"
        );
        if (Utility.runCommands(commands, true, true, true, true)) {
            return "root";
        } else {
            return null;
        }
    }

    /**
     * Validates configured subvolume paths before snapshot entry creation.
     * Note: In a chroot environment, subvolume paths cannot be verified during restore.
     * Therefore, configuration is validated before being stored in the new snapshot for later restore.
     */
    public void validateAndSetSubvolPaths() {
        String rootMount = Utility.getSubvolFromProcMounts("/");
        String snapMount = Utility.getSubvolFromProcMounts("/.snapshots");
        if (rootMount == null || rootMount.isBlank()) {
            Utility.exitWithError(Messages.get("error.no-root-subvolume-found-proc"));
            return;
        }
        List<String> errors = new ArrayList<>();
        if (Config.USE_OPENSUSE_LAYOUT) {
            // The system must be running on either a writable or read-only Snapper snapshot.
            if (!rootMount.endsWith("/snapshot")) {
                errors.add(Messages.get("error.opensuse-style-layout-requires-system"));
            }
            // The subvolume '/@/.snapshots' must be mounted at '/.snapshots'
            if (snapMount == null || !snapMount.endsWith("/.snapshots")) {
                errors.add(Messages.get("error.opensuse-style-layout-requires-snapshots"));
            }
            // This check is important to prevent misconfiguration during restore.
            if (Config.ROOT_SNAPSHOTS_PATH != null && !Config.ROOT_SNAPSHOTS_PATH.equals(snapMount)) {
                errors.add(Messages.get("error.opensuse-style-layout-requires-removing"));
            }
            if (!errors.isEmpty()) {
                errors.addFirst(Messages.get("error.restore-method-snapper-needs-opensuse-layout"));
                errors.add(Messages.get("error.opensuse-style-layout-requires-removing-2"));
            } else {
                Config.ROOT_SNAPSHOTS_PATH = snapMount;
                Config.ROOT_SUBVOLUME_PATH = ""; // Not needed for OpenSUSE-style layout because the active root subvolume path changes after rollback.
            }
        } else {
            // Check and set ROOT_SUBVOLUME_PATH
            if (Config.ROOT_SUBVOLUME_PATH == null) {
                Config.ROOT_SUBVOLUME_PATH = rootMount;
            } else if (!rootMount.equals(Config.ROOT_SUBVOLUME_PATH) && !rootMount.endsWith("/snapshot")) {
                // Ignore the error when switching from the restore method "opensuse" to "replace" or "rsync"
                errors.add(Messages.get("error.root-subvolume-path-does-not-match", Config.ROOT_SUBVOLUME_PATH, rootMount));
                errors.add(Messages.get("error.please-set-root_subvolume_path-remove-override", rootMount, Config.DEFAULT_CONFIG_FILE_PATH));
            }
            // Check and set ROOT_SNAPSHOTS_PATH
            String expectedSnapshotsPath;
            if (snapMount == null || snapMount.isBlank()) {
                String rootSubvolPath = "/".equals(Config.ROOT_SUBVOLUME_PATH) ? "" : Config.ROOT_SUBVOLUME_PATH;
                expectedSnapshotsPath = rootSubvolPath + "/.snapshots"; // Default Snapper layout
            } else {
                expectedSnapshotsPath = snapMount; // Custom Snapper layout
            }
            if (Config.ROOT_SNAPSHOTS_PATH == null || Config.ROOT_SNAPSHOTS_PATH.isBlank()) {
                Config.ROOT_SNAPSHOTS_PATH = expectedSnapshotsPath;
            } else if (!Config.ROOT_SNAPSHOTS_PATH.equals(expectedSnapshotsPath)) {
                errors.add(Messages.get("error.root-snapshots-path-does-not-match", Config.ROOT_SNAPSHOTS_PATH, expectedSnapshotsPath));
                errors.add(Messages.get("error.please-set-root_snapshots_path-remove-override", expectedSnapshotsPath, Config.DEFAULT_CONFIG_FILE_PATH));
            }
        }
        if (!errors.isEmpty()) {
            Utility.exitWithError(String.join("\n", errors));
        }
    }
}
