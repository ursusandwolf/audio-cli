package com.stt;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Main entry point for the Audio Transcriber CLI.
 * 
 * Usage:
 *   java -jar stt.jar <audio-file> [--model <path>] [--language <code>]
 * 
 * Examples:
 *   java -jar stt.jar input.mp3
 *   java -jar stt.jar input.webm --model models/ggml-large-v3.bin
 *   java -jar stt.jar input.wav --language en
 */
public class Main {

    private static final String DEFAULT_MODEL_PATH = "models/ggml-large-v3.bin";
    private static final String DEFAULT_LANGUAGE = "ru";

    public static void main(String[] args) {
        // Parse command line arguments
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String inputFile = null;
        String modelPath = DEFAULT_MODEL_PATH;
        String language = DEFAULT_LANGUAGE;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            if (arg.equals("--help") || arg.equals("-h")) {
                printUsage();
                System.exit(0);
            } else if (arg.equals("--model") || arg.equals("-m")) {
                if (i + 1 < args.length) {
                    modelPath = args[++i];
                } else {
                    System.err.println("Error: --model requires a path argument");
                    System.exit(1);
                }
            } else if (arg.equals("--language") || arg.equals("-l")) {
                if (i + 1 < args.length) {
                    language = args[++i];
                } else {
                    System.err.println("Error: --language requires a language code argument");
                    System.exit(1);
                }
            } else if (!arg.startsWith("-")) {
                inputFile = arg;
            } else {
                System.err.println("Unknown option: " + arg);
                printUsage();
                System.exit(1);
            }
        }

        // Validate input file
        if (inputFile == null) {
            System.err.println("Error: No input file specified");
            printUsage();
            System.exit(1);
        }

        try {
            // Run transcription
            transcribe(inputFile, modelPath, language);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (IllegalStateException e) {
            System.err.println("Configuration Error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * Performs the full transcription pipeline.
     */
    private static void transcribe(String inputFile, String modelPath, String language) 
            throws Exception {
        
        System.err.println("Audio Transcriber CLI");
        System.err.println("=====================");
        System.err.println("Input file: " + inputFile);
        System.err.println("Model: " + modelPath);
        System.err.println("Language: " + language);
        System.err.println();

        // Validate input file exists
        Utils.validateInputFile(inputFile);
        Path inputPath = Paths.get(inputFile);

        // Step 1: Convert audio to WAV format using FFmpeg
        System.err.println("Step 1/2: Converting audio to WAV (16kHz, mono, PCM)...");
        AudioConverter converter = new AudioConverter();
        converter.validateFfmpegInstalled();
        
        Path wavPath = null;
        try {
            wavPath = converter.convertToWav(inputPath);
            System.err.println("  ✓ Audio converted successfully");
        } catch (Exception e) {
            System.err.println("  ✗ Audio conversion failed");
            throw e;
        }

        // Step 2: Transcribe using whisper.cpp
        System.err.println("Step 2/2: Transcribing audio with whisper.cpp...");
        WhisperRunner runner = new WhisperRunner("whisper-cli", modelPath, language);
        runner.validateWhisperInstalled();
        
        String transcription;
        try {
            transcription = runner.transcribe(wavPath);
            System.err.println("  ✓ Transcription completed");
        } catch (Exception e) {
            System.err.println("  ✗ Transcription failed");
            throw e;
        } finally {
            // Cleanup temporary WAV file
            Utils.deleteFileIfExists(wavPath);
            System.err.println("  ✓ Temporary files cleaned up");
        }

        // Output result
        System.err.println();
        System.err.println("=====================");
        System.err.println("TRANSCRIPTION RESULT:");
        System.err.println("=====================");
        
        if (transcription != null && !transcription.isEmpty()) {
            System.out.println(transcription);
        } else {
            System.out.println("[No speech detected]");
        }
    }

    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Audio Transcriber CLI - Local speech-to-text tool");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar stt.jar <audio-file> [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  <audio-file>           Path to audio file (mp3, wav, webm, opus, m4a, etc.)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -m, --model <path>     Path to whisper model (default: models/ggml-large-v3.bin)");
        System.out.println("  -l, --language <code>  Language code (default: ru)");
        System.out.println("  -h, --help             Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar stt.jar input.mp3");
        System.out.println("  java -jar stt.jar input.webm --model models/ggml-large-v3.bin");
        System.out.println("  java -jar stt.jar input.wav --language en");
        System.out.println();
        System.out.println("Requirements:");
        System.out.println("  - FFmpeg installed and in PATH");
        System.out.println("  - whisper.cpp CLI installed and in PATH");
        System.out.println("  - Whisper model file downloaded");
    }
}
