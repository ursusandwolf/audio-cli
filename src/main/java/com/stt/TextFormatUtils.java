package com.stt;

import java.time.Duration;

/**
 * Utility class for text formatting and duration manipulation.
 */
public final class TextFormatUtils {

    private TextFormatUtils() {
        // Utility class
    }

    /**
     * Checks if a character is a sentence ending punctuation.
     */
    public static boolean isSentenceEnding(char c) {
        return c == '.' || c == '!' || c == '?' || c == '…';
    }

    /**
     * Formats a duration as HH:mm:ss.
     */
    public static String formatTimestamp(Duration d) {
        long s = d.getSeconds();
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
