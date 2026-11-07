package org.limine.snapper.processes;

import org.limine.snapper.formats.json1.*;
import org.limine.snapper.formats.snapper.SnapperProperty;
import org.limine.snapper.objects.Config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;

/** Writes snapshot entries in the Boot Loader Specification format. */
public final class BlsWriter {
    private final Config config;

    public BlsWriter(Config config) { this.config = config; }

    public void save(Manifest manifest) {
        Path entries = Path.of(config.espPath(), "loader", "entries");
        try {
            Files.createDirectories(entries);
            String prefix = safe(config.machineID()) + "-lss-snapshot-";
            try (var files = Files.list(entries)) {
                files.filter(p -> p.getFileName().toString().startsWith(prefix) && p.toString().endsWith(".conf"))
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException e) { throw new UncheckedIOException(e); } });
            }
            for (SnapshotEntry snapshot : manifest.getSnapshotEntries()) {
                String id = String.valueOf(snapshot.snapperEntry().snapshotID());
                for (int i = 0; i < snapshot.kernelEntries().size(); i++) {
                    KernelEntry kernel = snapshot.kernelEntries().get(i);
                    List<ImageDetail> images = kernel.allImages().stream().sorted(Comparator.comparing(ImageDetail::lineIndex, Comparator.nullsLast(Integer::compareTo))).toList();
                    String linux = null;
                    String efi = null;
                    List<String> initrds = new ArrayList<>();
                    for (ImageDetail image : images) {
                        String key = image.limineKey().toUpperCase(Locale.ROOT);
                        String path = "/" + config.machineID() + "/" + Config.HISTORY_FOLDER + "/" + image.fileHashName();
                        if (key.equals("LINUX") || key.equals("EFI") || key.equals("KERNEL_PATH") || key.equals("PATH")) {
                            if ("true".equals(image.properties().get("BLS_EFI"))) efi = path;
                            else linux = path;
                        } else if (key.equals("INITRD") || key.equals("MODULE_PATH") || key.equals("IMAGE_PATH")) initrds.add(path);
                    }
                    if (linux == null && efi == null) continue;
                    String options = kernel.cmdlineDetails().stream().findFirst().map(CmdlineDetail::snapshotCmdline).orElse("");
                    String description = Optional.ofNullable(snapshot.snapperEntry().properties().get(SnapperProperty.DESCRIPTION.replace)).orElse("");
                    String title = Config.TARGET_OS_NAME + " " + snapshotTitle(snapshot, description);
                    StringBuilder out = new StringBuilder("title ").append(title).append('\n')
                            .append("version ").append(safe(kernel.kernelVersion())).append('\n')
                            .append("machine-id ").append(config.machineID()).append('\n');
                    if (efi != null) out.append("efi ").append(efi).append('\n');
                    else out.append("linux ").append(linux).append('\n');
                    for (String initrd : initrds) out.append("initrd ").append(initrd).append('\n');
                    out.append("options ").append(options).append('\n');
                    Path target = entries.resolve(prefix + id + "-" + i + ".conf");
                    Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
                    Files.writeString(tmp, out.toString());
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
            }
        } catch (IOException | UncheckedIOException e) {
            Utility.exitWithError("Failed writing BLS snapshot entries: " + e.getMessage());
        }
    }

    private static String snapshotTitle(SnapshotEntry snapshot, String description) {
        String id = String.valueOf(snapshot.snapperEntry().snapshotID());
        String timestamp = Objects.requireNonNullElse(snapshot.snapperEntry().localTime(), "");
        return switch (Config.SNAPSHOT_FORMAT_CHOICE) {
            case "1" -> id + "│" + timestamp;
            case "2" -> id + " │ " + timestamp;
            case "3" -> timestamp + "│" + id;
            case "4" -> timestamp + " │ " + id;
            case "5" -> timestamp;
            case "6" -> id;
            case "7" -> id + "│" + description;
            case "8" -> timestamp + "│" + description;
            case "9" -> "│" + description;
            default -> "ID=" + id + " " + timestamp;
        };
    }

    private static String safe(String value) { return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_"); }
}
