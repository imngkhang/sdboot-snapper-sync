package org.limine.snapper.processes;

import org.limine.snapper.objects.Messages;
import java.io.FileWriter;


public class SnapperWriter {

    /**
     * Write a new info XML about the Snapper snapshot.
     */
    public static void writeInfoXml(int snapshotID, String utcTime, String description, String filePath) {
        String content = buildXml(snapshotID, utcTime, description);
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(content);
            Utility.infoMessage(Messages.get("info.saved-2"), filePath);
        } catch (Exception e) {
            Utility.errorMessage(Messages.get("error.save-failed"), filePath);
            Utility.errorMessage(Messages.get("error.reason"), e.getMessage());
        }
    }


    private static String buildXml(int snapshotID, String utcTime, String description) {
        return "<?xml version=\"1.0\"?>\n" +
                "<snapshot>\n" +
                "  <type>single</type>\n" +
                "  <num>" + snapshotID + "</num>\n" +
                "  <date>" + utcTime + "</date>\n" +
                "  <description>" + escapeXml(description) + "</description>\n" +
                "</snapshot>\n";
    }

    /**
     * Helper function to handle basic XML escape characters
     */
    private static String escapeXml(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}
