# Changelog

## 1.32.1 - 2026-10-03

### Fixed

* Add a JSON parsing filter to ignore invalid or non-JSON lines produced by Snapper when using unsupported locales or
  other unexpected output. (Fix #18)
* Do not ignore new snapshots with older UTC timestamps when the last UTC timestamp in the manifest is in the future due
  to an incorrect hardware clock after switching between operating systems. (Fix #17)
* Fix an issue where `snapper list-configs` was empty after initializing the `root` Snapper config by removing the
  `--no-dbus` option from the Snapper CLI.

### Added

* Add Russian translation. (@wehrwolfmann for !71)

## 1.32.0 - 2026-09-20

### Added

- Add a translation mechanism to support multiple languages. Thanks @wehrwolfmann for !70
- Config: Exclude Windows boot entries from snapshots by default.

### Changed

- Replace Snapper CSV parsing with JSON parsing to avoid issues with escaped characters.
- Improve Macro replacer performance.
- Bump GraalVM built tool and Jackson versions.

### Removed

- `limine-snapper-sync` CLI: Drop the unnecessary `--no-force-save` option.
- Remove the deprecated JSON field `espDirPath`, which duplicated another field.

## 1.31.0 - 2026-06-30

### Changed

- Replaced `bootctl --print-esp-path` with simple internal boot partition detection.
- Moved lock files from `/tmp/` to `/run/lock/` for consistency with the FHS.
- limine-snapper-notify: Exit quietly when `notify-send` is not available.
- Bump GraalVM build tool and jackson-databind versions.

### Fixed

- Snapper validation now shows a clear error message instead of the raw command error output for invalid snapper config.

## 1.30.1 - 2026-06-04

### Fixed

- `MAX_SNAPSHOT_ENTRIES` falls back to `auto` when unset.

## 1.30.0 - 2026-06-04

### Changed

- `MAX_SNAPSHOT_ENTRIES` defaults to auto mode. Logs a console warning when older entries are pruned.
- `FS_UUID` is auto-configured if not set.
- Improved detection of invalid config when `FS_UUID` is manually set by the user.
- `ROOT_SUBVOLUME_PATH` and `ROOT_SNAPSHOTS_PATH` are auto-configured if not explicitly set.
- `inotifywait` fallback now shows an info message instead of a warning when Snapper plugin integration is active.
- Improved restore safeguards by adding additional validity checks for subvolume paths after mount.
- Separated restore logic into distinct Btrfs and kernel restore functions.
- Reordered kernel restore phases to reduce unnecessary writes and improve SSD lifespan.

### Fixed

- Fixed an issue where pacman lock cleanup could fail on fast startup in certain cases.

## 1.29.0 - 2026-05-19

### Added

- New `--add <ID>` option to prevent out-of-sync issues when many snapshots are added in a very short time.
- Thread pool with 5 threads for readers and writers to allow parallel execution and reduce sync time.
- `limine-snapper-restore`: auto-detects a COSMIC terminal when `TERMINAL` is not set.

### Changed

- Move the 3rd manifest backup location from `/var/lib/limine/snapshots.json` to
  `/var/cache/boot/<machine-id>/lss/snapshots.json` for more consistent backup restoration.
- Renamed the pacman hook script limine-snapper-lock; it now uses the shared mutex library instead of its own mutex
  implementation.
- Simplified CLI output and restore messages.
- Bump GraalVM build tool and jackson-databind versions.

### Removed

- snapper plugin: Removed the 1-second delay when adding a snapshot, reducing wait time before adding the next snapshot,
  since `--add` now handles out-of-sync prevention.

### Fixed

- Automatic cleanup of the pacman lock after restore when a custom `DBPath` is set in `/etc/pacman.conf`.

## 1.28.0 - 2026-05-02

### Added

- Auth helper avoids manually typing `sudo` for each CLI command in any terminal by using configured `AUTH_METHOD`
- Support Limine v12 `$boot():` path syntax
  for [gzip decompression](https://github.com/Limine-Bootloader/Limine/blob/v12.x/CONFIG.md#paths).
- Warn about unsupported Limine path resources (e.g. `hdd()`, `odd()`) and skip invalid snapshot image creation.

## 1.27.0 - 2026-04-25

### Added

- `EXCLUDE_SNAPSHOT_ENTRIES` allows excluding kernel entries from newly created snapshots.
- `limine-snapper-info` shows which kernel entries or snapshot types are excluded.
- Case-insensitive matching of "Snapshots" entry is allowed (e.g. `//+snapshots`)
- Safeguards for snapshot entry creation:
    * Skip invalid snapshot-like entry names (e.g. `//my snapshots` or `//snapshot menu`) to avoid recursive or
      duplicate entries.
    * Skip backup-like entry names (e.g. `//Backup`), which are excluded from snapshot entries by default.

### Changed

- Reuse the "Snapshots" node in LimineWriter instead of searching for it again.
- Simplify tree node lookup logic.

### Fixed

- Entry names containing macros are resolved to their clean equivalent.
- Fixed ESP path detection for bootctl output containing multiple lines.
- Merge stderr into stdout for subprocess output instead of separate streams.

## 1.26.0 - 2026-04-14

### Added

- Pre- / post-hook mechanism allows custom scripts to run before and after kernel management and limine.conf updates.

### Deprecated

- The inflexible config options `COMMANDS_BEFORE_SAVE` and `COMMANDS_AFTER_SAVE` are deprecated in favor of the hook
  mechanism and will be removed in a future major release.

### Fixed

- Fixed snapper plugin issue where `systemd-run` unit names are not unique, causing failures when running multiple
  `snapper modify` with the same snapshot ID in a short time (#12)

## 1.25.0 - 2026-04-02

### Added

- `limine-snapper-info` now prompts to clean up unused files when any are detected.
- A 3rd backup of the manifest is stored in `/var/lib/limine/` on a native Linux filesystem by default.
- Automatic recovery from the 3rd backup if two manifest files on the FAT32 boot partition are corrupted due to
  hardware issues, unexpected power loss, or user errors.

### Changed

- Use native Files API instead of external commands (cp, mv, rm) for file operations.
- Simplified error and info messages for clarity.
- Bump GraalVM build tool and jackson-databind versions.

### Fixed

- `limine-snapper-info`: **FS UUID** is no longer highlighted in red after initialization when no snapshot entries
  exist, reducing user confusion.

## 1.24.0 - 2026-03-25

### Added

- `limine-snapper-info`: Verify files in parallel using multiple threads to reduce execution time.
- `limine-snapper-restore`: Add additional fallback terminals when `TERMINAL` is not set and no default terminal apps
  are available.

### Changed

- Bump GraalVM build tool and jackson-databind versions.

## 1.23.1 - 2026-03-08

### Fixed

- Disable snapper's D-Bus option when running in a chroot environment.
- `limine-snapper-notify`: Only parse required configuration options instead of loading all key=value pairs.

## 1.23.0 - 2026-02-21

### Added

- Add `limine-snapper-remove` to remove Limine snapshot boot entries and free up space on the boot partition.
- Support an `auto` mode for `MAX_SNAPSHOT_ENTRIES`, which automatically deletes snapshot boot entries based on
  `LIMIT_USAGE_PERCENT`.
- Implement debounce mechanism to group many simultaneous snapshot deletions into a single sync, improving efficiency.
- Warn users if an OS entry lacks the required kernel command line needed for snapshot boot.

### Removed

- Remove the deprecated `LIMIT_NUMBER` config option, as it is redundant with `MAX_SNAPSHOT_ENTRIES`

### Fixed

- Remove redundant optional `dunst` dependency (upstream changes broke compatibility). Use `libnotify` instead, as
  `dunst` itself depends on `libnotify`.
- Ensure correct log order when multiple logger processes run concurrently by waiting for each to finish.

## 1.22.2 - 2026-02-08

### Fixed

- Auto-update OS snapshot name to match the main OS entry name when it is outside a `/Snapshots` menu.
- Fix case handling when parsing entry options from Limine comments.

## 1.22.1 - 2026-02-05

### Fixed

- Fix handling of the `EXCLUDE_SNAPSHOT_TYPES=` option.
- Adjust GraalVM native-image build flags for different x86_64 CPUs.
- Preserve CLI progress output (e.g. rsync) by streaming bytes instead of lines.

## 1.22.0 - 2026-01-31

### Added

- `limine-snapper-info`: displays which snapshot IDs are affected by corrupted or missing files.
- `limine-snapper-info`: displays total snapshot files and their size.
- `limine-snapper-restore`: warns users when a selected snapshot contains corrupted or missing files during restore.
- `limine-snapper-sync`: adds a new config option `EXCLUDE_SNAPSHOT_TYPES` to exclude snapshots of matching types.
- `limine-snapper-notfy`: adds "Disable" button to turn off notifications until reboot.

### Changed

- Migrated build system from Maven with JVM to Gradle with GraalVM
    * Runs as a native executable, no JRE required.
    * Performance improved by over 2.4x at runtime.

### Fixed

- Corrected the logo icon resolution to 128×128 for GNOME.
- `limine-snapper-info`: hides "Last snapshot" if no snapshots exist during initialization.

## 1.21.0 - 2026-01-24

### Added

- Automatically determine the default OS name from `PRETTY_NAME` or `NAME` in `/etc/os-release` when `TARGET_OS_NAME` is
  not set.
- Provide more details about boot partition usage so that non-technical users could understand why the limit is
  exceeded.

### Changed

- Rename the configuration option `UUID` to `FS_UUID` for clarity, as `UUID` is too generic and could refer to a
  filesystem, GPT, or backup filesystem. The old `UUID` remains for backward compatibility.
- The `inotify-tools` package is no longer required; the snapper plugin is used instead.

### Fixed

- Abort sync immediately if `snapper list-configs` fails in a chroot environment.

## 1.20.0 - 2026-01-15

### Added

- Config option `SNAPSHOT_FORMAT_CHOICE` adds 3 snapshot naming formats with descriptions for ultra-wide monitors.
- Automatic rootless desktop notifications for ESP usage limits, hardware issues, or configuration errors.
- Config option `ENABLE_NOTIFICATION=yes|no` to enable or disable automatic desktop notifications.

## 1.19.2 - 2026-01-11

### Fixed or improved

- Improved safeguards by automatically aborting sync when no standard fallback hash function is available.
- Automatically disable colored log output on terminals without color support.
- `limine-snapper-info`: Skip file hashing when required hash functions are missing.
- `limine-snapper-restore`: Exit with status code 0 instead of 1 when canceled.

## 1.19.1 - 2026-01-04

### Fixed or improved

- Terminal input reader (`TerminalReader`):
    * Correctly handles UTF-8 characters
    * Avoids busy-looping CPU while waiting for user input
    * Supports the Delete key

## 1.19.0 - 2026-01-02

### Added

- Implemented a `TerminalReader` that supports arrow keys (Up, Down, Right, Left) for user input.
- `limine-snapper-restore`: Up/Down arrow keys can be used to select a snapshot ID.

### Changed

- Upgraded the `jackson-databind` dependency from v2 to v3 for JSON reading/writing.

### Fixed

- `limine-snapper-list`: Table formatting falls back to ASCII when Unicode is not available in TTY.

## 1.18.1 - 2025-12-12

### Fixed

- Return exit code 0 instead of 1 when a restore operation is intentionally aborted.
- Display an informational message indicating **which restore method will be used** before the restore process begins.
- Add comments marking where snapshot entries were generated, improving transparency and preventing unintended manual
  edits of auto-generated entries in limine.conf

## 1.18.0 - 2025-11-27

### Added

- **3rd restore method:** `opensuse` (`snapper`), supporting native `snapper rollback <id>` on OpenSUSE-style layouts.
- Automatic Btrfs layout validation according to your restore method configuration. If the layout is invalid, snapshot
  creation is stopped to prevent user errors.
- If a hash mismatch is detected more than 3 times, Btrfs root and home subvolumes are automatically set to read-only
  to prevent further metadata corruption caused by serious non-ECC RAM faults.

### Changed

- The config option `ENABLE_RSYNC_ASK="yes|no"` has been replaced with `RESTORE_METHOD="rsync|replace|opensuse"`.

## 1.17.1 - 2025-10-28

### Fixed and improved

- Avoid showing "null" when no snapshot description is available in Snapper boot entry comments.
- Show snapshot count and limit in the "Snapshots" boot entry comment to clarify separate limit settings in Snapper and
  Limine-snapper-sync and prevent user confusion.

## 1.17.0 - 2025-10-26

### Added

- Ability to replace an entire kernel command line using `SNAPSHOT_KERNEL_PARAMETERS=` (for multiple-profile UKIs).

### Changed

- Switched to the standard Java library for detecting filesystem types instead of relying on `findmnt`.
- Updated the Maven dependency for JSON reading/writing.

### Fixed

- `limine-snapper-restore` now finds an ID in the last `subvol=` flag in a kernel command line rather than the first,
  when multiple `subvol=` flags are present.
- `limine-snapper-list` no longer crashes if a snapshot has no description.

## 1.16.2 - 2025-10-18

### Fixed

- Avoid aborting restore if child subvolume paths have unexpected or special characters.
- Moving `limine.conf.tmp` to `limine.conf` **before** starting `sync` to ensure a complete write on FAT32 (**not
  after**)

## 1.16.1 - 2025-10-17

### Fixed or improved

- Improved reliability of the Snapper plugin across different environments.
    * Simplified the `run_sync()` logic.
    * Added checks to ensure some standard core commands are available before use.
    * Ensured safe fallback behavior on non-systemd systems and minimal environments when the core commands are missing.
- Prevent process cancellation when `MAX_SNAPSHOT_ENTRIES` is invalid by using the default fallback value.
- Added a warning to make users aware that the Snapper snapshot limit and `MAX_SNAPSHOT_ENTRIES` are separate settings
  to prevent confusion about snapshot counts.
    * This warning disappears when `MAX_SNAPSHOT_ENTRIES` is set to any value other than the default.

## 1.16.0 - 2025-10-10

### Added

- Handle read-only root subvolume during restoration:
    * Stop `rsync` restoration process
    * For Btrfs, automatically make the subvolume writable so the restore can continue
- Added Snapper plugin:
    * Automatically triggered by Snapper to run `limine-snapper-sync` directly
    * Using the service `limine-snapper-sync.service` or `limine-snapper-watcher` is no longer required (they are
      now optional)
- New configuration option `SET_SNAPSHOT_AS_DEFAULT` to control whether a restored snapshot's subvolume becomes the
  default.

### Changed

- Disabled the timeout for the restoration prompt notification

## 1.15.0 - 2025-09-18

### Added

- Support for `SNAPSHOT_KERNEL_PARAMETERS+` to add extra kernel parameters dynamically
- Support for `SNAPSHOT_KERNEL_PARAMETERS-` to remove specific kernel parameters dynamically

### Changed

- Switched from checking supported Limine major versions to explicitly checking unsupported Limine major versions

### Removed

- Removed unnecessary tool name from version info output in `limine-snapper-info`

## 1.14.0 - 2025-09-14

### Added

- Support for xxHash, a fast hashing function that produces shorter hashes compared to others (e.g. sha256).

### Changed

- Improved the `lss-check-java` helper script.
- Shortened info messages by removing the word "successfully".

## 1.13.1 - 2025-08-09

### Fixed

- Removed the `BACKUP_THRESHOLD is deprecated` warning to reduce confusion. Note: `BACKUP_THRESHOLD` is no longer used.

## 1.13.0 - 2025-08-07

### Added

- Support for snapshot boot entries with nested subkernels, allowing defining multiple boot profiles.<br>
  Compatible with limine-entry-tool v2: multiple cmdlines per kernel entry are supported.

### Changed

- Simplified XML writing by generating raw strings instead of Java XML libraries.
- Renamed the version field and its getter/setter methods for clarity.

### Fixed

- Escaped quotes in config values are now preserved; only leading and trailing quotes are removed.

## 1.12.1 - 2025-07-25

### Fixed

- Renamed config option `BACKUP_THRESHOLD` to `CONFIG_BACKUP_THRESHOLD` to avoid confusion, as BACKUP is too general.
- If `SPACE_NUMBER` is not set, it now correctly defaults to 5 instead of nothing.

## 1.12.0 - 2025-07-11

### Added

- Provided `/lib/limine/limine-mutex`, a shell library offering `mutex_lock` / `mutex_unlock` functions to help external
  tools safely access `limine.conf` and boot-related files on the same partition, avoiding potential conflicts.

### Changed

- Refactored ConfigReader to handle multiple config files.
- Updated `jackson-databind` dependency from 2.17.0 to 2.19.1.
- Renamed class `LimineHistory` to `Manifest` to reduce confusion.

### Fixed

- Added lockfile `limine-snapper-cleanup.lock` to prevent `limine-snapper-sync` from accessing snapper CLI during
  `snapper-cleanup.service`. Sync runs afterward. (#4)

## 1.11.0 - 2025-06-25

### Added

- New `SNAPSHOT_WRITABLE` option to make created snapshots writable when write access is needed at boot.
- Enhanced `limine-snapper-watcher` to create multiple processes in parallel, improving performance when many snapshots
  are deleted simultaneously.

### Changed

- Refactored `limine-snapper-watcher` for cleaner structure and improved readability.
- Simplified `limine-snapper-restore` by merging two redundant terminal-launching functions into one.

### Removed

- Removed the non-atomic lock file `limine-snapper-sync.lock` and replaced it with the atomic mutex `limine-global.lock`
  for reliable concurrency control.

### Fixed

- Prevented unnecessary repeated writes to `limine.conf` when multiple snapshots are deleted simultaneously.
- Sync aborts immediately during Snapper snapshot deletion if target OS name is missing in `limine.conf`,
  preventing unintended deletion of bootable files.
- Fixed incorrect version information displayed during JSON format upgrade.

## 1.10.1 - 2025-06-10

### Fixed

* Missing `@JsonIgnore` on `snapshotLine` field, which was unintentionally serialized to JSON.

## 1.10.0 - 2025-06-10

### Added

* `limine-snapper-restore`: auto-detects `kitty` terminal (by @dianaw353)
* Secondary parsing of snapshot entries from JSON to Limine entries when `allInSnapshotConfig` is not used — this is
  preparation for version 2.

### Changed

* Updated JSON format in preparation for the upcoming version 2.0.0:
    - `SnapshotEntry`: Removed redundant `name` field.
    - `KernelEntry`: Removed unused `orderNumber`.
    - `ImageDetail`: Replaced `espDirPath` with `dirPath`.
    - `CmdlineDetail`: Removed two redundant `parameters` fields.
    - Renamed `lineOrderNumber` to `lineIndex` in all entry types.

## 1.9.5 - 2025-05-18

### Fixed

* Restricted access to the temporary config file `/tmp/limine-snapper-sync.conf` for security reasons.
    - As an exception, only `limine-snapper-restore` is allowed to read this config file if all conditions are met:
        - Booted from a read-only Btrfs snapshot without overlayfs.
        - A valid Snapper snapshot path is detected.
        - The config file is owned by the root user.

## 1.9.4 - 2025-05-16

### Fixed

* Sync cached writes to FAT32 to prevent data loss in case of power failure or forced shutdown.
* Don't touch an OS entry with the same name if its machine-ID does not match and no identical OS entry is found.
* Fixed restore for Btrfs root partition without a root subvolume using `rsync`.

## 1.9.3 - 2025-05-10

### Fixed or improved

* When booting from a read-only Btrfs system, `/etc/default/limine` is automatically copied to `/tmp/limine`,
  allowing configuration.
* `QUIET_MODE=yes` to hide all success messages and reduce log spam. This applies only to `limine-snapper-sync`.
* Automatically removes the `//Snapshots` entry if no snapshots are present, preventing boot panic when selecting this
  entry.
* Increased the default usage limit from 80% to 85%.

## 1.9.2 - 2025-04-26

### Fixed

* Replaced unsafe `source` with key-value parsing when loading config files in the restore script.
* Removed `eval` and quoted strings in command arguments to ensure reliable launching in some terminals (e.g., `foot`,
  `kitty`, etc.).

## 1.9.1 - 2025-04-21

### Fixed or improved

* Improved snapshot information formatting in `limine-snapper-info` for clearer display of usage and limits.
* Avoided unsafe `source` by implementing manual key-value parsing via `read` and `export` when loading config files.

## 1.9.0 - 2025-04-17

### Added

* Skipped `bootctl` execution on non-EFI systems.
* Added hardening options to the systemd service.

### Changed

* Refactored Java version check logic.

### Removed

* Removed deprecated the service `limine-snapper-watcher.service`

## 1.8.6 - 2025-03-26

### Fixed or improved

* Implemented an atomic mutex lock to prevent concurrent processes from accessing the same resources on the ESP.
* Added automatic Java version validation. If an unsupported version is detected, an error message is logged.
* Added `JAVA_BIN_PATH` configuration option for specifying a custom Java binary path.

## 1.8.5 - 2025-03-21

### Fixed or improved

* Renamed systemd service`limine-snapper-watcher.service` to `limine-snapper-sync.service` to avoid confusion.
* Removed unnecessary `echo` in a loop while waiting for a Snapper process to finish before starting a Pacman upgrade.

## 1.8.4 - 2025-03-16

### Fixed

* Switched local time to standard UTC time globally.
  This prevents unintended deletion of all snapshot entries due to inconsistent time across different time zones.
* Enforced setting the current JSON format version when saving `snapshots.json` to avoid inconsistencies between
  different LSS versions.

## 1.8.3 - 2025-03-08

### Fixed or improved

* Enhanced protection against accidental deletion of all snapshot entries due to user error.
* Made replacing the kernel cmdline with its snapshotted cmdline more reliable to ignore misconfiguration of users.
* Improved verification of `ROOT_SUBVOLUME_PATH` and `ROOT_SNAPSHOTS_PATH` before creating a snapshot.
  This ensures the correct configuration when restoring from a snapshot.
* Reduced message code and removed some unnecessary code.
* Prevented any terminal GUI from closing automatically in case of a restore failure.

## 1.8.2 - 2025-03-06

### Fixed

* A broken cmdline will be forcibly replaced with a valid cmdline during restore to create a "backup" entry and prevent
  interruption.
* Keep any terminal GUI open on restore failure instead of closing automatically.
* Skip `reboot` command if it is not available after restore.
* Prompt for OS name if not found before starting restore.
* Allow updating `snapshots.json` when an ESP usage limit is exceeded, but do not update the last Snapper ID.

## 1.8.1 - 2025-03-01

### Improved

* Removed duplicate and unnecessary code.
* Renamed some variables and methods for better readability.
* Use Java's native library and Bash utilities instead of `grep`, `touch`, `stat` and `mkdir`.

### Fixed

- Fixed multiple commands running before or after saving `limine.conf`.
- Fixed the default icon for `libnotify` notification.
- No longer adds a snapshot entry if no snapshotted kernel entry exists in snapshots.json.

## 1.8.0 - 2025-02-23

### Added

- Support for restoring snapshots in a chroot environment.
- Automatically stop snapshot restore if the predicted ESP usage is expected to be fully exhausted.

### Changed

- `LIMIT_USAGE_PERCENT` now defaults to 80 if not set.
- Improved ESP usage check before creating a snapshot.

## 1.7.1 - 2025-02-17

### Fixed

- **limine-snapper-info** now properly supports Limine version 9.

## 1.7.0 - 2025-02-16

### Added

- Added support for `path:` in `limine.conf` (Limine version 8.7.1 or newer)

### Changed

- Allow copying of any Limine key (e.g.,`resolution`) into new snapshot entry during snapshot creation.
- Converted hardcoded enums to flexible strings for JSON (de)serialization to avoid compatibility issues during upgrades
  or downgrades.
- Replaced `LIMIT_NUMBER` with `MAX_SNAPSHOT_ENTRIES`.

### Removed

- Removed the first hardcoded Limine key from each detail line in the JSON file.

## 1.6.0 - 2025-01-17

### Added

- Automatically initialize a Snapper config for `/` if one is not already present.
- Automatically create a root snapshot after initializing the Snapper config.

### Changed

- Replaced hardcoded version config with dynamic version retrieval.
- Reformatted `pom.xml` file.

## 1.5.2 - 2024-12-31

### Fixed

- No need to create a backup of snapshots.json if it does not exist.
- Improved language clarity for the configuration error message.

## 1.5.1 - 2024-12-27

### Fixed

- Renamed `BACKUP_INTERVAL` to `BACKUP_THRESHOLD` for better clarity.
- Clarified configuration descriptions for `COMMANDS_BEFORE_SAVE` and `COMMANDS_AFTER_SAVE`.

## 1.5.0 - 2024-12-24

### Added

- Support for reading `/etc/default/limine.conf`, which overrides the existing `/etc/limine-snapper-sync.conf`.
- New config option `COMMANDS_BEFORE_SAVE` for running custom commands before saving `limine.conf`.
- New config option `COMMANDS_AFTER_SAVE` for running custom commands after saving `limine.conf`.

### Changed

- Refactored `ConfigReader` to support reading from multiple configuration files.

### Fixed

- Fixed a typo in the machine-ID regex.

## 1.4.0 - 2024-12-18

### Added

- Support for reading options in any boot entry comments.
  These options are compatible with `limine-entry-tool 1.7.0` or newer.

### Changed

- Improved the algorithm for identifying machine-ID in boot entry comments.

## 1.3.0 - 2024-12-15

### Added

- Support for using `notify-send` as an alternative to `dunst`.
- A new logo for use as the notification icon.
- A `limine-snapper-restore.desktop` app has been added.
- A method to validate names as valid file names on FAT32.
- A method to handle case-sensitive paths on FAT32.

### Changed

- Switched from static backups to time-based backups.
- Refactored variable names for clarity.
- Simplified and adjusted the README.
- Automatically create a default `//Snapshots` entry in limine.conf if both `//Snapshots` and `/Snapshots` are missing.

### Fixed

- Resolved a permission issue that prevented restore functionality when `sudo` or `pkexec` was not used.

## 1.2.0 - 2024-12-10

### Changed

- Improved the output details of the executable command method in the Java code.
- Simplified the restoration bash script by reducing its code.

### Added

- Introduced two new methods to validate EFI and BIOS Legacy modes.
- Added `TERMINAL_ARG` to the configuration file.
- Added additional default terminal for restoration, depending on different desktop environment,
  if `TERMINAL` is not set in the config.

## 1.1.2 - 2024-11-24

### Fixed

- During a restore, 'bad' bootable files should not be deleted too early; they should first be moved to the Limine
  backup if not already present.

## 1.1.1 - 2024-11-10

### Removed

- Remove the hardcoded version in the executable JAR file name to simplify use in some scripts.

## 1.1.0 - 2024-10-03

### Added

- Users can now choose a custom format for snapshot names as they appear in Limine bootloader.
- Added support for the Blake3 hash function, which is faster than SHA256.
- Automatic backups of `snapshots.json` and `limine.conf` are created before saving.
- Automatically load `snapshots.json.old` as a backup if `snapshots.json` is corrupted.
- Using two processes to calculate two hashes of the same file to better detect corruption/hardware issues.
- Damaged bootable files are now automatically replaced with healthy files when creating a new snapshot.

### Changed

- README: Installing `dunst` for desktop notification of one-click restoration is now optional, not mandatory.

## 1.0.0 - 2024-08-20

### Added

- Limine-Snapper-Sync is finally created
- Changelog
- License