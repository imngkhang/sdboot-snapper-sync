package org.limine.snapper.formats.limine8;

import org.limine.snapper.objects.Config;

import java.util.Map;
import java.util.Objects;

public enum Limine {
    COMMENT("comment:"),
    PROTOCOL("protocol:"),
    PATH("path:"),
    CMDLINE("cmdline:"),
    KERNEL_CMDLINE("kernel_cmdline:"),
    MODULE_PATH("module_path:"),
    KERNEL_PATH("kernel_path:"),
    IMAGE_PATH("image_path:"),
    UNKNOWN("unknown:"),
    ENTRY_KEY("/"),
    SUB_ENTRY_KEY("//"),
    SUB_X2_ENTRY_KEY("///"),
    SUB_X3_ENTRY_KEY("////"),
    EXPAND("+"),
    SNAPSHOTS_INSIDE_OS_NODE("//" + Config.SNAPSHOTS_NODE_NAME),
    SNAPSHOTS_OUTSIDE_OS_NODE("/" + Config.SNAPSHOTS_NODE_NAME),
    HASH("#"),
    PATH_RESOURCE("${PATH_RESOURCE}"),
    PATH_RESOURCE_PATTERN("\\$?boot\\(\\):"),
    HASH_PATTERN("#[a-fA-F0-9]*"); // BLAKE2b or BLAKE3 hash pattern

    private final String replace;

    Limine(String replace) {
        this.replace = replace;
    }

    /**
     * Extracts the value from a string formatted as "<key>: value".
     *
     * @param input The input string in the format "<key>: value".
     * @return The value after the key and colon, or an empty string if the format is invalid.
     */
    public static String extractValue(String input) {
        // Check if the input string is valid
        if (input == null) {
            return "";
        }
        // Find the index of the colon
        int colonIndex = input.indexOf(':');
        // Extract the substring after the colon
        return input.substring(colonIndex + 1).trim();
    }

    public static String extractKey(String input) {
        // Check if the input string is valid
        if (input == null) {
            return "";
        }
        // Find the index of the colon
        int colonIndex = input.indexOf(':');
        // Extract the substring before the colon
        return input.substring(0, colonIndex + 1).trim();
    }

    public static String replace(String key) {
        for (Limine value : Limine.values()) {
            if (UNKNOWN.equals(value)) {
                break; // Stops the loop immediately at UNKNOWN
            } else if (value.name().equals(key)) {
                return value.replace;
            }
        }
        // If no match is found, return the original key
        return key;
    }

    public static String uri(Map<String, String> properties, String path) {
        String pathResource = properties.get(PATH_RESOURCE.name());
        return Objects.requireNonNullElse(pathResource, "boot():") + path;
    }

    /**
     * Remove leading symbols in the name.
     */
    public static String cleanName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        name = name.trim();

        String entryKey = Limine.ENTRY_KEY.toString();
        String expandKey = Limine.EXPAND.toString();

        int index = 0;

        // Skip all leading ENTRY_KEY markers
        while (name.startsWith(entryKey, index)) {
            index += entryKey.length();
        }

        // Check if EXPAND marker exists next
        if (name.startsWith(expandKey, index)) {
            index += expandKey.length();
        }

        // Avoid IndexOutOfBounds just in case
        if (index >= name.length()) {
            return "";
        }

        return name.substring(index);
    }

    @Override
    public String toString() {
        return this.replace;
    }

}
