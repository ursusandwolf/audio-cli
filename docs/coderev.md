Отлично, теперь у меня полная картина. Делаю детальный review.Теперь у меня полная картина. Пишу подробный review.---

## Code Review: audio-cli + YtdlpJava

### Общая оценка

Оба проекта написаны аккуратно — есть интерфейсы, `record`-конфиги, приличное разделение слоёв. Главные проблемы: **дублирование кода между проектами и внутри YtdlpJava**, плюс один большой "велосипед" — `RussianStemmer` — при том что Lucene с морфологическим анализатором **уже подключён** в `pom.xml`.

---

### Находки

#### 🔴 Критично

**1. `RussianStemmer` + `LemmatizerService` — это велосипед, который уже есть в pom.xml**

В `pom.xml` подключены `lucene-analysis-morfologik` и `lucene-analysis-common`. `MorfologikAnalyzer` делает полноценную лемматизацию русского через словарь Morfologik — это совсем другой уровень точности, чем алгоритмический Porter Stemmer. Слова типа "бежал/бегут/беги" правильно сведутся к "бежать", а не к разным обрубкам.

```java
// Текущий велосипед (RussianStemmer.java) — ~80 строк regex + алгоритм
// Заменяется на:

import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public class LemmatizerService {
    public String getLemma(String word, Language lang) {
        Analyzer analyzer = lang == Language.RU
            ? new RussianAnalyzer()   // Morfologik под капотом
            : new EnglishAnalyzer();  // уже в lucene-analysis-common
        try (TokenStream ts = analyzer.tokenStream("", word)) {
            CharTermAttribute attr = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            return ts.incrementToken() ? attr.toString() : word;
        }
    }
}
```

---

**2. Два идентичных `SubtitleParser` в одном проекте**

`com.ytdlpjava.processor.SubtitleParser` и `com.ytdlpjava.subtitle.processor.SubtitleParser` — полные копии (только пакет и местоположение `SubtitleBlock` record отличаются). Это классический Shotgun Surgery: если поменять regex — нужно помнить об обоих.

```java
// Оставить один — в subtitle.processor, как часть нового API
// SubtitleCleaner (processor) должен использовать его же
```

---

**3. `unescapeHtml` в `SubtitleCleanerService` — ручная и неполная реализация**

```java
// Сейчас — только 5 сущностей, остальные вернутся как есть:
private String unescapeHtml(String s) {
    return s.replace("&amp;", "&").replace("&lt;", "<")...
}

// Заменить на Apache Commons Text (или Jsoup, оба легковесны):
// commons-text уже есть во многих транзитивных зависимостях
import org.apache.commons.text.StringEscapeUtils;
String clean = StringEscapeUtils.unescapeHtml4(line);
```

---

#### 🟡 Стоит улучшить

**4. `ProcessExecutor` (YtdlpJava) и `DefaultCommandExecutor` (audio-cli) — одна и та же абстракция**

Оба проекта оборачивают `ProcessBuilder`, читают stdout, обрабатывают exit code. Если проекты могут шарить зависимость — стоит вынести в общий модуль `process-executor-core`. Если нет — хотя бы привести к одному дизайну: в YtdlpJava есть таймаут и retry-логика, в audio-cli их нет.

**5. `isSentenceEnding` и `formatTimestamp` — 3 копии**

Методы с одинаковой сигнатурой и телом сидят в `SubtitleCleaner`, `SubtitleTextAssembler` и `MarkdownFormatter`. Решение:

```java
// Новый класс-утилита (не static, чтобы не ломать тестируемость — или static final)
public final class TextFormatUtils {
    public static boolean isSentenceEnding(char c) {
        return c == '.' || c == '!' || c == '?' || c == '…';
    }
    public static String formatTimestamp(Duration d) {
        long s = d.getSeconds();
        return "%02d:%02d:%02d".formatted(s / 3600, (s % 3600) / 60, s % 60);
    }
}
```

**6. Magic numbers в `SubtitleCleaner`**

```java
// Сейчас — магия прямо в коде:
} else if (currentParagraph.length() > 600) {
} else if (currentParagraph.length() > 800) {

// Должно быть в конфиге (аналогично SubtitleConfig в новом API):
config.paragraphSoftLimit()   // 600
config.paragraphHardLimit()   // 800
```

Собственно, в `SubtitleTextAssembler` это уже сделано через `SubtitleConfig` — `SubtitleCleaner` просто не успел быть рефакторен.

**7. `Main.parseArgs` в audio-cli — заглушка, не парсит аргументы**

```java
static CliOptions parseArgs(String[] args) {
    String inputFile = null;   // <-- всегда null
    // ... никаких args не читается
    return new CliOptions(inputFile, modelPath, ...);
}
```

Это приведёт к `NullPointerException` при любом реальном запуске. В `pom.xml` уже есть `jcommander` — нужно им воспользоваться.

---

#### 🟢 Мелочи

- `KeywordHighlighter`: `.max(...).get().getKey()` без проверки на пустой список — скрытый `NoSuchElementException`. Заменить на `.orElseThrow()` или проверку.
- `AbstractYoutubeService.runResiliently` — 5 try/catch блоков с одинаковой структурой. Можно заменить на `List<Supplier<String>>` с циклом.
- `SubtitleCleaner` реализует и `ContentProcessor`, и `TextProcessor` — два интерфейса с разными контрактами. Потенциальное нарушение ISP; стоит проверить, нужны ли оба.
- `DefaultCommandExecutor.buildCommandList` — можно заменить на `Stream.concat(Stream.of(command), Arrays.stream(args)).toList()`.

---

### Итоговые рекомендации (по приоритету)

1. **Удалить `RussianStemmer` / `LemmatizerService`** → использовать `RussianAnalyzer` из Lucene, который уже в pom.xml.
2. **Слить два `SubtitleParser`** в один (оставить `subtitle.processor`).
3. **Вынести `isSentenceEnding` / `formatTimestamp`** в `TextFormatUtils`.
4. **Починить `Main.parseArgs`** в audio-cli через jcommander.
5. **Заменить ручной `unescapeHtml`** на `StringEscapeUtils.unescapeHtml4`.
6. **Перенести magic numbers** из `SubtitleCleaner` в конфиг (по образцу `SubtitleTextAssembler`).