package org.limine.snapper.formats.json1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record ImageDetail(@JsonProperty("limineKey") String limineKey,
                          @JsonProperty("leadingSpaces") String leadingSpaces,
                          @JsonProperty("lineIndex") Integer lineIndex,
                          @JsonProperty("fileName") String fileName,
                          @JsonProperty("fileHashName") String fileHashName,
                          @JsonProperty("dirPath") String dirPath,
                          @JsonProperty("filePathLine") String filePathLine,
                          @JsonProperty("snapshotFilePathLine") String snapshotFilePathLine,
                          @JsonProperty("properties") Map<String, String> properties) {

    /**
     * Custom constructor with default value for properties
     */
    public ImageDetail {
        if (properties == null) {
            properties = new HashMap<>();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ImageDetail that = (ImageDetail) o;
        return Objects.equals(fileHashName, that.fileHashName) && Objects.equals(dirPath, that.dirPath);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(fileHashName) + Objects.hashCode(dirPath);
    }

    @JsonIgnore
    public String getSnapshotLine() {
        String key = switch (limineKey) {
            case "EFI" -> "efi";
            case "INITRD" -> "initrd";
            default -> "linux";
        };
        return key + " " + this.snapshotFilePathLine().trim();
    }
}
