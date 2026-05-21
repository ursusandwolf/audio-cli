package com.stt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs whisper.cpp CLI for speech-to-text transcription.
 */
public class WhisperRunner {

    private final CommandExecutor commandExecutor;
    private final TranscriptionConfig config;

    public WhisperRunner(CommandExecutor commandExecutor, TranscriptionConfig config) {
        this.commandExecutor = commandExecutor;
        this.config = config;
    }

    public String transcribe(Path wavPath) throws Exception {
        if (!Files.exists(wavPath)) {
            throw new IllegalArgumentException("Audio file does not exist: " + wavPath);
        }

        Path outputPrefix = Files.createTempFile("stt_transcription_", "");
        Files.deleteIfExists(outputPrefix);
        Path outputTextFile = Path.of(outputPrefix.toString() + ".txt");

        try {
            List<String> args = new ArrayList<>();
            if (config.modelPath() != null && !config.modelPath().isEmpty()) {
                args.add("-m");
                args.add(config.modelPath());
            }
            args.add("-f");
            args.add(wavPath.toString());
            args.add("-l");
            args.add(config.language());
            args.add("-t");
            args.add(String.valueOf(config.threadLimit()));
            args.add("--print-progress");
            args.add("--no-prints");
            args.add("--output-txt");
            args.add("--output-file");
            args.add(outputPrefix.toString());

            commandExecutor.execute(config.whisperCliPath(), args.toArray(new String[0]));

            if (!Files.exists(outputTextFile)) {
                throw new IOException("whisper.cpp completed but did not produce transcription text output");
            }

            return normalizeTranscription(Files.readString(outputTextFile));
        } finally {
            Utils.deleteFileIfExists(outputTextFile);
            Utils.deleteFileIfExists(outputPrefix);
        }
    }

    static String normalizeTranscription(String output) {
        if (output == null || output.trim().isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        String[] lines = output.split("\\R");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(line);
        }

        return result.toString().trim();
    }
}
