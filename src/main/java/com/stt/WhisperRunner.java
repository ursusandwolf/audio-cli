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

            commandExecutor.execute(
                config.whisperCliPath(),
                config.timeoutSeconds(),
                config.retryCount(),
                args.toArray(new String[0])
            );

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

    public static int resolveDefaultThreadLimit() {
        return 1;
    }

    public static int validateThreadLimit(int threadLimit) {
        if (threadLimit < 1) {
            throw new IllegalArgumentException("--threads must be 1 or greater");
        }
        return threadLimit;
    }

    static Integer extractProgressPercent(String line) {
        if (line == null || !line.contains("progress =")) {
            return null;
        }
        try {
            String part = line.substring(line.indexOf("progress =") + 10).trim();
            int spaceIdx = part.indexOf('%');
            if (spaceIdx > 0) {
                return Integer.parseInt(part.substring(0, spaceIdx).trim());
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    static String formatProgressBar(int percent) {
        int width = 20;
        int completed = (int) (width * (percent / 100.0));
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            if (i < completed) sb.append("#");
            else sb.append("-");
        }
        sb.append("]  ").append(percent).append("%");
        return sb.toString();
    }

    static void terminateProcess(Process process, long pid) throws IOException {
        process.destroy();
        try {
            if (!process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                if (!process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IOException("Failed to terminate whisper.cpp process PID " + pid);
                }
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    static Thread createShutdownHook(Process process, long pid) {
        return new Thread(() -> {
            try {
                if (process.isAlive()) {
                    terminateProcess(process, pid);
                }
            } catch (IOException e) {
                System.err.println("Warning: " + e.getMessage());
            }
        });
    }
}
