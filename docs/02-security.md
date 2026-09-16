# Security analysis: OWASP API Security Top 10

Vulnerabilities from the OWASP API Security Top 10 (2023) that affect the API and worker defined in `01-system-design.html` (the RFC), with their equivalent in the OWASP Top 10 (2021), the mitigation adopted, the actual file that implements it, and its planned evolution.

## 1. Exposure context

The API is consumed publicly over the internet: three REST endpoints (`GET /notification_events`, `GET /notification_events/{id}`, `POST /notification_events/{id}/replay`) in `rest/NotificationEventController.java`, protected with Bearer JWT by `security/SecurityConfig.java` (RFC §3, §16). In AWS it enters through an ALB with WAF (RFC §10).

The worker makes an HTTPS POST to the URL each client defined in its subscription (RFC §1, §16). It is both a target (financial data of several clients in a single database) and a possible vector, because it makes outbound requests to third-party destinations. The worker does not receive inbound traffic; it pulls from the queue (RFC §1).

## 2. Vulnerabilities

### 2.1 API1:2023 Broken Object Level Authorization (A01:2021 Broken Access Control)

**How it would affect the system.** Identifiers are readable and sequential (`EVT005`). `CLIENT002`, with a valid token, requests `GET /notification_events/EVT005`, which belongs to `CLIENT003`, or triggers `POST .../EVT005/replay` and causes a delivery to a webhook that is not its own.

**Mitigation adopted.** `client_id` never travels as a parameter: `AuthenticatedClientArgumentResolver` (`security/AuthenticatedClientArgumentResolver.java`) resolves any controller parameter annotated `@AuthenticatedClient` by taking the `Jwt` from the `JwtAuthenticationToken` in the security context and delegating to `ClientIdResolver` (`security/ClientIdResolver.java`), which reads the configurable claim (`JWT_CLIENT_CLAIM`, `sub` by default). The two use cases that expose an event to a client (`GetNotificationEvent`, `ReplayNotificationEvent`) call `NotificationEventRepository.findByClientAndId(clientId, eventId)` (`application/port/NotificationEventRepository.java`); an event that does not belong to the client returns 404, not 403, via `ApiExceptionHandler.handleNotFound` (RFC §7). The replay also conditions the `UPDATE` on `client_id` and status `FAILED` (RFC §8). The port also declares a `findById(EventId)` with no client filter, but only `ProcessDueDeliveries` (the worker, to process an attempt already claimed internally) uses it; no controller invokes it. Verified with an API test using two tokens (RFC §19).

**Evolution.** None pending; the guarantee is structural.

### 2.2 API2:2023 Broken Authentication (A07:2021 Identification and Authentication Failures)

**How it would affect the system.** A token without expiration or with an unverified signature would allow impersonating any client; a token accepted in the query string ends up in logs and proxies.

