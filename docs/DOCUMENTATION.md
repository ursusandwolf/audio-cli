# Documentation: Audio Transcriber CLI

## Usage
`java -jar stt.jar -i <input> [options]`

### Options
- `-i, --input <path>`: Input audio file or directory.
- `-m, --model <path>`: Path to whisper.cpp model (default: `models/ggml-medium.bin`).
- `-l, --language <code>`: Transcription language (default: `ru`).
- `-w, --whisper <path>`: Path to whisper-cli executable.
- `-t, --threads <n>`: Number of threads (default: 1).
- `--timeout <sec>`: Execution timeout in seconds.
- `--retries <n>`: Number of retries on failure.
- `--cooldown <sec>`: Sleep between files in batch mode.
- `-h, --help`: Show help.

### Interactive Mode
If no input file is provided via `-i`, the tool will prompt for a path and show a history of the last 3 unique input files.
