package com.orvigas.observability;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

/**
 * Logback converter that redacts sensitive data fields from log messages.
 *
 * <p>Matches JSON key-value pairs and query-parameter-style values for fields
 * whose names match patterns like password, secret, token, pan, cvv, ssn,
 * or taxId and replaces the value with {@code [REDACTED]}.
 *
 * <p>Registered in {@code logback-spring.xml} under the conversion word
 * {@code maskedMessage}.
 *
 * @author orvigas@gmail.com
 */
public class SensitiveDataConverter extends MessageConverter {

    private static final Pattern JSON_PATTERN = Pattern.compile(
            "(\"(?:password|secret|token|pan|cvv|ssn|taxId)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern KV_PATTERN = Pattern.compile(
            "((?:password|secret|token|pan|cvv|ssn|taxId)=)[^&\\s,;\"]+",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String convert(ILoggingEvent event) {
        return maskSensitiveData(event.getFormattedMessage());
    }

    /**
     * Applies redaction patterns to the given message string.
     *
     * @param message the raw log message
     * @return the redacted message, or null if the input was null
     */
    public static String maskSensitiveData(String message) {
        if (message == null) {
            return null;
        }
        String result = JSON_PATTERN.matcher(message).replaceAll(m -> {
            String prefix = m.group(1);
            return prefix + "\"[REDACTED]\"";
        });
        result = KV_PATTERN.matcher(result).replaceAll(m -> {
            String prefix = m.group(1);
            return prefix + "[REDACTED]";
        });
        return result;
    }
}
