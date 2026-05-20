package com.stt;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MainTest {

    private static final String HISTORY_FILE_PROPERTY = "audio.cli.history.file";
    private static final String WHISPER_FILE_PROPERTY = "audio.cli.whisper.file";
    private static final String MODEL_FILE_PROPERTY = "audio.cli.model.file";

    @Test
    void parseArgsUsesMediumModelByDefault() {
        Main.CliOptions options = Main.parseArgs(new String[]{"input.mp3"});

        assertEquals("input.mp3", options.inputFile());
        assertEquals("models/ggml-medium.bin", options.modelPath());
        assertEquals("ru", options.language());
        assertEquals("whisper-cli", options.whisperCliPath());
        assertEquals(0, options.cooldownSeconds());
        assertEquals(WhisperRunner.resolveDefaultThreadLimit(), options.threadCount());
    }

    @Test
    void parseArgsAcceptsCustomWhisperCliPath() {
        Main.CliOptions options = Main.parseArgs(new String[]{
            "input.mp3", "--whisper-cli", "/opt/whisper.cpp/build/bin/whisper-cli", "--cooldown", "30", "--threads", "1"
        });

        assertEquals("/opt/whisper.cpp/build/bin/whisper-cli", options.whisperCliPath());
        assertEquals(30, options.cooldownSeconds());
        assertEquals(1, options.threadCount());
    }

    @Test
    void parseArgsRejectsMultipleInputFiles() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> Main.parseArgs(new String[]{"a.mp3", "b.mp3"})
        );

        assertEquals("Only one input file is supported. Received: 'a.mp3' and 'b.mp3'", error.getMessage());
    }

    @Test
    void parseArgsAllowsInteractiveModeWithoutInputFile() {
        Main.CliOptions options = Main.parseArgs(new String[]{});

        assertEquals(null, options.inputFile());
        assertEquals("models/ggml-medium.bin", options.modelPath());
    }

    @Test
    void parseThreadCountRejectsZero() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> Main.parseThreadCount("0")
        );

        assertEquals("--threads must be 1 or greater", error.getMessage());
    }

    @Test
    void resolveInputFilePromptsFromStdIn() throws Exception {
        InputStream originalIn = System.in;
        String originalHistoryFile = System.getProperty(HISTORY_FILE_PROPERTY);
        Path historyFile = Files.createTempFile("audio-cli-history", ".txt");
        try {
            System.setProperty(HISTORY_FILE_PROPERTY, historyFile.toString());
            System.setIn(new ByteArrayInputStream("/tmp/audio.mp3\n".getBytes()));

            assertEquals("/tmp/audio.mp3", Main.resolveInputFile(null));
        } finally {
            restoreHistoryFileProperty(originalHistoryFile);
            System.setIn(originalIn);
            Files.deleteIfExists(historyFile);
        }
    }

    @Test
    void parseArgsStripsWrappingQuotesFromInputFile() {
        Main.CliOptions options = Main.parseArgs(new String[]{"\"/tmp/audio file.mp3\""});

        assertEquals("/tmp/audio file.mp3", options.inputFile());
    }

    @Test
    void resolveInputFileStripsWrappingQuotesFromPromptInput() throws Exception {
        InputStream originalIn = System.in;
        String originalHistoryFile = System.getProperty(HISTORY_FILE_PROPERTY);
        Path historyFile = Files.createTempFile("audio-cli-history", ".txt");
        try {
            System.setProperty(HISTORY_FILE_PROPERTY, historyFile.toString());
            System.setIn(new ByteArrayInputStream("\"/tmp/audio file.mp3\"\n".getBytes()));

            assertEquals("/tmp/audio file.mp3", Main.resolveInputFile(null));
        } finally {
            restoreHistoryFileProperty(originalHistoryFile);
            System.setIn(originalIn);
            Files.deleteIfExists(historyFile);
        }
    }

    @Test
    void selectInputFileUsesHistoryNumber() {
        String selected = Main.selectInputFile("2", List.of("/tmp/one.mp3", "/tmp/two.mp3"));

        assertEquals("/tmp/two.mp3", selected);
    }

    @Test
    void selectInputFileRejectsMissingHistoryNumber() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> Main.selectInputFile("3", List.of("/tmp/one.mp3"))
        );

        assertEquals("Invalid history selection: 3", error.getMessage());
    }

    @Test
    void saveInputFileToHistoryKeepsLastThreeUniqueFiles() throws Exception {
        String originalHistoryFile = System.getProperty(HISTORY_FILE_PROPERTY);
        Path historyFile = Files.createTempFile("audio-cli-history", ".txt");
        try {
            System.setProperty(HISTORY_FILE_PROPERTY, historyFile.toString());

            Main.saveInputFileToHistory("/tmp/one.mp3");
            Main.saveInputFileToHistory("/tmp/two.mp3");
            Main.saveInputFileToHistory("/tmp/three.mp3");
            Main.saveInputFileToHistory("/tmp/two.mp3");
            Main.saveInputFileToHistory("/tmp/four.mp3");

            assertEquals(
                List.of("/tmp/four.mp3", "/tmp/two.mp3", "/tmp/three.mp3"),
                Main.loadInputFileHistory()
            );
        } finally {
            restoreHistoryFileProperty(originalHistoryFile);
            Files.deleteIfExists(historyFile);
        }
    }

    @Test
    void saveAndLoadWhisperCliPath() throws Exception {
        String originalWhisperFile = System.getProperty(WHISPER_FILE_PROPERTY);
        Path whisperFile = Files.createTempFile("audio-cli-whisper", ".txt");
        try {
            System.setProperty(WHISPER_FILE_PROPERTY, whisperFile.toString());

            Main.saveWhisperCliPath("/tmp/whisper-cli");

            assertEquals("/tmp/whisper-cli", Main.loadWhisperCliPath());
        } finally {
            restoreProperty(WHISPER_FILE_PROPERTY, originalWhisperFile);
            Files.deleteIfExists(whisperFile);
        }
    }

    @Test
    void resolveWhisperCliPathUsesStoredValueWhenDefaultMissing() throws Exception {
        String originalWhisperFile = System.getProperty(WHISPER_FILE_PROPERTY);
        Path whisperFile = Files.createTempFile("audio-cli-whisper", ".txt");
        try {
            System.setProperty(WHISPER_FILE_PROPERTY, whisperFile.toString());
            Main.saveWhisperCliPath("/bin/echo");

            assertEquals("/bin/echo", Main.resolveWhisperCliPath("whisper-cli"));
        } finally {
            restoreProperty(WHISPER_FILE_PROPERTY, originalWhisperFile);
            Files.deleteIfExists(whisperFile);
        }
    }

    @Test
    void buildTranscriptionPathUsesTxtNextToAudio() {
        Path input = Path.of("/tmp/audio.mp3");

        assertEquals(Path.of("/tmp/audio.txt"), Main.buildTranscriptionPath(input));
    }

    @Test
    void buildTranscriptionPathAvoidsOverwritingExistingTxtFile() throws Exception {
        Path tempDir = Files.createTempDirectory("audio-cli-output");
        Path input = tempDir.resolve("audio.mp3");
        Path existingTxt = tempDir.resolve("audio.txt");
        try {
            Files.writeString(existingTxt, "existing", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            assertEquals(tempDir.resolve("audio.transcription.txt"), Main.buildTranscriptionPath(input));
        } finally {
            Files.deleteIfExists(existingTxt);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void saveTranscriptionWritesFileContent() throws Exception {
        Path tempDir = Files.createTempDirectory("audio-cli-save");
        Path input = tempDir.resolve("audio.mp3");
        try {
            Path output = Main.saveTranscriptionForInput(input, "hello world");

            assertEquals(tempDir.resolve("audio.txt"), output);
            assertEquals("hello world" + System.lineSeparator(), Files.readString(output));
            Files.deleteIfExists(output);
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void saveTranscriptionCreatesNestedOutputDirectories() throws Exception {
        Path tempDir = Files.createTempDirectory("audio-cli-nested-save");
        Path output = tempDir.resolve("txt/course1/lesson.txt");
        try {
            Main.saveTranscription(output, "hello world");

            assertEquals("hello world" + System.lineSeparator(), Files.readString(output));
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(output.getParent());
            Files.deleteIfExists(output.getParent().getParent());
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void saveTranscriptionReplacesTargetViaTempFile() throws Exception {
        Path tempDir = Files.createTempDirectory("audio-cli-atomic-save");
        Path output = tempDir.resolve("lesson.txt");
        try {
            Main.saveTranscription(output, "first");
            Main.saveTranscription(output, "second");

            assertEquals("second" + System.lineSeparator(), Files.readString(output));
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void formatTranscriptionTextAddsParagraphAfterFiveSentences() {
        String source = "One. Two. Three. Four. Five. Six. Seven.";

        assertEquals(
            "One. Two. Three. Four. Five." + System.lineSeparator() + System.lineSeparator() + "Six. Seven.",
            Main.formatTranscriptionText(source)
        );
    }

    @Test
    void buildBatchTranscriptionPathUsesTxtSubdirectory() {
        Path inputDir = Path.of("/tmp/audio");
        Path inputFile = inputDir.resolve("lecture.mp3");

        assertEquals(Path.of("/tmp/audio/txt/lecture.txt"), Main.buildBatchTranscriptionPath(inputDir, inputFile));
    }

    @Test
    void buildBatchTranscriptionPathMirrorsNestedDirectories() {
        Path inputDir = Path.of("/tmp/audio");
        Path inputFile = inputDir.resolve("course1/week2/lecture.mp3");

        assertEquals(
            Path.of("/tmp/audio/txt/course1/week2/lecture.txt"),
            Main.buildBatchTranscriptionPath(inputDir, inputFile)
        );
    }

    @Test
    void buildBatchOutputDirReusesAncestorTxtDirectory() throws Exception {
        Path root = Files.createTempDirectory("audio-cli-batch-root");
        Path existingTxtDir = root.resolve("txt");
        Path inputDir = root.resolve("05");
        try {
            Files.createDirectories(existingTxtDir);
            Files.createDirectories(inputDir);

            assertEquals(existingTxtDir.resolve("05"), Main.buildBatchOutputDir(inputDir));
        } finally {
            Files.deleteIfExists(inputDir);
            Files.deleteIfExists(existingTxtDir);
            Files.deleteIfExists(root);
        }
    }

    @Test
    void buildBatchTranscriptionPathReusesAncestorTxtDirectory() throws Exception {
        Path root = Files.createTempDirectory("audio-cli-batch-root");
        Path existingTxtDir = root.resolve("txt");
        Path inputDir = root.resolve("05");
        Path inputFile = inputDir.resolve("lecture.mp3");
        try {
            Files.createDirectories(existingTxtDir);
            Files.createDirectories(inputDir);

            assertEquals(
                existingTxtDir.resolve("05/lecture.txt"),
                Main.buildBatchTranscriptionPath(inputDir, inputFile)
            );
        } finally {
            Files.deleteIfExists(inputDir);
            Files.deleteIfExists(existingTxtDir);
            Files.deleteIfExists(root);
        }
    }

    @Test
    void relativeDisplayPathUsesRootRelativePath() {
        Path inputDir = Path.of("/tmp/audio");
        Path inputFile = inputDir.resolve("course1/week2/lecture.mp3");

        assertEquals("course1/week2/lecture.mp3", Main.relativeDisplayPath(inputDir, inputFile));
    }

    @Test
    void formatDurationUsesSecondsWithTwoDecimals() {
        assertEquals("1.23s", Main.formatDuration(1_234_000_000L));
    }

    @Test
    void selectModelPathUsesMenuNumber() {
        assertEquals("models/ggml-medium.bin", Main.selectModelPath("3"));
    }

    @Test
    void selectModelPathUsesDefaultForBlankInput() {
        assertEquals("models/ggml-medium.bin", Main.selectModelPath(""));
    }

    @Test
    void saveAndLoadModelPath() throws Exception {
        String originalModelFile = System.getProperty(MODEL_FILE_PROPERTY);
        Path modelFile = Files.createTempFile("audio-cli-model", ".txt");
        try {
            System.setProperty(MODEL_FILE_PROPERTY, modelFile.toString());

            Main.saveModelPath("models/ggml-small.bin");

            assertEquals("models/ggml-small.bin", Main.loadModelPath());
        } finally {
            restoreProperty(MODEL_FILE_PROPERTY, originalModelFile);
            Files.deleteIfExists(modelFile);
        }
    }

    @Test
    void parseCooldownSecondsRejectsNegativeValues() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> Main.parseCooldownSeconds("-1")
        );

        assertEquals("--cooldown must be 0 or greater", error.getMessage());
    }

    @Test
    void parseCooldownSecondsRejectsNonNumericValues() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> Main.parseCooldownSeconds("abc")
        );

        assertEquals("--cooldown must be an integer number of seconds", error.getMessage());
    }

    private static void restoreHistoryFileProperty(String originalValue) {
        restoreProperty(HISTORY_FILE_PROPERTY, originalValue);
    }

    private static void restoreProperty(String key, String originalValue) {
        if (originalValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, originalValue);
        }
    }
}
