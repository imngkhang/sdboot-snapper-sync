package org.limine.snapper.objects;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EntryOptions {

    public String machineID = null;

    public void parseComment(String comment) {
        // Check for null or empty comment
        if (comment == null || comment.isBlank()) {
            return;
        }

        // Regex for machine-ID
        Pattern machineIdPattern = Pattern.compile(Name.MACHINE_ID + "([a-f0-9]{32})", Pattern.CASE_INSENSITIVE);
        Matcher machineIdMatcher = machineIdPattern.matcher(comment);

        // Extract machine-ID, if available
        if (machineIdMatcher.find()) {
            this.machineID = machineIdMatcher.group(1);
        }
    }

    public EntryOptions setID(String machineID) {
        this.machineID = machineID;
        return this;
    }

    public enum Name {
        MACHINE_ID("machine-id=");
        private final String replace;

        Name(String replace) {
            this.replace = replace;
        }

        @Override
        public String toString() {
            return this.replace;
        }
    }
}
