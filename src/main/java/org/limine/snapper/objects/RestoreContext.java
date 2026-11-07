package org.limine.snapper.objects;

import org.limine.snapper.formats.json1.SnapshotEntry;

public record RestoreContext(SnapshotEntry snapshotEntry, SnapshotEntry targetEntry, String backupDescription,
                             boolean isBackupNeeded, boolean isValid) {
}
