# Webhook contract

This is the contract of the outbound `POST` `notifications-worker` sends to each client's
receiver: one HTTP call per delivery attempt, for events the client subscribed to. It does not
cover the queue that feeds the worker (`docs/api/asyncapi.yaml`) or the self-service REST API
(`docs/api/openapi.json`).

Implementation: `services/notifications/infrastructure/src/main/java/co/cobre/notifications/infrastructure/webhook/`
(`HttpWebhookSender`, `WebhookPayloadMapper`, `WebhookSigner`, `WebhookClientConfig`,
`WebhookUrlValidator`). Design rationale: `docs/01-system-design.html` (the RFC, in Spanish),
sections on delivery and on SSRF; security analysis: `docs/02-security.md`.

## Request

**Method and URL.** `POST` to the `url` configured on the client's subscription
(`Subscription.url`, `WebhookUrl` value object). The URL must be `https://` unless its host is in
`notifications.webhook.allowlist` — in production `requireHttps: true` rejects `http://`
unconditionally, regardless of the allowlist; only the local profile
(`application-local.yaml`, `requireHttps: false`, allowlist `wiremock` by default) permits
`http://`, and only for allowlisted hosts (`WebhookUrlValidator.validate`).

**Headers**, one real example (values line up with the body example below):

```
POST /webhooks/cobre HTTP/1.1
Host: merchant.example.com
Content-Type: application/json
x-cobre-event-id: EVT001
x-cobre-attempt: 1
event-timestamp: 2024-03-15T09:30:22.418601Z
event-signature: aec47d71ede011e6d51ca2aee9422406e25018ffadd8c9159c17902d7bf82192
```

- `x-cobre-event-id` — the event's `event_id`, same value as `id` in the body. Stable across every
  attempt and every replay of the same event.
- `x-cobre-attempt` — the 1-based attempt number *within the current delivery cycle*
  (`DeliveryAttempt.attemptNumber`). Starts at 1 on the first attempt and increments by 1 on each
  system retry; a replay opens a new cycle and this header starts at 1 again (see "Replay" below).
- `event-timestamp` / `event-signature` — present **only if the subscription has a signing key**
  (`Subscription.signatureKey`, optional). If it does not, the request carries no signature
  headers at all; `HttpWebhookSender.send` only calls `WebhookSigner.sign` and sets these two
  headers inside `if (subscription.signatureKey().isPresent())`. `event-timestamp` is the
  instant the worker computed the signature at (`Clock.instant().toString()`, an ISO-8601 instant
  with microsecond precision here — precision is whatever `Instant.toString()` produces for that
  instant, not a fixed format); `event-signature` is described below.

## Body

JSON, `Content-Type: application/json`. Shape (`WebhookPayload`, field order as declared in the
record, Jackson 3 default field ordering):

```json
{"id":"EVT001","event_key":"credit_card_payment","client_id":"CLIENT001","created_at":null,"content":"Credit card payment received for $150.00"}
```

Pretty-printed for readability (the wire body is compact, no extra whitespace):

```json
{
  "id": "EVT001",
  "event_key": "credit_card_payment",
  "client_id": "CLIENT001",
  "created_at": null,
  "content": "Credit card payment received for $150.00"
}
```

| Field | From | Notes |
|---|---|---|
| `id` | `NotificationEvent.eventId` | Same value as the `x-cobre-event-id` header and as the queue message's `event_id`. Idempotency key for the receiver. |
| `event_key` | `NotificationEvent.eventKey` | Same value as the queue message's `event_type`. |
| `client_id` | `NotificationEvent.clientId` | The receiving client's id — useful if one endpoint serves more than one client. |
| `created_at` | `NotificationEvent.createdAt` | This is the queue message's `occurred_at` (when the event happened at the source), **not** when the worker sent this HTTP request. There is no "sent at" field in the body; use `event-timestamp` for that if the subscription is signed. |
| `content` | `NotificationEvent.content` | Free-form text, forwarded unchanged from the queue message. |

The body is byte-for-byte identical on every attempt and on every replay of the same event: it is
computed once per delivery cycle from data that never changes after registration.

