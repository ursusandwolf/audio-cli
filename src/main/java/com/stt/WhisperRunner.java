package com.stt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs whisper.cpp CLI for speech-to-text transcription.
 */
public class WhisperRunner {

    private static final String DEFAULT_WHISPER_CLI = "whisper-cli";
    private static final String DEFAULT_MODEL_PATH = "models/ggml-small.bin";
    private static final int PROGRESS_BAR_WIDTH = 20;
    private static final int DEFAULT_THREAD_LIMIT = 1;
    private static final long PROCESS_TIMEOUT_MINUTES = 15;
    
    private final String whisperCliPath;
    private final String modelPath;
    private final String language;
    private final int threadLimit;

    /**
     * Creates a WhisperRunner with default settings.
     */
    public WhisperRunner() {
        this(DEFAULT_WHISPER_CLI, DEFAULT_MODEL_PATH, "ru", resolveDefaultThreadLimit());
    }

    /**
     * Creates a WhisperRunner with custom paths.
     * 
     * @param whisperCliPath Path to whisper-cli executable (or just "whisper-cli" if in PATH)
     * @param modelPath Path to the whisper model file (e.g., ggml-large-v3.bin)
     * @param language Language code for transcription (e.g., "ru" for Russian)
     */
    public WhisperRunner(String whisperCliPath, String modelPath, String language) {
        this(whisperCliPath, modelPath, language, resolveDefaultThreadLimit());
    }

    public WhisperRunner(String whisperCliPath, String modelPath, String language, int threadLimit) {
        this.whisperCliPath = whisperCliPath;
        this.modelPath = modelPath;
        this.language = language != null ? language : "ru";
        this.threadLimit = validateThreadLimit(threadLimit);
    }

    /**
     * Transcribes an audio file to text using whisper.cpp.
     * 
     * @param wavPath Path to the WAV audio file (16kHz, mono, PCM 16-bit LE)
     * @return Transcribed text
     * @throws IOException If transcription fails
     * @throws InterruptedException If process is interrupted
     */
    public String transcribe(Path wavPath) throws IOException, InterruptedException {
        if (!Files.exists(wavPath)) {
            throw new IllegalArgumentException("Audio file does not exist: " + wavPath);
        }

        // Validate whisper.cpp is available
        validateWhisperInstalled();

        Path outputPrefix = Files.createTempFile("stt_transcription_", "");
        Files.deleteIfExists(outputPrefix);
        Path outputTextFile = Path.of(outputPrefix.toString() + ".txt");
        ExecutorService outputReaderExecutor = Executors.newSingleThreadExecutor();

        try {
            List<String> command = buildWhisperCommand(wavPath, outputPrefix);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            long pid = process.pid();
            System.err.println("  whisper.cpp PID: " + pid);
            Thread shutdownHook = createShutdownHook(process, pid);
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            Future<String> outputFuture = outputReaderExecutor.submit(() -> captureProcessOutput(process));

            if (!process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                terminateProcess(process, pid);
                throw new IOException(
                    "whisper.cpp exceeded timeout of " + PROCESS_TIMEOUT_MINUTES + " minutes and was terminated"
                );
            }

            String output = waitForOutput(outputFuture);
            int exitCode = process.exitValue();
            removeShutdownHook(shutdownHook);

            if (exitCode != 0) {
                throw new IOException(
                    "whisper.cpp failed with exit code " + exitCode + "\nOutput: " + output
                );
            }

            if (!Files.exists(outputTextFile)) {
                throw new IOException("whisper.cpp completed but did not produce transcription text output");
            }

            return normalizeTranscription(Files.readString(outputTextFile));
        } finally {
            outputReaderExecutor.shutdownNow();
            Utils.deleteFileIfExists(outputTextFile);
            Utils.deleteFileIfExists(outputPrefix);
        }
    }

    /**
     * Builds the whisper.cpp command for transcription.
     */
    private List<String> buildWhisperCommand(Path audioPath, Path outputPrefix) {
        List<String> command = new ArrayList<>();

        command.add(whisperCliPath);

        // Model path
        if (modelPath != null && !modelPath.isEmpty()) {
            command.add("-m");
            command.add(modelPath);
        }

        // Input file
        command.add("-f");
        command.add(audioPath.toString());

        // Language
        command.add("-l");
        command.add(language);

        command.add("-t");
        command.add(String.valueOf(threadLimit));

        command.add("--print-progress");
        command.add("--no-prints");
        command.add("--output-txt");
        command.add("--output-file");
        command.add(outputPrefix.toString());

        return command;
    }

