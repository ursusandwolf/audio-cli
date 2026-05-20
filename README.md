# Audio Transcriber CLI

Минимальный CLI-инструмент для локальной транскрибации аудио → текст.

## 🔧 Требования

### 1. Java 17+

Проверка версии:
```bash
java --version
```

### 2. FFmpeg

**Ubuntu/Debian:**
```bash
sudo apt-get update
sudo apt-get install ffmpeg
```

**macOS:**
```bash
brew install ffmpeg
```

**Windows:**
Скачайте с https://ffmpeg.org/download.html и добавьте в PATH

Проверка установки:
```bash
ffmpeg -version
```

### 3. whisper.cpp

**Установка:**
```bash
# Клонируем репозиторий
git clone https://github.com/ggerganov/whisper.cpp.git
cd whisper.cpp

# Собираем
make

# Добавляем в PATH (опционально)
export PATH=$PATH:$(pwd)
```

Или используйте полный путь к `whisper-cli` при запуске.

**Проверка:**
```bash
./whisper-cli --help
```

### 4. Модель whisper

Скачиваем модель (требуется ~3GB):

```bash
mkdir -p models
wget -P models https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3.bin
```

**Альтернативные модели (меньше размер, ниже качество):**
- `ggml-tiny.bin` (~75 MB)
- `ggml-base.bin` (~142 MB)
- `ggml-small.bin` (~466 MB)
- `ggml-medium.bin` (~1.5 GB)

## 📦 Сборка проекта

```bash
mvn clean package
```

JAR-файл будет создан в `target/audio-transcriber-1.0-SNAPSHOT.jar`

## 🚀 Запуск

### Базовый вариант (русский язык):
```bash
java -jar target/audio-transcriber-1.0-SNAPSHOT.jar input.mp3
```

### С указанием модели:
```bash
java -jar target/audio-transcriber-1.0-SNAPSHOT.jar input.webm --model models/ggml-large-v3.bin
```

### С указанием языка:
```bash
java -jar target/audio-transcriber-1.0-SNAPSHOT.jar input.wav --language en
```

### Полные опции:
```bash
java -jar target/audio-transcriber-1.0-SNAPSHOT.jar --help
```

## 📋 Поддерживаемые форматы

Любые форматы, которые поддерживает FFmpeg:
- MP3
- WAV
- WebM
- Opus
- M4A/AAC
- FLAC
- OGG
- AVI
- MKV
- и другие

## 🏗 Архитектура

```
src/main/java/com/stt/
├── Main.java           # Точка входа, CLI парсинг
├── AudioConverter.java # Конвертация через FFmpeg
├── WhisperRunner.java  # Транскрибация через whisper.cpp
└── Utils.java          # Вспомогательные утилиты
```

## 🔄 Pipeline

1. **Вход**: audio.any (любой формат)
2. **Конвертация**: FFmpeg → temp.wav (16kHz, mono, PCM 16-bit LE)
3. **Транскрибация**: whisper.cpp → текст
4. **Вывод**: текст в stdout
5. **Очистка**: удаление временных файлов

## ⚠️ Обработка ошибок

- Файл не найден → ошибка с путем
- FFmpeg не установлен → инструкция по установке
- whisper.cpp не найден → инструкция по установке
- Модель не найдена → ссылка на скачивание
- Неподдерживаемый формат → ошибка FFmpeg

## 🧪 Тестирование

### Короткий файл (<10 сек):
```bash
java -jar target/audio-transcriber-1.0-SNAPSHOT.jar test_short.mp3
```

### Длинный файл (>10 мин):
```bash
java -jar target/audio-transcriber-1.0-SNAPSHOT.jar test_long.webm
```

## 💡 Примеры использования

### Транскрибация записи встречи:
```bash
java -jar target/audio-transcriber-1.0-SNAPSHOT.jar meeting_recording.m4a --language ru
```

### Расшифровка подкаста:
```bash
java -jar target/audio-transcriber-1.0-SNAPSHOT.jar podcast_episode.mp3 --language en
```

### Конвертация голосового сообщения:
```bash
java -jar target/audio-transcriber-1.0-SNAPSHOT.jar voice_message.opus
```

## 📝 Лицензия

MIT