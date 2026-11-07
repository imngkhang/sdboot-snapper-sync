package org.limine.snapper.formats.json1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.limine.snapper.processes.Utility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Manifest {

    @JsonProperty("snapshotEntries")
    private final List<SnapshotEntry> snapshotEntries;
    @JsonProperty("properties")
    private final HashMap<String, String> properties;
    @JsonProperty("jsonFormatVersion")
    private String version;
    @JsonProperty("lastUTCTime")
    private String lastUTCTime;
    @JsonProperty("lastTimestamp")
    private String lastLocalTime;
    @JsonProperty("lastSnapshotID")
    private int lastSnapshotID;
    @JsonProperty("uuid")
    private String uuid;

    /**
     * Please do not remove the standard constructor (required for Jackson)
     */
    public Manifest() {
        this.properties = new HashMap<>();
        this.snapshotEntries = new ArrayList<>();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getLastUTCTime() {
        if ((lastUTCTime == null || lastUTCTime.isEmpty()) && lastLocalTime != null) {
            lastUTCTime = Utility.convertLocalToUTC(lastLocalTime);
        }
        return lastUTCTime;
    }

    public void setLastUTCTime(String lastUTCTime) {
        this.lastUTCTime = lastUTCTime;
    }

    public String getLastLocalTime() {
        return lastLocalTime;
    }

    public void setLastLocalTime(String lastLocalTime) {
        this.lastLocalTime = lastLocalTime;
    }

    public int getLastSnapshotID() {
        return lastSnapshotID;
    }

    public void setLastSnapshotID(int lastSnapshotID) {
        this.lastSnapshotID = lastSnapshotID;
    }

    public HashMap<String, String> getProperties() {
        return properties;
    }

    public List<SnapshotEntry> getSnapshotEntries() {
        return snapshotEntries;
    }

    @JsonIgnore
    public void addProperty(String key, String value) {
        properties.put(key, value);
    }
}
