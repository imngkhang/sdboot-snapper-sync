package org.limine.snapper.objects;

import org.limine.snapper.processes.Utility;

import java.util.HashMap;
import java.util.Map;

public enum Hash {
    SHA1("sha1", "sha1sum", "_sha1_"),
    SHA256("sha256", "sha256sum", "_sha256_"),
    BLAKE3("blake3", "b3sum", "_b3_"),
    XXHASH("xxhash", "xxhsum", "_xxh_");

    private static final Map<String, Hash> BY_NAME = new HashMap<>();

    static {
        for (Hash h : Hash.values()) {
            BY_NAME.put(h.name.toLowerCase(), h);
        }
    }

    public final String name;
    public final String command;
    public final String hashName;

    Hash(String name, String command, String hashName) {
        this.name = name;
        this.command = command;
        this.hashName = hashName;
    }

    public static Hash getHash(String fileName) {
        if (fileName == null) {
            return null;
        }
        Hash match = null;
        int index = -1;
        for (Hash hash : Hash.values()) {
            int idx = fileName.lastIndexOf(hash.hashName);
            if (idx > index) {
                index = idx;
                match = hash;
            }
        }
        return match;
    }

    public static Hash getHashFunction(String name) {
        if (name == null) {
            return null;
        }
        return BY_NAME.get(name.toLowerCase());
    }

    public static boolean isFileHashable(String hashedFilePath) {
        var hashFunction = getHash(hashedFilePath);
        return hashFunction != null && Utility.isCommandPresent(hashFunction.command);
    }

    public static boolean hasCorruption(String hashedFilePath) {
        var hashFunction = getHash(hashedFilePath);
        if (hashFunction == null || !Utility.isCommandPresent(hashFunction.command)) {
            return false;
        }
        var hashSum = Utility.fileHashIntegrityCheck(hashedFilePath, hashFunction, 2);
        return !hashedFilePath.endsWith(hashSum);  // true = corrupted
    }

}
