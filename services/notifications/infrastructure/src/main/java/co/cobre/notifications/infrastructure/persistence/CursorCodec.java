package co.cobre.notifications.infrastructure.persistence;

import java.time.Instant;
import java.util.Base64;

final class CursorCodec {

    static String encode(Instant createdAt, String eventId) {
        String decoded = createdAt.toString() + "|" + eventId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(decoded.getBytes());
    }

    static Cursor decode(String encoded) {
        byte[] decoded = Base64.getUrlDecoder().decode(encoded);
        var decodedStr = new String(decoded);
        int lastPipe = decodedStr.lastIndexOf('|');
        if (lastPipe <= 0) {
            throw new IllegalArgumentException("Invalid cursor format");
        }
        String instantStr = decodedStr.substring(0, lastPipe);
        String eventId = decodedStr.substring(lastPipe + 1);

        return new Cursor(Instant.parse(instantStr), eventId);
    }

    record Cursor(Instant createdAt, String eventId) {}

    private CursorCodec() {
    }
}
