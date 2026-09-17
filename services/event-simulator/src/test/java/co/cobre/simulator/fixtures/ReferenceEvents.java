package co.cobre.simulator.fixtures;

import co.cobre.simulator.ReferenceEvent;

import java.time.Instant;
import java.util.List;

public final class ReferenceEvents {

    private static final Instant DELIVERY_2024_03_15 = Instant.parse("2024-03-15T09:30:22Z");

    public static ReferenceEvent evt001() {
        return new ReferenceEvent(
            "EVT001",
            "credit_card_payment",
            "CLIENT001",
            "Credit card payment received for $150.00",
            DELIVERY_2024_03_15
        );
    }

    public static ReferenceEvent evt002() {
        return new ReferenceEvent(
            "EVT002",
            "debit_card_withdrawal",
            "CLIENT001",
            "ATM withdrawal of $200.00",
            Instant.parse("2024-03-15T10:15:45Z")
        );
    }

    public static ReferenceEvent evt003() {
        return new ReferenceEvent(
            "EVT003",
            "credit_transfer",
            "CLIENT002",
            "Bank transfer received from Account #4567 for $1,500.00",
            Instant.parse("2024-03-15T11:20:18Z")
        );
    }

    public static ReferenceEvent evt004() {
        return new ReferenceEvent(
            "EVT004",
            "debit_automatic_payment",
            "CLIENT002",
            "Monthly utility bill payment of $85.50",
            Instant.parse("2024-03-15T12:05:33Z")
        );
    }

    public static ReferenceEvent evt005() {
        return new ReferenceEvent(
            "EVT005",
            "credit_refund",
            "CLIENT003",
            "Refund processed for order #789 for $45.99",
            Instant.parse("2024-03-15T13:45:10Z")
        );
    }

    public static ReferenceEvent evt006() {
        return new ReferenceEvent(
            "EVT006",
            "debit_transfer",
            "CLIENT003",
            "Money transfer sent to Account #8901 for $500.00",
            Instant.parse("2024-03-15T14:30:55Z")
        );
    }

    public static ReferenceEvent evt007() {
        return new ReferenceEvent(
            "EVT007",
            "credit_deposit",
            "CLIENT001",
            "Direct deposit received from Employer XYZ for $2,500.00",
            Instant.parse("2024-03-15T15:20:40Z")
        );
    }

    public static ReferenceEvent evt008() {
        return new ReferenceEvent(
            "EVT008",
            "debit_purchase",
            "CLIENT002",
            "Point of sale purchase at Store ABC for $75.25",
            Instant.parse("2024-03-15T16:10:15Z")
        );
    }

    public static ReferenceEvent evt009() {
        return new ReferenceEvent(
            "EVT009",
            "credit_cashback",
            "CLIENT003",
            "Cashback reward credited for $25.00",
            Instant.parse("2024-03-15T17:25:30Z")
        );
    }

    public static ReferenceEvent evt010() {
        return new ReferenceEvent(
            "EVT010",
            "debit_subscription",
            "CLIENT001",
            "Monthly streaming service payment of $14.99",
            Instant.parse("2024-03-15T18:05:12Z")
        );
    }

    public static List<ReferenceEvent> all() {
        return List.of(evt001(), evt002(), evt003(), evt004(), evt005(),
                       evt006(), evt007(), evt008(), evt009(), evt010());
    }

    private ReferenceEvents() {
    }
}
