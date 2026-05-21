package com.stt;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
        CliOptions options = new CliOptions();
        JCommander jc = JCommander.newBuilder()
                .addObject(options)
                .build();
        jc.setProgramName("audio-transcriber");

        try {
            jc.parse(args);
            if (options.isHelp()) {
                jc.usage();
                return;
            }

            String inputFile = resolveInputFile(options.inputFile());
            String whisperCliPath = resolveWhisperCliPath(options.whisperCliPath());
            String modelPath = resolveModelPath(options.modelPath());

            TranscriptionConfig config = new TranscriptionConfig(
                whisperCliPath,
                modelPath,
                options.language(),
                options.threadCount(),
                options.timeoutSeconds(),
                options.retryCount()
            );
            AudioConverter converter = new AudioConverter(COMMAND_EXECUTOR);
            WhisperRunner runner = new WhisperRunner(COMMAND_EXECUTOR, config);

            Path inputPath = Paths.get(inputFile);
            if (Files.isDirectory(inputPath)) {
                BatchTranscriber batchTranscriber = new BatchTranscriber(converter, runner);
                batchTranscriber.transcribeDirectory(inputPath);
            } else {
                transcribe(converter, runner, inputFile);
            }
        } catch (ParameterException e) {
            System.err.println("Error: " + e.getMessage());
            jc.usage();
            System.exit(1);
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
        
        Utils.validateInputFile(inputFile);
        Path inputPath = Paths.get(inputFile);

        // Step 1: Convert audio
        System.err.println("Step 1/2: Converting audio to WAV...");
        Path wavPath = converter.convertToWav(inputPath);
        
        // Step 2: Transcribe
        System.err.println("Step 2/2: Transcribing audio...");
        String transcription = runner.transcribe(wavPath);
        Utils.deleteFileIfExists(wavPath);

        System.err.println();
        System.err.println("TRANSCRIPTION RESULT:");
        saveTranscriptionForInput(inputPath, transcription);
        System.out.println(transcription);
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

        return DEFAULT_WHISPER_CLI;
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

        return DEFAULT_MODEL_PATH;
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
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            );
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tempOutputPath, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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

    static String formatDuration(long nanos) {
        return String.format("%.2fs", nanos / 1_000_000_000.0);
    }

    static int parseThreadCount(String value) {
        return WhisperRunner.validateThreadLimit(Integer.parseInt(value));
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

    static CliOptions parseArgs(String[] args) {
        CliOptions options = new CliOptions();
        JCommander jc = JCommander.newBuilder().addObject(options).build();
        try {
            jc.parse(args);
            
            if (options.inputFile == null && !options.mainParams.isEmpty()) {
                if (options.mainParams.size() > 1) {
                    throw new IllegalArgumentException(
                        "Only one input file is supported. Received: '" + 
                        options.mainParams.get(0) + "' and '" + options.mainParams.get(1) + "'"
                    );
                }
                options.inputFile = normalizePath(options.mainParams.get(0));
            } else if (options.inputFile != null && !options.mainParams.isEmpty()) {
                throw new IllegalArgumentException(
                    "Only one input file is supported. Received: '" + 
                    options.inputFile + "' and '" + options.mainParams.get(0) + "'"
                );
            } else if (options.inputFile != null) {
                options.inputFile = normalizePath(options.inputFile);
            }
            
            return options;
        } catch (ParameterException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public static class CliOptions {
        @Parameter(description = "Main parameters")
        private List<String> mainParams = new ArrayList<>();

        @Parameter(names = {"-i", "--input"}, description = "Input audio file or directory")
        private String inputFile;

        @Parameter(names = {"-m", "--model"}, description = "Path to whisper.cpp model")
        private String modelPath = DEFAULT_MODEL_PATH;

        @Parameter(names = {"-l", "--language"}, description = "Transcription language")
        private String language = DEFAULT_LANGUAGE;

        @Parameter(names = {"-w", "--whisper-cli", "--whisper"}, description = "Path to whisper-cli executable")
        private String whisperCliPath = DEFAULT_WHISPER_CLI;

        @Parameter(names = {"-t", "--threads"}, description = "Number of threads")
        private int threadCount = WhisperRunner.resolveDefaultThreadLimit();

        @Parameter(names = {"--timeout"}, description = "Execution timeout in seconds")
        private int timeoutSeconds = 0;

        @Parameter(names = {"--retries"}, description = "Number of retries on failure")
        private int retryCount = 0;

        @Parameter(names = {"--cooldown"}, description = "Cooldown between files")
        private int cooldownSeconds = 0;

        @Parameter(names = {"-h", "--help"}, help = true, description = "Show help")
        private boolean help;

        public String inputFile() { return inputFile; }
        public String modelPath() { return modelPath; }
        public String language() { return language; }
        public String whisperCliPath() { return whisperCliPath; }
        public int threadCount() { return threadCount; }
        public int timeoutSeconds() { return timeoutSeconds; }
        public int retryCount() { return retryCount; }
        public int cooldownSeconds() { return cooldownSeconds; }
        public boolean isHelp() { return help; }
    }
}
