package quest.gekko.spiketracker.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Slf4j
@Component
public class InputValidator {
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]{1,20}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$");
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile("(?i).*(union|select|insert|update|delete|drop|create|alter|exec|script|javascript|vbscript|onload|onerror).*");
    private static final Pattern XSS_PATTERN = Pattern.compile("(?i).*(<script|javascript:|vbscript:|onload=|onerror=|<iframe|<object|<embed).*");

    private static final int MAX_TEAM_NAME_LENGTH = 50;
    private static final int MAX_EVENT_NAME_LENGTH = 200;
    private static final int MAX_URL_LENGTH = 500;
    private static final int MAX_SEARCH_QUERY_LENGTH = 100;

    private final Pattern teamNamePattern;

    public InputValidator() {
        this.teamNamePattern = Pattern.compile("^[a-zA-Z0-9\\s\\-_.]{1," + MAX_TEAM_NAME_LENGTH + "}$");
    }

    public String validateAndSanitizeTeamName(final String teamName) {
        if (teamName == null || teamName.trim().isEmpty()) {
            throw new IllegalArgumentException("Team name cannot be empty");
        }

        final String sanitized = sanitizeInput(teamName.trim());

        if (sanitized.length() > MAX_TEAM_NAME_LENGTH) {
            throw new IllegalArgumentException("Team name too long (max " + MAX_TEAM_NAME_LENGTH + " characters)");
        }

        if (!teamNamePattern.matcher(sanitized).matches()) {
            throw new IllegalArgumentException("Team name contains invalid characters");
        }

        return sanitized;
    }

    public int validateLimit(final Integer limit, final int defaultValue, final int maxValue) {
        if (limit == null || limit <= 0) {
            return defaultValue;
        }

        if (limit > maxValue) {
            log.warn("Requested limit {} exceeds maximum {}, using maximum", limit, maxValue);
            return maxValue;
        }

        return limit;
    }

    public String validateUrl(final String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        final String sanitized = url.trim();

        if (sanitized.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("URL too long");
        }

        if (!URL_PATTERN.matcher(sanitized).matches()) {
            throw new IllegalArgumentException("Invalid URL format");
        }

        return sanitized;
    }

    public String sanitizeInput(final String input) {
        if (input == null) {
            return null;
        }

        final String sanitized = input.replaceAll("[\u0000-\u001F\u007F-\u009F]", "");

        if (SQL_INJECTION_PATTERN.matcher(sanitized).matches()) {
            log.warn("Potential SQL injection attempt detected: {}", getAnonymizedInput(input));
            throw new IllegalArgumentException("Invalid input detected");
        }

        if (XSS_PATTERN.matcher(sanitized).matches()) {
            log.warn("Potential XSS attempt detected: {}", getAnonymizedInput(input));
            throw new IllegalArgumentException("Invalid input detected");
        }

        return sanitized;
    }

    public String validateEventName(final String eventName) {
        if (eventName == null || eventName.trim().isEmpty()) {
            return null;
        }

        final String sanitized = sanitizeInput(eventName.trim());

        if (sanitized.length() > MAX_EVENT_NAME_LENGTH) {
            throw new IllegalArgumentException("Event name too long");
        }

        return sanitized;
    }

    public String validateMatchPageUrl(final String matchPageUrl) {
        if (matchPageUrl == null || matchPageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Match page URL is required");
        }

        final String validated = validateUrl(matchPageUrl);

        if (!validated.contains("vlr.gg")) {
            log.warn("Non-VLR.gg match page URL: {}", validated);
        }

        return validated;
    }

    public String validateNumericString(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return "0";
        }

        final String sanitized = value.trim();

        if (!sanitized.matches("^[0-9\\-]{1,10}$")) {
            throw new IllegalArgumentException("Invalid numeric format");
        }

        return sanitized;
    }

    public String validateCountryCode(final String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            return null;
        }

        final String sanitized = sanitizeInput(countryCode.trim().toLowerCase());

        if (!sanitized.matches("^(flag_)?[a-z]{2,10}$")) {
            log.warn("Invalid country code format: {}", countryCode);
            return null;
        }

        return sanitized;
    }

    private String getAnonymizedInput(final String input) {
        if (input == null || input.length() <= 3) {
            return "***";
        }

        return input.substring(0, 3) + "***";
    }

    public String validateTextInput(final String input, int maxLength) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        final String sanitized = sanitizeInput(input.trim());

        if (sanitized.length() > maxLength) {
            throw new IllegalArgumentException("Input too long (max " + maxLength + " characters)");
        }

        return sanitized;
    }

    public String validateSearchQuery(final String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be empty");
        }

        String sanitized = sanitizeInput(query.trim());

        if (sanitized.length() > MAX_SEARCH_QUERY_LENGTH) {
            throw new IllegalArgumentException("Search query too long");
        }

        sanitized = sanitized.replaceAll("[+\\-*~\"()\\[\\]]", " ");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();

        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Invalid search query");
        }

        return sanitized;
    }
}