The signature above is real: HMAC-SHA256 with the local demo key of CLIENT001 (`local-signing-key-client001`, from `V2__initial_subscriptions.sql`) over `event-timestamp + "." + body`, the body being the compact form shown first. Re-computing it is the quickest way to validate a receiver implementation.

## Verifying the signature

Pseudocode:

```
message   = event-timestamp header + "." + raw request body bytes
expected  = hex(HMAC-SHA256(subscription_signing_key, message))
accept if constant-time-equal(expected, event-signature header)
        and abs(now - parse(event-timestamp)) <= tolerance
```

Python (uses the raw bytes of the body, not a re-serialized/re-parsed version — re-serializing can
reorder keys or change whitespace and break the signature):

```python
import hmac, hashlib

def verify_signature(secret, timestamp, body, signature):
    data = timestamp.encode() + b"." + body
    expected = hmac.new(secret.encode(), data, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature)
```

`secret` is the subscription's signing key (out of band, never sent on the wire); `body` is the
exact bytes received (`request.body`, not `json.dumps(request.json())`); `signature` is the
`event-signature` header value, already lowercase hex.

**Clock tolerance.** `notifications.webhook.timestamp-tolerance` is `5m` (`application.yaml`, not
overridden for `local`) — that is what this service documents as the recommended tolerance for a
receiver comparing `event-timestamp` against its own clock: accept the timestamp only if it is
within 5 minutes of "now", to limit the replay window of a captured request while tolerating
reasonable clock drift between the worker and the receiver. The worker itself does not enforce
this window on anything it sends or receives — it is a receiver-side check, and 5 minutes is the
number this repository recommends and treats as configuration (`WebhookProperties.timestampTolerance`).

There is no `Idempotency-Key` header and no request-signing nonce beyond the timestamp; the RFC
explicitly scopes `Idempotency-Key` out (`docs/01-system-design.html`, evolution). Dedupe on `id`
instead (see "Requirements for the receiver" below).

## Response handling

The worker does not read the response body — only the status code, and only up to
`notifications.webhook.read-timeout` (see "Timeouts" below). `HttpWebhookSender.send` classifies it:

| Status | Outcome | What happens next |
|---|---|---|
| `200`–`299` | `Success` | Event marked `COMPLETED`. No further attempts, ever (short of a replay). |
| `500`–`599`, `408`, `429` | `TransientFailure` | Counted as a retryable failure (see "Retry policy"). |
| `400`–`499` other than `408`/`429` | `PermanentFailure` | Event marked `FAILED` immediately — no retries, regardless of attempts remaining. |
| Connection error, DNS failure, connect/read timeout (`ResourceAccessException`/`IOException`) | `TransientFailure` (no status code) | Same as a 5xx: counted as a retryable failure. |
| Any other status code (unexpected range) | `TransientFailure` | Treated conservatively as retryable. |

A `TransientFailure` schedules the next attempt unless attempts are exhausted (`max-attempts`,
see below), in which case the event is marked `FAILED` too. `URL` validation failures (a
subscription URL that no longer resolves to an allowed address, for instance) are treated as an
immediate `PermanentFailure` with no HTTP call at all.

## Retry policy

Exponential backoff with jitter, computed by `RetryPolicy.delayBefore`
(`co.cobre.notifications.domain.RetryPolicy`): for attempt *n* (n ≥ 2), nominal delay is
`base-delay * factor^(n-2)`, capped at `max-delay`, then jittered by `± jitter-ratio` and clamped
to `max-delay` again (so a capped attempt's jitter can only ever pull it *down* from the cap, never
above it). Attempt 1 always fires immediately (delay 0). Configuration:
`notifications.retry.*` (`RetryProperties`, bound by `RetryConfig`).

**Production** (`application.yaml`: `base-delay: 30s`, `factor: 4`, `max-delay: 15m`,
`jitter-ratio: 0.2`, `max-attempts: 5`):

| Attempt | Nominal delay | With ±20% jitter | If this attempt also fails |
|---|---|---|---|
| 1 | 0 s (immediate) | — | RETRYING |
| 2 | 30 s | 24 – 36 s | RETRYING |
| 3 | 2 min | 96 – 144 s | RETRYING |
| 4 | 8 min | 6.4 – 9.6 min | RETRYING |
| 5 | 15 min (capped) | 12 – 15 min | FAILED |

