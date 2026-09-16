package co.cobre.notifications.domain;

import java.util.regex.Pattern;

public record EventKey(String value) {
    private static final String WILDCARD = "*";
    private static final Pattern EVENT_KEY_PATTERN = Pattern.compile("^[a-z0-9_]+(\\.[a-z0-9_]+)*$");

    public EventKey {
        if (value == null) {
            throw new IllegalArgumentException("Event key cannot be null");
        }
        if (!WILDCARD.equals(value) && !EVENT_KEY_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Event key must be either '*' or match pattern ^[a-z0-9_]+(\\.[a-z0-9_]+)*$"
            );
        }
    }

    public boolean isWildcard() {
        return WILDCARD.equals(value);
    }

    public static EventKey wildcard() {
        return new EventKey(WILDCARD);
    }
}
