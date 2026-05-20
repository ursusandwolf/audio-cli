package com.stt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Utility class for common operations.
 */
public class Utils {

    private static final Set<String> SUPPORTED_AUDIO_EXTENSIONS = Set.of(
        "mp3", "wav", "webm", "opus", "m4a", "aac", "flac", "ogg", "avi", "mkv", "mp4", "mov"
    );

    /**
     * Checks if a command/executable exists in the system PATH.
     */
    public static boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb.command("cmd", "/c", "where", command);
            } else {
                pb.command("which", command);
            }
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Creates a temporary file with the given extension.
     */
    public static Path createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(prefix, suffix);
    }

    /**
     * Deletes a file if it exists.
     */
    public static void deleteFileIfExists(Path path) {
        if (path != null && Files.exists(path)) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                System.err.println("Warning: Could not delete temp file: " + path);
            }
        }
    }

    /**
     * Validates that a file exists and is readable.
     */
    public static void validateInputFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("Input file path is empty");
        }

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Input file not found: " + filePath);
        }

        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("Input file is not readable: " + filePath);
        }

        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Input path is not a file: " + filePath);
        }
    }

    /**
     * Validates that a path exists and is readable.
     */
    public static void validateInputPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("Input path is empty");
        }

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Input path not found: " + filePath);
        }

        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("Input path is not readable: " + filePath);
        }
    }

    /**
     * Gets the file extension from a path.
     */
    public static String getFileExtension(String filePath) {
        if (filePath == null) {
            return "";
        }
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filePath.length() - 1) {
            return filePath.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Checks whether the file extension looks like a supported audio container.
     */
    public static boolean isSupportedAudioFile(Path path) {
        return Files.isRegularFile(path) && SUPPORTED_AUDIO_EXTENSIONS.contains(getFileExtension(path.toString()));
    }
}
