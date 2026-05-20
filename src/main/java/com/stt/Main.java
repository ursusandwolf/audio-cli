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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Main entry point for the Audio Transcriber CLI.
 * 
 * Usage:
 *   java -jar stt.jar [<audio-file>] [--model <path>] [--language <code>] [--whisper-cli <path>] [--cooldown <seconds>] [--threads <count>]
 * 
 * Examples:
 *   java -jar stt.jar
 *   java -jar stt.jar input.webm --model models/ggml-small.bin
 *   java -jar stt.jar input.wav --language en
 */
public class Main {

    private static final String DEFAULT_MODEL_PATH = "models/ggml-medium.bin";
    private static final String DEFAULT_LANGUAGE = "ru";
    private static final String DEFAULT_WHISPER_CLI = "whisper-cli";
    private static final int DEFAULT_THREADS = WhisperRunner.resolveDefaultThreadLimit();
    private static final int MAX_HISTORY_SIZE = 3;
    private static final String[] MODEL_OPTIONS = {
        "models/ggml-tiny.bin",
        "models/ggml-small.bin",
        "models/ggml-medium.bin"
    };
    private static final Path WHISPER_CLI_FILE = Paths.get(
        System.getProperty("user.home"),
        ".audio-transcriber-whisper-cli"
    );
    private static final Path MODEL_FILE = Paths.get(
        System.getProperty("user.home"),
        ".audio-transcriber-model"
    );

