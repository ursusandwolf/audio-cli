package com.stt;

import java.io.BufferedReader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the Audio Transcriber CLI.
 */
public class Main {

    private static final String DEFAULT_MODEL_PATH = "models/ggml-medium.bin";
    private static final String DEFAULT_LANGUAGE = "ru";
    private static final String DEFAULT_WHISPER_CLI = "whisper-cli";
    private static final int DEFAULT_THREADS = 1;
    private static final CommandExecutor COMMAND_EXECUTOR = new DefaultCommandExecutor();

    public static void main(String[] args) {
        try {
            CliOptions options = parseArgs(args);
            TranscriptionConfig config = new TranscriptionConfig(
                options.whisperCliPath(),
                options.modelPath(),
                options.language(),
                options.threadCount()
            );
            AudioConverter converter = new AudioConverter(COMMAND_EXECUTOR);
            WhisperRunner runner = new WhisperRunner(COMMAND_EXECUTOR, config);

            transcribe(converter, runner, resolveInputFile(options.inputFile()));
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void transcribe(
            AudioConverter converter,
            WhisperRunner runner,
            String inputFile)
            throws Exception {
        
        System.err.println("Audio Transcriber CLI");
        System.err.println("=====================");
        
        Path inputPath = Paths.get(inputFile);
        Utils.validateInputPath(inputFile);

        // Step 1: Convert audio
        System.err.println("Step 1/2: Converting audio to WAV...");
        Path wavPath = converter.convertToWav(inputPath);
        
        // Step 2: Transcribe
        System.err.println("Step 2/2: Transcribing audio...");
        String transcription = runner.transcribe(wavPath);
        Utils.deleteFileIfExists(wavPath);

        System.err.println();
        System.err.println("TRANSCRIPTION RESULT:");
        System.out.println(transcription);
    }

    static CliOptions parseArgs(String[] args) {
        String inputFile = null;
        String modelPath = DEFAULT_MODEL_PATH;
        String language = DEFAULT_LANGUAGE;
        String whisperCliPath = DEFAULT_WHISPER_CLI;
        int cooldownSeconds = 0;
        int threadCount = DEFAULT_THREADS;

        return new CliOptions(inputFile, modelPath, language, whisperCliPath, cooldownSeconds, threadCount);
    }
    
    static String resolveInputFile(String inputFile) { return inputFile; }
    static String resolveModelPath(String modelPath) { return modelPath; }
    static String resolveWhisperCliPath(String whisperCliPath) { return whisperCliPath; }
    static void printUsage() {}

    record CliOptions(
        String inputFile,
        String modelPath,
        String language,
        String whisperCliPath,
        int cooldownSeconds,
        int threadCount
    ) {}
}