**Mitigation adopted.** `security/SecurityConfig.java` configures an OAuth2 resource server: `NimbusJwtDecoder.withPublicKey(...)` validates the RS256 signature with a public key loaded from a file (`JwtProperties.publicKey`, a `file:` type `Resource`, path in `JWT_PUBLIC_KEY_PATH`), and a `JwtClaimValidator` for audience is added on top of `JwtValidators.createDefault()` (which already validates expiration) to require `aud = JWT_AUDIENCE`. `authorizeHttpRequests` allows only `/actuator/health/**` and `/actuator/prometheus` without authentication; everything else requires `oauth2ResourceServer().jwt(...)`, which only accepts the token in the `Authorization` header (Spring Security does not read a JWT from the query string). An invalid or missing token returns 401 with no detail (the resource server's default behavior). The private key never lives in the API or in the repository (`deploy/local/keys/` is git-ignored in `.gitignore`); in AWS the public key lives in SSM (RFC §7, §10, §16). The client claim name and the audience are configurable (`JwtProperties.clientClaim`, `JwtProperties.audience`) because the claims of Cobre's real token are not documented. Token issuance is out of scope because it already exists in the platform (RFC §15).

Note on `/actuator/prometheus`: it stays public on purpose (Prometheus needs to scrape it without credentials), and `management.endpoints.web.exposure.include` in `application.yaml` only exposes `health`, `prometheus`, and `info` — not `env`, `beans`, or `heapdump`. There is no separate management port configured (`management.server.port` is not set): isolation of these two endpoints is enforced at the `SecurityConfig` rule level, not at the network level.

**Evolution.** Client claim and audience are configurable because the claims of the real token are not documented; validation against the issuer's JWKS is verified during integration (RFC §16, §18).

### 2.3 API4:2023 Unrestricted Resource Consumption (A01:2021, per the RFC's mapping)

**How it would affect the system.** A client could request pages of a million rows, publish a webhook that holds every connection open until the timeout to exhaust the workers, or chain replays on the same event.

**Mitigation adopted.** `rest/ListRequest.java` bounds `limit` with `@Min(1)` and `@Max(100)` (400 if exceeded), with 20 as the default value, and paginates by keyset (`cursor`); the 400 is produced by `ApiExceptionHandler.handleMethodArgumentNotValid`/`handleConstraintViolation`. `webhook/WebhookProperties` sets `connect-timeout` at 3 s and `read-timeout` at 5 s, used by `WebhookClientConfig` to build the `HttpClient`. Backoff has a ceiling (`RetryProperties.maxDelay`, 15 min by default) and a maximum number of attempts (`RetryProperties.maxAttempts`, 5). `DeliveryAttemptRepositoryAdapter.claimDue` applies `max-per-client` within each claimed batch. Replay is only accepted from `FAILED`, with a conditional `UPDATE` (RFC §5, §8, §14, §16). The POST runs outside the claim transaction so it does not exhaust the pool (RFC §13). Per-client rate limiting lives on the ALB with WAF in AWS; it does not apply locally (RFC §10, §16).

**Evolution.** Application-level rate limiting, per-subscription circuit breaker, honoring `Retry-After` on 429, and adaptive batches (RFC §14, §18).

### 2.4 API7:2023 Server-Side Request Forgery (A10:2021 Server-Side Request Forgery)

**How it would affect the system.** A subscription whose URL points to `169.254.169.254` or to an internal host turns the worker into a signed proxy into Cobre's network. With DNS rebinding, the name resolves to a public IP when validated and to a private one when connecting.

**Mitigation adopted.** `webhook/WebhookUrlValidator.java` validates before every send (called from `HttpWebhookSender.send`): if `requireHttps` is `true` (the value in `application.yaml`, non-local profile), any `http` scheme is rejected outright, whether or not the host is in the allowlist; if `requireHttps` is `false` (`local` profile, `application-local.yaml`), `http` is allowed only if the host is in `WEBHOOK_ALLOWLIST`. DNS resolution uses `InetAddress.getAllByName(host)` and takes the first address; site-local, loopback, link-local, "any local", multicast, and IPv6 unique local addresses (`fc00::/7`) are rejected — **unless the host is in the allowlist**, in which case none of those range checks apply (the allowlist is a total exception for that host, not only for the scheme). `webhook/WebhookClientConfig.java` builds the Apache HttpClient 5 `HttpClient` with `disableRedirectHandling()`, `disableAutomaticRetries()`, the connect/read timeouts from `WebhookProperties`, and a custom `DnsResolver`: `webhook/PinnedDnsResolver.java`, registered via `setDnsResolver(dnsResolver)` on the `PoolingHttpClientConnectionManagerBuilder`. `PinnedDnsResolver.resolve(host)` delegates to `WebhookUrlValidator.validateHost(host)`, so the address that opens the TCP connection is exactly the one the validator just resolved and checked against the restricted ranges, with no independent DNS resolution at connect time. There is no window between validating and connecting where the name could resolve to a different IP: DNS rebinding is closed off. In AWS, an egress security group only allows outbound internet traffic, and IMDSv2 with a one-hop limit; the NAT Gateway is not a mitigation. Subscriptions are loaded only via migration (`V2__initial_subscriptions.sql`), with no CRUD, which reduces the attack surface (RFC §16).

**Evolution.** With subscription CRUD, this validation will be the first line of defense; URL verification with a challenge is out of scope (RFC §11, §16).

### 2.5 API8:2023 Security Misconfiguration (A05:2021 Security Misconfiguration)

**How it would affect the system.** A public Actuator could expose `/actuator/env` with credentials; a stack trace could reveal JPA entities and versions; Swagger in production would document the attack surface.

**Mitigation adopted.** `rest/ApiExceptionHandler.java` maps every domain exception to an `ErrorResponse{code, message}` with fixed text (for example `"Invalid parameter"` for `IllegalArgumentException`, without `ex.getMessage()`); no handler returns the original exception's message or stack trace. `management.endpoints.web.exposure.include` limits Actuator to `health`, `prometheus`, `info` (`application.yaml`); there is no `env`, `beans`, `heapdump`, or a separate management port (see §2.2). Containers run as a non-root user: both `docker/notifications.Dockerfile` and `docker/simulator.Dockerfile` create an `app` user (`addgroup -S app && adduser -S -G app app`) and declare `USER app` before the `ENTRYPOINT`. Dependencies are managed by the Spring Boot BOM (`spring-boot-starter-parent` in `services/notifications/pom.xml`). The Spring profile turns off beans: the SQS listener and the scheduler carry `@Profile("worker")`, so in the `api` profile there is no connection to the queue (RFC §12). `.env.example` is versioned, `.env` is git-ignored (`.gitignore`).

**Note on Swagger.** `application.yaml` sets `springdoc.api-docs.enabled: false` and `springdoc.swagger-ui.enabled: false`, so in the default profiles (`api`, `worker`) Swagger UI and `/v3/api-docs` are off. `application-local.yaml` overrides both properties to `true` only for the `local` profile. No Swagger route is in `SecurityConfig`'s `permitAll` list, so when it is enabled in `local` it still requires the same Bearer JWT as the rest of the API: it is not public even there.

**Evolution.** Distroless and multi-architecture images are out of scope (RFC §12).

## 3. Summary table

| API Security 2023 | Top 10 2021 | Mitigation adopted | File(s) | Evolution |
|---|---|---|---|---|
| API1 BOLA | A01 | `client_id` from the JWT via a dedicated resolver; repository filters by client; 404 | `AuthenticatedClientArgumentResolver`, `ClientIdResolver`, `NotificationEventRepository`, `ApiExceptionHandler` | None |
| API2 Broken Authentication | A07 | RS256 with a file-based public key, configurable `aud`, header-only `Authorization`; actuator limited to `health`/`prometheus`/`info`, no separate port | `SecurityConfig`, `JwtProperties`, `application.yaml` | Real claims, JWKS |
| API4 Resource Consumption | A01 (RFC) | `limit` 1–100, keyset, 3 s/5 s timeouts, capped backoff, `max-per-client` | `ListRequest`, `WebhookProperties`, `RetryProperties`, `DeliveryAttemptRepositoryAdapter` | App-level rate limiting, circuit breaker |
| API7 SSRF | A10 | HTTPS unless allowlisted, private ranges rejected unless allowlisted, no redirects, no HTTP client retries, `PinnedDnsResolver` pins the connection to the validated IP | `WebhookUrlValidator`, `WebhookClientConfig`, `PinnedDnsResolver` | Subscription CRUD |
| API8 Misconfiguration | A05 | Fixed error messages, scoped actuator, non-root, Spring Boot BOM; Swagger only on local profile, authenticated | `ApiExceptionHandler`, `application.yaml`, `application-local.yaml`, `docker/*.Dockerfile`, `pom.xml`, `SecurityConfig` | Distroless |

## 4. Cross-cutting measures present in the design

- **TLS by default toward webhooks.** `WebhookUrlValidator` requires HTTPS unless the host is in `WEBHOOK_ALLOWLIST` with `requireHttps=false` (`local` profile only) (RFC §9, §16). Covers A02:2021 Cryptographic Failures.
- **HMAC signature on the webhook.** `WebhookSigner` computes `event-signature = HMAC-SHA256(key, event-timestamp + "." + body)`; the 5-minute tolerance window on the timestamp is documented for the receiver. The key is never returned in full by the API; encryption at rest and rotation are future work (RFC §16). Full contract (headers, body, verification, retries, replay): [`docs/api/webhook-contract.md`](api/webhook-contract.md).
- **Secrets kept out of the repository.** `.env` and `deploy/local/keys/*.pem` are git-ignored (`.gitignore`); in AWS, the public key and signing keys live in SSM (RFC §10, §12).
- **Non-root containers.** `docker/notifications.Dockerfile` and `docker/simulator.Dockerfile`: final image `eclipse-temurin:21-jre-alpine` with a non-root `app` user (RFC §12).
- **Dependencies managed by the Spring Boot BOM** (`spring-boot-starter-parent`, RFC §16).
- **Logs.** Today the only logging of its own is `DeliveryScheduler` recording scheduler errors with SLF4J; the structured JSON line per attempt (`event_id`, `client_id`, `attempt_number`, `response_status`, `correlation_id`) described in RFC sections 10 and 16 does not exist yet. **This remains near-term future work**, not a mitigation already in place.
- **No injection.** Spring Data JPA with bound parameters (derived queries and parameterized `@Query` in `DeliveryAttemptJpaRepository`, `NotificationEventJpaRepository`) and input validation with Bean Validation (`ListRequest`) cover A03:2021 Injection (RFC §7, §16).

## 5. References

- OWASP API Security Top 10 (2023): <https://owasp.org/API-Security/editions/2023/en/0x11-t10/>
- OWASP Top 10 (2021): <https://owasp.org/Top10/>
