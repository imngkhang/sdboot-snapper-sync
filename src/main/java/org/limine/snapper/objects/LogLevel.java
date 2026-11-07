package org.limine.snapper.objects;

public enum LogLevel {
    CRITICAL("crit"), ERROR("err"), WARNING("warn"), INFO("info"), DEBUG("debug");

    public final String level;

    LogLevel(String level) {
        this.level = level;
    }
}
