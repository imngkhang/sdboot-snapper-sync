# Limine-Snapper-Sync

### Contents

- [Description](#description)
- [Screenshots](#screenshots)
- [Requirements](#requirements)
- [Installation](#installation)
- [Boot Configuration](#boot-configuration)
- [Tool Configuration](#tool-configuration)
- [Hooks](#hooks)
- [Translations](#translations)
- [Troubleshooting](#troubleshooting)
- [Features](#features)
- [Donate](#donate)
- [Planned features](#donate)

---

## Description

**Limine-Snapper-Sync** integrates **[Limine](https://github.com/limine-bootloader/limine)** bootloader with **[Snapper](https://github.com/openSUSE/snapper)**,
synchronizing snapshot boot entries with Btrfs snapshots and updating the bootloader configuration accordingly.

<details>
<summary>What it does</summary>

* **Adding Snapshots:**<br>
When a Snapper snapshot is created, a snapshot boot entry with its matching kernel versions is added to Limine.

* **Deleting Snapshots:**<br>
Deleting a Snapper snapshot also removes the related snapshot boot entry and kernel files.

* **Restoring Snapshot:**<br>
The tool provides `rsync`, `replace` or `snapper` (`opensuse`) to restore a selected snapshot with matching kernel versions.

* **Backup Entry:**<br>
After restoring, a "backup" snapshot entry is added to Limine, allowing you to revert or remove it later.

* **Boot Files Repair:**<br>
Automatically detects and repairs a corrupted bootable snapshot file by replacing it with a matching healthy file
during new snapshot creation.
Additionally, it logs hardware issues if two hashes of the same bootable file don’t match.

**Note:**
- Does **not** generate kernel boot entries for newly installed kernels. Use [Entry Automation for Limine bootloader](https://gitlab.com/Zesko/limine-entry-tool) for that functionality.
</details>

<details>
<summary>Why it was created</summary>

**Limine-Snapper-Sync** was created to provide a faster, more efficient, and easier-to-manage snapshot boot solution for Limine:

* **Faster encrypted snapshot boot:**
    - Bootable files (kernel, initramfs/initrd, or UKI) are stored outside a fully encrypted partition,
      allowing faster decryption and boot times.
    - Allows Plymouth graphical password prompts during boot for a more polished user experience.
* **Multiple boot support:**
    - Each mutable Linux distro can have its own snapshot entries in separate boot submenus,
      making it easier to select OS or snapshot entry.
    - Allows switching between A/B systems by creating a new subvolume with its own machine-ID and `subvol=` boot parameter.
* **Flexible and efficient design:**
    - Avoids reliance on third-party drivers (e.g., filesystems, LUKS2, different encryption algorithms, LVM, etc.);
      all of this is handled by kernel, initramfs, or UKI, improving compatibility, performance and reliability.
* **Instant snapshot updates:**
    - Automatically updates snapshot boot entries in real time without scanning all subvolumes or snapshots.
* **Rollback and "Backup" snapshot support:**
    - Allows quick system restore and adds a "backup" snapshot boot entry.
* **Checksum correction & faulty RAM detection:**
    - Detects faulty RAM or CPU cache by comparing two independently generated hashes.
    - Automatically corrects checksum errors by rechecking up to 3 times on mismatch.
    - If mismatches occur more than 3 times, Btrfs root and home are set to read-only to prevent further corruption.
</details>

<details>
<summary>Limitations</summary>

* **Old snapshots before installing the tool**:
    - Older snapshots will not be added to Limine because the required kernel files are not included in the Btrfs snapshots.
* **Limited Btrfs layout support:**
    - Supports any Snapper-managed Btrfs layout, but not arbitrary Btrfs layouts without Snapper.

</details>

#### 5 commands, each without options:

* `limine-snapper-sync` updates Limine snapshot entries with current Snapper snapshots.
* `limine-snapper-list` displays all available snapshots in Limine's boot menu.
* `limine-snapper-info` shows details about versions, total bootable snapshots, kernel verification status.
* `limine-snapper-restore` restores a selected snapshot to your system.
* `limine-snapper-remove <ID 1>..<ID 2>` removes snapshot boot entries to free up space on the boot partition.

---

## Screenshots:

<details>
<summary>📷 Show screenshots</summary>

Choose between two different Limine menu layouts:

### Snapshots-menu inside OS entry

The "Snapshots" menu is integrated within the OS entry.

![image](screenshots/1.jpg){width=800 height=800}

Expand it to view snapshot details such as **ID**, **date** and **description**. 
Each Linux distro can have its own separate snapshot list.

![image](screenshots/2.jpg){width=800 height=800}

### Snapshots-menu outside the OS entry

The "Snapshots" menu is listed separately from the OS entry.

![image](screenshots/7.jpg){width=800 height=800}

After booting from a snapshot, a notification appears, allowing one-click restoration.

![image](screenshots/3.jpg){width=400 height=400}

Clicking "Restore now" provides more details about the restore process.

![image](screenshots/4.jpg){width=800 height=800}

Run `limine-snapper-info` to verify bootable snapshot files.

![image](screenshots/6.jpg){width=800 height=800}

</details>

---

## Requirements

* A FAT32 boot partition of at least **4 GiB** is recommended, depending on how many snapshots and kernel versions you keep.
  - **Note:** NVIDIA drivers and DKMS modules can use over 300 MiB per kernel version. Your boot partition should be large enough for future kernels and snapshots without resizing.
* Limine version **8 or newer**
* `snapper` and `btrfs-progs`
* Standard coreutils: `cp`, `mv`, `rm`, `sha256sum`
* Standard util-linux: `logger`, `mount`, `umount` and `flock`
* `gradle` and GraalVM 25 (build-time only)


---

## Installation:

### Arch Linux:

**🔄 Switching from GRUB, systemd-boot, or rEFInd to Limine?**<br>
**Tip:**
Limine setup allows you to keep your existing EFI bootloader as a fallback.<br>
It won’t overwrite your existing EFI bootloader unless you explicitly choose to replace it.<br>
Just follow the **easy setup with `limine-entry-tool`** below if you are unsure.

<details>
<summary>🚀 Easy setup with limine-entry-tool</summary>

1. Choose one of the two initramfs integration packages for [limine-entry-tool](https://gitlab.com/Zesko/limine-entry-tool):
   * For **Dracut** users: Install [limine-dracut-support](https://aur.archlinux.org/packages/limine-dracut-support/)
   * For **mkinitcpio** users: Install [limine-mkinitcpio-hook](https://aur.archlinux.org/packages/limine-mkinitcpio-hook/)
     1. Manually add `btrfs-overlayfs` after `filesystems` in `HOOKS=(...)` in `/etc/mkinitcpio.conf`<br>
        (**Note:** For systemd hooks, use `sd-btrfs-overlayfs`, since `btrfs-overlayfs` is incompatible.)
     2. Run `limine-update` to rebuild the initramfs.
2. Install [limine-snapper-sync](https://aur.archlinux.org/packages/limine-snapper-sync/) from AUR.
3. Enable automatic syncing:
```bash
systemctl enable --now limine-snapper-sync.service
```
4. (Optional) Install `snap-pac` to trigger Snapper to create snapshots during system updates.

**Tip:** After changing your config in `/etc/default/limine`, just run `limine-update` to apply the changes.
It will rebuild your initramfs or UKI, similar to reinstalling the kernel.

</details>

**or**

<details>
<summary>Manual setup without limine-entry-tool</summary>

1. Make sure Limine can boot your system by following the [Arch Wiki:Limine](https://wiki.archlinux.org/title/Limine#Limine-Snapper-Sync_setup).
1. Properly configure `limine.conf` to work with this tool. See [Boot Configuration](#boot-configuration) for details.
1. Install [limine-snapper-sync](https://aur.archlinux.org/packages/limine-snapper-sync/) from AUR.
1. If your boot partition is mounted at `/efi`, `/boot`, `/boot/efi`, or `/limine` and contains `limine.conf`, it is detected automatically.
    * If it is not detected, create or edit `/etc/default/limine` and set `ESP_PATH` to your FAT32 boot partition.
1. Create a root snapshot using `snapper`, then test `limine-snapper-sync` to see if
   `Updated: $ESP_PATH/limine.conf`.
1. If everything works, enable automatic syncing:
```bash
systemctl enable --now limine-snapper-sync.service
```

**Tip:**<br>
For headless or server environments, install [limine-snapper-cli](https://aur.archlinux.org/packages/limine-snapper-cli/) without desktop notifications.

If you write your own scripts or tools that access or modify `limine.conf` or other files on the same boot partition,  
make sure to source the Bash library `/usr/lib/limine/limine-mutex` and use the functions `mutex_lock "name"` and `mutex_unlock` to prevent race conditions.

</details>

### Other Linux distributions:

This tool should work on most Linux distributions (with or without systemd).
No pre-built packages are available for different package managers, but package maintainers are welcome to create them.

<details>
<summary>Manual installation</summary>

* Ensure that Limine can boot your system.
* Download [GraalVM 25](https://www.graalvm.org/latest/reference-manual/native-image/)
* Clone the repository:
```shell
git clone https://gitlab.com/Zesko/limine-snapper-sync.git
cd limine-snapper-sync
```
* Build the project
```shell
JAVA_HOME=<path-to-graalVM> gradle clean nativeCompile
```
* Install the necessary files:
```shell
cp build/native/nativeCompile/limine-snapper-sync /usr/lib/limine/
cp install/arch-linux/usr/bin/* /usr/local/bin/
cp -r install/arch-linux/usr/lib/limine /usr/lib/
cp -r install/arch-linux/usr/lib/snapper /usr/lib/
cp -r install/arch-linux/usr/lib/systemd/* /etc/systemd/ # Optional for systemd
cp install/arch-linux/etc/xdg/autostart/*.desktop /etc/xdg/autostart/` # Adjust based on ${XDG_CONFIG_DIRS}
cp install/arch-linux/etc/*.conf /etc/
install -Dm644 install/arch-linux/etc/default/sdboot-snapper-sync /etc/default/sdboot-snapper-sync
for po in po/*.po; do lang="$(basename "$po" .po)"; mkdir -p "/usr/share/locale/$lang/LC_MESSAGES"; msgfmt "$po" -o "/usr/share/locale/$lang/LC_MESSAGES/limine-snapper-sync.mo"; done
````
* Install `libnotify` or [dunst](https://github.com/dunst-project/dunst) for desktop notifications on X11 or Wayland.
* Configure systemd-boot Type #1 entries under `$ESP_PATH/loader/entries`.
* Test running `limine-snapper-sync`; it writes snapshot entries there in BLS format.

**Tip:**<br>
If you write your own scripts or tools that access or modify `limine.conf` or other files on the same boot partition,  
make sure to source the Bash library `/usr/lib/limine/limine-mutex` and use the functions `mutex_lock "name"` and `mutex_unlock` to prevent race conditions.

</details>

---

## Boot Configuration:

This version supports systemd-boot only. Keep normal Type #1 BLS entries in
`$ESP_PATH/loader/entries`; each entry must provide `linux` (or `efi` for a UKI),
and may provide one or more `initrd` lines and an `options` line. Entries are
selected by `machine-id`, the machine-id filename prefix, or a title matching
`TARGET_OS_NAME`. Snapshot entries are generated alongside them in BLS format.

For ESP mounts at `/efi` or `/boot/efi`, the sync process calls
`kernel-install --esp-path=<ESP> add-all` to install kernels and generate BLS
entries. `/boot` remains the snapshot storage location when it is the configured
boot partition.

---

## Tool Configuration

The config file is located at `/etc/default/sdboot-snapper-sync`.

**Note:**

- When booting into a read-only snapshot where `/etc/*` cannot be configured,
`/etc/default/sdboot-snapper-sync` is automatically copied to the temporary location `/tmp/limine-snapper-sync.conf`,
allowing temporary configuration.

Adjust the settings in `/etc/default/sdboot-snapper-sync` as needed.

```properties
### OS Entry Targeting
### This tool does not automatically know the custom OS name used in $ESP_PATH/limine.conf.
### Set the OS name here to match the entry name in $ESP_PATH/limine.conf.
###
### Tips:
### * Alternatively, add "comment: machine-id=<machine-id>" under your OS entry name in $ESP_PATH/limine.conf.
###   The machine-id allows the tool to automatically target the correct entry. Changing the OS name does not matter.
### * If unset, it reads PRETTY_NAME or NAME from /etc/os-release, with a fallback to /etc/lsb-release.
###
#TARGET_OS_NAME="Arch Linux"


### Boot Partition Path
### If not set, it is found automatically by checking /efi, /boot, /boot/efi, and /limine.
### Set this to skip the automatic search or if your boot partition uses a different mount path.
#ESP_PATH="/boot"


### Boot Partition Usage Limit
### Sets the maximum usage (in percent) for your FAT32 boot partition. Value must be between 1 and 99. Default: 85.
### No new snapshot entries will be added to Limine if this limit is reached.
### A larger FAT32 boot partition allows for more snapshot entries.
###
### Tips:
### * You will be alerted when ENABLE_NOTIFICATION is set to "yes" and the limit is reached during snapshot creation.
### * Do not increase the limit to 95% or higher on a small boot partition, as restoration will be blocked due to insufficient free space.
###
LIMIT_USAGE_PERCENT=85


### Max Snapshot Entries
### Limits how many snapshot entries appear in the "Snapshots" boot menu.
### This depends on the size of your FAT32 boot partition and how many different kernel and module versions you want to keep.
### A larger FAT32 boot partition allows more snapshot entries.
###
### Value: Number or "auto"
###
### What is "auto":
### It is flexible and automatically removes older snapshot boot entries based on LIMIT_USAGE_PERCENT without warning.
### A warning is only triggered if no snapshot boot entries are left and usage still exceeds LIMIT_USAGE_PERCENT.
###
### WARNING:
### If LIMIT_USAGE_PERCENT is set very low (e.g. 1%) and the boot partition still has no free space,
### "auto" will delete all snapshot boot entries, including all kernel versions.
###
### Default: "auto" if unset
#MAX_SNAPSHOT_ENTRIES=auto


### Exclude Snapshot Types
### If a snapshot has a matching type, it will not be added into Limine.
###
### Example:
###   EXCLUDE_SNAPSHOT_TYPES="pre, post"
###
#EXCLUDE_SNAPSHOT_TYPES=""


### Exclude Entries from Snapshots
### Matching kernels or entries are excluded from Limine snapshots. This helps reduce boot partition space usage.
###
### WARNING:
### - Restoring an old snapshot may use outdated config and exclude unintended entries from the current "backup" snapshot.
### - Excluded entries are preserved after restore, but may not match an old system version. They should be removed or updated manually.
###
### Note:
### - "backup*" and "*snapshot*" entries are always excluded by default, regardless of any configuration.
###
### Wildcards:
###  "*" matches any sequence of characters
###
### Example: excluding all fallback kernel entries
#EXCLUDE_SNAPSHOT_ENTRIES="*fallback"
EXCLUDE_SNAPSHOT_ENTRIES="Windows*, windows*"


### Snapshot Kernel Parameters
### Add or remove kernel parameters for new snapshot boot entries.
###
### - SNAPSHOT_KERNEL_PARAMETERS+ : Add extra kernel parameters
### - SNAPSHOT_KERNEL_PARAMETERS- : Remove specific kernel parameters
### - SNAPSHOT_KERNEL_PARAMETERS= : Replace the entire kernel command line (note: for multiple-profile UKIs only)
###
### Tips:
### - No quotes are needed at the start or end of command lines, since quotes inside are not escaped.
### - Do not add `#` as a comment at the end of command lines.
###
### Examples:
#SNAPSHOT_KERNEL_PARAMETERS-=quiet splash
#SNAPSHOT_KERNEL_PARAMETERS+=systemd.volatile=overlay


### Make Snapshots Writable
### Set to 'yes' to make created snapshots writable. (yes|no)
### This may be required by some programs or display managers (e.g. GDM) that need write access at boot when overlayfs is not used.
###
### WARNING:
### Writable snapshots can be modified and will no longer reflect the original 1:1 state.
### Enable only if absolutely necessary.
###
### IMPORTANT:
### If you boot into a writable snapshot, you should immediately click "Restore now" to avoid making unintended changes.
#SNAPSHOT_WRITABLE=no


### Snapper Configuration Name
### Specify the Snapper configuration name. If not set, the tool will automatically detect it from "snapper list-configs".
### Specifying it manually improves performance by avoiding automatic detection overhead.
#SNAPPER_CONFIG_NAME="root"


### Root Subvolume Path
### If not set, the path is automatically detected during snapshot creation.
### Override this value only if the detected path is incorrect.
#ROOT_SUBVOLUME_PATH="/@"


### Root Snapshot Path
### If not set, the path is automatically detected during snapshot creation.
### Override this value only if the detected path is incorrect.
#ROOT_SNAPSHOTS_PATH="/@/.snapshots"


### Restore Method Selection
### Choose a restore method depending on your Btrfs layout and boot configuration.
### Indicators: (+) Advantage, (-) Disadvantage, (o) Neutral/Special note
###
### rsync:
### + Works on a system running on a root partition without a root subvolume.
### + Supports multiple Linux installations on the same Btrfs filesystem.
### + No issues with overlayfs used as a temporary writable layer on top of a read-only snapshot.
### o Keeps the original subvolume ID after restore.
### - Slower because all data is copied 1:1 from a selected snapshot to the root subvolume.
### - Does not track which snapshot the restored data originally came from.
###
### replace:
### + Very fast, creates a new subvolume from a selected snapshot and replaces the old one.
### + Supports multiple root subvolumes (e.g., multiple distros) on the same Btrfs filesystem.
### + No issues with overlayfs used as a temporary writable layer on top of a read-only snapshot.
### o Changes the subvolume ID after restore.
### - Restored subvolumes lose tracking of their relationship to older snapshots in the Snapper list.
### - Cannot restore when system runs on a root partition without a root subvolume.
###
### opensuse (alias of "snapper"):
### + Supports native snapper rollback.
### + Very fast, 'snapper rollback' creates two snapshots (read-only and writable).
### + Tracks snapshot relationships in the Snapper list, unlike "rsync" and "replace".
### o Requires an OpenSUSE-style layout compatible with snapper rollback.
### o Keeps the original main subvolume unchanged as the oldest base.
### - Does not support multiple Linux distros on the same Btrfs (only one default subvolume for booting).
### - Refuses to work when overlayfs is used as a temporary writable layer for testing changes on an immutable system.
### - Some init services (e.g., display managers) require write access and may fail when booting a read-only snapshot.
### (Info: In major v2, new 4th restore method would remove these last two disadvantages)
###
### Recommendation: Choosing a safe restore method if unsure:
### * If your system normally runs on a root subvolume, use "replace" or "rsync". (typical for most Arch-based installations)
### * If your system normally runs on a root Btrfs partition without a root subvolume, use "rsync".
### * If your system normally runs on a writable snapshot, use "snapper". (For OpenSUSE or an OpenSUSE-like layout).
###
### Available restore methods: "replace", "rsync" or "snapper"
RESTORE_METHOD=replace


### Set Snapshot as Default
### After restoring a snapshot, you can choose whether its new subvolume should automatically become the default (yes|no).
###
### What is a "default subvolume"?
### It is the subvolume your system boots into if no subvolume is specified in the kernel command line.
### On OpenSUSE, it is also required for 'snapper rollback'.
###
### yes: A newly created snapshot will automatically become the default subvolume after restoration.
###       WARNING: This may cause problems or conflicts if multiple Linux distributions (e.g. OpenSUSE) share the same Btrfs partition.
###       Recommendation: Set to yes only if your distro is the only one on the Btrfs partition.
###
### no:  The original subvolume remains the default.
###       Note: If it is listed as the "backup" subvolume in Snapper, it cannot be deleted.
###
### Note:
### This option is not needed for rsync-based restoration, which always preserves the original subvolume.
###
#SET_SNAPSHOT_AS_DEFAULT=no


### Btrfs UUID
### Specify the UUID of your Btrfs filesystem, used only during the restore process.
### If not set, the tool automatically determines the UUID during initialization and saves it in 'snapshots.json'. 
### If the saved UUID becomes outdated, set it manually.
#FS_UUID=


### Authentication Method
### Specify an authentication method: "sudo", "doas", "run0 --background=" or another method of your choice.
### If not set, the default is "sudo".
#AUTH_METHOD="run0 --background="


### Specify a terminal app and its argument to open the console UI for running the restore process with details.
### For example, "konsole -e" for KDE or "gnome-terminal -- bash -c" for GNOME.
#TERMINAL=konsole
#TERMINAL_ARG="-e"


### Snapshot Entry Formatting
### Set the number of spaces to indent each line in all snapshot entries within $ESP_PATH/limine.conf.
### If not set, the default is 5.
#SPACE_NUMBER=5


### Snapshot Name Format
### Choose a format for how snapshot entries look in the Limine bootloader:
### 0. ID=111 2023-12-20 10:59:59 (default)
### 1. 111│2023-12-20 10:59:59
### 2. 111 │ 2023-12-20 10:59:59
### 3. 2023-12-20 10:59:59│111
### 4. 2023-12-20 10:59:59 │ 111
### 5. 2023-12-20 10:59:59
### 6. 111
### 7. 111│snapshot description
### 8. 2023-12-20 10:59:59│snapshot description
### 9. │snapshot description
SNAPSHOT_FORMAT_CHOICE=2


### Desktop Notifications
### Enable desktop notifications for warnings and errors in limine-snapper-sync, e.g., hardware issues or boot partition usage limits.
### Default: yes
#ENABLE_NOTIFICATION=yes


### Hash Function for Deduplication and Checksum
### Choose a hash function to detect duplicates and avoid extra copies.
### Options: "blake3", "xxhash", "sha1", or "sha256".
###
### WARNING:
### Changing the hash function does not re-hash existing files, which may result in duplicates.
###
### Note:
### If you choose "blake3" or "xxhash", you must manually install the matching utility:
### - Install 'b3sum' package for blake3
### - Install 'xxhash' package for xxhash
HASH_FUNCTION=xxhash


### Notification Icon
### Set a custom icon to display in notifications when booting into a read-only snapshot and prompting for restoration.
#NOTIFICATION_ICON="/usr/share/icons/hicolor/128x128/apps/LimineSnapperSync.png"


### Automatic Config Backup
### Automatically create a backup of 'limine.conf' and 'snapshots.json' before saving any changes
### but only if their existing backups ('*.old') are older than the specified time (in hours).
### This setting is unrelated to snapshot creation.
### Default: 6 hours if unset.
#CONFIG_BACKUP_THRESHOLD=6


### Quiet Mode
### Set to "yes" to hide all success messages and avoid log spam. (yes|no)
#QUIET_MODE=no

```

## Hooks

`limine-snapper-sync` provides the same [hook mechanism](https://gitlab.com/Zesko/limine-entry-tool#hooks) as `limine-entry-tool`,
allowing custom scripts to run during boot partition operations.<br>
**Note:** Config checksum enrollment hooks are provided by `limine-entry-tool`.

---

## Translations

The messages are translatable. The tool itself reads its text from
`src/main/resources/messages.properties`; a language is added by putting
`messages_<lang>.properties` next to it, for example `messages_de.properties`, and by
listing the language in `-H:IncludeLocales` in `build.gradle.kts`. Russian
(`messages_ru.properties`, `po/ru.po`) is shipped. Keys that a
translation does not carry fall back to the English file, so a partial translation is
harmless.

The shell scripts use the gettext catalog `limine-snapper-sync`. `po/limine-snapper-sync.pot`
holds the strings, refreshed with `bash --dump-po-strings`, and a language is added as
`po/<lang>.po`, compiled with
`msgfmt po/<lang>.po -o /usr/share/locale/<lang>/LC_MESSAGES/limine-snapper-sync.mo`.

The build does that step itself: `gradle build` and `gradle nativeCompile` compile every
`po/*.po` into `install/arch-linux/usr/share/locale/<lang>/LC_MESSAGES/`, which is part of
the tree packaging already installs, so a package picks the catalogs up without any extra
step. It needs `msgfmt` (the `gettext` package) at build time; without it the build still
succeeds and only warns, and the scripts keep speaking English.

The language comes from the environment, so `LANG=C limine-snapper-list` always gives
the English text.

---

## Troubleshooting

<details>
<summary>Why does my boot partition usage grow too fast?</summary>

This can happen for several reasons:

- **Heavy modules and DKMS drivers in early boot**
    - **Nvidia, Plymouth, and some DKMS drivers** are often included in each initramfs and can increase usage by more than 200 MiB per kernel.
        - **Fix:** Review and configure your initramfs and remove modules that are not required for early boot.
            * Example: Is network access required during early boot on an unencrypted boot partition? If not, move network driver out of the initramfs so it is loaded later from the root filesystem.

- **Excessive automatic snapshots created during rolling updates**
    - **Fix:** Limit or disable automatic hooks, for example: uninstall `snap-pac` package on Arch Linux.
    - **Fix:** Edit `/etc/default/limine` and add `EXCLUDE_SNAPSHOT_TYPES="post"` to ignore matching snapshots.
    - **Fix:** Instead of relying on automatic hooks, create a few snapshots manually or configure time-based snapshot creation.

- **`sbctl` produces a different non-reproducible hash on each run**, even for identical files, causing unnecessary duplication.
    - **Fix:** Request that the `sbctl` maintainer add support for reproducible hashing.<br>
    - Alternatively, use Limine `protocol: linux` to boot reproducible initramfs and vmlinuz, bypassing Secure Boot (no signing required).

- **Some initramfs tools generate non-reproducible initramfs images**, even when modules and configuration have not changed, causing unnecessary duplication.
    * **For mkinitcpio:**
        - **Fix:** Edit `/etc/mkinitcpio.conf` and set `COMPRESSION="zstd"` to make initramfs builds reproducible.

    * **For Dracut:**
        - **Fix:** Add `reproducible=yes` and `compress="zstd"` to any dracut drop-in configuration file.

- **Fallback initramfs is huge and includes every module**, which consumes a lot of space and may expose sensitive information on an unencrypted boot partition.
    - **Fix:** Disable fallback initramfs generation if you do not plan to use the same disk on **multiple different devices**.

- **Too many kernels installed per snapshot.**<br>(Note: Using multiple kernel versions increase the risk of breaking filesystem when frequently switching between them.)
    - **Fix:** Keep only one kernel that works on your system.

- **Too many snapshots without real benefit.**
    - **Fix:** Limit the number of snapshots.
    - **Fix:** Avoid creating new snapshots when rebuilding non-reproducible initramfs or UKI, unless system changes were made.

- **The boot partition is too small.**
    - **Fix:** Resize it or move to a larger FAT32 partition. Larger disks are cheaper this year than before.
    - **Example:** If you have plenty of free disk space and want to create a larger boot partition:
        1. Back up your important data first.
        2. Be careful to shrink your root partition **from the end (right to left)**.
        3. Create a new larger FAT32 partition (e.g. 16 GiB or 32 GiB, similar to swap size).
        4. Back up the current boot folder:
        ```sh
        sudo cp -r /<boot_or_efi_path>/$(cat /etc/machine-id)  /boot_bak
        sudo cp /<boot_or_efi_path>/limine.conf  /limine.conf_bak
        ```
        5. Update `/etc/fstab` with **the new UUID** for `/boot`. **This step is important. Do not skip it.**
        6. Unmount and remount `/boot` after updating `/etc/fstab`
        ```sh
        sudo umount /<boot_or_efi_path>
        sudo mount /<boot_or_efi_path>
        ```
        7. Restore the boot folder:
        ```sh
        sudo cp -r /boot_bak  /<boot_or_efi_path>/$(cat /etc/machine-id)
        sudo cp /limine.conf_bak  /<boot_or_efi_path>/limine.conf
        ```
        8. Reinstall Limine and update:
        ```
        sudo limine-install --fallback  # command from limine-entry-tool package
        sudo limine-update              # command from limine-dracut/mkinitcpio-hook package
        sudo limine-snapper-sync
        ```
</details>

<details>
<summary>Why is restore blocked?</summary>

The restore function includes several built-in safeguards to prevent user mistakes.

- **The boot partition does not have enough free space for restoring kernel files**
    - **Fix:** Run `limine-snapper-remove <ID 1>..<ID 2>` to remove older snapshot boot entries and free up space on the boot partition.

- **Different configuration errors**, such as:
    - Using incompatible Btrfs layout
    - Invalid value of `ESP_PATH`
    - Outdated `FS_UUID` value used for mounting Btrfs
    - etc...

</details>

<details>
<summary>Why am I seeing the "Broken manifest detected" message?</summary>

A broken manifest *snapshots.json* can be caused by:
* FAT32 corruption (e.g., power loss)
* Hardware issues (disk, RAM, or CPU)

**How to fix:**

* `limine-snapper-sync` automatically recovers the backup `/var/cache/boot/<machine-id>/lss/snapshots.json` if the hardware is fine.
* If the hardware is faulty, back up your data and replace or repair the hardware.

</details>

<details>
<summary>Why are fewer snapshots shown in Limine than in Snapper?</summary>

The Limine Snapshot menu only shows up to the number of snapshots set by **MAX_SNAPSHOT_ENTRIES**.<br>
This limit helps prevent your boot partition from filling up with too many snapshots or kernel versions.

**How to fix:**

- Edit `/etc/default/limine` and set `MAX_SNAPSHOT_ENTRIES=` to a higher number.
</details>

<details>
<summary>Some display managers fail to boot into read-only snapshots</summary>

This can happen if your system expects a **writable** root filesystem.<br>

**How to fix:**

1. Reboot and go to the Limine boot menu
2. Select a snapshot (don't boot it). Press <kbd>E</kbd> to edit the `kernel_cmdline: ...` to add `1` for enabling rescue mode.
3. Press <kbd>F10</kbd> to boot. Log in as root user in text mode.
4. Run `limine-snapper-restore` to restore the snapshot.

Once the snapshot is restored and the system boots normally, here are two ways to prevent this issue in the future:

#### ✅ Option 1: Use OverlayFS to simulate a writable layer

This is the recommended solution for two restore methods: `replace` or `rsync`.<br>
**Note:** Using an openSUSE-style layout with the restore method `opensuse` (`snapper`) does not support OverlayFS.

* **With Dracut** (on Arch Linux):<br>
Install [`limine-dracut-support`](https://aur.archlinux.org/packages/limine-dracut-support/),
then create a new snapshot.<br>
OverlayFS support is enabled automatically.
<br>**or**</br>
* **With mkinitcpio** (on Arch Linux):<br>
    1. Install [`limine-mkinitcpio-hook`](https://aur.archlinux.org/packages/limine-mkinitcpio-hook/).
    2. Edit `/etc/mkinitcpio.conf` and add `btrfs-overlayfs` after `filesystems` in `HOOKS=(...)` when using busybox hooks.<br>
       (**Note:** For systemd hooks, use `sd-btrfs-overlayfs`, since `btrfs-overlayfs` is incompatible.)
    3. Run `sudo limine-update`
    4. Create a new snapshot (older ones won’t work).

#### ⚠️ Option 2: Force snapshots to be writable (not recommended)

If you don’t want to use OverlayFS, you can force newly created snapshots to be writable:

Edit `/etc/default/limine` and set this line:
```bash
SNAPSHOT_WRITABLE=yes
```
**Important:**
If you boot into a writable snapshot, restore it immediately and avoid making changes to the snapshot itself. Modifying a snapshot directly can break consistency.

</details>

<details>
<summary>Boot entries missing in Limine</summary>

This happens when `limine.conf` is missing or empty on FAT32 boot partition.<br>
**Possible causes:**
- FAT32 has no journaling: power loss or hard reset during a write may leave `limine.conf` empty.
- Some text editors fail to safely overwrite `limine.conf` on FAT32.

**How to fix:**
1. Boot another OS.
2. Mount the FAT32 boot partition where Limine is installed.
3. If `limine.conf.old` exists, restore it by copying it to `limine.conf`
4. Add `hash_mismatch_panic: no` to the header of `limine.conf` to avoid a panic on checksum mismatches (Limine will only warn instead).

</details>

<details>
<summary>No restore notification appears after booting a snapshot</summary>

Some window managers (e.g., Hyprland) do not use the standard XDG autostart mechanism by default.

**How to fix:**

* Add `limine-snapper-restore --notify` to your window manager’s autostart as a regular user.
* Make sure a package providing the standard `notify-send` is installed, as `limine-snapper-restore --notify` relies on it (e.g., `swaync` supports this).
* If your environment does not support `notify-send`, ask its developers to add support rather than requesting it here.

</details>

<details>
<summary>What happens after moving boot files and Btrfs to another disk?</summary>

The stored UUID may become outdated after moving your boot files and Btrfs to another disk.<br>
As a result, the system might fail to boot until the UUID is updated.

**How to fix:**

1. Boot into a live chroot environment.
2. Find the current UUID of your Btrfs partition:
   ```bash
   lsblk -o NAME,FSTYPE,UUID,SIZE,LABEL,MOUNTPOINT,PTTYPE
   ```
3. Edit `/etc/default/limine` and update `FS_UUID=` to your current Btrfs UUID.
4. If you are using `limine-entry-tool`, make sure to update the kernel command line:
   ```
   KERNEL_CMDLINE[default]=... root=UUID=...
   ```
5. Update Limine boot configuration by running `limine-update` and `limine-snapper-sync`

</details>

---

## Features

### Core sync system

* Snapper plugin integration for snapshot create, modify, delete, and rollback events
* Parse Snapper metadata, limine.conf, history manifest and configs
* Atomic mutex to prevent concurrent processes from accessing the same boot partition
* Debounce mechanism to group multiple snapshot deletions/modifications into a single sync
* Multithreaded readers and writers for parallel execution and faster processing
* Boot entries from limine.conf are converted into a tree structure for simplified management
* Verify configuration before processing
* Identify a custom OS entry via machine-id based identification
* Generate a snapshot boot entry from the identified OS entry
* Support for multiple nested boot entries
* Support for multiple hash functions without restrictions
* Scalable manifest without structural limits, including automatic upgrade from old to new manifest format
* Remove snapshot boot entries when Snapper snapshots are deleted
* Atomic file writes on FAT32 to reduce the risk of corruption during power outages, crashes, or hard resets
* Deduplicate kernel files to avoid copies by re-using existing files on the boot partition

### Backup & restore

* Automatic backup of limine.conf before modifications
* Automatic backup of manifest in 2 different locations: boot partition and Linux partition
* Automatic recovery of manifest when corrupted or lost
* Automatic snapshot kernel repair by restoring a valid kernel when corruption is detected
* Support for 3 different restore methods: replace, rsync and snapper
* Restore original boot entry from manifest back into OS section of limine.conf
* Restore all snapshot entries from manifest when they are lost in limine.conf
* Backup boot entry with kernels added to Limine during restore

### Snapshot & kernel management

* `SNAPSHOT_KERNEL_PARAMETERS` to manage kernel parameters for newly created snapshots
* `EXCLUDE_SNAPSHOT_ENTRIES` to skip kernel entries (e.g. fallback kernels) in newly created snapshots
* `EXCLUDE_SNAPSHOT_TYPES` to exclude specific snapshot types
* `SNAPSHOT_WRITABLE` to make snapshots writable for boot when needed
* `MAX_SNAPSHOT_ENTRIES=auto` to auto-remove older snapshot boot entries based on boot partition usage limit
* `limine-snapper-remove` to remove selected snapshot boot entries to reduce boot partition usage
* `limine-snapper-restore` to restore a snapshot from the boot menu

### Detection & monitoring

* Two ways to monitor snapshot changes: `limine-snapper-watcher` or Snapper plugin integration
* Hardware issue detection (e.g. non-ECC RAM or disk problems) by comparing file hashes
* Detection of boot partition usage limits with detailed reporting
* `limine-snapper-info` shows snapshot IDs affected by missing or corrupted kernel files
* `limine-snapper-info` can prompt cleanup of unused files when detected

### System integration & UX

* Extensive safeguards to reduce breakages from user errors
* Ensure /etc/machine-id exists, generate if missing
* Automatic detection of default OS name from /etc/os-release
* Almost zero configuration thanks to automatic system detection.
* Desktop notifications for hardware issues, misconfigurations, or boot partition limit warnings
* Authentication helper to avoid typing `sudo` or other auth methods every time
* Pre- and post-hooks for running custom scripts before or after snapshot updates
* Pacman hook integration to wait for sync completion before package updates
* Pacman lock cleanup after restore on Pacman-based systems
* Sync support in chroot environment across multiple offline subvolumes/partitions
* Atomic-like update: Switch between A/B systems after system update in offline subvolume/partition
* Support for booting different mutable or immutable system versions
* Choice of snapshot naming formats shown in the boot menu, plus two different boot menu layouts
* `limine-snapper-list` displays snapshot boot entries on the boot partition
* Terminal input support with arrow-key navigation (Up, Down, Left, Right)

### CoW-like behavior on FAT32 boot partition

Combining **limine-snapper-sync** and **limine-entry-tool** turns a FAT32 boot partition into a CoW-like system for kernel boot management.

**Here is why:**
* **Copy-on-write-like behavior**: Different kernel versions are preserved when snapshots are created.
* **Deduplication**: Kernel files are re-used based on hashing to avoid unnecessary duplication.
* **Integrity checks**: Each kernel version has an automatically generated checksum.
* **Boot verification**: Limine verifies kernel integrity using stored checksums during boot.
* **Compression**: Kernel files (vmlinuz, initramfs, UKI) are stored in compressed form by default.
* **Self-healing**: Broken kernel files are automatically replaced when a valid version exists.
* **Metadata recovery**: If snapshots.json is lost or corrupted, it can be restored automatically.
* **Hardware issue detection**: Hash comparisons help detect faulty storage or memory behavior.
* **Scrubbing and repair**: detects missing or corrupted boot files and restores them from backups (planned).
* **RAID 1 support**: planned

Together, these features make FAT32 behave like a CoW-inspired boot filesystem.

## Third-party code

Some source code under `src/` is derived from and ported from [boom-boot](https://github.com/snapshotmanager/boom-boot), copyrighted 2017-now Bryn M. Reeves, and is licensed under Apache-2.0. 

Its integration code is licensed under GPL-3.0-or-later. See [LICENSE](LICENSE) for this project's license.

This project is based on [Limine-Snapper-Sync](https://gitlab.com/Zesko/limine-snapper-sync), copyrighted 2024-now Zesko, and is licensed under GPL-3.0-or-later.


