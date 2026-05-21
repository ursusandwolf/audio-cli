package com.stt;

/**
 * Configuration for transcription process.
 */
public record TranscriptionConfig(
    String whisperCliPath,
    String modelPath,
    String language,
    int threadLimit
) {}
