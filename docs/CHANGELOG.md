# Changelog

## [1.0.1] - 2026-05-21
### Added
- JCommander for robust argument parsing.
- Timeout support for system command execution.
- **Text Processing Overhaul**:
    - Integrated **Apache Lucene** for high-quality Russian and English lemmatization.
    - Added **Apache Commons Text** for reliable HTML unescaping.
    - Centralized formatting logic in `TextFormatUtils`.
- Interactive mode: history selection and prompting for missing input files.
- Batch processing support for directories.

### Fixed
- Stub implementation of `parseArgs` in `Main`.
- Broken tests after previous refactoring.
- Redundant logic in command list building (now uses Streams).
- Inconsistent error messages in `WhisperRunner`.

### Changed
- Refactored `CommandExecutor` to support configuration (timeout).
- **Removed retry logic** as it's redundant for local-only processing.
- Updated `TranscriptionConfig` to include execution parameters.
