CREATE TABLE subscriptions (
    id VARCHAR(32) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL,
    event_keys TEXT[] NOT NULL,
    url TEXT NOT NULL,
    description TEXT,
    event_signature_key VARCHAR(64),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX subscriptions_client_id_active ON subscriptions(client_id, active);

CREATE TABLE notification_events (
    event_id VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    subscription_id VARCHAR(32) REFERENCES subscriptions(id),
    cycle INT NOT NULL DEFAULT 0,
    delivered_at TIMESTAMPTZ
);

CREATE INDEX notification_events_client_status_created ON notification_events(client_id, status, created_at DESC, event_id DESC);

CREATE INDEX notification_events_client_created ON notification_events(client_id, created_at DESC, event_id DESC);

CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL REFERENCES notification_events(event_id),
    cycle INT NOT NULL,
    attempt_number INT NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claimed_at TIMESTAMPTZ,
    claimed_by VARCHAR(64),
    executed_at TIMESTAMPTZ,
    response_status INT,
    failure_reason TEXT,
    latency_ms BIGINT,
    origin VARCHAR(16) NOT NULL
);

CREATE INDEX delivery_attempts_next_attempt_at ON delivery_attempts(next_attempt_at) WHERE executed_at IS NULL;

CREATE INDEX delivery_attempts_event_cycle_attempt ON delivery_attempts(event_id, cycle, attempt_number);