About 26 minutes elapse in total from the first attempt to `FAILED` if every attempt fails.

**Local** (`application-local.yaml`: `base-delay: 2s`, `factor: 4`, `max-delay: 10s`, same
`jitter-ratio: 0.2` and `max-attempts: 5`, overridable via `RETRY_BASE_DELAY`/`RETRY_MAX_DELAY`):

| Attempt | Nominal delay | With ±20% jitter | If this attempt also fails |
|---|---|---|---|
| 1 | 0 s (immediate) | — | RETRYING |
| 2 | 2 s | 1.6 – 2.4 s | RETRYING |
| 3 | 8 s | 6.4 – 9.6 s | RETRYING |
| 4 | 32 s → capped to 10 s | 8 – 10 s | RETRYING |
| 5 | 32 s → capped to 10 s | 8 – 10 s | FAILED |

About 30 seconds elapse in total, nominally, from the first attempt to `FAILED`.

## Replay

`POST /notification_events/{event_id}/replay` (self-service API, JWT-authenticated, documented in
`docs/api/openapi.json`) is only accepted while the event's status is `FAILED`
(`ReplayNotificationEvent.replay`; any other status responds `409 Conflict`). A successful replay:

- Increments the event's internal `cycle` counter and resets status to `PENDING`
  (`NotificationEvent.replay`).
- Creates a brand-new `DeliveryAttempt` at `attemptNumber = 1` for that cycle
  (`DeliveryAttempt.first(eventId, event.cycle(), now, AttemptOrigin.REPLAY)`).
- Sends the **same** `id` and the **same** body as the original delivery — nothing about the
  event's data changes on replay, only that a new attempt cycle starts.

So `x-cobre-attempt` restarts at `1` on a replay's first HTTP call, exactly like it did on the
original cycle's first call. **There is no header or body field that names the cycle** — the
worker does not send `cycle` on the wire. A receiver cannot distinguish "cycle 1, attempt 1" from
"cycle 2 (replay), attempt 1" by inspecting a single request; both look identical except for
timing. This is fine as long as the receiver's idempotency check is keyed on `id` alone (see next
section): whether a given `id` arrives once, is retried a few times, or is replayed weeks later,
the receiver processes the underlying event once. If a receiver genuinely needs to tell cycles
apart (for example, to alert when the same event is being redelivered days after it first
`FAILED`), the only signal available is elapsed time between requests with the same `id`, or
correlating against the `cycle` field on each delivery attempt returned by
`GET /notification_events/{notification_event_id}` in the self-service API.

## Timeouts

`notifications.webhook.connect-timeout` and `-read-timeout` configure the Apache HttpClient 5
instance the worker sends with (`WebhookClientConfig`, `HttpComponentsClientHttpRequestFactory`):
**3 seconds** to connect, **5 seconds** to read a response, in every profile (not overridden
locally). The client follows no redirects (`disableRedirectHandling()`) and performs no retries of
its own (`disableAutomaticRetries()`) — every retry the receiver sees is a distinct HTTP request
decided by `RetryPolicy` in the domain layer, not by the HTTP client.

## Requirements for the receiver

- **Respond `2xx` quickly, within the worker's read timeout (5 seconds in every profile).**
  Anything slower risks a `TransientFailure` from a timeout even if the receiver would eventually
  have succeeded, which burns a retry attempt for no reason.
- **Do the actual processing after responding**, not before — acknowledge receipt first
  (`2xx`), then process asynchronously. The worker does not care what the receiver does after it
  returns the status code; it has already stopped watching by then (no body is read on success).
- **Be idempotent on `id`.** The same `id` can legitimately arrive more than once: an
  at-least-once retry after a timeout where the first attempt actually succeeded server-side, or a
  replay of a `FAILED` event. Track processed `id`s and skip reprocessing a repeat, regardless of
  which attempt or cycle it arrived on.
- **Reject or ignore anything that fails signature verification** (when the subscription is
  signed) rather than processing it — this is what makes the `event-signature` header meaningful.
- **Do not rely on delivery order** — nothing upstream of the receiver (the queue, the retry
  scheduler, or concurrent workers) guarantees that events arrive in the order they occurred; use
  `created_at` in the body if ordering matters to the receiver's own logic.
