package org.limine.snapper.processes;

import org.limine.snapper.objects.Messages;
import org.limine.snapper.formats.json1.CmdlineDetail;
import org.limine.snapper.formats.json1.ImageDetail;
import org.limine.snapper.formats.json1.KernelEntry;
import org.limine.snapper.formats.json1.OptionDetail;
import org.limine.snapper.formats.limine8.Limine;
import org.limine.snapper.objects.Config;
import org.limine.snapper.objects.TreeNode;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KernelReader {

    private final Config config;
    private final HashMap<String, ImageDetail> imageCache;
    private boolean isBootConfigValid;

    public KernelReader(Config config) {
        this.config = config;
        this.imageCache = new HashMap<>();
        this.isBootConfigValid = true;
    }

    public String applyKernelParameterChanges(String value) {
        if (!Config.KERNEL_PARAMETERS_TO_REPLACE.isEmpty()) {
            value = String.join(" ", Config.KERNEL_PARAMETERS_TO_REPLACE);
        }

        if (Config.KERNEL_PARAMETERS_TO_ADD.isEmpty() && Config.KERNEL_PARAMETERS_TO_REMOVE.isEmpty()) {
            return value;
        }

        // Preserve insertion order, avoid duplicates
        Set<String> tokens = new LinkedHashSet<>();
        if (value != null && !value.isEmpty()) {
            String[] parts = value.trim().split("\\s+");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    tokens.add(part);
                }
            }
        }

        // Remove unwanted parameters
        for (String param : Config.KERNEL_PARAMETERS_TO_REMOVE) {
            tokens.remove(param);
        }

        // Add required parameters
        tokens.addAll(Config.KERNEL_PARAMETERS_TO_ADD);

        // Join back into a single string
        return String.join(" ", tokens);
    }

    /**
     * Create a bootable kernel entry of the current OS.
     */
    public KernelEntry createKernelEntry(TreeNode kernelNode, String leadingSpaces, int rootSnapshotID, List<String> bootConfig) {

        List<String> clearBootConfigs = new ArrayList<>();
        List<ImageDetail> imageDetails = new ArrayList<>();
        List<CmdlineDetail> cmdlineDetails = new ArrayList<>();
        List<OptionDetail> optionDetails = new ArrayList<>();
        List<KernelEntry> subKernels = new ArrayList<>();

        int lineIndex = 0;

        for (String configLine : bootConfig) {
            configLine = MacroReplacer.replaceMacros(configLine);
            // Add the config line without a macro. This is necessary for restoring without macros interfering.
            clearBootConfigs.add(configLine);
            String spaces = Utility.getLeadingSpaces(configLine);
            String lowerCaseTrimLine = configLine.toLowerCase().trim();
            String extractValue = Limine.extractValue(configLine);

            if (lowerCaseTrimLine.startsWith("#") || lowerCaseTrimLine.isBlank()) {
                continue;

            } else if (lowerCaseTrimLine.startsWith(Limine.PATH.toString())) {
                ImageDetail imageDetail = this.convertToImageDetail(Limine.PATH.name(), spaces, lineIndex, extractValue);
                if (imageDetail == null) continue;
                imageDetails.add(imageDetail);

            } else if (lowerCaseTrimLine.startsWith(Limine.KERNEL_PATH.toString())) {
                ImageDetail imageDetail = this.convertToImageDetail(Limine.KERNEL_PATH.name(), spaces, lineIndex, extractValue);
                if (imageDetail == null) continue;
                imageDetails.add(imageDetail);

            } else if (lowerCaseTrimLine.startsWith(Limine.IMAGE_PATH.toString())) {
                ImageDetail imageDetail = this.convertToImageDetail(Limine.IMAGE_PATH.name(), spaces, lineIndex, extractValue);
                if (imageDetail == null) continue;
                imageDetails.add(imageDetail);

            } else if (lowerCaseTrimLine.startsWith(Limine.MODULE_PATH.toString())) {
                ImageDetail imageDetail = this.convertToImageDetail(Limine.MODULE_PATH.name(), spaces, lineIndex, extractValue);
                if (imageDetail == null) continue;
                imageDetails.add(imageDetail);

            } else if (lowerCaseTrimLine.startsWith(Limine.CMDLINE.toString())) {
                String rootSubvolumePath = Config.ROOT_SUBVOLUME_PATH;
                String rootSnapshotPath = Utility.buildPath(Config.ROOT_SNAPSHOTS_PATH, rootSnapshotID + "/snapshot");
                String kernelCmdline = applyKernelParameterChanges(extractValue);
                String snapshotCmdline = replaceSubvolPath(kernelCmdline, rootSubvolumePath, rootSnapshotPath);
                cmdlineDetails.add(new CmdlineDetail(Limine.CMDLINE.name(), spaces, lineIndex, extractValue, snapshotCmdline, null));

            } else if (lowerCaseTrimLine.startsWith(Limine.KERNEL_CMDLINE.toString())) {
                String rootSubvolumePath = Config.ROOT_SUBVOLUME_PATH;
                String rootSnapshotPath = Utility.buildPath(Config.ROOT_SNAPSHOTS_PATH, rootSnapshotID + "/snapshot");
                String kernelCmdline = applyKernelParameterChanges(extractValue);
                String snapshotCmdline = replaceSubvolPath(kernelCmdline, rootSubvolumePath, rootSnapshotPath);
                cmdlineDetails.add(new CmdlineDetail(Limine.KERNEL_CMDLINE.name(), spaces, lineIndex, extractValue, snapshotCmdline, null));

            } else if (lowerCaseTrimLine.startsWith(Limine.COMMENT.toString())) {
                optionDetails.add(new OptionDetail(Limine.COMMENT.name(), spaces, lineIndex, extractValue, extractValue, null));

            } else if (lowerCaseTrimLine.startsWith(Limine.PROTOCOL.toString())) {
                optionDetails.add(new OptionDetail(Limine.PROTOCOL.name(), spaces, lineIndex, extractValue, extractValue, null));

            } else if (lowerCaseTrimLine.startsWith(Limine.SUB_ENTRY_KEY.toString())) {
                String errMessage = Messages.get("text.incorrect-config-some-bug-entry", configLine.trim());
                Utility.exitWithError(errMessage);
                return null;
            } else {
                optionDetails.add(new OptionDetail(Limine.extractKey(configLine), spaces, lineIndex, extractValue, extractValue, null));
            }
            lineIndex++;
        }
        if (cmdlineDetails.isEmpty() && kernelNode.nodes.isEmpty()) {
            Utility.errorMessage(Messages.get("error.kernel-entry-has-no-kernel", kernelNode.cleanName, Utility.buildPath(config.espPath(), Config.LIMINE_CONFIG_FILE)));
        }

        for (TreeNode subKernelNode : kernelNode.nodes) {
            KernelEntry subKernelEntry = createKernelEntry(subKernelNode, Utility.getLeadingSpaces(subKernelNode.name), rootSnapshotID, subKernelNode.configLines);
            subKernels.add(subKernelEntry);
        }
        return new KernelEntry(kernelNode.cleanName, leadingSpaces, optionDetails, imageDetails, cmdlineDetails, clearBootConfigs, subKernels, null, null);
    }

    private ImageDetail convertToImageDetail(String limineKey, String leadingSpaces, int lineIndex, String pathLine) {

        if (imageCache.containsKey(pathLine)) {
            return imageCache.get(pathLine);
        }

        Map<String, String> imageProperties = new HashMap<>();

        // Define regular expressions for matching keys and extracting their values
        Pattern pathResourceRegex = Pattern.compile(Limine.PATH_RESOURCE_PATTERN.toString());
        Pattern hashRegex = Pattern.compile(Limine.HASH_PATTERN.toString());

        Matcher pathResourceMatcher = pathResourceRegex.matcher(pathLine);
        Matcher hashMatcher = hashRegex.matcher(pathLine);

        // Variables to store the extracted keys and hash
        String pathResource;
        String pathValue = pathLine;
        String blake2sum = "";

        // Check for and extract the path resource.
        if (pathResourceMatcher.find()) {
            pathResource = pathResourceMatcher.group();
            pathValue = pathLine.substring(pathValue.indexOf(pathResource) + pathResource.length()).trim();
        } else {
            Utility.errorMessage(Messages.get("error.unsupported-boot-path-limine-conf"));
            isBootConfigValid = false;
            imageCache.put(pathLine, null);
            return null;
        }

        // Check for and extract the hash
        if (hashMatcher.find()) {
            blake2sum = hashMatcher.group();
            pathValue = pathValue.substring(0, pathValue.indexOf(blake2sum)).trim();
        }

        String fileName;
        String espDirPath = "";
        // Find the last "/" in the path
        int lastSlashIndex = pathValue.lastIndexOf('/');

        // If there is no "/" in the string
        if (lastSlashIndex == -1) {
            fileName = pathValue;
        } else {
            // Extract the file name (everything after the last "/")
            fileName = pathValue.substring(lastSlashIndex + 1);
            // Extract the directory path (everything before the last "/")
            espDirPath = pathValue.substring(0, lastSlashIndex + 1);
        }

        String filePath = Utility.buildPath(config.espPath(), espDirPath, fileName);
        String hashSum;
        if (Utility.isFilePresent(filePath)) {
            hashSum = Utility.calculateHash(filePath);
        } else if (!Config.RESTORE_MODE) {
            Utility.errorMessage(Messages.get("error.skipping-snapshot-kernel-entry-creation"), Messages.get("error.missing-file", filePath));
            hashSum = Config.NO_FILE_NAME;
            isBootConfigValid = false;
        } else {
            hashSum = Config.NO_FILE_NAME;
            isBootConfigValid = false;
        }

        String fileHashName = fileName + Config.HASH_FUNCTION.hashName + hashSum;
        String snapshotPathValue = Utility.buildPath(config.machineID(), Config.HISTORY_FOLDER, fileHashName + blake2sum);
        imageProperties.put(Limine.PATH_RESOURCE.name(), pathResource);
        imageProperties.put(Limine.HASH.name(), blake2sum);
        ImageDetail newImage = new ImageDetail(limineKey, leadingSpaces, lineIndex, fileName, fileHashName, espDirPath, pathValue, snapshotPathValue, imageProperties);
        imageCache.put(pathLine, newImage);
        return newImage;
    }

    /**
     * Replace a subvolume path with a snapshot path.
     */
    public String replaceSubvolPath(String kernelCmdline, String subvolumePath, String snapshotPath) {
        // Skip if KERNEL_PARAMETERS_TO_REPLACE is set, since it has already been applied.
        if (!Config.KERNEL_PARAMETERS_TO_REPLACE.isEmpty()) {
            return kernelCmdline;
        }
        StringBuilder keyword = new StringBuilder();
        // Keyword adds some spaces and "kernel_cmdline:" or "cmdline:" from the kernel cmdline.
        if (kernelCmdline.contains(Limine.KERNEL_CMDLINE.toString()) || kernelCmdline.contains(Limine.CMDLINE.toString())) {
            for (int i = 0; i < kernelCmdline.length(); i++) {
                char c = kernelCmdline.charAt(i);
                keyword.append(c);
                if (c == ':') {
                    kernelCmdline = kernelCmdline.substring(i + 1).trim();
                    break;
                }
            }
        }

        String[] kernelParameters = kernelCmdline.split(" ");
        boolean isRootFlagsFound = false;
        boolean isSubvolFlagFound = false;
        for (int i = 0; i < kernelParameters.length; i++) {
            String[] rootflags = Utility.readRootFlags(kernelParameters[i]);
            if (rootflags != null) {
                kernelParameters[i] = "rootflags=";
                isRootFlagsFound = true;
                // Split the rootflags configuration part into individual flags
                for (int f = 0; f < rootflags.length; f++) {
                    // Check if the flag contains the "subvol" keyword
                    if (rootflags[f].startsWith("subvol=")) {
                        isSubvolFlagFound = true;
                        String subvol = rootflags[f].substring("subvol=".length());
                        subvol = Utility.buildPath(subvol);
                        if (!subvol.equals(subvolumePath) && !Config.RESTORE_MODE && !Config.USE_OPENSUSE_LAYOUT) {
                            //A broken cmdline is detected, inform user with an error message. This ensures user awareness.
                            Utility.errorMessage(Messages.get("error.mismatch-detected"), Messages.get("error.root-subvolume-path-cmdline-mismatch", subvolumePath, subvol));
                        }
                        // Replace the subvolume path with the snapshot path
                        rootflags[f] = "subvol=" + snapshotPath;
                    }
                    kernelParameters[i] += rootflags[f] + (f + 1 == rootflags.length ? "" : ",");
                }
                if (!isSubvolFlagFound) {
                    if (!Config.RESTORE_MODE && !Config.USE_OPENSUSE_LAYOUT) {
                        //A broken cmdline is detected, inform user with an error message. This ensures user awareness.
                        Utility.errorMessage(Messages.get("error.missing-subvol-kernel-cmdline-please", Config.ROOT_SUBVOLUME_PATH));
                    }
                    if (0 < rootflags.length) {
                        kernelParameters[i] += ",subvol=" + snapshotPath;
                    } else {
                        kernelParameters[i] += "subvol=" + snapshotPath;
                    }
                }
                //break; // If without break, then replace all the same subvolumes.
            }
        }

        //If a broken cmdline is detected, it will be automatically fixed by appending 'rootflags=subvol=<snapshot>' at the end of the cmdline
        if (!isRootFlagsFound) {
            kernelParameters[kernelParameters.length - 1] += " rootflags=subvol=" + snapshotPath;
            if (!Config.RESTORE_MODE && !Config.USE_OPENSUSE_LAYOUT) {
                //A broken cmdline is detected, inform user with an error message. This ensures user awareness.
                Utility.errorMessage(Messages.get("error.missing-rootflags-subvol-kernel-cmdline", Config.ROOT_SUBVOLUME_PATH));
            }
        }

        StringBuilder cmdline = new StringBuilder();
        cmdline.append(keyword);
        for (int i = 0; i < kernelParameters.length; i++) {
            cmdline.append(kernelParameters[i]);
            if (i < kernelParameters.length - 1) {
                cmdline.append(" ");
            }
        }
        return cmdline.toString();
    }

    public boolean isBootConfigValid() {
        return isBootConfigValid;
    }

    public void resetBootConfigValid() {
        isBootConfigValid = true;
    }
}
