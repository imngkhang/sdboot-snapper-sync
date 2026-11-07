package org.limine.snapper.objects;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record Config(String machineID, String espPath) {
    public static final String VERSION = "1.32.1";
    public static final String MANIFEST_VERSION = "1.3.0";
    public static final Set<Integer> UNSUPPORTED_LIMINE_MAJOR_VERSIONS = Set.of(1, 2, 3, 4, 5, 6, 7);
    public static final String HISTORY_FOLDER = "limine_history";
    public static final String MANIFEST_FILE = "snapshots.json";
    public static final String DEFAULT_CONFIG_FILE_PATH = "/etc/default/limine";
    public static final String TMP_CONFIG_FILE_PATH = "/tmp/limine-snapper-sync.conf";
    public static final String LIMINE_CONFIG_FILE = "limine.conf";
    public static final String DEFAULT_TIMESTAMP = "1970-01-01 00:00:00";
    public static final String NO_FILE_NAME = "is_not_available";
    public static final String RSYNC_CMDLINE = "rsync -aAhHxX --delete --info=progress2 ";
    public static final String PROC_CMDLINE = "/proc/cmdline";
    public static final String BACKUP_SUFFIX = ".old";
    public static final String RESTORE_LOCK_FILE = "/run/lock/limine-snapper-restore.lock";
    public static final List<String> KERNEL_PARAMETERS_TO_ADD = new ArrayList<>();
    public static final List<String> KERNEL_PARAMETERS_TO_REMOVE = new ArrayList<>();
    public static final List<String> KERNEL_PARAMETERS_TO_REPLACE = new ArrayList<>();
    public static final LinkedHashSet<String> EXCLUDE_SNAPSHOT_ENTRIES = new LinkedHashSet<>();
    public static final LinkedHashSet<String> EXCLUDE_SNAPSHOT_TYPES = new LinkedHashSet<>();
    public static final String SNAPSHOTS_NODE_NAME = "Snapshots";
    public static final int AUTO_SNAPSHOT_LIMIT = -1;
    public static String LSS_STATE_DIR = "/var/lib/limine/";
    public static int MAX_SNAPSHOT_ENTRIES = Integer.MAX_VALUE;
    public static boolean SNAPSHOT_WRITABLE = false;
    public static boolean SET_SNAPSHOT_AS_DEFAULT = false;
    public static String COMMANDS_BEFORE_SAVE;
    public static String COMMANDS_AFTER_SAVE;
    public static Double LIMIT_USAGE_PERCENT = 85.0;
    public static RestoreMethod RESTORE_METHOD;
    public static boolean ENABLE_TMP_CONFIG = false;
    public static boolean RESTORE_MODE = false; // If true, it will disable some error messages.
    public static String SNAPPER_CONFIG_NAME = null;
    public static String SNAPPER_LIST_CMDLINE = null;
    public static String TARGET_OS_NAME = null;
    public static String FS_UUID = null;
    public static String SPACES = "     ";
    public static String SNAPSHOT_FORMAT_CHOICE = "0";
    public static Hash HASH_FUNCTION = Hash.SHA256;
    public static Integer CONFIG_BACKUP_THRESHOLD = 6;
    public static boolean QUIET = false;
    public static boolean USE_OPENSUSE_LAYOUT = false;
    public static boolean ENABLE_COLOR = false;
    public static String ROOT_SUBVOLUME_PATH = null;
    public static String ROOT_SNAPSHOTS_PATH = null;
}
