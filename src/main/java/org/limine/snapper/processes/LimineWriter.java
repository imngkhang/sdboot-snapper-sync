package org.limine.snapper.processes;

import org.limine.snapper.objects.Messages;
import org.limine.snapper.formats.json1.*;
import org.limine.snapper.formats.limine8.Limine;
import org.limine.snapper.formats.snapper.SnapperProperty;
import org.limine.snapper.objects.Config;
import org.limine.snapper.objects.EntryOptions;
import org.limine.snapper.objects.TreeNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class LimineWriter {

    private final Config config;
    private final LimineReader limine;

    public LimineWriter(Config config, LimineReader limine) {
        this.config = config;
        this.limine = limine;
    }

    /**
     * Back up limine.conf
     */
    public void backup() {
        String limineConfigFilePath = Utility.buildPath(config.espPath(), Config.LIMINE_CONFIG_FILE);
        if (Utility.isFilePresent(limineConfigFilePath)) {
            // Auto time-based backup. Check if an older backup exists.
            String backupPath = limineConfigFilePath + Config.BACKUP_SUFFIX;
            boolean isBackupNeeded = true;
            if (Utility.isFilePresent(backupPath)) {
                isBackupNeeded = Utility.isFileOlderThan(backupPath, Config.CONFIG_BACKUP_THRESHOLD);
            }
            if (isBackupNeeded) {
                Path src = Path.of(limineConfigFilePath);
                Path dest = Path.of(backupPath);
                try {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    Utility.errorMessage(Messages.get("error.failed-backup-limine-conf"), e.getMessage());
                }
            }
        }
    }

    public void setSnapshots(Manifest manifest) {
        if (manifest == null) {
            Utility.errorMessage(Messages.get("error.bug"), Messages.get("error.no-manifest"));
            return;
        }
        // Save all snapshots (from the manifest) in the Snapshots-node
        this.overwriteSnapshotsNode(manifest.getSnapshotEntries());
        // If there is no snapshot, then delete the snapshots-menu in the OS node.
        if (limine.snapshotsNode.nodes.isEmpty()) {
            limine.targetOS.nodes.remove(limine.snapshotsNode);
        }
    }

    public void setTargetOS(SnapshotEntry restoredOS) {
        // Save the restored entry (from the Limine snapshots) in the OS node.
        List<TreeNode> restoredKernels = createKernelNodes(restoredOS.kernelEntries(), Limine.SUB_ENTRY_KEY.toString(), 2, Config.TARGET_OS_NAME);
        List<TreeNode> entries = new ArrayList<>(limine.targetOS.nodes.size());
        int restoreIndex = 0;
        int restoreSize = restoredKernels.size();
        for (TreeNode node : limine.targetOS.nodes) {
            if (node == limine.snapshotsNode || limine.isReservedEntry(node.cleanName)) {
                entries.add(node);
                continue;
            }
            if (limine.isEntryExcluded(node.cleanName)) {
                entries.add(node);
                continue;
            }
            if (restoreIndex < restoreSize) {
                entries.add(restoredKernels.get(restoreIndex++));
            }
        }
        // Append remaining restored kernels
        while (restoreIndex < restoreSize) {
            entries.add(restoredKernels.get(restoreIndex++));
        }
        limine.targetOS.nodes.clear();
        limine.targetOS.nodes.addAll(entries);
        limine.targetOS.enableLineBreak = true;
    }

    private List<TreeNode> createKernelNodes(List<KernelEntry> kernels, String entryKey, int depth, String parentNodeName) {
        List<TreeNode> nodes = new ArrayList<>();
        for (KernelEntry k : kernels) {
            String entryName = k.leadingSpaces() + entryKey + k.kernelVersion();
            TreeNode node = new TreeNode(entryName, depth, parentNodeName);
            node.configLines.addAll(k.allInConfig());
            if (k.subKernels() != null && !k.subKernels().isEmpty()) {
                String newEntryKey = entryKey + Limine.ENTRY_KEY;
                List<TreeNode> subNodes = createKernelNodes(k.subKernels(), newEntryKey, depth + 1, k.kernelVersion());
                node.nodes.addAll(subNodes);
            }
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * Save the tree node in limine.conf
     */
    public void save() {
        List<String> lines = new ArrayList<>();
        this.writeNodeToLines(limine.treeRoot, lines, 0);
        String limineConfigPathStr = Utility.buildPath(config.espPath(), Config.LIMINE_CONFIG_FILE);
        // Check if "/<esp>/limine.conf" exists.
        if (!Utility.isFilePresent(limineConfigPathStr)) {
            Utility.warnMessage(Messages.get("warn.missing-file"), Messages.get("warn.it-will-be-restored", limineConfigPathStr));
            Utility.ensurePathExists(limineConfigPathStr);
        }
        Path limineConfigPath = Path.of(limineConfigPathStr);
        Path limineTmpConfigPath = Path.of(limineConfigPathStr + ".tmp");
        try {
            boolean runCommands = false;
            // Run commands before save
            if (Config.COMMANDS_BEFORE_SAVE != null && !Config.COMMANDS_BEFORE_SAVE.isEmpty()) {
                Utility.runCommand(Config.COMMANDS_BEFORE_SAVE, !Config.QUIET, false, true);
                runCommands = true;
            }
            // Atomic write
            Files.write(limineTmpConfigPath, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(limineTmpConfigPath, limineConfigPath, StandardCopyOption.REPLACE_EXISTING);
            Utility.runCommand("sync -f " + limineConfigPathStr, false, false, true);
            Utility.infoMessage(Messages.get("info.updated"), limineConfigPathStr);
            // Run command after save
            if (Config.COMMANDS_AFTER_SAVE != null && !Config.COMMANDS_AFTER_SAVE.isEmpty()) {
                Utility.runCommand(Config.COMMANDS_AFTER_SAVE, !Config.QUIET, false, true);
                runCommands = true;
            }
            if (runCommands) {
                Utility.warnMessage(Messages.get("warn.deprecated"), Messages.get("warn.commands_before_save-commands_after_save-are-deprecated-use"));
                Utility.infoMessage(Messages.get("info.remove-commands_before_save-commands_after_save-from-etc", Config.DEFAULT_CONFIG_FILE_PATH));
            }
        } catch (IOException e) {
            Utility.exitWithError(Messages.get("error.error-writing-file", e.getMessage()));
        }
    }

    /**
     * Overwrite the updated snapshot entries in the snapshots-node.
     */
    private void overwriteSnapshotsNode(List<SnapshotEntry> snapshotEntries) {
        if (limine.snapshotsNode == null) {
            Utility.errorMessage(Messages.get("error.bug"), "no \"Snapshots\" entry");
            return;
        }
        int maxWidth = 0;
        if (!snapshotEntries.isEmpty()) {
            Integer lastSnapshotID = snapshotEntries.getLast().snapperEntry().snapshotID();
            maxWidth = String.valueOf(lastSnapshotID).length();
        }
        String snapshotsComment;
        if (0 <= Config.MAX_SNAPSHOT_ENTRIES && Config.MAX_SNAPSHOT_ENTRIES <= 10000) {
            snapshotsComment = snapshotEntries.size() + " / " + Config.MAX_SNAPSHOT_ENTRIES + " snapshots";
        } else {
            snapshotsComment = snapshotEntries.size() + " snapshots";
        }

        if (limine.isSnapshotsInsideOsNode) {
            // Snapshots-node "//Snapshots" is inside your OS node
            limine.snapshotsNode.configLines.clear();
            limine.snapshotsNode.nodes.clear();
            limine.snapshotsNode.addConfigLine(Config.SPACES + "### Auto-generated by limine-snapper-sync");
            limine.snapshotsNode.addConfigLine(Config.SPACES + Limine.COMMENT + " " + snapshotsComment);
            limine.snapshotsNode.enableLineBreak = true;

            for (SnapshotEntry snapshotEntry : snapshotEntries) {
                String description = snapshotEntry.snapperEntry().properties().get(SnapperProperty.DESCRIPTION.replace);
                if (description == null) {
                    description = "";
                }
                String snapshotName = this.snapshotNameFormat(maxWidth, snapshotEntry.snapperEntry().snapshotID(), snapshotEntry.snapperEntry().localTime(), description);
                TreeNode snapshotNode = new TreeNode(snapshotName, 3, Config.TARGET_OS_NAME);
                snapshotNode.addConfigLine(Config.SPACES + Limine.COMMENT + " " + description);
                for (KernelEntry kernel : snapshotEntry.kernelEntries()) {
                    TreeNode kernelNode = buildBootNode(kernel, Limine.SUB_X3_ENTRY_KEY.toString(), 4, "Snapshots");
                    if (!kernelNode.configLines.isEmpty() || !kernelNode.nodes.isEmpty()) {
                        snapshotNode.addNode(kernelNode);
                    }
                }
                if (!snapshotNode.nodes.isEmpty()) {
                    // Insert the snapshots-node at the beginning of the list.
                    limine.snapshotsNode.nodes.addFirst(snapshotNode);
                }
            }
        } else {
            // Snapshots-node "/Snapshots" is outside your OS node.
            // Search your OS as a sub snapshots-node inside the snapshots-node.
            // If not available, create a new sub-snapshots-node of your OS inside the snapshots-node.
            TreeNode osNode = limine.snapshotsNode.findOsNode(config.machineID(), Config.TARGET_OS_NAME, 1);
            if (osNode == null) {
                // Create a new node of your OS snapshots
                String osName = Config.SPACES + Limine.SUB_ENTRY_KEY + Config.TARGET_OS_NAME;
                osNode = new TreeNode(osName, 2, Limine.SNAPSHOTS_OUTSIDE_OS_NODE.toString());
                limine.snapshotsNode.addNode(osNode);
            } else {
                osNode.configLines.clear();
                osNode.nodes.clear();
            }
            // Update the OS name - rename the old OS name to the new OS name
            osNode.name = Config.SPACES + Limine.SUB_ENTRY_KEY + Config.TARGET_OS_NAME;
            osNode.cleanName = Config.TARGET_OS_NAME;
            osNode.addConfigLine(Config.SPACES + "### Auto-generated by limine-snapper-sync");
            osNode.addConfigLine(Config.SPACES + Limine.COMMENT + " " + snapshotsComment);
            osNode.addConfigLine(Config.SPACES + Limine.COMMENT + " " + EntryOptions.Name.MACHINE_ID + config.machineID());
            osNode.enableLineBreak = true;

            for (SnapshotEntry snapshotEntry : snapshotEntries) {
                String description = snapshotEntry.snapperEntry().properties().get(SnapperProperty.DESCRIPTION.replace);
                if (description == null) {
                    description = "";
                }
                String snapshotName = this.snapshotNameFormat(maxWidth, snapshotEntry.snapperEntry().snapshotID(), snapshotEntry.snapperEntry().localTime(), description);
                TreeNode snapshotNode = new TreeNode(snapshotName, 3, Config.TARGET_OS_NAME);
                snapshotNode.addConfigLine(Config.SPACES + Limine.COMMENT + " " + description);
                for (KernelEntry kernel : snapshotEntry.kernelEntries()) {
                    TreeNode kernelNode = buildBootNode(kernel, Limine.SUB_X3_ENTRY_KEY.toString(), 4, "Snapshots");
                    if (!kernelNode.configLines.isEmpty() || !kernelNode.nodes.isEmpty()) {
                        snapshotNode.addNode(kernelNode);
                    }
                }
                if (!snapshotNode.nodes.isEmpty()) {
                    // Insert the snapshots-node at the beginning of the list.
                    osNode.nodes.addFirst(snapshotNode);
                }
            }
        }
    }

    private TreeNode buildBootNode(KernelEntry kernel, String entryKey, int depth, String parentName) {
        String kernelName = Config.SPACES + entryKey + kernel.kernelVersion();
        TreeNode kernelNode = new TreeNode(kernelName, 3, parentName);

        if (kernel.allInSnapshotConfig() == null || kernel.allInSnapshotConfig().isEmpty()) {
            for (OptionDetail option : kernel.optionDetails()) {
                kernelNode.configLines.add(Config.SPACES + option.getSnapshotLine());
            }
            for (ImageDetail image : kernel.imageDetails()) {
                kernelNode.configLines.add(Config.SPACES + image.getSnapshotLine());
            }
            for (CmdlineDetail cmdline : kernel.cmdlineDetails()) {
                kernelNode.configLines.add(Config.SPACES + cmdline.getSnapshotLine());
            }
        } else {
            for (String snapConfigLine : kernel.allInSnapshotConfig()) {
                kernelNode.configLines.add(Config.SPACES + snapConfigLine.trim());
            }
        }

        if (kernel.subKernels() != null && !kernel.subKernels().isEmpty()) {
            for (KernelEntry subKernel : kernel.subKernels()) {
                TreeNode subKernelNode = buildBootNode(subKernel, entryKey + Limine.ENTRY_KEY, depth + 1, kernel.kernelVersion());
                if (!subKernelNode.configLines.isEmpty() || !subKernelNode.nodes.isEmpty()) {
                    kernelNode.addNode(subKernelNode);
                }
            }
        }
        return kernelNode;
    }

    private String snapshotNameFormat(int maxWidth, Integer snapshotID, String timestamp, String description) {
        // Format snapshotID to be left-aligned with dynamic width.
        // Note: Do not use "right-aligned" as Limine removes right-aligned spaces by default.
        String formattedSnapshotID = String.format("%-" + maxWidth + "d", snapshotID);
        return switch (Config.SNAPSHOT_FORMAT_CHOICE) {
            case "1" -> Config.SPACES + Limine.SUB_X2_ENTRY_KEY + formattedSnapshotID + "│" + timestamp;
            case "2" -> Config.SPACES + Limine.SUB_X2_ENTRY_KEY + formattedSnapshotID + " │ " + timestamp;
            case "3" -> Config.SPACES + Limine.SUB_X2_ENTRY_KEY + timestamp + "│" + snapshotID;
            case "4" -> Config.SPACES + Limine.SUB_X2_ENTRY_KEY + timestamp + " │ " + snapshotID;
            case "5" -> Config.SPACES + Limine.SUB_X2_ENTRY_KEY + timestamp;
            case "6" -> Config.SPACES + Limine.SUB_X2_ENTRY_KEY + snapshotID;
            case "7" -> Config.SPACES + Limine.SUB_X2_ENTRY_KEY + snapshotID + "│" + description;
            case "8" -> Config.SPACES + Limine.SUB_X2_ENTRY_KEY + timestamp + "│" + description;
            // Note: "│" helps avoid escaping characters when matching Limine keys e.g, + or /
            case "9" -> Config.SPACES + Limine.SUB_X2_ENTRY_KEY + "│" + description;
            default -> Config.SPACES + Limine.SUB_X2_ENTRY_KEY + "ID=" + formattedSnapshotID + " " + timestamp;
        };
    }

    private void writeNodeToLines(TreeNode node, List<String> lines, int depth) {
        if (depth > 0) {
            lines.add(node.name);
        }
        lines.addAll(node.configLines);
        for (TreeNode childNode : node.nodes) {
            writeNodeToLines(childNode, lines, depth + 1);
        }
        if (node.enableLineBreak) {
            lines.add("");
        }
    }
}


