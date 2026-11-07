package org.limine.snapper.formats.json1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public record OptionDetail(@JsonProperty("limineKey") String limineKey,
                           @JsonProperty("leadingSpaces") String leadingSpaces,
                           @JsonProperty("lineIndex") Integer lineIndex,
                           @JsonProperty("optionLine") String optionLine,
                           @JsonProperty("snapshotOptionLine") String snapshotOptionLine,
                           @JsonProperty("properties") Map<String, String> properties) {

    /**
     * Custom constructor with default value for properties
     */
    public OptionDetail {
        if (properties == null) {
            properties = new HashMap<>();
        }
    }

    @JsonIgnore
    public String getSnapshotLine() {
        return limineKey.toLowerCase(java.util.Locale.ROOT) + " " + this.snapshotOptionLine().trim();
    }
}
