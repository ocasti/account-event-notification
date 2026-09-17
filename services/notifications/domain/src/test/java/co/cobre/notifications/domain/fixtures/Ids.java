package co.cobre.notifications.domain.fixtures;

import co.cobre.notifications.domain.ClientId;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;

/**
 * Identifiers reused across fixtures and tests, taken from the reference dataset in
 * {@code docs/notification_events.json}.
 */
public final class Ids {

    public static final ClientId CLIENT_001 = new ClientId("CLIENT001");
    public static final ClientId CLIENT_002 = new ClientId("CLIENT002");
    public static final ClientId CLIENT_003 = new ClientId("CLIENT003");

    public static final EventId EVT_001 = new EventId("EVT001");
    public static final EventId EVT_003 = new EventId("EVT003");

    public static final EventKey CREDIT_CARD_PAYMENT = new EventKey("credit_card_payment");
    public static final EventKey CREDIT_TRANSFER = new EventKey("credit_transfer");

    public static final String WORKER_1 = "worker-1";

    private Ids() {
    }
}
