package org.limine.snapper.formats.json1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.limine.snapper.formats.limine8.Limine;

import java.util.HashMap;
import java.util.Map;

public record CmdlineDetail(@JsonProperty("limineKey") String limineKey,
                            @JsonProperty("leadingSpaces") String leadingSpaces,
                            @JsonProperty("lineIndex") Integer lineIndex,
                            @JsonProperty("cmdline") String cmdline,
                            @JsonProperty("snapshotCmdline") String snapshotCmdline,
                            @JsonProperty("properties") Map<String, String> properties) {

    /**
     * Custom constructor with default value for properties
     */
    public CmdlineDetail {
        if (properties == null) {
            properties = new HashMap<>();
        }
    }

    @JsonIgnore
    public String getSnapshotLine() {
        return Limine.replace(limineKey) + " " + this.snapshotCmdline().trim();
    }
}
