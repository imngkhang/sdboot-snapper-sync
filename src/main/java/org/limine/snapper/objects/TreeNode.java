package org.limine.snapper.objects;

import org.limine.snapper.processes.MacroReplacer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TreeNode implements Comparable<TreeNode> {

    public final List<String> configLines;
    public final LinkedList<TreeNode> nodes;
    public final int depth;
    public final String parentNodeName;
    public String name, cleanName;
    public boolean enableLineBreak;
    private EntryOptions options;

    public TreeNode(String name, int depth, String parentNodeName) {
        this.name = name;
        this.cleanName = MacroReplacer.replaceMacros(name == null ? null : name.trim().replaceFirst("^/+", ""));
        this.configLines = new ArrayList<>();
        this.nodes = new LinkedList<>();
        this.depth = depth;
        this.parentNodeName = parentNodeName;
        this.enableLineBreak = false;
        this.options = null;
    }

    public void addConfigLine(String line) {
        this.configLines.add(line);
    }

    public void addNode(TreeNode node) {
        this.nodes.add(node);
    }

    public TreeNode findOsNode(String machineID, String osName, int selectedDepth) {
        TreeNode node = findNodeByMachineID(machineID, selectedDepth);
        if (node == null) {
            return findNodeByCleanName(osName, machineID, selectedDepth);
        } else {
            return node;
        }
    }

    public TreeNode findNodeByCleanName(String nodeCleanName, String machineID, int selectedDepth) {
        if (nodeCleanName == null || nodeCleanName.isEmpty()) return null;
        // Check for match at target depth
        if (selectedDepth <= 0) {
            if (!nodeCleanName.equals(this.cleanName)) return null;
            // Match if machineID is null/empty or matches current machine
            boolean machineIDMatches = this.options == null
                    || this.options.machineID == null
                    || this.options.machineID.isEmpty()
                    || this.options.machineID.equals(machineID);
            return machineIDMatches ? this : null;
        }
        // Search children with reduced depth
        int remainingDepth = selectedDepth - 1;
        for (TreeNode node : nodes) {
            TreeNode result = node.findNodeByCleanName(nodeCleanName, machineID, remainingDepth);
            if (result != null) return result;
        }
        return null;
    }

    public TreeNode findNodeByName(String name, int selectedDepth, boolean isNameFullLine, boolean ignoreCase) {
        if (name == null || name.isEmpty()) return null;
        // Check for match at target depth
        if (selectedDepth <= 0) {
            boolean isMatch;
            if (isNameFullLine) {
                isMatch = ignoreCase
                        ? this.name.trim().equalsIgnoreCase(name)
                        : this.name.trim().equals(name);
            } else {
                isMatch = ignoreCase
                        ? cleanName.equalsIgnoreCase(name)
                        : cleanName.equals(name);
            }
            return isMatch ? this : null;
        }
        // Search children with reduced depth
        int remainingDepth = selectedDepth - 1;
        for (TreeNode node : nodes) {
            TreeNode result = node.findNodeByName(name, remainingDepth, isNameFullLine, ignoreCase);
            if (result != null) return result;
        }
        return null;
    }

    public TreeNode findNodeByMachineID(String machineID, int selectedDepth) {
        if (0 >= selectedDepth) {
            if (machineID.equals(this.getOrCreateOptions().machineID)) {
                return this;
            }
            return null;
        }
        selectedDepth--;
        for (TreeNode node : nodes) {
            TreeNode result = node.findNodeByMachineID(machineID, selectedDepth);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private EntryOptions getOrCreateOptions() {
        if (this.options == null) {
            this.options = new EntryOptions();
            for (String line : this.configLines) {
                if (line.trim().startsWith("comment:")) {
                    this.options.parseComment(MacroReplacer.replaceMacros(line));
                }
            }
        }
        return options;
    }

    @Override
    public String toString() {
        return "TreeNode{" + "name='" + name + '\'' + ", configBlock=" + configLines + ", depth=" + depth + ", parentNodeName='" + parentNodeName + '\'' + '}';
    }

    @Override
    public int compareTo(TreeNode other) {
        return this.cleanName.compareTo(other.cleanName);
    }

}
