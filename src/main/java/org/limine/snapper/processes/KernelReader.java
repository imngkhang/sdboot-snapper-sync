package org.limine.snapper.processes;

import org.limine.snapper.objects.Messages;
import org.limine.snapper.formats.json1.CmdlineDetail;
import org.limine.snapper.formats.json1.ImageDetail;
import org.limine.snapper.formats.json1.KernelEntry;
import org.limine.snapper.formats.json1.OptionDetail;
import org.limine.snapper.objects.Config;
import org.limine.snapper.objects.TreeNode;

import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

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
            int colon = configLine.indexOf(':');
            String key = colon < 0 ? "" : configLine.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String extractValue = colon < 0 ? "" : configLine.substring(colon + 1).trim();

            if (lowerCaseTrimLine.startsWith("#") || lowerCaseTrimLine.isBlank()) {
                continue;

            } else if (key.equals("linux") || key.equals("efi") || key.equals("initrd")) {
                ImageDetail imageDetail = this.convertToImageDetail(key.toUpperCase(Locale.ROOT), spaces, lineIndex, extractValue);
                if (imageDetail == null) continue;
                imageDetails.add(imageDetail);
            } else if (key.equals("options")) {
                String rootSubvolumePath = Config.ROOT_SUBVOLUME_PATH;
                String rootSnapshotPath = Utility.buildPath(Config.ROOT_SNAPSHOTS_PATH, rootSnapshotID + "/snapshot");
                String kernelCmdline = applyKernelParameterChanges(extractValue);
                String snapshotCmdline = replaceSubvolPath(kernelCmdline, rootSubvolumePath, rootSnapshotPath);
                cmdlineDetails.add(new CmdlineDetail("OPTIONS", spaces, lineIndex, extractValue, snapshotCmdline, null));
            } else {
                optionDetails.add(new OptionDetail(key.toUpperCase(Locale.ROOT), spaces, lineIndex, extractValue, extractValue, null));
            }
            lineIndex++;
        }
        if (imageDetails.isEmpty() && kernelNode.nodes.isEmpty()) {
            Utility.errorMessage(Messages.get("error.kernel-entry-has-no-kernel", kernelNode.cleanName, Utility.buildPath(config.espPath(), Config.BOOT_ENTRIES_DIRECTORY)));
        }

        for (TreeNode subKernelNode : kernelNode.nodes) {
            KernelEntry subKernelEntry = createKernelEntry(subKernelNode, Utility.getLeadingSpaces(subKernelNode.name), rootSnapshotID, subKernelNode.configLines);
            subKernels.add(subKernelEntry);
        }
        return new KernelEntry(kernelNode.cleanName, leadingSpaces, optionDetails, imageDetails, cmdlineDetails, clearBootConfigs, subKernels, null, null);
    }

    private ImageDetail convertToImageDetail(String bootKey, String leadingSpaces, int lineIndex, String pathLine) {

        String cacheKey = bootKey + ":" + pathLine;
        if (imageCache.containsKey(cacheKey)) {
            return imageCache.get(cacheKey);
        }

        Map<String, String> imageProperties = new HashMap<>();

        String pathValue = pathLine.trim();
        if (pathValue.startsWith("/")) pathValue = pathValue.substring(1);

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
            hashSum = calculateSha256(filePath);
        } else if (!Config.RESTORE_MODE) {
            Utility.errorMessage(Messages.get("error.skipping-snapshot-kernel-entry-creation"), Messages.get("error.missing-file", filePath));
            hashSum = Config.NO_FILE_NAME;
            isBootConfigValid = false;
        } else {
            hashSum = Config.NO_FILE_NAME;
            isBootConfigValid = false;
        }

        // A content-addressed name makes equal kernel/initramfs payloads share one stored copy,
        // even when their source names differ.
        String fileHashName = "image_sha256_" + hashSum;
        String snapshotPathValue = Utility.buildPath(config.machineID(), Config.HISTORY_FOLDER, fileHashName);
        if (bootKey.equals("EFI")) imageProperties.put("BLS_EFI", "true");
        ImageDetail newImage = new ImageDetail(bootKey, leadingSpaces, lineIndex, fileName, fileHashName, espDirPath, pathValue, snapshotPathValue, imageProperties);
        imageCache.put(cacheKey, newImage);
        return newImage;
    }

    private static String calculateSha256(String path) {
        try (var input = Files.newInputStream(Path.of(path))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            Utility.errorMessage(Messages.get("error.hash-calculation-failed-no-file", path));
            return Config.NO_FILE_NAME;
        }
    }

    /**
     * Replace a subvolume path with a snapshot path.
     */
    public String replaceSubvolPath(String kernelCmdline, String subvolumePath, String snapshotPath) {
        // Skip if KERNEL_PARAMETERS_TO_REPLACE is set, since it has already been applied.
        if (!Config.KERNEL_PARAMETERS_TO_REPLACE.isEmpty()) {
            return kernelCmdline;
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
