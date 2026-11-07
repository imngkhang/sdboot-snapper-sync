package org.limine.snapper.objects;

import java.util.Locale;


public record PartitionUsage(long totalBytes, long usedBytes, long deltaBytes, boolean exceedsLimit) {

    public double percent(long bytes) {
        return bytes * 100.0 / totalBytes;
    }

    private String toPercent(long bytes) {
        if (totalBytes <= 0) {
            return "0.0%";
        }
        double percent = percent(bytes);
        return String.format(Locale.ROOT, "%." + 1 + "f%%", percent);
    }

    private String toMiB(long bytes) {
        double mib = bytes / (1024.0 * 1024.0);
        return String.format(Locale.ROOT, "%." + 1 + "f MiB", mib);
    }

    public boolean increaseAtLeastMiB(int megabytes) {
        if (megabytes <= 0 || deltaBytes < 0) {
            return false;
        }
        long bytes = megabytes * 1024L * 1024L;
        return deltaBytes >= bytes;
    }

    public String usedPercent() {
        return toPercent(usedBytes);
    }

    public String newPercent() {
        return toPercent(usedBytes + deltaBytes);
    }

    public String currentMiB() {
        return toMiB(usedBytes);
    }

    public String newMiB() {
        return toMiB(usedBytes + deltaBytes);
    }

    public String deltaMiB() {
        return toMiB(deltaBytes);
    }
}
