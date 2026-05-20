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

    private static final String DEFAULT_WHISPER_CLI = "whisper-cli";
    private static final String DEFAULT_MODEL_NAME = "ggml-large-v3.bin";
    
    private final String whisperCliPath;
    private final String modelPath;
    private final String language;

    /**
     * Creates a WhisperRunner with default settings.
     */
    public WhisperRunner() {
        this(DEFAULT_WHISPER_CLI, null, "ru");
    }

    /**
     * Creates a WhisperRunner with custom paths.
     * 
     * @param whisperCliPath Path to whisper-cli executable (or just "whisper-cli" if in PATH)
     * @param modelPath Path to the whisper model file (e.g., ggml-large-v3.bin)
     * @param language Language code for transcription (e.g., "ru" for Russian)
     */
    public WhisperRunner(String whisperCliPath, String modelPath, String language) {
        this.whisperCliPath = whisperCliPath;
        this.modelPath = modelPath;
        this.language = language != null ? language : "ru";
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

        // Build whisper command
        List<String> command = buildWhisperCommand(wavPath);

        // Execute whisper
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Capture output
        String output = new String(process.getInputStream().readAllBytes());

        // Wait for completion
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException(
                "whisper.cpp failed with exit code " + exitCode + "\nOutput: " + output
            );
        }

        // Parse and return the transcription result
        return parseTranscription(output);
    }

    /**
     * Builds the whisper.cpp command for transcription.
     */
    private List<String> buildWhisperCommand(Path audioPath) {
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

        // Output format: text only (no timestamps by default for cleaner output)
        // whisper.cpp outputs to stdout by default when no output file specified

        return command;
    }

    /**
     * Parses the transcription output from whisper.cpp.
     * whisper.cpp outputs lines like: "[00:00:00]  Hello world"
     * We extract just the text part.
     */
    private String parseTranscription(String output) {
        if (output == null || output.trim().isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        String[] lines = output.split("\n");

        for (String line : lines) {
            line = line.trim();
            
            // Skip empty lines
            if (line.isEmpty()) {
                continue;
            }

            // whisper.cpp format: "[timestamp]  text" or just "text"
            // Try to extract text after timestamp pattern
            if (line.startsWith("[")) {
                int bracketEnd = line.indexOf("]");
                if (bracketEnd > 0 && bracketEnd < line.length() - 1) {
                    String text = line.substring(bracketEnd + 1).trim();
                    // Remove leading double space if present
                    if (text.startsWith(" ")) {
                        text = text.substring(1).trim();
                    }
                    if (!text.isEmpty()) {
                        if (result.length() > 0) {
                            result.append(" ");
                        }
                        result.append(text);
                    }
                    continue;
                }
            }

            // If no timestamp format, just add the line
            if (!line.startsWith("###") && !line.contains("model path") && 
                !line.contains("processing") && !line.contains("loading")) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(line);
            }
        }

        return result.toString().trim();
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
                "  wget -P models https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3.bin"
            );
        }

        // Validate model exists if specified
        if (modelPath != null && !modelPath.isEmpty()) {
            Path model = java.nio.file.Paths.get(modelPath);
            if (!Files.exists(model)) {
                throw new IllegalStateException(
                    "Whisper model not found at: " + modelPath + "\n" +
                    "Download the model:\n" +
                    "  wget -P models https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3.bin"
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
