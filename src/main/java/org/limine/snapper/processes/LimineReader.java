package org.limine.snapper.processes;

import org.limine.snapper.objects.Messages;
import org.limine.snapper.formats.json1.KernelEntry;
import org.limine.snapper.formats.json1.SnapperEntry;
import org.limine.snapper.formats.json1.SnapshotEntry;
import org.limine.snapper.formats.limine8.Limine;
import org.limine.snapper.objects.Config;
import org.limine.snapper.objects.TreeNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LimineReader {

    public final boolean isSnapshotsInsideOsNode;
    public final TreeNode snapshotsNode;
    public final String limineConfigPath;
    public final TreeNode treeRoot;
    public final TreeNode targetOS;
    private final Config config;

    public LimineReader(Config config) {
        this(config, false);
    }

    public LimineReader(Config config, boolean askUserIfOSnotFound) {
        this.config = config;
        this.limineConfigPath = Utility.buildPath(config.espPath(), Config.LIMINE_CONFIG_FILE);

        TreeNode root = this.readLimineConfig();
        String osName = Config.TARGET_OS_NAME;
        TreeNode os = root.findOsNode(config.machineID(), osName, 1);

        // User gives input in the terminal manually.
        if (os == null && askUserIfOSnotFound) {
            do {
                String warn = Messages.get("warn.target-os-name-not-found-in-limine-conf", osName, config.espPath(), Config.LIMINE_CONFIG_FILE);
                Utility.warnMessage(warn);
                osName = Limine.cleanName(Utility.userInput(Messages.get("prompt.enter-os-name-must-match")));
                //re-load changed limine.conf
                root = this.readLimineConfig();
                os = root.findOsNode(config.machineID(), osName, 1);
            } while (os == null);
        }
        treeRoot = root;
        targetOS = os;
        if (targetOS == null) {
            String errMessage = Messages.get("text.target-os-name-not-found", osName, config.espPath(), Config.LIMINE_CONFIG_FILE, Config.DEFAULT_CONFIG_FILE_PATH);
            Utility.exitWithError(errMessage);
        } else {
            // Set a target OS name if the matching machine-ID is found in limine.conf
            Config.TARGET_OS_NAME = targetOS.cleanName;
        }
        TreeNode existingSnapshotsNode = targetOS.findNodeByName(Config.SNAPSHOTS_NODE_NAME, 1, false, true);
        if (existingSnapshotsNode == null) {
            existingSnapshotsNode = treeRoot.findNodeByName(Config.SNAPSHOTS_NODE_NAME, 1, false, true);
        }
        if (existingSnapshotsNode == null) {
            // The OS node does not have a "Snapshots" node, so a default "Snapshots" node is created.
            snapshotsNode = new TreeNode(Config.SPACES + Limine.SNAPSHOTS_INSIDE_OS_NODE, 2, targetOS.name);
            targetOS.nodes.add(snapshotsNode);
            isSnapshotsInsideOsNode = true;
        } else {
            snapshotsNode = existingSnapshotsNode;
            isSnapshotsInsideOsNode = Objects.equals(snapshotsNode.parentNodeName, targetOS.name);
        }
    }

    private TreeNode readLimineConfig() {
        TreeNode root = new TreeNode("root", 0, null);
        try {
            List<String> lines = Files.readAllLines(Paths.get(limineConfigPath));
            this.parseLinesToTree(lines, root, 0, lines.size());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    public boolean isTargetOsNotFound() {
        return targetOS == null;
    }

    /**
     * Create a current Limine entry for a new snapshot only. It requires the latest Snapper entry.
     */
    public SnapshotEntry createCurrentLimineEntry(SnapperEntry lastSnapperEntry) {
        // Create a current OS entry
        List<KernelEntry> kernelEntries = this.createKernelEntries(targetOS, lastSnapperEntry, config);
        return new SnapshotEntry(lastSnapperEntry, kernelEntries, null);
    }

    /**
     * Create a current Limine entry for the restore only. It does not need a Snapper entry.
     */
    public SnapshotEntry createCurrentLimineEntry() {
        // Create a current OS entry without Snapper
        SnapperEntry snapperEntry = new SnapperEntry(0, null, null, null);
        return this.createCurrentLimineEntry(snapperEntry);
    }

    /**
     * Create multiple kernel entries of your OS
     */
    private List<KernelEntry> createKernelEntries(TreeNode targetOsNode, SnapperEntry lastSnapperEntry, Config config) {

        List<KernelEntry> kernelEntries = new ArrayList<>();
        KernelReader kernelReader = new KernelReader(config);
        for (TreeNode kernelNode : targetOsNode.nodes) {
            // Ignore reading "Snapshots" or "Backup" entries by default
            if (kernelNode.configLines.isEmpty() || isReservedEntry(kernelNode.cleanName)) {
                continue;
            }
            // Skip kernel entry if it matches any pattern in EXCLUDE_SNAPSHOT_ENTRIES
            if (isEntryExcluded(kernelNode.cleanName)) {
                continue;
            }
            String leadingSpaces = Utility.getLeadingSpaces(kernelNode.name);
            KernelEntry kernelEntry = kernelReader.createKernelEntry(kernelNode, leadingSpaces, lastSnapperEntry.snapshotID(), kernelNode.configLines);
            // If the kernel entry is not valid, then throw it away.
            if (kernelReader.isBootConfigValid()) {
                kernelEntries.add(kernelEntry);
            } else {
                kernelReader.resetBootConfigValid();
            }
        }
        return kernelEntries;
    }

    private int parseLinesToTree(List<String> lines, TreeNode currentNode, int startIndex, int endIndex) {

        int index = startIndex;
        while (index < endIndex) {
            String line = lines.get(index);
            String trimLine = line.trim();
            if (trimLine.startsWith(Limine.ENTRY_KEY.toString())) {
                int depth = calculateDepth(trimLine);
                TreeNode childNode = new TreeNode(line, depth, currentNode.name);
                currentNode.addNode(childNode);

                // Find the range for the child node
                int nextIndex = this.findNextNodeIndex(lines, index + 1, endIndex, depth);
                index = this.parseLinesToTree(lines, childNode, index + 1, nextIndex);

            } else {
                // Check if the line defines a macro
                MacroReplacer.parseMacro(line);
                currentNode.addConfigLine(line);
                index++;
            }
        }
        return index;
    }

    private int findNextNodeIndex(List<String> lines, int startIndex, int endIndex, int currentDepth) {
        for (int i = startIndex; i < endIndex; i++) {
            String line = lines.get(i).trim();
            if (line.startsWith(Limine.ENTRY_KEY.toString()) && calculateDepth(line) <= currentDepth) {
                return i;
            }
        }
        return endIndex;
    }

    private int calculateDepth(String line) {
        int depth = 0;
        while (depth < line.length() && line.startsWith(Limine.ENTRY_KEY.toString(), depth)) {
            depth++;
        }
        return depth;
    }

    public boolean isReservedEntry(String entryName) {
        return isSnapshotLikeEntry(entryName) || isBackupLikeEntry(entryName);
    }

    public boolean isSnapshotLikeEntry(String entryName) {
        return entryName.toLowerCase().contains("snapshot");
    }

    public boolean isBackupLikeEntry(String entryName) {
        return entryName.toLowerCase().startsWith("backup");
    }

    public boolean isEntryExcluded(String name) {
        for (String pattern : Config.EXCLUDE_SNAPSHOT_ENTRIES) {
            if ("*".equals(pattern)) return true;
            if (Utility.wildcardMatch(pattern, name)) return true;
        }
        return false;
    }
}