    public static void main(String[] args) {
        try {
            CliOptions options = parseArgs(args);
            transcribe(
                resolveInputFile(options.inputFile()),
                resolveModelPath(options.modelPath()),
                options.language(),
                resolveWhisperCliPath(options.whisperCliPath()),
                options.cooldownSeconds(),
                options.threadCount()
            );
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
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
     * Parses CLI arguments into typed options.
     */
    static CliOptions parseArgs(String[] args) {
        String inputFile = null;
        String modelPath = DEFAULT_MODEL_PATH;
        String language = DEFAULT_LANGUAGE;
        String whisperCliPath = DEFAULT_WHISPER_CLI;
        int cooldownSeconds = 0;
        int threadCount = DEFAULT_THREADS;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals("--help") || arg.equals("-h")) {
                printUsage();
                System.exit(0);
            } else if (arg.equals("--model") || arg.equals("-m")) {
                if (i + 1 < args.length) {
                    modelPath = args[++i];
                } else {
                    throw new IllegalArgumentException("--model requires a path argument");
                }
            } else if (arg.equals("--language") || arg.equals("-l")) {
                if (i + 1 < args.length) {
                    language = args[++i];
                } else {
                    throw new IllegalArgumentException("--language requires a language code argument");
                }
            } else if (arg.equals("--whisper-cli") || arg.equals("-w")) {
                if (i + 1 < args.length) {
                    whisperCliPath = args[++i];
                } else {
                    throw new IllegalArgumentException("--whisper-cli requires a path argument");
                }
            } else if (arg.equals("--cooldown") || arg.equals("-c")) {
                if (i + 1 < args.length) {
                    cooldownSeconds = parseCooldownSeconds(args[++i]);
                } else {
                    throw new IllegalArgumentException("--cooldown requires a number of seconds");
                }
            } else if (arg.equals("--threads") || arg.equals("-t")) {
                if (i + 1 < args.length) {
                    threadCount = parseThreadCount(args[++i]);
                } else {
                    throw new IllegalArgumentException("--threads requires a number");
                }
            } else if (!arg.startsWith("-")) {
                if (inputFile != null) {
                    throw new IllegalArgumentException(
                        "Only one input file is supported. Received: '" + inputFile + "' and '" + arg + "'"
                    );
                }
                inputFile = normalizePath(arg);
            } else {
                throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }

        return new CliOptions(inputFile, modelPath, language, whisperCliPath, cooldownSeconds, threadCount);
    }

    static int parseCooldownSeconds(String value) {
        try {
            int seconds = Integer.parseInt(value);
            if (seconds < 0) {
                throw new IllegalArgumentException("--cooldown must be 0 or greater");
            }
            return seconds;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--cooldown must be an integer number of seconds");
        }
    }

    static int parseThreadCount(String value) {
        try {
            return WhisperRunner.validateThreadLimit(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--threads must be an integer number");
        }
    }

    static String resolveInputFile(String inputFile) throws IOException {
        if (inputFile != null && !inputFile.isBlank()) {
            String normalized = normalizePath(inputFile);
            saveInputFileToHistory(normalized);
            return normalized;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        List<String> history = loadInputFileHistory();
        printInputPrompt(history);
        String enteredPath = reader.readLine();

        if (enteredPath == null || enteredPath.isBlank()) {
            throw new IllegalArgumentException("No input file specified");
        }

        String selectedPath = selectInputFile(normalizePath(enteredPath), history);
        saveInputFileToHistory(selectedPath);
        return selectedPath;
    }

    static String resolveWhisperCliPath(String whisperCliPath) throws IOException {
        String normalized = normalizePath(whisperCliPath);
        if (normalized != null
                && !normalized.isBlank()
                && !DEFAULT_WHISPER_CLI.equals(normalized)
                && Utils.isCommandAvailable(normalized)) {
            saveWhisperCliPath(normalized);
            return normalized;
        }

        String savedPath = loadWhisperCliPath();
        if (savedPath != null && Utils.isCommandAvailable(savedPath)) {
            return savedPath;
        }

        if (normalized != null && !normalized.isBlank() && Utils.isCommandAvailable(normalized)) {
            saveWhisperCliPath(normalized);
            return normalized;
        }

        System.out.print("Enter path to whisper-cli: ");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String enteredPath = reader.readLine();
        String resolvedPath = normalizePath(enteredPath);

        if (resolvedPath == null || resolvedPath.isBlank()) {
            return DEFAULT_WHISPER_CLI;
        }

        saveWhisperCliPath(resolvedPath);
        return resolvedPath;
    }

    static String resolveModelPath(String modelPath) throws IOException {
        String normalized = normalizePath(modelPath);
        if (normalized != null && !normalized.isBlank() && !DEFAULT_MODEL_PATH.equals(normalized)) {
            saveModelPath(normalized);
            return normalized;
        }

        if (normalized != null && !normalized.isBlank() && Files.exists(Paths.get(normalized))) {
            saveModelPath(normalized);
            return normalized;
        }

        String savedPath = loadModelPath();
        if (savedPath != null && Files.exists(Paths.get(savedPath))) {
            return savedPath;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        printModelPrompt();
        String enteredValue = normalizePath(reader.readLine());
        String selectedPath = selectModelPath(enteredValue);
        saveModelPath(selectedPath);
        return selectedPath;
    }

    static String normalizePath(String path) {
        if (path == null) {
            return null;
        }

        String normalized = path.trim();
        if (normalized.length() >= 2) {
            char first = normalized.charAt(0);
            char last = normalized.charAt(normalized.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                normalized = normalized.substring(1, normalized.length() - 1).trim();
            }
        }

        return normalized;
    }

    static List<String> loadInputFileHistory() throws IOException {
        Path historyFile = getHistoryFile();
        if (!Files.exists(historyFile)) {
            return List.of();
        }

        List<String> lines = Files.readAllLines(historyFile);
        List<String> history = new ArrayList<>();
        for (String line : lines) {
            String normalized = normalizePath(line);
            if (!normalized.isBlank() && !history.contains(normalized)) {
                history.add(normalized);
            }
            if (history.size() == MAX_HISTORY_SIZE) {
                break;
            }
        }
        return history;
    }

    static void saveInputFileToHistory(String inputFile) throws IOException {
        String normalized = normalizePath(inputFile);
        if (normalized == null || normalized.isBlank()) {
            return;
        }

        List<String> history = new ArrayList<>();
        history.add(normalized);
        for (String existing : loadInputFileHistory()) {
            if (!existing.equals(normalized) && history.size() < MAX_HISTORY_SIZE) {
                history.add(existing);
            }
        }

        Files.write(getHistoryFile(), history);
    }

    static String selectInputFile(String enteredValue, List<String> history) {
        if (enteredValue.matches("[1-3]")) {
            int index = Integer.parseInt(enteredValue) - 1;
            if (index < history.size()) {
                return history.get(index);
            }
            throw new IllegalArgumentException("Invalid history selection: " + enteredValue);
        }
        return enteredValue;
    }

    private static void printInputPrompt(List<String> history) {
        System.out.println("Select audio file:");
        for (int i = 0; i < history.size(); i++) {
            System.out.println((i + 1) + ". " + history.get(i));
        }
        if (!history.isEmpty()) {
            System.out.println("Or paste a new path.");
        }
        System.out.print("> ");
    }

    static Path getHistoryFile() {
        String override = System.getProperty("audio.cli.history.file");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get(System.getProperty("user.home"), ".audio-transcriber-history");
    }

    static String loadWhisperCliPath() throws IOException {
        Path file = getWhisperCliFile();
        if (!Files.exists(file)) {
            return null;
        }
        String value = normalizePath(Files.readString(file));
        return value == null || value.isBlank() ? null : value;
    }

    static void saveWhisperCliPath(String whisperCliPath) throws IOException {
        String normalized = normalizePath(whisperCliPath);
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        Files.writeString(getWhisperCliFile(), normalized);
    }

    static Path getWhisperCliFile() {
        String override = System.getProperty("audio.cli.whisper.file");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return WHISPER_CLI_FILE;
    }

    static String loadModelPath() throws IOException {
        Path file = getModelFile();
        if (!Files.exists(file)) {
            return null;
        }
        String value = normalizePath(Files.readString(file));
        return value == null || value.isBlank() ? null : value;
    }

    static void saveModelPath(String modelPath) throws IOException {
        String normalized = normalizePath(modelPath);
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        Files.writeString(getModelFile(), normalized);
    }

    static Path getModelFile() {
        String override = System.getProperty("audio.cli.model.file");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return MODEL_FILE;
    }

    static String selectModelPath(String enteredValue) {
        if (enteredValue == null || enteredValue.isBlank()) {
            return DEFAULT_MODEL_PATH;
        }
        if (enteredValue.matches("[1-3]")) {
            return MODEL_OPTIONS[Integer.parseInt(enteredValue) - 1];
        }
        return enteredValue;
    }

    private static void printModelPrompt() {
        System.out.println("Select model:");
        System.out.println("1. tiny   - fastest");
        System.out.println("2. small  - balanced");
        System.out.println("3. medium - better quality");
        System.out.println("Or paste a custom model path.");
        System.out.println("Tip: use --threads <count> to increase whisper CPU usage if needed.");
        System.out.print("> ");
    }

    static Path saveTranscriptionForInput(Path inputPath, String transcription) throws IOException {
        Path outputPath = buildTranscriptionPath(inputPath);
        return saveTranscription(outputPath, transcription);
    }

    static Path saveTranscription(Path outputPath, String transcription) throws IOException {
        String content = formatTranscriptionText(transcription);
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempOutputPath = Files.createTempFile(
            parent != null ? parent : Paths.get("."),
            outputPath.getFileName().toString(),
            ".tmp"
        );
        Files.writeString(
            tempOutputPath,
            content + System.lineSeparator(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
        try {
            Files.move(
                tempOutputPath,
                outputPath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempOutputPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return outputPath;
    }

    static Path buildTranscriptionPath(Path inputPath) {
        Path parent = inputPath.getParent();
        String fileName = inputPath.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        String baseName = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;

        Path primary = parent != null
            ? parent.resolve(baseName + ".txt")
            : Paths.get(baseName + ".txt");

        if (!Files.exists(primary) || primary.equals(inputPath)) {
            return primary;
        }

        return parent != null
            ? parent.resolve(baseName + ".transcription.txt")
            : Paths.get(baseName + ".transcription.txt");
    }

    static Path buildBatchOutputDir(Path inputDir) {
        Path current = inputDir;
        while (current != null) {
            Path candidate = current.resolve("txt");
            if (Files.isDirectory(candidate)) {
                return candidate.resolve(current.relativize(inputDir));
            }
            current = current.getParent();
        }
        return inputDir.resolve("txt");
    }

    static Path buildBatchTranscriptionPath(Path inputDir, Path inputFile) {
        Path relativeFile = inputDir.relativize(inputFile);
        Path relativeParent = relativeFile.getParent();
        String fileName = inputFile.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        String baseName = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
        Path outputDir = buildBatchOutputDir(inputDir);
        if (relativeParent != null) {
            outputDir = outputDir.resolve(relativeParent);
        }
        return outputDir.resolve(baseName + ".txt");
    }

    static String relativeDisplayPath(Path inputDir, Path path) {
        return inputDir.relativize(path).toString();
    }

    static String formatTranscriptionText(String transcription) {
        String normalized = transcription != null && !transcription.isEmpty()
            ? transcription.trim()
            : "[No speech detected]";
        if (normalized.equals("[No speech detected]")) {
            return normalized;
        }

        String[] sentences = normalized.split("(?<=[.!?])\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i].trim();
            if (sentence.isEmpty()) {
                continue;
            }

            if (result.length() > 0) {
                if (i % 5 == 0) {
                    result.append(System.lineSeparator()).append(System.lineSeparator());
                } else {
                    result.append(' ');
                }
            }
            result.append(sentence);
        }
        return result.toString();
    }

    /**
     * Performs the full transcription pipeline.
     */
    private static void transcribe(
            String inputFile,
            String modelPath,
            String language,
            String whisperCliPath,
            int cooldownSeconds,
            int threadCount)
            throws Exception {
        long totalStart = System.nanoTime();
        
        System.err.println("Audio Transcriber CLI");
        System.err.println("=====================");
        System.err.println("Input file: " + inputFile);
        System.err.println("Model: " + modelPath);
        System.err.println("Language: " + language);
        System.err.println("Whisper CLI: " + whisperCliPath);
        System.err.println("Whisper threads: " + threadCount);
        System.err.println();

        Path inputPath = Paths.get(inputFile);
        Utils.validateInputPath(inputFile);

        if (Files.isDirectory(inputPath)) {
            transcribeDirectory(inputPath, modelPath, language, whisperCliPath, cooldownSeconds, threadCount, totalStart);
            return;
        }

        transcribeFile(inputPath, modelPath, language, whisperCliPath, threadCount, totalStart);
    }

    private static void transcribeDirectory(
            Path inputDir,
            String modelPath,
            String language,
            String whisperCliPath,
            int cooldownSeconds,
            int threadCount,
            long totalStart)
            throws Exception {
        Path outputDir = buildBatchOutputDir(inputDir);
        Files.createDirectories(outputDir);

        List<Path> audioFiles;
        try (Stream<Path> stream = Files.walk(inputDir)) {
            audioFiles = stream
                .filter(Utils::isSupportedAudioFile)
                .sorted(Comparator.comparing(path -> relativeDisplayPath(inputDir, path).toLowerCase()))
                .toList();
        }

        if (audioFiles.isEmpty()) {
            throw new IllegalArgumentException("No supported audio files found in directory: " + inputDir);
        }

        System.err.println("Batch mode: " + audioFiles.size() + " audio files found");
        System.err.println("Output dir: " + outputDir);
        System.err.println();

        List<String> failedFiles = new ArrayList<>();
        int skippedFiles = 0;
        for (int i = 0; i < audioFiles.size(); i++) {
            Path audioFile = audioFiles.get(i);
            String displayPath = relativeDisplayPath(inputDir, audioFile);
            System.err.println("File " + (i + 1) + "/" + audioFiles.size() + ": " + displayPath);
            Path batchOutputPath = buildBatchTranscriptionPath(inputDir, audioFile);
            if (Files.exists(batchOutputPath)) {
                skippedFiles++;
                System.err.println("SKIP: output already exists at " + batchOutputPath);
                System.err.println();
                continue;
            }
            try {
                transcribeFile(
                    audioFile,
                    modelPath,
                    language,
                    whisperCliPath,
                    threadCount,
                    null,
                    batchOutputPath
                );
            } catch (Exception e) {
                failedFiles.add(displayPath + " -> " + e.getMessage());
                System.err.println("FAILED: " + displayPath);
                System.err.println("Reason: " + e.getMessage());
            }
            System.err.println();

            if (cooldownSeconds > 0 && i < audioFiles.size() - 1) {
                System.err.println("Cooldown: sleeping for " + cooldownSeconds + "s before next file");
                Thread.sleep(cooldownSeconds * 1000L);
                System.err.println();
            }
        }

        if (!failedFiles.isEmpty()) {
            System.err.println("Batch finished with " + failedFiles.size() + " failed file(s):");
            for (String failedFile : failedFiles) {
                System.err.println("  " + failedFile);
            }
            System.err.println();
        }

        if (skippedFiles > 0) {
            System.err.println("Skipped existing outputs: " + skippedFiles);
        }
        System.err.println("Batch completed in " + formatDuration(System.nanoTime() - totalStart));
    }

    private static void transcribeFile(
            Path inputPath,
            String modelPath,
            String language,
            String whisperCliPath,
            int threadCount,
            Long totalStart)
            throws Exception {
        transcribeFile(inputPath, modelPath, language, whisperCliPath, threadCount, totalStart, null);
    }

    private static void transcribeFile(
            Path inputPath,
            String modelPath,
            String language,
            String whisperCliPath,
            int threadCount,
            Long totalStart,
            Path outputPath)
            throws Exception {
        Utils.validateInputFile(inputPath.toString());

        // Step 1: Convert audio to WAV format using FFmpeg
        System.err.println("Step 1/2: Converting audio to WAV (16kHz, mono, PCM)...");
        AudioConverter converter = new AudioConverter();
        converter.validateFfmpegInstalled();
        
        Path wavPath = null;
        long conversionStart = System.nanoTime();
        try {
            wavPath = converter.convertToWav(inputPath);
            System.err.println("  ✓ Audio converted successfully in " + formatDuration(System.nanoTime() - conversionStart));
        } catch (Exception e) {
            System.err.println("  ✗ Audio conversion failed");
            throw e;
        }

        // Step 2: Transcribe using whisper.cpp
        System.err.println("Step 2/2: Transcribing audio with whisper.cpp...");
        WhisperRunner runner = new WhisperRunner(whisperCliPath, modelPath, language, threadCount);
        runner.validateWhisperInstalled();
        
        String transcription;
        long transcriptionStart = System.nanoTime();
        try {
            transcription = runner.transcribe(wavPath);
            System.err.println("  ✓ Transcription completed in " + formatDuration(System.nanoTime() - transcriptionStart));
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

        Path transcriptionFile = outputPath != null
            ? saveTranscription(outputPath, transcription)
            : saveTranscriptionForInput(inputPath, transcription);
        System.err.println("Saved to: " + transcriptionFile);
        if (totalStart != null) {
            System.err.println("Total time: " + formatDuration(System.nanoTime() - totalStart));
        }
        System.err.println();

        if (transcription != null && !transcription.isEmpty()) {
            System.out.println(transcription);
        } else {
            System.out.println("[No speech detected]");
        }
    }

    static String formatDuration(long nanos) {
        return String.format("%.2fs", nanos / 1_000_000_000.0);
    }

    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Audio Transcriber CLI - Local speech-to-text tool");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar stt.jar [audio-file] [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  [audio-file]           Path to audio file (mp3, wav, webm, opus, m4a, etc.)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -m, --model <path>     Path to whisper model (default: models/ggml-medium.bin)");
        System.out.println("  -l, --language <code>  Language code (default: ru)");
        System.out.println("  -w, --whisper-cli      Path to whisper-cli binary (default: whisper-cli from PATH)");
        System.out.println("  -c, --cooldown <sec>   Sleep between files in batch mode (default: 0)");
        System.out.println("  -t, --threads <count>  Number of whisper threads (default: " + DEFAULT_THREADS + ")");
        System.out.println("                         Increase only if the machine has spare CPU/RAM.");
        System.out.println("  -h, --help             Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar stt.jar");
        System.out.println("  java -jar stt.jar input.mp3");
        System.out.println("  java -jar stt.jar input.webm --model models/ggml-medium.bin");
        System.out.println("  java -jar stt.jar input.wav --language en");
        System.out.println("  java -jar stt.jar /path/to/folder --cooldown 30");
        System.out.println("  java -jar stt.jar input.mp3 --threads 1");
        System.out.println("  java -jar stt.jar input.mp3 --whisper-cli /opt/whisper.cpp/build/bin/whisper-cli");
        System.out.println();
        System.out.println("Requirements:");
        System.out.println("  - FFmpeg installed and in PATH");
        System.out.println("  - whisper.cpp CLI installed and in PATH");
        System.out.println("  - Whisper model file downloaded");
    }

    record CliOptions(
        String inputFile,
        String modelPath,
        String language,
        String whisperCliPath,
        int cooldownSeconds,
        int threadCount
    ) {
    }
}
