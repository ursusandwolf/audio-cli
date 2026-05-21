package com.stt;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;

/**
 * Service for lemmatization and text cleaning.
 */
public class TextProcessor {

    public enum Language {
        RU, EN
    }

    /**
     * Returns the lemma of a word using Lucene's morphological analyzers.
     */
    public String getLemma(String word, Language lang) {
        try (Analyzer analyzer = (lang == Language.RU) ? new RussianAnalyzer() : new EnglishAnalyzer();
             TokenStream ts = analyzer.tokenStream("", word)) {
            CharTermAttribute attr = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            return ts.incrementToken() ? attr.toString() : word;
        } catch (IOException e) {
            return word;
        }
    }

    /**
     * Cleans HTML entities and normalizes whitespace.
     */
    public String cleanText(String text) {
        if (text == null) return "";
        String unescaped = StringEscapeUtils.unescapeHtml4(text);
        return unescaped.replaceAll("\\s+", " ").trim();
    }
}
