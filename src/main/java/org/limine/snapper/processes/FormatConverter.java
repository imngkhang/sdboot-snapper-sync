package org.limine.snapper.processes;

import org.limine.snapper.objects.Messages;
import org.limine.snapper.formats.json1.Manifest;
import org.limine.snapper.formats.json1.SnapperEntry;
import org.limine.snapper.formats.json1.SnapshotEntry;
import org.limine.snapper.objects.Config;

import java.util.List;


public class FormatConverter {

    /**
     * Checks the manifest version and performs an upgrade if necessary.
     *
     * @param manifest The object is checked and updated if necessary.
     * @return true if an upgrade was performed, false otherwise.
     */
    public static boolean upgradeManifest(Manifest manifest) {
        if (Config.MANIFEST_VERSION.equals(manifest.getVersion())) {
            return false;
        }
        if ("1.0.0".equals(manifest.getVersion())) {
            // Upgrade to 1.1.0
            upgradeToV110(manifest);
            Utility.infoMessage(Messages.get("info.the-manifest-version-upgraded", manifest.getVersion()));
        }
        if ("1.1.0".equals(manifest.getVersion())) {
            // Upgrade to 1.2.0
            upgradeToV120(manifest);
            Utility.infoMessage(Messages.get("info.the-manifest-version-upgraded-2", manifest.getVersion()));
        }
        if ("1.2.0".equals(manifest.getVersion())) {
            // Upgrade to 1.3.0
            upgradeToV130(manifest);
            Utility.infoMessage(Messages.get("info.the-manifest-version-upgraded-3", manifest.getVersion()));
        }
        return true;
    }

    private static void upgradeToV110(Manifest manifest) {
        List<SnapshotEntry> snapshotEntries = manifest.getSnapshotEntries();
        for (int i = 0; i < snapshotEntries.size(); i++) {
            SnapshotEntry snapshotEntry = snapshotEntries.get(i);
            SnapperEntry snapperEntry = snapshotEntry.snapperEntry();
            if (snapperEntry.utcTime() == null || snapperEntry.utcTime().isEmpty()) {
                SnapperEntry updatedSnapperEntry = new SnapperEntry(
                        snapperEntry.snapshotID(),
                        Utility.convertLocalToUTC(snapperEntry.localTime()), // Convert UTC time
                        snapperEntry.localTime(),
                        snapperEntry.properties()
                );
                snapshotEntries.set(i, new SnapshotEntry(
                        updatedSnapperEntry,
                        snapshotEntry.kernelEntries(),
                        snapperEntry.properties())
                );
            }
        }
        manifest.setLastUTCTime(Utility.convertLocalToUTC(manifest.getLastLocalTime()));
        manifest.setVersion("1.1.0");
    }

    /**
     * Upgrade notes for future version 2:
     * - Replace `espDirPath` with `dirPath` in ImageDetail
     * - Remove redundant 'name' field from SnapshotEntry
     * - Rename 'lineOrderNumber' to 'lineIndex'
     * - Remove unused 'orderNumber' from KernelEntry
     * <p>
     * However, no upgrade implementation is required here:
     * - 'espDirPath' is already automatically converted to 'dirPath' when loading the JSON file.
     * - Version 1.10.0 or older don't care about 'name', 'lineOrderNumber' and 'orderNumber' at all.
     */
    private static void upgradeToV120(Manifest manifest) {
        manifest.setVersion("1.2.0");
    }

    /**
     * KernelEntry introduces a field: `subKernels`.
     * <p>
     * No upgrade implementation is required:
     * - `subKernels` will be added automatically if needed (e.g. when using multiple boot profiles per kernel).
     * - The old manifest version 1.2.0 is fully compatible with 1.3.0 as long as `subKernels` is not used.
     */
    private static void upgradeToV130(Manifest manifest) {
        manifest.setVersion("1.3.0");
    }
}