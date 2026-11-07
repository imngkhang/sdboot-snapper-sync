package org.limine.snapper.formats.snapper;

public enum SnapperProperty {
    // Check properties from "snapper list --columns --help"
    DESCRIPTION("description"),
    CLEANUP("cleanup"),
    DATE("date"),
    TYPE("type"),
    PRE("pre"),
    DEFAULT("default"),
    SUBVOLUME("subvolume"),
    ACTIVE("active"),
    READ_ONLY("ro"),
    USER("user"),
    PRE_NUMBER("pre-number"),
    POST_NUMBER("post-number"),
    SNAPPER_VERSION("snapper-version");

    public final String replace;

    SnapperProperty(String replace) {
        this.replace = replace;
    }

    @Override
    public String toString() {
        return this.replace;
    }
}
