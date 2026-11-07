package org.limine.snapper.processes;

import org.limine.snapper.objects.ConsoleColor;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class TerminalReader {

    /**
     * Reads a line from the terminal with colored prompt, arrow key navigation, backspace, and history support.
     *
     * @param prompt  The prompt text to display
     * @param history List of previous input entries
     * @return The line entered by the user
     */
    public static String readLine(String prompt, List<String> history) {
        boolean isRawModeEnabled = enableRawMode();
        System.out.print(ConsoleColor.YELLOW + prompt + ConsoleColor.RESET);
        System.out.flush();

        StringBuilder input = new StringBuilder();
        int cursor = 0;
        int historyIndex = history.size();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            while (true) {
                int ch = reader.read();
                if (ch == -1) continue;

                // Ctrl + C
                if (ch == 3) {
                    System.out.println();
                    disableRawMode(isRawModeEnabled);
                    System.exit(130);
                }

                // ENTER
                if (ch == '\r' || ch == '\n') {
                    System.out.println();
                    break;
                }

                // ESC sequences
                if (ch == 27) {
                    reader.mark(16);
                    String seq = readEscSequence(reader);

                    if (seq.startsWith("\u001B[")) {
                        if (seq.contains("A")) { // UP
                            historyIndex = Math.max(0, historyIndex - 1);
                            String up = historyIndex < history.size() ? history.get(historyIndex) : "";
                            loadHistory(prompt, input, up);
                            cursor = input.length();
                        } else if (seq.contains("B")) { // DOWN
                            historyIndex = Math.min(history.size(), historyIndex + 1);
                            String down = historyIndex < history.size() ? history.get(historyIndex) : "";
                            loadHistory(prompt, input, down);
                            cursor = input.length();
                        } else if (seq.contains("C")) { // RIGHT
                            cursor = Math.min(input.length(), cursor + 1);
                            setCursor(prompt, cursor);
                        } else if (seq.contains("D")) { // LEFT
                            cursor = Math.max(0, cursor - 1);
                            setCursor(prompt, cursor);
                        } else if (seq.contains("3~")) { // DELETE
                            if (cursor < input.length()) {
                                input.deleteCharAt(cursor);
                                redraw(prompt, input, cursor);
                            }
                        } else {
                            reader.reset(); // ignore unknown sequences
                        }
                    }
                    continue;
                }

                // BACKSPACE
                if ((ch == 127 || ch == 8) && cursor > 0) {
                    int cpBefore = input.codePointBefore(cursor);
                    int charCount = Character.charCount(cpBefore);
                    cursor -= charCount;
                    input.delete(cursor, cursor + charCount);
                    redraw(prompt, input, cursor);
                    continue;
                }

                // Printable Unicode characters
                if (!Character.isISOControl(ch)) {
                    String chars = new String(Character.toChars(ch));
                    input.insert(cursor, chars);
                    cursor += chars.length();
                    redraw(prompt, input, cursor);
                }
            }
        } catch (Exception e) {
            disableRawMode(isRawModeEnabled);
            throw new RuntimeException(e);
        }

        disableRawMode(isRawModeEnabled);
        return input.toString();
    }

    /**
     * Read full ESC sequence from BufferedReader without busy-looping CPU.
     */
    private static String readEscSequence(BufferedReader reader) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append((char) 27); // ESC
        while (reader.ready()) {
            sb.append((char) reader.read());
        }
        return sb.toString();
    }

    private static void redraw(String prompt, StringBuilder input, int cursor) {
        System.out.print("\r" + ConsoleColor.YELLOW + prompt + ConsoleColor.RESET + input + "\u001B[K");
        setCursor(prompt, cursor);
        System.out.flush();
    }

    private static void setCursor(String prompt, int cursor) {
        int pos = prompt.length() + cursor;
        System.out.print("\r\u001B[" + pos + "C");
        System.out.flush();
    }

    private static void loadHistory(String prompt, StringBuilder input, String entry) {
        input.setLength(0);
        input.append(entry);
        System.out.print("\r" + ConsoleColor.YELLOW + prompt + ConsoleColor.RESET + entry + "\u001B[K");
        System.out.flush();
    }

    private static boolean enableRawMode() {
        try {
            if (Utility.isCommandPresent("stty") && new File("/dev/tty").exists()) {
                Runtime.getRuntime().exec(new String[]{"/usr/bin/env", "bash", "-c", "stty raw -echo < /dev/tty"}).waitFor();
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void disableRawMode(boolean enabled) {
        try {
            if (enabled) {
                Runtime.getRuntime().exec(new String[]{"/usr/bin/env", "bash", "-c", "stty sane < /dev/tty"}).waitFor();
            }
        } catch (Exception ignored) {
        }
        System.out.print("\r");
        System.out.flush();
    }

    /**
     * Convenience overload without history
     */
    public static String readLine(String prompt) {
        return readLine(prompt, List.of());
    }
}
