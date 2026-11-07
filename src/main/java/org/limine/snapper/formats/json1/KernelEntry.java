package org.limine.snapper.formats.json1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

public record KernelEntry(@JsonProperty("kernelVersion") String kernelVersion,
                          @JsonProperty("leadingSpaces") String leadingSpaces,
                          @JsonProperty("optionDetails") List<OptionDetail> optionDetails,
                          @JsonProperty("imageDetails") List<ImageDetail> imageDetails,
                          @JsonProperty("cmdlineDetails") List<CmdlineDetail> cmdlineDetails,
                          @JsonProperty("allInConfig") List<String> allInConfig,
                          @JsonProperty("subKernels") List<KernelEntry> subKernels,
                          @JsonProperty("allInSnapshotConfig") List<String> allInSnapshotConfig,
                          @JsonProperty("properties") Map<String, String> properties) {

    /**
     * Custom constructor with default value for properties
     */
    public KernelEntry {
        if (properties == null) {
            properties = new HashMap<>();
        }
        if (optionDetails == null) {
            optionDetails = new ArrayList<>();
        }
        if (imageDetails == null) {
            imageDetails = new ArrayList<>();
        }
        if (cmdlineDetails == null) {
            cmdlineDetails = new ArrayList<>();
        }
        if (allInConfig == null) {
            allInConfig = new ArrayList<>();
        }
        if (subKernels == null) {
            subKernels = new ArrayList<>();
        }
        if (allInSnapshotConfig == null) {
            allInSnapshotConfig = new ArrayList<>();
        }
    }

    @JsonIgnore
    public HashSet<ImageDetail> allImages() {
        // Add own images
        HashSet<ImageDetail> allImages = new HashSet<>(imageDetails);
        // Recursively add images of each subkernel
        for (KernelEntry subKernel : subKernels) {
            allImages.addAll(subKernel.allImages());
        }
        return allImages;
    }
}
