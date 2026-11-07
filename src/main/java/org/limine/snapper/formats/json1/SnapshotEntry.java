package org.limine.snapper.formats.json1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

public record SnapshotEntry(@JsonProperty("snapperID") SnapperEntry snapperEntry,
                            @JsonProperty("kernelEntries") List<KernelEntry> kernelEntries,
                            @JsonProperty("properties") Map<String, String> properties) {

    /**
     * Custom constructor with default value for properties
     */
    public SnapshotEntry {
        if (properties == null) {
            properties = new HashMap<>();
        }
        if (kernelEntries == null) {
            kernelEntries = new ArrayList<>();
        }
    }

    @JsonIgnore
    public HashSet<ImageDetail> allImages() {
        HashSet<ImageDetail> allImages = new HashSet<>();
        for (KernelEntry kernel : kernelEntries) {
            allImages.addAll(kernel.allImages());
        }
        return allImages;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SnapshotEntry that = (SnapshotEntry) o;
        return Objects.equals(snapperEntry, that.snapperEntry);
    }

    @Override
    public int hashCode() {
        return snapperEntry.hashCode();
    }
}
