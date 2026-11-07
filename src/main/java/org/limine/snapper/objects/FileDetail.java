package org.limine.snapper.objects;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public record FileDetail(String fileName, String basePath, long bytes) {

    /**
     * create a FileDetail if the file exists, otherwise return null
     */
    public static FileDetail create(String fileName, String basePath) {
        Path path = Path.of(basePath, fileName);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            long fileBytes = Files.size(path);
            return new FileDetail(fileName, basePath, fileBytes);
        } catch (IOException e) {
            // Could not read file size
            return null;
        }
    }

    public static String toMiB(long bytes, int decimals) {
        double mib = bytes / (1024.0 * 1024.0);
        String pattern = "%." + decimals + "f MiB";
        return String.format(Locale.ROOT, pattern, mib);
    }

    public static String toMiB(long bytes) {
        return toMiB(bytes, 1);
    }
}
