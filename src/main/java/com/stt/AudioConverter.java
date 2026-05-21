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

    private final CommandExecutor commandExecutor;
    private static final String FFMPEG_COMMAND = "ffmpeg";
    
    // Target format: 16kHz sample rate, mono channel, PCM signed 16-bit little-endian
    private static final int TARGET_SAMPLE_RATE = 16000;
    private static final int TARGET_CHANNELS = 1;

    public AudioConverter(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    /**
     * Converts an audio file to WAV format suitable for whisper.cpp.
     * 
     * @param inputPath Path to the input audio file (any format)
     * @return Path to the converted WAV file
     * @throws IOException If conversion fails
     */
    public Path convertToWav(Path inputPath) throws Exception {
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Input file does not exist: " + inputPath);
        }

        // Create temporary WAV file
        Path tempWav = Utils.createTempFile("stt_", ".wav");

        try {
            // Build FFmpeg command arguments
            List<String> args = new ArrayList<>();
            args.add("-i");
            args.add(inputPath.toString());
            args.add("-ar");
            args.add(String.valueOf(TARGET_SAMPLE_RATE));
            args.add("-ac");
            args.add(String.valueOf(TARGET_CHANNELS));
            args.add("-c:a");
            args.add("pcm_s16le");
            args.add("-y");
            args.add(tempWav.toString());

            // Execute FFmpeg
            String output = commandExecutor.executeAndCapture(FFMPEG_COMMAND, args.toArray(new String[0]));

            // Verify output file was created
            if (!Files.exists(tempWav) || Files.size(tempWav) == 0) {
                Utils.deleteFileIfExists(tempWav);
                throw new IOException("FFmpeg did not create output file or file is empty: " + output);
            }

            return tempWav;

        } catch (Exception e) {
            Utils.deleteFileIfExists(tempWav);
            throw e;
        }
    }
}
