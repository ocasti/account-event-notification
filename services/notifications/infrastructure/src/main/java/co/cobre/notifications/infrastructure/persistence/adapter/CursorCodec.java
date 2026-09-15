package co.cobre.notifications.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.Base64;

final class CursorCodec {

    static String encode(Instant createdAt, String eventId) {
        long epochMillis = createdAt.toEpochMilli();
        String decoded = epochMillis + "|" + eventId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(decoded.getBytes());
    }

    static Cursor decode(String encoded) {
        byte[] decoded = Base64.getUrlDecoder().decode(encoded);
        String decodedStr = new String(decoded);
        String[] parts = decodedStr.split("\\|");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid cursor format");
        }
        long epochMillis = Long.parseLong(parts[0]);
        String eventId = parts[1];
        return new Cursor(Instant.ofEpochMilli(epochMillis), eventId);
    }

    record Cursor(Instant createdAt, String eventId) {}

    private CursorCodec() {
    }
}
