package org.limine.snapper.formats.json1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.limine.snapper.processes.Utility;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record SnapperEntry(@JsonProperty("snapshotID") Integer snapshotID,
                           @JsonProperty("utcTime") String utcTime,
                           @JsonProperty("timestamp") String localTime,
                           @JsonProperty("properties") Map<String, String> properties) {

    /**
     * Custom constructor with default value for properties
     */
    public SnapperEntry {
        if (properties == null) {
            properties = new HashMap<>();
        }
        if ((utcTime == null || utcTime.isEmpty()) && localTime != null) {
            utcTime = Utility.convertLocalToUTC(localTime);
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
        SnapperEntry that = (SnapperEntry) o;
        return Objects.equals(snapshotID, that.snapshotID) && Objects.equals(utcTime, that.utcTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotID, utcTime);
    }

    @JsonIgnore
    public String timeId() {
        return snapshotID + utcTime;
    }
}