    /**
     * Normalizes the plain-text transcription file into a single line.
     */
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

    static Integer extractProgressPercent(String line) {
        if (line == null) {
            return null;
        }

        int marker = line.indexOf("progress =");
        int percentIndex = line.indexOf('%');
        if (marker < 0 || percentIndex < 0 || percentIndex <= marker) {
            return null;
        }

        String value = line.substring(marker + "progress =".length(), percentIndex).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String formatProgressBar(int percent) {
        int bounded = Math.max(0, Math.min(100, percent));
        int filled = (bounded * PROGRESS_BAR_WIDTH) / 100;
        StringBuilder bar = new StringBuilder(PROGRESS_BAR_WIDTH + 10);
        bar.append('[');
        for (int i = 0; i < PROGRESS_BAR_WIDTH; i++) {
            bar.append(i < filled ? '#' : '-');
        }
        bar.append("] ");
        if (bounded < 10) {
            bar.append(' ');
        }
        if (bounded < 100) {
            bar.append(' ');
        }
        bar.append(bounded).append('%');
        return bar.toString();
    }

    private String captureProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        Integer lastProgress = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());

                Integer progress = extractProgressPercent(line);
                if (progress != null) {
                    lastProgress = progress;
                    System.err.print("\r  " + formatProgressBar(progress));
                    continue;
                }

                if (!line.isBlank()) {
                    System.err.println("  " + line);
                }
            }
        }

        if (lastProgress != null) {
            System.err.println();
        }

        return output.toString();
    }

    static int resolveDefaultThreadLimit() {
        int available = Runtime.getRuntime().availableProcessors();
        int reservedForSystem = available > 2 ? 1 : 0;
        int limit = available - reservedForSystem;
        limit = Math.max(1, limit);
        return Math.min(DEFAULT_THREAD_LIMIT, limit);
    }

    static int validateThreadLimit(int threadLimit) {
        if (threadLimit < 1) {
            throw new IllegalArgumentException("--threads must be 1 or greater");
        }
        return threadLimit;
    }

    static Thread createShutdownHook(Process process, long pid) {
        return new Thread(() -> {
            if (process.isAlive()) {
                try {
                    terminateProcess(process, pid);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignored) {
                    // JVM is shutting down; best effort only.
                }
            }
        }, "whisper-cli-cleanup-" + pid);
    }

    static void removeShutdownHook(Thread shutdownHook) {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down.
        }
    }

    static void terminateProcess(Process process, long pid) throws IOException, InterruptedException {
        process.destroy();
        if (process.waitFor(5, TimeUnit.SECONDS)) {
            return;
        }

        process.destroyForcibly();
        if (process.waitFor(5, TimeUnit.SECONDS)) {
            return;
        }

        if (process.isAlive()) {
            throw new IOException("Failed to terminate whisper.cpp process PID " + pid);
        }
    }

    private String waitForOutput(Future<String> outputFuture) throws IOException, InterruptedException {
        try {
            return outputFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IOException("Timed out while draining whisper.cpp output", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to read whisper.cpp output", cause);
        }
    }

    /**
     * Validates that whisper.cpp is available on the system.
     */
    public void validateWhisperInstalled() {
        if (!Utils.isCommandAvailable(whisperCliPath)) {
            throw new IllegalStateException(
                "whisper.cpp CLI ('" + whisperCliPath + "') is not installed or not in PATH.\n" +
                "Installation instructions:\n" +
                "  1. Clone: git clone https://github.com/ggerganov/whisper.cpp.git\n" +
                "  2. Build: cd whisper.cpp && make\n" +
                "  3. Add to PATH or specify full path to whisper-cli\n" +
                "\n" +
                "Model download:\n" +
                "  wget -P models https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
            );
        }

        // Validate model exists if specified
        if (modelPath != null && !modelPath.isEmpty()) {
            Path model = java.nio.file.Paths.get(modelPath);
            if (!Files.exists(model)) {
                throw new IllegalStateException(
                    "Whisper model not found at: " + modelPath + "\n" +
                    "Download the model:\n" +
                    "  wget -P models https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
                );
            }
        }
    }

    /**
     * Checks if a specific model file exists.
     */
    public boolean modelExists() {
        if (modelPath == null || modelPath.isEmpty()) {
            return false;
        }
        return Files.exists(java.nio.file.Paths.get(modelPath));
    }
}
