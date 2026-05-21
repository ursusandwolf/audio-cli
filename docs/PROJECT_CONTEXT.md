# Project Context: audio-cli

## Current State
The project is a CLI tool for local audio transcription using FFmpeg and whisper.cpp.
It has been recently refactored to use Dependency Injection and JCommander for argument parsing.
It supports both single file transcription and batch directory processing.

## Recent Changes
- Replaced stub argument parsing with JCommander.
- Implemented timeout and retry logic in `DefaultCommandExecutor`.
- Restored interactive features (input history, prompting for missing paths) that were lost in previous refactors.
- Fixed several bugs identified in the code review (`docs/coderev.md`).
- Unified command execution logic using Java Streams.
- Ensured 100% test pass rate for existing tests.

## Pending Items
- Implement Lucene-based lemmatization for transcription post-processing if needed.
- Add more robust error handling for FFmpeg conversion.
- Implement progress bars for batch processing.
