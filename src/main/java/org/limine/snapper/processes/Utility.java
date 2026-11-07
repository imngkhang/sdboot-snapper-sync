package org.limine.snapper.processes;

import org.limine.snapper.objects.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utility {

    private static final JsonMapper OBJECT_MAPPER = new JsonMapper();
    private static final int MACHINE_ID_LENGTH = 32;
    private static final String MACHINE_ID_FILE_PATH = "/etc/machine-id";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    // Regex for forbidden escape sequences and characters
    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
            "\\x1B\\[[A-Za-z]|\n"  // Arrow key escape sequences (e.g., \x1B[A for Up Arrow)
    );


    /**
     * Check if you use Btrfs?
     *
     * @return if you don't use Btrfs, then return false.
     */
    public static boolean areYouInBtrfs() {
        return areYouInBtrfs("/");
    }

    public static boolean areYouInBtrfs(String path) {
        return "btrfs".equalsIgnoreCase(getFileSystemType(path, true));
    }


    /**
     * Chroot detection by comparing device and inode of "/" vs "/proc/1/root/."
     */
    public static boolean areYouInChroot() {
        String root = getDeviceAndInode("/");
        String pid1Root = getDeviceAndInode("/proc/1/root/.");
        return root != null && pid1Root != null && !root.equals(pid1Root);
    }


    /**
     * Checks if a snapshot path exists in the /proc/cmdline command.
     *
     * @return true, if a snapshot path is found, otherwise false.
     */
    public static boolean areYouInSnapshot() {
        String snapshotPattern = "subvol=.*?/([0-9]+)/snapshot";
        try (BufferedReader reader = new BufferedReader(new FileReader(Config.PROC_CMDLINE))) {
            String line = reader.readLine();
            if (line != null) {
                Pattern pattern = Pattern.compile(snapshotPattern);
                Matcher matcher = pattern.matcher(line);
                return matcher.find();
            }
        } catch (IOException ignored) {
        }
        return false;
    }


    /**
     * Ask user to choose "yes", "no" or "cancel"
     */
    public static boolean askUser(String ask, String prompt, String yes, String no, String cancel) {
        // Read user input from the console
        boolean isYes = false;
        while (true) {
            // Ask the user to provide something
            System.out.println(ask);
            // Read the entire entered line
            String userInput = TerminalReader.readLine(prompt);
            if (userInput.trim().isEmpty()) {
                System.err.println(ConsoleColor.RED + Messages.get("text.input-must-not-be-empty") + ConsoleColor.RESET);
            } else if (yes.startsWith(userInput.toLowerCase())) {
                isYes = true;
                break;
            } else if (no.startsWith(userInput.toLowerCase())) {
                break;
            } else if (cancel.startsWith(userInput.toLowerCase())) {
                System.out.println(Messages.get("text.aborted"));
                System.exit(0);
                isYes = false;
            } else {
                System.err.println(ConsoleColor.RED + Messages.get("text.unknown-input") + ConsoleColor.RESET + userInput);
            }
        }
        return isYes;
    }


    /**
     * Build a path.
     */
    public static String buildPath(String... parts) {
        String path = Paths.get("", parts).toString();
        if (path.startsWith(File.separator)) {
            return path;
        } else {
            return File.separator + path;
        }
    }


    /**
     * Calculate a hash code of a chosen file.
     */
    public static String calculateHash(String absoluteFilePath) {
        return fileHashIntegrityCheck(absoluteFilePath, Config.HASH_FUNCTION, 2);
    }

    /**
     * Calculate a hash code of a chosen file.
     */
    private static String calculateHash(String absoluteFilePath, Hash hashFunction) {
        Output output = getCommandOutput(hashFunction.command + " " + absoluteFilePath, false, Messages.get("error.hash-calculation-failed-no-file", absoluteFilePath));
        if (output.isSuccess()) {
            return extractHash(output.text().getFirst());
        } else {
            return Config.NO_FILE_NAME;
        }
    }

    /**
     * Calculate the hash of a file using two parallel threads and check for hash corruption.
     * If corruption is detected, the process is repeated a specified number of times.
     *
     * @param absoluteFilePath  The path of the file to calculate the hash for.
     * @param hashFunction      The hashing function to use.
     * @param repeatIfCorrupted The number of times to retry if corruption is detected.
     * @return The final hash if no corruption is detected.
     */
    public static String fileHashIntegrityCheck(String absoluteFilePath, Hash hashFunction, int repeatIfCorrupted) {
        int attempts = 0;
        while (attempts <= repeatIfCorrupted) {
            try {
                // Run hash calculation asynchronously in two threads using CompletableFuture
                CompletableFuture<String> hashFuture1 = CompletableFuture.supplyAsync(() -> calculateHash(absoluteFilePath, hashFunction));
                CompletableFuture<String> hashFuture2 = CompletableFuture.supplyAsync(() -> calculateHash(absoluteFilePath, hashFunction));

                // Get the hash results from both futures
                String hashResult1 = hashFuture1.get();
                String hashResult2 = hashFuture2.get();

                // Compare the results
                if (hashResult1.equals(hashResult2)) {
                    return hashResult1;  // Return the hash if they match (no corruption detected)
                } else {
                    // Log and print an error message if corruption is detected
                    String errMessage = Messages.get("error.hardware-error-hash-mismatch",
                            absoluteFilePath, attempts + 1, repeatIfCorrupted + 1);
                    errorMessage(errMessage);
                    if (attempts == repeatIfCorrupted) {
                        // If max attempts reached, return the first result and log a critical error
                        errMessage = Messages.get("text.repeated-hash-mismatches-hardware-is");
                        if (areYouInBtrfs("/")) {
                            setSubvolumeProperty("/", true);
                        }
                        if (areYouInBtrfs("/home")) {
                            setSubvolumeProperty("/home", true);
                        }
                        errorMessage(errMessage);
                        return hashResult1;
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error occurred while calculating the hash.", e);
            }
            attempts++;
        }
        // Should not reach this point, but return the last result as a fallback
        throw new IllegalStateException("Unexpected state in hash calculation.");
    }

    private static String extractHash(String outputLine) {
        StringBuilder hash = new StringBuilder();
        outputLine = outputLine.trim();
        for (int i = 0; i < outputLine.length(); i++) {
            char c = outputLine.charAt(i);
            if (c == ' ') {
                break;
            }
            hash.append(c);
        }
        return hash.toString();
    }


    /**
     * Change POSIX permissions of a file.
     */
    public static void changeFilePermissions(String filePath, String permissions) {
        Path path = Paths.get(filePath);
        // Convert the permission string to a set of PosixFilePermissions
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString(permissions);
        try {
            Files.setPosixFilePermissions(path, perms);
            infoMessage(Messages.get("info.permissions-changed-file", permissions), filePath);
        } catch (IOException e) {
            String errMessage = Messages.get("text.failed-change-permissions-file", permissions, filePath);
            errorMessage(errMessage);
        }
    }


    /**
     * Check if your Btrfs layout is a valid OpenSUSE-style layout
     */
    public static boolean checkOpenSuseLayout() {
        boolean isValid = true;
        String path = "/.snapshots";
        File snapshots = new File(path);
        List<String> errorMessages = new ArrayList<>();
        errorMessages.add(Messages.get("error.your-btrfs-layout-is-not"));

        // 1. Must exist
        if (!snapshots.exists()) {
            errorMessages.add(Messages.get("error.directory-does-not-exist", path));
            isValid = false;
        }

        // 2. Must be a directory
        if (!snapshots.isDirectory()) {
            errorMessages.add(Messages.get("error.is-not-directory", path));
            isValid = false;
        }

        // 3. Must be Btrfs filesystem
        if (!"btrfs".equalsIgnoreCase(getFileSystemType(path, true))) {
            errorMessages.add(Messages.get("error.is-not-btrfs-filesystem", path));
            isValid = false;
        }

        // 4. Must not be empty
        File[] sub = snapshots.listFiles();
        boolean empty = true;
        if (sub != null) {
            for (File f : sub) {
                if (f.isDirectory()) {
                    empty = false;
                    break;
                }
            }
        }
        if (empty) {
            errorMessages.add(Messages.get("error.is-empty-no-snapshots-found", path));
            isValid = false;
        }

        // 5. Root must also be Btrfs
        if (!"btrfs".equalsIgnoreCase(getFileSystemType("/", true))) {
            errorMessages.add(Messages.get("error.root-is-not-btrfs-filesystem"));
            isValid = false;
        }

        if (!isValid) {
            errorMessage(String.join("\n", errorMessages));
        }
        return isValid;
    }


    /**
     * Get list information about all files from the directory.
     */
    public static List<String> collectFilesFromDirectory(String directoryPath) {
        List<String> list = new ArrayList<>();
        Path path = Paths.get(directoryPath);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                list.add(fileName);
            }
        } catch (IOException ignored) {
            errorMessage(Messages.get("error.the-directory-cannot-be-listed"), directoryPath);
        }
        return list;
    }


    /**
     * Compare two timestamps.
     *
     * @return If the value is less than 0, timestamp A is before timestamp B.
     * If it is greater than 0, timestamp A is after timestamp B.
     * If it is 0, both are the same.
     */
    public static int compareTimestamps(String timestampA, String timestampB) {
        return timestampA.compareTo(timestampB);
    }


    /**
     * Convert local time to UTC time.
     */
    public static String convertLocalToUTC(String localTime) {
        // Parsing local time directly to LocalDateTime
        LocalDateTime localLdt = LocalDateTime.parse(localTime, TIME_FORMATTER);
        // Convert to UTC time
        ZonedDateTime utcZdt = localLdt.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC);
        // Return formatted as string
        return TIME_FORMATTER.format(utcZdt);
    }


    /**
     * Convert UTC time to local time.
     */
    public static String convertUTCtoLocal(String utcTime) {
        // Parsing UTC time directly to ZonedDateTime in UTC
        LocalDateTime utcLdt = LocalDateTime.parse(utcTime, TIME_FORMATTER);
        // Convert to local time using the system default time zone
        ZonedDateTime localZdt = utcLdt.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault());
        // Return formatted local time
        return TIME_FORMATTER.format(localZdt);
    }


    /**
     * Copy a file.
     */
    public static void copyFile(String sourceFilePath, String targetFilePath, boolean force, boolean showOutput) {
        Path source = Path.of(sourceFilePath);
        Path target = Path.of(targetFilePath);
        try {
            if (force) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, target);
            }
            if (showOutput) {
                infoMessage(Messages.get("info.copied"), sourceFilePath + " -> " + targetFilePath);
            }
        } catch (IOException e) {
            errorMessage(Messages.get("error.failed-copy"), sourceFilePath + " -> " + targetFilePath + " (" + e.getMessage() + ")");
        }
    }

    public static void copyFile(String sourceFilePath, String targetFilePath) {
        copyFile(sourceFilePath, targetFilePath, true, true);
    }


    /**
     * Delete a file.
     */
    public static void deleteFile(String absoluteFilePath, boolean showOutput) {
        Path path = Path.of(absoluteFilePath);
        try {
            if (Files.deleteIfExists(path)) {
                if (showOutput) {
                    infoMessage(Messages.get("info.removed"), absoluteFilePath);
                }
            }
        } catch (IOException e) {
            errorMessage(Messages.get("error.failed-remove"), absoluteFilePath + " (" + e.getMessage() + ")");
        }
    }

    public static void deleteFile(String absoluteFilePath) {
        deleteFile(absoluteFilePath, true);
    }


    /**
     * Check whether the command exists.
     */
    public static boolean isCommandPresent(String command) {
        // Get the PATH environment variable
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) {
            return false; // If the PATH is empty, the command cannot be found
        }

        // Split the PATH by the system's path separator (":" on Linux/Unix)
        String[] paths = pathEnv.split(File.pathSeparator);

        // Iterate through each directory in PATH
        for (String path : paths) {
            // Check if the command exists in the current directory
            File commandFile = new File(path, command);
            if (commandFile.isFile() && commandFile.canExecute()) {
                return true; // Command exists and is executable
            }
        }
        return false; // Command isn't found in any PATH directories
    }


    /**
     * Check if the directory is present?
     */
    public static boolean isDirectoryPresent(String dirPath) {
        Path path = Paths.get(dirPath);
        try {
            // Check if the path exists and is a directory (following symbolic links)
            return Files.exists(path) && Files.isDirectory(path);
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * Verifies if the file is older than the specified number of hours.
     *
     * @param filePath Path to the file
     * @param hours    the number of hours to compare
     * @return true if the file is older than the specified hours, false otherwise.
     */
    public static boolean isFileOlderThan(String filePath, int hours) {
        File file = new File(filePath);
        long lastModified = file.lastModified();
        if (lastModified == 0L) {
            // File does not exist or the timestamp is invalid
            return false;
        }
        long now = System.currentTimeMillis();
        long diffMillis = now - lastModified;
        long thresholdMillis = hours * 3600_000L;
        return diffMillis > thresholdMillis;
    }


    /**
     * Check if the file is present?
     */
    public static boolean isFilePresent(String filePath) {
        File file = new File(filePath);
        return file.exists() && file.isFile();
    }


    /**
     * Check if the path is present?
     */
    public static boolean isPathPresent(String path) {
        return new File(path).exists();
    }


    /**
     * Check if root owns the file?
     */
    public static boolean isRootOwnedFile(String filePath) {
        try {
            return Objects.equals(Files.getOwner(Paths.get(filePath)).getName(), "root");
        } catch (IOException e) {
            return false;
        }
    }


    /**
     * Check if a time format is valid?
     * Note: If the snapper list and all snapshot entries do not have the same time format,
     * this will lead to all snapshot entries in the bootloader being wiped out.
     * That is why this method is important to prevent this issue.
     */
    public static boolean isTimeFormatNotValid(String timeString) {
        try {
            LocalDateTime.parse(timeString, TIME_FORMATTER);
            return false;
        } catch (DateTimeParseException e) {
            return true;
        }
    }


    public static boolean isUnicodeSupported() {
        String locale = System.getenv("LC_ALL");
        if (locale == null || locale.isEmpty()) {
            locale = System.getenv("LANG");
        }
        return locale != null && locale.toUpperCase().contains("UTF-8");
    }


    /**
     * Checks whether the specified string represents a valid UUID.
     *
     * @param uuidString the string to be checked
     * @return true, if the string is a valid UUID, otherwise false
     */
    public static boolean isUuidValid(String uuidString) {
        if (uuidString == null || uuidString.isEmpty()) {
            return false;
        }
        try {
            UUID uuid = UUID.fromString(uuidString);
            return uuidString.equals(uuid.toString());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }


    /**
     * Make sure that the directory exists. If it does not exist, it will be created.
     */
    public static boolean ensureDirectoryExists(String dirPath) {
        // Construct the full directory path
        File dir = new File(dirPath);
        // Check if the directory exists
        if (!dir.exists()) {
            // If it doesn't exist, create the directory
            if (dir.mkdirs()) {
                return true;
            } else {
                errorMessage(Messages.get("error.failed-create-directory"), dir.getPath());
                return false;
            }
        }
        return true;
    }


    /**
     * Make sure that the path exists. If it does not exist, it will be created.
     */
    public static void ensurePathExists(String filePath) {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            return;
        }
        ensureDirectoryExists(path.getParent().toString());
    }

    public static void infoMessage(String message) {
        infoMessage(message, null);
    }

    public static void infoMessage(String message, String output) {
        if (Config.QUIET) {
            return;
        }
        if (output == null || output.isEmpty()) {
            System.out.println(ConsoleColor.WHITE_BOLD + message + ConsoleColor.RESET);
        } else {
            System.out.println(ConsoleColor.GREEN_BOLD + message + ConsoleColor.RESET + " " + output);
        }
    }

    public static void warnMessage(String message) {
        warnMessage(message, null);
    }

    public static void warnMessage(String message, String output) {
        if (output == null || output.isEmpty()) {
            System.out.println(ConsoleColor.YELLOW_BOLD + message + ConsoleColor.RESET);
            loggerSend(message, LogLevel.WARNING);
        } else {
            System.out.println(ConsoleColor.YELLOW_BOLD + message + ConsoleColor.RESET + " " + output);
            loggerSend(message + " " + output, LogLevel.WARNING);
        }
    }

    public static void errorMessage(String message) {
        errorMessage(message, null);
    }

    public static void errorMessage(String message, String output) {
        if (output == null || output.isEmpty()) {
            System.err.println(ConsoleColor.RED_BOLD + message + ConsoleColor.RESET);
            loggerSend(message, LogLevel.ERROR);
        } else {
            System.err.println(ConsoleColor.RED_BOLD + message + ConsoleColor.RESET + " " + output);
            loggerSend(message + " " + output, LogLevel.ERROR);
        }
    }

    public static void exitWithError(String message) {
        exitWithError(message, null, false);
    }

    public static void exitWithError(String message, boolean hold) {
        exitWithError(message, null, hold);
    }

    public static void exitWithError(String message, String output) {
        exitWithError(message, output, false);
    }

    public static void exitWithError(String message, String output, boolean hold) {
        errorMessage(message, output);
        if (hold || Config.RESTORE_MODE) {
            deleteFile(Config.RESTORE_LOCK_FILE, false);
            // Prevent any terminal GUI from closing automatically on restore failure.
            infoMessage(Messages.get("info.review-error-before-closing-terminal"), null);
            while (!yesOrNo(Messages.get("prompt.close-the-terminal-when-ready"), false)) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
        System.exit(1);
    }


    /**
     * Get a parent directory path.
     */
    public static String extractParentDirectoryPath(String path) {
        int lastIndex = path.lastIndexOf('/');
        if (lastIndex != -1) {
            return path.substring(0, lastIndex) + "/";
        } else {
            // If no '/' is found, return ""
            return "";
        }
    }


    /**
     * Get the latest ID of Snapper snapshot.
     */
    public static int findLatestSnapperNumber(String directoryPath) {
        Path path = Paths.get(directoryPath);
        int maxNumber = -1;
        Pattern pattern = Pattern.compile("\\d+"); // Regex to match directory names that are numbers

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                if (pattern.matcher(fileName).matches()) {
                    int number = Integer.parseInt(fileName);
                    if (number > maxNumber) {
                        maxNumber = number;
                    }
                }
            }
        } catch (IOException ignored) {
            String errMessage = Messages.get("text.the-latest-snapper-snapshot-number", directoryPath);
            errorMessage(errMessage);
        }
        return maxNumber;
    }


    /**
     * Generate a machine-ID.
     */
    public static String generateRandomMachineID() {
        SecureRandom random = new SecureRandom();
        StringBuilder newMachineID = new StringBuilder(MACHINE_ID_LENGTH);
        for (int i = 0; i < MACHINE_ID_LENGTH; i++) {
            int nextInt = random.nextInt(16); // 0-15 for hexadecimal
            newMachineID.append(Integer.toHexString(nextInt));
        }
        try {
            Files.writeString(Paths.get(MACHINE_ID_FILE_PATH), newMachineID);
            infoMessage(Messages.get("info.new-machine-id-generated"), newMachineID.toString());
            infoMessage(Messages.get("info.this-machine-id-saved"), MACHINE_ID_FILE_PATH);
            changeFilePermissions(MACHINE_ID_FILE_PATH, "r--r--r--");
        } catch (IOException e) {
            exitWithError(Messages.get("error.new-machine-id-generator-failed"), e.getMessage());
        }
        return newMachineID.toString();
    }


    /**
     * Get the current ISO time now.
     */
    public static String getCurrentIsoFormattedTime() {
        return LocalDateTime.now().withNano(0).format(ISO_FORMATTER);
    }


    /**
     * Get the current UTC time now
     */
    public static String getCurrentUTCTime() {
        return LocalDateTime.now(ZoneOffset.UTC).format(TIME_FORMATTER);
    }


    /**
     * Returns a string representing the device and inode of the given path.
     * This is used to detect chroot environments by comparing the root (/)
     * of the current process with the root of PID 1 (/proc/1/root/.).
     *
     * @param path The filesystem path to check (e.g., "/", "/proc/1/root/.")
     * @return A string in the format "device:inode" if successful, or null if an error occurs.
     */
    public static String getDeviceAndInode(String path) {
        try {
            Path p = Paths.get(path);
            Object devAttr = Files.getAttribute(p, "unix:dev");
            Object inoAttr = Files.getAttribute(p, "unix:ino");
            if (devAttr instanceof Long && inoAttr instanceof Long) {
                return devAttr + ":" + inoAttr;
            }
            return null;
        } catch (IOException ignored) {
            return null;
        }
    }


    /**
     * Returns the type of the filesystem of the given mount point.
     *
     * @param mountPoint      the path to check
     * @param mustBeDirectory whether the path must be a directory
     * @return the filesystem type, or empty string if not determinable
     */
    public static String getFileSystemType(String mountPoint, boolean mustBeDirectory) {
        File path = new File(mountPoint);
        if (!path.exists()) {
            errorMessage(Messages.get("error.the-specified-path-does-not", path));
            return "";
        }
        if (mustBeDirectory && !path.isDirectory()) {
            errorMessage(Messages.get("error.the-specified-path-is-not", path));
            return "";
        }
        try {
            FileStore store = Files.getFileStore(path.toPath());
            return store.type();
        } catch (IOException e) {
            errorMessage(Messages.get("error.error-checking-filesystem-type-path", mountPoint, e.getMessage()));
            return "";
        }
    }


    /**
     * Extracts the leading spaces from a given string.
     *
     * @param input The input string from which leading spaces are to be extracted.
     * @return A string containing only the leading spaces.
     */
    public static String getLeadingSpaces(String input) {
        if (input == null) {
            return "";
        }
        // Initialize a StringBuilder to store the leading spaces
        StringBuilder leadingSpaces = new StringBuilder();
        int length = input.length();

        // Iterate through the string to collect leading spaces
        for (int i = 0; i < length; i++) {
            if (input.charAt(i) == ' ') {
                leadingSpaces.append(' ');
            } else {
                // Stop when a non-space character is found
                break;
            }
        }
        return leadingSpaces.toString();
    }


    public static String getLinuxDistroName() {
        Path osReleasePath = Paths.get("/etc/os-release");
        try {
            if (Files.exists(osReleasePath)) {
                List<String> lines = Files.readAllLines(osReleasePath);

                for (String line : lines) {
                    if (line.startsWith("PRETTY_NAME=")) {
                        return line.split("=")[1].replace("\"", "");
                    }
                }
                for (String line : lines) {
                    if (line.startsWith("NAME=")) {
                        return line.split("=")[1].replace("\"", "");
                    }
                }
            }

            Path debianVersionPath = Paths.get("/etc/debian_version");
            Path redhatVersionPath = Paths.get("/etc/redhat-release");

            // Fallbacks if /etc/os-release is not available
            if (Files.exists(Paths.get("/etc/lsb-release"))) {
                return getLinuxDistroNameFromLsbReleaseFile();

            } else if (Files.exists(debianVersionPath)) {
                return "Debian " + Files.readString(debianVersionPath).trim();

            } else if (Files.exists(redhatVersionPath)) {
                return Files.readString(redhatVersionPath).trim();

            }
        } catch (IOException e) {
            errorMessage(Messages.get("error.error-reading-linux-distro-information"), e.getMessage());
        }
        return null; // Return null if no information is found
    }

    private static String getLinuxDistroNameFromLsbReleaseFile() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("/etc/lsb-release"));
        for (String line : lines) {
            if (line.startsWith("DISTRIB_DESCRIPTION=")) {
                return line.split("=")[1].replace("\"", "").trim();
            }
        }
        return null;
    }


    public static String getSubvolFromProcMounts(String mount) {
        Path procPath = Path.of("/proc/mounts");
        String prefix = "subvol=";
        try (BufferedReader reader = Files.newBufferedReader(procPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains(" " + mount + " ")) {
                    continue;
                }
                String[] parts = line.split(" ");
                if (parts.length < 4 || !parts[1].equals(mount)) {
                    continue;
                }
                String options = parts[3];
                int idx = options.indexOf(prefix);
                if (idx != -1) {
                    int start = idx + prefix.length();
                    int end = options.indexOf(',', start);
                    return (end == -1) ? options.substring(start) : options.substring(start, end);
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }


    /**
     * Get the result from a command output using ProcessBuilder.
     *
     * @param commandLine  The command to be executed.
     * @param exitIfError  If true, the application will exit in case of error.
     * @param errorMessage Custom error message to display/log in case of error.
     * @return List of strings containing the output of the command.
     */
    public static Output getCommandOutput(String commandLine, boolean exitIfError, String errorMessage) {
        // Use ProcessBuilder to create and start the process
        ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", commandLine)
                .redirectErrorStream(true);// Redirect error stream to the standard output stream
        try {
            Process process = processBuilder.start();
            List<String> textOutput = new ArrayList<>();

            // Use try-with-resources to automatically close BufferedReader
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEach(textOutput::add);
            }

            // Wait for the process to finish and get the exit code
            int errorCode = process.waitFor();
            if (errorCode == 0) {
                return new Output(textOutput, null, errorCode, true);
            } else {
                if (errorMessage != null) {
                    if (!errorMessage.isEmpty()) {
                        errorMessage(errorMessage);
                    }
                } else {
                    errorMessage(Messages.get("error.command-failed"), commandLine);
                    if (!textOutput.isEmpty()) {
                        if (!textOutput.getFirst().trim().isEmpty()) {
                            errorMessage(Messages.get("error.output"), Arrays.toString(textOutput.toArray()));
                        }
                    }
                }
                if (exitIfError) {
                    exitWithError(Messages.get("error.exit-code"), String.valueOf(errorCode));
                }
                return new Output(null, textOutput, errorCode, false);
            }

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Get info about boot partition usage.
     */
    public static double getUsagePercent(String mountPoint) {
        Path path = Paths.get(mountPoint);
        try {
            FileStore store = Files.getFileStore(path);
            long total = store.getTotalSpace();// Total size in bytes
            long usable = store.getUsableSpace();
            long used = total - usable;
            double percent = (double) used / total * 100;
            return new BigDecimal(percent)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        } catch (IOException e) {
            errorMessage(Messages.get("error.error-when-checking-usage", mountPoint), e.getMessage());
            return -1;
        }
    }


    /**
     * Get the total space of the partition.
     */
    public static String getTotalSpace(String mountPoint) {
        Path path = Paths.get(mountPoint);
        try {
            FileStore store = Files.getFileStore(path);
            double totalGB = store.getTotalSpace() / (1024.0 * 1024 * 1024);
            return String.format(Locale.ROOT, "%.2f GiB", totalGB);
        } catch (IOException e) {
            errorMessage(Messages.get("error.error-when-checking-total-space", mountPoint, e.getMessage()));
            return "-- GiB";
        }
    }


    /**
     * Read UUID.
     */
    public static String getFsUuid() {
        Output output = getCommandOutput("findmnt --mountpoint / -no UUID", true, null);
        if (output.isSuccess()) {
            if (!output.text().isEmpty() && !output.text().getFirst().isEmpty()) {
                return output.text().getFirst();
            }
        }
        return null;
    }


    /**
     * Predict if a partition space is enough when adding new files?
     */
    public static PartitionUsage checkPartitionUsage(double limitUsagePercent, String mountPoint, Set<String> filePaths, Set<String> deletedFilePaths) {
        // Calculate the total size of the new files
        long newFilesSize = calculateFilesSize(filePaths, deletedFilePaths);
        // Size of new files + 1 MiB (snapshots.json + limine.conf + 2x.old + deviation)
        long deltaBytes = newFilesSize + 1048576;
        return calculatePartitionUsage(limitUsagePercent, mountPoint, deltaBytes);
    }

    public static PartitionUsage calculatePartitionUsage(double limitUsagePercent, String mountPoint, long deltaBytes) {
        try {
            // Determine the FileStore for the specified boot partition path
            FileStore store = Files.getFileStore(Paths.get(mountPoint));
            long totalBytes = store.getTotalSpace();
            long usedBytes = totalBytes - store.getUsableSpace();
            long futureBytes = usedBytes + deltaBytes;
            double usagePercent = (double) futureBytes / totalBytes * 100;
            boolean isLimitExceeded = usagePercent > limitUsagePercent;
            return new PartitionUsage(totalBytes, usedBytes, deltaBytes, isLimitExceeded);
        } catch (IOException e) {
            exitWithError(e.getMessage());
            return null;
        }
    }

    private static long calculateFilesSize(Set<String> filePaths, Set<String> deletedFilePaths) {
        long newFilesSize = 0;
        File file;
        for (String filePath : filePaths) {
            file = new File(filePath);
            if (file.exists() && file.isFile()) {
                newFilesSize += file.length();
            }
        }
        for (String filePath : deletedFilePaths) {
            file = new File(filePath);
            if (file.exists() && file.isFile()) {
                newFilesSize -= file.length();
            }
        }
        return newFilesSize;
    }


    public static String findLimineBootPartition(String limineConfigFile) {
        String[] mounts = {"/efi", "/boot", "/boot/efi", "/limine"};
        for (String mount : mounts) {
            Path path = Path.of(mount);
            if (!Files.isDirectory(path)) {
                continue;
            }
            boolean isVfat;
            try {
                isVfat = Files.getFileStore(path).type().equalsIgnoreCase("vfat");
            } catch (IOException e) {
                isVfat = false;
            }
            if (!isVfat) {
                continue;
            }
            if (Files.isRegularFile(path.resolve(limineConfigFile))) {
                return mount;
            }
        }
        return null;
    }


    /**
     * Logger
     */
    public static void loggerSend(String message, LogLevel logLevel) {
        try {
            // Bugfix: Do not use Runtime.getRuntime() that has a bug to escape some character like '-'
            // Use ProcessBuilder instead.
            Process process = new ProcessBuilder("bash", "-c", "logger -t limine-snapper-sync -p " + logLevel.level + " \"" + message + "\"").start();
            process.waitFor();
        } catch (IOException | InterruptedException ignored) {
        }
    }


    /**
     * Move a file.
     */
    public static void moveFile(String sourceFilePath, String targetFilePath, boolean force, boolean showOutput) {
        Path source = Path.of(sourceFilePath);
        Path target = Path.of(targetFilePath);
        try {
            if (force) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target);
            }
            if (showOutput) {
                infoMessage(Messages.get("info.moved"), sourceFilePath + " -> " + targetFilePath);
            }
        } catch (IOException e) {
            errorMessage(Messages.get("error.failed-move"), sourceFilePath + " -> " + targetFilePath + " (" + e.getMessage() + ")");
        }
    }

    public static void moveFile(String sourceFilePath, String targetFilePath) {
        moveFile(sourceFilePath, targetFilePath, true, true);
    }

    /**
     * Read a machine ID in /etc/machine-id.
     * Note: If it does not exist, a new machine-ID will be generated.
     */
    public static String readMachineID() {
        String machineID;
        try {
            machineID = new String(Files.readAllBytes(Paths.get("/etc/machine-id"))).trim();
        } catch (IOException e) {
            machineID = generateRandomMachineID();
        }
        return machineID;
    }


    /**
     * Remove the surrounding splashes from a path.
     */
    public static String removeSurroundingSlashes(String path) {
        if (path == null) {
            return null;
        }
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') {
            start++;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(start, end).trim();
    }

    /**
     * Remove the surrounding delimiter from a string.
     */
    public static String removeSurrounding(String str, String delimiter) {
        if (str != null && str.length() >= 2 && str.startsWith(delimiter) && str.endsWith(delimiter)) {
            return str.substring(delimiter.length(), str.length() - delimiter.length());
        }
        return str;
    }


    /**
     * Run a single command.
     */
    public static boolean runCommand(String command, boolean showOutput, boolean showCommand, boolean showError) {
        return runCommands(List.of(command), true, showOutput, showCommand, showError);
    }


    /**
     * Run multiple commands.
     */
    public static boolean runCommands(List<String> commands, boolean stopNextCommandOnError, boolean showOutput, boolean showCommand, boolean showError) {
        boolean isSuccess = true;
        try {
            for (String command : commands) {
                ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command).redirectErrorStream(true);
                Process process = processBuilder.start();
                CompletableFuture<Void> outputFuture = null;
                if (showOutput) {
                    // Use CompletableFuture to asynchronously read output and error streams
                    outputFuture = CompletableFuture.runAsync(() -> printStream(process.getInputStream()));
                }
                // Wait for the process to complete
                int errorCode = process.waitFor();
                if (showOutput) {
                    // Wait for the output/error streams to finish processing
                    outputFuture.join();
                }
                if (errorCode != 0) {
                    isSuccess = false;
                    if (showError) {
                        errorMessage(Messages.get("error.failed"), Messages.get("error.command-with-exit-code", command, errorCode));
                    }
                    if (stopNextCommandOnError) {
                        // Stop continues to the next command
                        break;
                    }
                } else {
                    if (showCommand) {
                        infoMessage(Messages.get("info.success"), command);
                    }
                }
            }
            return isSuccess;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void printStream(InputStream inputStream) {
        try {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                String text = new String(buffer, 0, len);
                System.out.print(text);
            }
        } catch (IOException ignored) {
            errorMessage(Messages.get("error.error-reading-process-stream"));
        }
    }


    public static void setSubvolumeProperty(String subvolumePath, boolean readOnly) {
        runCommand("btrfs property set '" + subvolumePath + "' ro " + readOnly, false, false, true);
    }


    /**
     * Find out what Snapper config name is for the root subvolume.
     */
    public static String snapperConfigName(boolean disableDbus) {
        String dbusOption = disableDbus ? " --no-dbus" : "";
        Output output = getCommandOutput("snapper --jsonout" + dbusOption + " list-configs", true, null);
        if (!output.isSuccess()) {
            return null;
        }
        String text = String.join("\n", output.text());
        try {
            // Ignore everything before the actual JSON output.
            int jsonStart = text.indexOf('{');
            if (jsonStart < 0) {
                Utility.exitWithError(Messages.get("text.mismatch-snapper-list-config-format-output"), text);
                return null;
            }
            JsonNode root = OBJECT_MAPPER.readTree(text.substring(jsonStart));
            JsonNode configs = root.get("configs");
            if (configs == null || !configs.isArray()) {
                return null;
            }
            for (JsonNode config : configs) {
                JsonNode subvolume = config.get("subvolume");
                if (subvolume != null && "/".equals(subvolume.asString())) {
                    return config.get("config").asString();
                }
            }
        } catch (Exception e) {
            Utility.exitWithError(Messages.get("text.mismatch-snapper-list-config-format-output"), text);
        }
        return null;
    }


    /**
     * User input in the terminal.
     */
    public static String userInput(String prompt) {
        System.out.println();
        while (true) {
            // Read the entire entered line
            String userInput = TerminalReader.readLine(prompt).trim();
            if (isValidInput(userInput)) {
                return userInput;
            } else {
                System.err.println(ConsoleColor.RED + Messages.get("text.invalid-input-please-try-again") + ConsoleColor.RESET);
            }
        }
    }

    private static boolean isValidInput(String input) {
        // Check for forbidden escape sequences and characters
        return !FORBIDDEN_PATTERN.matcher(input).find();
    }


    /**
     * Get user input.
     */
    public static String userSelect(String ask, String prompt, List<String> history) {
        // Ask the user to provide something
        System.out.println(ask);
        String input = TerminalReader.readLine(prompt, history);
        if (!input.trim().isEmpty() && "cancel".startsWith(input.toLowerCase())) {
            System.out.println(Messages.get("text.aborted"));
            System.exit(0);
        }
        // Read the entire entered line
        return input;
    }


    /**
     * Get a valid snapshot ID.
     *
     * @return -1 is invalid, otherwise valid.
     */
    public static int getSnapshotIdFromKernelCmdline() {
        String line;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(Config.PROC_CMDLINE));
            line = reader.readLine();
        } catch (IOException e) {
            line = null;
        }

        int snapshotID = -1;
        boolean isRootFlagsFound = false;
        if (line != null) {
            // Check if the keyword "rootflags" exists in the output.
            String[] kernelParameters = line.split(" ");
            for (String parameter : kernelParameters) {
                // Split the rootflags configuration part into individual flags
                String[] rootflags = readRootFlags(parameter);
                if (rootflags != null) {
                    for (String flag : rootflags) {
                        // Check if the flag contains the "snapshot" keyword
                        if (flag.startsWith("subvol=") && flag.endsWith("/snapshot")) {
                            // Extract the snapshot ID from the flag
                            snapshotID = extractSnapshotID(flag);
                        }
                    }
                    isRootFlagsFound = true;
                }
            }
        } else {
            errorMessage(Messages.get("error.no-kernel-cmdline-from", Config.PROC_CMDLINE));
            return -1;
        }

        if (!isRootFlagsFound) {
            // You are probably in a chroot environment that does not have 'rootflags'
            return -1;
        }
        return snapshotID;
    }

    private static int extractSnapshotID(String flag) {
        try {
            String[] dirs = flag.split("/");
            String snapshotIDStr = dirs[dirs.length - 2];
            if (snapshotIDStr != null) {
                return Integer.parseInt(snapshotIDStr);
            }
        } catch (NumberFormatException ignored) {
        }
        errorMessage(Messages.get("error.failed-extract-snapshot-id-from", flag));
        return -1;
    }

    public static String[] readRootFlags(String rootFlags) {
        if (rootFlags.startsWith("rootflags=")) {
            String rootflagsPart = rootFlags.substring("rootflags=".length());
            // Split the rootflags configuration part into individual flags
            return rootflagsPart.split(",");
        }
        return null;
    }

    public static boolean wildcardMatch(String pattern, String text) {
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else {
                // Escape all regex special characters
                if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                    regex.append("\\");
                }
                regex.append(c);
            }
        }
        return text.matches(regex.toString());
    }

    /**
     * Prompt user with a yes/no question.
     *
     * @param ask          The question to ask.
     * @param isYesDefault The default value if the user just presses Enter.
     * @return true for yes, false for no.
     */
    public static boolean yesOrNo(String ask, boolean isYesDefault) {
        String input = TerminalReader.readLine(ask).trim().toLowerCase(Locale.getDefault());
        if (input.isEmpty()) {
            return isYesDefault;
        }
        return switch (input) {
            case "y", "yes" -> true;
            case "n", "no" -> false;
            default -> isYesDefault;
        };
    }
}
