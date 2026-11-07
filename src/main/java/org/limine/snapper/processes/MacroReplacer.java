package org.limine.snapper.processes;

import org.limine.snapper.objects.Messages;
import java.util.HashMap;
import java.util.Map;

public class MacroReplacer {
    private static final Map<String, String> macros = new HashMap<>();

    public static void parseMacro(String line) {
        if (line == null || line.isEmpty()) return;

        // Skip leading whitespace manually
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;

        // Must start with ${
        if (i + 1 >= line.length() || line.charAt(i) != '$' || line.charAt(i + 1) != '{') return;

        int keyStart = i + 2;
        int end = line.indexOf('}', keyStart);
        if (end == -1) return;

        // Must have '=' right after }
        if (end + 1 >= line.length() || line.charAt(end + 1) != '=') return;

        String key = line.substring(keyStart, end);
        String value = line.substring(end + 2);
        macros.put(key, value);
    }

    public static String replaceMacros(String line) {
        if (line == null || line.isEmpty()) return line;

        // Skip leading whitespace and return if comment
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        if (i < line.length() && line.charAt(i) == '#') return line;

        // Return line if no macro found
        if (!line.contains("${")) return line;

        String result = line;
        boolean changed = false;

        for (Map.Entry<String, String> entry : macros.entrySet()) {
            String token = "${" + entry.getKey() + "}";
            if (result.contains(token)) {
                result = result.replace(token, entry.getValue());
                changed = true;
            }
        }

        if (!changed) return line;

        // Unresolved macro check
        if (result.contains("${")) {
            Utility.exitWithError(Messages.get("text.incorrect-config-no-definition-macro"),
                    result.replace("${", "\\${").trim());
        }
        return result;
    }

}
