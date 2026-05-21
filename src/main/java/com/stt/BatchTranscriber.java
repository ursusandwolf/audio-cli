package com.stt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for batch processing directories of audio files.
 */
public class BatchTranscriber {

    private final AudioConverter converter;
    private final WhisperRunner runner;

    public BatchTranscriber(AudioConverter converter, WhisperRunner runner) {
        this.converter = converter;
        this.runner = runner;
    }

    public void transcribeDirectory(Path inputDir) throws Exception {
        List<Path> audioFiles;
        try (Stream<Path> stream = Files.walk(inputDir)) {
            audioFiles = stream
                .filter(Utils::isSupportedAudioFile)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }

        System.err.println("Batch processing " + audioFiles.size() + " files...");

        for (Path audioFile : audioFiles) {
            System.err.println("Processing: " + audioFile.getFileName());
            try {
                Path wavPath = converter.convertToWav(audioFile);
                String transcription = runner.transcribe(wavPath);
                Utils.deleteFileIfExists(wavPath);
                
                Path outputPath = inputDir.resolve(audioFile.getFileName().toString() + ".txt");
                Files.writeString(outputPath, transcription);
                System.err.println("Saved: " + outputPath);
            } catch (Exception e) {
                System.err.println("Failed: " + audioFile.getFileName() + " -> " + e.getMessage());
            }
        }
    }
}
