# Project Context: audio-cli

## Current State
The project is a CLI tool for local audio transcription using FFmpeg and whisper.cpp.
It has been recently refactored to use Dependency Injection and JCommander for argument parsing.
It supports both single file transcription and batch directory processing.

## Recent Changes
- **Removed Retry Logic**: Eliminated retry mechanisms from `CommandExecutor` as they are unnecessary for local system calls without network dependencies.
- **Improved Text Processing**: 
    - Introduced `TextProcessor` utilizing **Apache Lucene** for morphological analysis (lemmatization) and **Apache Commons Text** for robust HTML entity unescaping.
    - Unified text formatting utilities into `TextFormatUtils`.
- **Refactored Argument Parsing**: Switched to JCommander for better flag handling and positional argument support.
- **Restored Interactive Features**: Re-implemented input history and prompting for missing paths.
- **Ensured Test Integrity**: All 40 tests are passing after refactoring.

## Pending Items
- Add more robust error handling for FFmpeg conversion.
- Implement progress bars for batch processing.
- Add support for custom output formats (e.g., JSON, VTT).
