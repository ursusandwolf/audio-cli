package com.stt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts audio files to WAV format (16kHz, mono, PCM 16-bit LE) using FFmpeg.
 */
public class AudioConverter {

    private static final String FFMPEG_COMMAND = "ffmpeg";
    
    // Target format: 16kHz sample rate, mono channel, PCM signed 16-bit little-endian
    private static final int TARGET_SAMPLE_RATE = 16000;
    private static final int TARGET_CHANNELS = 1;

    /**
     * Converts an audio file to WAV format suitable for whisper.cpp.
     * 
     * @param inputPath Path to the input audio file (any format)
     * @return Path to the converted WAV file
     * @throws IOException If conversion fails
     * @throws IllegalStateException If FFmpeg is not available
     */
    public Path convertToWav(Path inputPath) throws IOException, InterruptedException {
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Input file does not exist: " + inputPath);
        }

        // Check if FFmpeg is available
        if (!Utils.isCommandAvailable(FFMPEG_COMMAND)) {
            throw new IllegalStateException(
                "FFmpeg is not installed or not in PATH. " +
                "Please install FFmpeg: https://ffmpeg.org/download.html"
            );
        }

        // Create temporary WAV file
        Path tempWav = Utils.createTempFile("stt_", ".wav");

        try {
            // Build FFmpeg command
            List<String> command = buildFfmpegCommand(inputPath, tempWav);

            // Execute FFmpeg
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output for error messages
            String output = new String(process.getInputStream().readAllBytes());

            // Wait for completion
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                Utils.deleteFileIfExists(tempWav);
                throw new IOException(
                    "FFmpeg failed with exit code " + exitCode + ": " + output
                );
            }

            // Verify output file was created
            if (!Files.exists(tempWav) || Files.size(tempWav) == 0) {
                Utils.deleteFileIfExists(tempWav);
                throw new IOException("FFmpeg did not create output file or file is empty");
            }

            return tempWav;

        } catch (Exception e) {
            Utils.deleteFileIfExists(tempWav);
            throw e;
        }
    }

    /**
     * Builds the FFmpeg command for audio conversion.
     */
    private List<String> buildFfmpegCommand(Path input, Path output) {
        List<String> command = new ArrayList<>();
        
        command.add(FFMPEG_COMMAND);
        command.add("-i");
        command.add(input.toString());
        
        // Audio settings for whisper.cpp compatibility
        command.add("-ar");
        command.add(String.valueOf(TARGET_SAMPLE_RATE));
        
        command.add("-ac");
        command.add(String.valueOf(TARGET_CHANNELS));
        
        command.add("-c:a");
        command.add("pcm_s16le");
        
        // Overwrite output without asking
        command.add("-y");
        
        command.add(output.toString());
        
        return command;
    }

    /**
     * Validates that FFmpeg is available on the system.
     */
    public void validateFfmpegInstalled() {
        if (!Utils.isCommandAvailable(FFMPEG_COMMAND)) {
            throw new IllegalStateException(
                "FFmpeg is not installed or not in PATH.\n" +
                "Installation instructions:\n" +
                "  Ubuntu/Debian: sudo apt-get install ffmpeg\n" +
                "  macOS: brew install ffmpeg\n" +
                "  Windows: Download from https://ffmpeg.org/download.html"
            );
        }
    }
}
