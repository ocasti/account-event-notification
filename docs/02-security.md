# Análisis de seguridad: OWASP API Security Top 10

Vulnerabilidades del OWASP API Security Top 10 (2023) que afectan a la API y al worker definidos en `01-system-design.html` (el RFC), con su equivalente en el OWASP Top 10 (2021), la mitigación adoptada, el archivo real que la implementa y su evolución.

## 1. Contexto de exposición

La API se consume públicamente por internet: tres endpoints REST (`GET /notification_events`, `GET /notification_events/{id}`, `POST /notification_events/{id}/replay`) en `rest/NotificationEventController.java`, protegidos con Bearer JWT por `security/SecurityConfig.java` (RFC §3, §16). En AWS entra por ALB con WAF (RFC §10).

El worker hace POST HTTPS a la URL que cada cliente definió en su suscripción (RFC §1, §16). Es a la vez objetivo (datos financieros de varios clientes en una misma base) y posible vector, porque hace peticiones salientes a destinos de terceros. El worker no recibe tráfico entrante; consume la cola por pull (RFC §1).

## 2. Vulnerabilidades

### 2.1 API1:2023 Broken Object Level Authorization (A01:2021 Broken Access Control)

**Cómo afectaría.** Los identificadores son legibles y secuenciales (`EVT005`). `CLIENT002`, con token válido, pide `GET /notification_events/EVT005`, que pertenece a `CLIENT003`, o dispara `POST .../EVT005/replay` y provoca una entrega al webhook ajeno.

**Mitigación adoptada.** El `client_id` nunca viaja como parámetro: `AuthenticatedClientArgumentResolver` (`security/AuthenticatedClientArgumentResolver.java`) resuelve cualquier parámetro de controller anotado `@AuthenticatedClient` tomando el `Jwt` del `JwtAuthenticationToken` en el contexto de seguridad y delegando en `ClientIdResolver` (`security/ClientIdResolver.java`), que lee el claim configurable (`JWT_CLIENT_CLAIM`, por defecto `sub`). Los dos casos de uso que exponen un evento a un cliente (`GetNotificationEvent`, `ReplayNotificationEvent`) llaman `NotificationEventRepository.findByClientAndId(clientId, eventId)` (`application/port/NotificationEventRepository.java`); lo ajeno responde 404, no 403, vía `ApiExceptionHandler.handleNotFound` (RFC §7). El replay condiciona además el `UPDATE` a `client_id` y estado `FAILED` (RFC §8). El puerto también declara un `findById(EventId)` sin filtro de cliente, pero solo lo usa `ProcessDueDeliveries` (el worker, para procesar un intento ya reclamado internamente); ningún controller lo invoca. Se verifica con un test de API con dos tokens (RFC §19).

**Evolución.** Ninguna pendiente; la garantía es estructural.

### 2.2 API2:2023 Broken Authentication (A07:2021 Identification and Authentication Failures)

**Cómo afectaría.** Un token sin expiración o con firma no verificada permite suplantar a cualquier cliente; un token aceptado en la query string queda en logs y proxies.

**Mitigación adoptada.** `security/SecurityConfig.java` configura un resource server OAuth2: `NimbusJwtDecoder.withPublicKey(...)` valida la firma RS256 con una clave pública cargada desde archivo (`JwtProperties.publicKey`, un `Resource` tipo `file:`, ruta en `JWT_PUBLIC_KEY_PATH`), y un `JwtClaimValidator` de audiencia se añade sobre `JwtValidators.createDefault()` (que ya valida expiración) para exigir `aud = JWT_AUDIENCE`. `authorizeHttpRequests` solo permite sin autenticación `/actuator/health/**` y `/actuator/prometheus`; todo lo demás exige `oauth2ResourceServer().jwt(...)`, que solo acepta el token en el header `Authorization` (Spring Security no lee JWT de la query string). Un token inválido o ausente responde 401 sin detalle (comportamiento por defecto del resource server). La clave privada nunca está en la API ni en el repositorio (`deploy/local/keys/` está ignorado por git en `.gitignore`); en AWS la clave pública vive en SSM (RFC §7, §10, §16). El nombre del claim de cliente y la audiencia son configurables (`JwtProperties.clientClaim`, `JwtProperties.audience`) porque los claims del token real de Cobre no están documentados. La emisión de tokens queda fuera del alcance porque ya existe en la plataforma (RFC §15).

Nota sobre `/actuator/prometheus`: queda público a propósito (necesario para que Prometheus lo scrapee sin credenciales) y `management.endpoints.web.exposure.include` en `application.yaml` solo expone `health`, `prometheus` e `info` — no `env`, `beans` ni `heapdump`. No hay un puerto de gestión separado configurado (`management.server.port` no está definido): el aislamiento de estos dos endpoints es a nivel de regla de `SecurityConfig`, no de red.

**Evolución.** Claim de cliente y audiencia son configurables porque los claims del token real no están documentados; la validación contra el JWKS del emisor se verifica al integrar (RFC §16, §18).

### 2.3 API4:2023 Unrestricted Resource Consumption (A01:2021, según el mapeo del RFC)

**Cómo afectaría.** Un cliente pide páginas de un millón de filas, publica un webhook que retiene cada conexión hasta el timeout para agotar los workers, o encadena replays sobre el mismo evento.

**Mitigación adoptada.** `rest/ListRequest.java` acota `limit` con `@Min(1)` y `@Max(100)` (400 si se excede), con 20 como valor por defecto, y pagina por keyset (`cursor`); el 400 lo produce `ApiExceptionHandler.handleMethodArgumentNotValid`/`handleConstraintViolation`. `webhook/WebhookProperties` fija `connect-timeout` en 3 s y `read-timeout` en 5 s, usados por `WebhookClientConfig` para construir el `HttpClient`. El backoff tiene tope (`RetryProperties.maxDelay`, 15 min por defecto) y máximo de intentos (`RetryProperties.maxAttempts`, 5). `DeliveryAttemptRepositoryAdapter.claimDue` aplica `max-per-client` dentro de cada lote reclamado. El replay solo se acepta desde `FAILED`, con `UPDATE` condicional (RFC §5, §8, §14, §16). El POST corre fuera de la transacción de reclamo para no agotar el pool (RFC §13). El rate limiting por cliente vive en el ALB con WAF en AWS; en local no aplica (RFC §10, §16).

**Evolución.** Rate limiting en la aplicación, circuit breaker por suscripción, honrar `Retry-After` en 429 y lotes adaptativos (RFC §14, §18).

### 2.4 API7:2023 Server-Side Request Forgery (A10:2021 Server-Side Request Forgery)

**Cómo afectaría.** Una suscripción cuya URL apunte a `169.254.169.254` o a un host interno convierte al worker en un proxy firmado hacia la red de Cobre. Con DNS rebinding, el nombre resuelve a una IP pública al validar y a una privada al conectar.

**Mitigación adoptada.** `webhook/WebhookUrlValidator.java` valida antes de cada envío (llamado desde `HttpWebhookSender.send`): si `requireHttps` es `true` (el valor de `application.yaml`, perfil no local), cualquier esquema `http` se rechaza sin excepción, esté o no el host en la allowlist; si `requireHttps` es `false` (perfil `local`, `application-local.yaml`), `http` se permite solo si el host está en `WEBHOOK_ALLOWLIST`. La resolución de DNS usa `InetAddress.getAllByName(host)` y toma la primera dirección; se rechazan direcciones site-local, loopback, link-local, "any local", multicast, y direcciones únicas locales IPv6 (`fc00::/7`) — **salvo que el host esté en la allowlist**, en cuyo caso ninguna de esas comprobaciones de rango se aplica (la allowlist es una excepción total para ese host, no solo para el esquema). `webhook/WebhookClientConfig.java` construye el `HttpClient` de Apache HttpClient 5 con `disableRedirectHandling()`, `disableAutomaticRetries()`, los timeouts de conexión/lectura de `WebhookProperties`, y un `DnsResolver` propio: `webhook/PinnedDnsResolver.java`, registrado con `setDnsResolver(dnsResolver)` sobre el `PoolingHttpClientConnectionManagerBuilder`. `PinnedDnsResolver.resolve(host)` delega en `WebhookUrlValidator.validateHost(host)`, así que la dirección que abre la conexión TCP es exactamente la que el validador acaba de resolver y comprobar contra los rangos restringidos, sin resolución de DNS independiente en el momento de conectar. No hay ventana entre validar y conectar en la que el nombre pueda resolver a otra IP: el DNS rebinding queda cerrado. En AWS, security group de egreso solo hacia internet e IMDSv2 con límite de un salto; el NAT Gateway no es mitigación. Las suscripciones se cargan solo por migración (`V2__initial_subscriptions.sql`), sin CRUD, lo que reduce la superficie (RFC §16).

**Evolución.** Con el CRUD de suscripciones, esta validación será la primera línea de defensa; la verificación de URL con challenge no está en alcance (RFC §11, §16).

### 2.5 API8:2023 Security Misconfiguration (A05:2021 Security Misconfiguration)

**Cómo afectaría.** Un Actuator público expone `/actuator/env` con credenciales; un stack trace revela entidades JPA y versiones; Swagger en producción documenta la superficie de ataque.

**Mitigación adoptada.** `rest/ApiExceptionHandler.java` mapea cada excepción de dominio a un `ErrorResponse{code, message}` con texto fijo (por ejemplo `"Invalid parameter"` para `IllegalArgumentException`, sin `ex.getMessage()`); ningún handler devuelve el mensaje ni el stack trace de la excepción original. `management.endpoints.web.exposure.include` limita Actuator a `health`, `prometheus`, `info` (`application.yaml`); no hay `env`, `beans`, `heapdump` ni un puerto de gestión separado (ver §2.2). Los contenedores corren como usuario no root: ambos `docker/notifications.Dockerfile` y `docker/simulator.Dockerfile` crean un usuario `app` (`addgroup -S app && adduser -S -G app app`) y declaran `USER app` antes del `ENTRYPOINT`. Dependencias gestionadas por el BOM de Spring Boot (`spring-boot-starter-parent` en `services/notifications/pom.xml`). El perfil de Spring apaga beans: listener SQS y scheduler llevan `@Profile("worker")`, así que en perfil `api` no hay conexión a la cola (RFC §12). `.env.example` versionado, `.env` ignorado (`.gitignore`).

**Corrección sobre Swagger.** `application.yaml` fija `springdoc.api-docs.enabled: false` y `springdoc.swagger-ui.enabled: false`, así que en los perfiles por defecto (`api`, `worker`) Swagger UI y `/v3/api-docs` están apagados. `application-local.yaml` sobrescribe ambas propiedades a `true` únicamente para el perfil `local`. Ninguna ruta de Swagger está en la lista `permitAll` de `SecurityConfig`, así que cuando está habilitada en `local` sigue exigiendo el mismo Bearer JWT que el resto de la API: no queda pública ni siquiera ahí.

**Evolución.** Imágenes distroless y multi-arquitectura quedan fuera del alcance (RFC §12).

## 3. Tabla resumen

| API Security 2023 | Top 10 2021 | Mitigación adoptada | Archivo(s) | Evolución |
|---|---|---|---|---|
| API1 BOLA | A01 | `client_id` del JWT vía resolver dedicado; repositorio filtra por cliente; 404 | `AuthenticatedClientArgumentResolver`, `ClientIdResolver`, `NotificationEventRepository`, `ApiExceptionHandler` | Ninguna |
| API2 Broken Authentication | A07 | RS256 con clave pública por archivo, `aud` configurable, solo header `Authorization`; actuator limitado a `health`/`prometheus`/`info`, sin puerto separado | `SecurityConfig`, `JwtProperties`, `application.yaml` | Claims reales, JWKS |
| API4 Resource Consumption | A01 (RFC) | `limit` 1–100, keyset, timeouts 3 s/5 s, backoff con tope, `max-per-client` | `ListRequest`, `WebhookProperties`, `RetryProperties`, `DeliveryAttemptRepositoryAdapter` | Rate limiting en app, circuit breaker |
| API7 SSRF | A10 | HTTPS salvo allowlist, rangos privados rechazados salvo allowlist, sin redirecciones, sin reintentos del cliente HTTP, `PinnedDnsResolver` fija la conexión a la IP validada | `WebhookUrlValidator`, `WebhookClientConfig`, `PinnedDnsResolver` | CRUD de suscripciones |
| API8 Misconfiguration | A05 | Errores con mensaje fijo, actuator acotado, sin root, BOM de Spring Boot; Swagger solo en perfil local, autenticado | `ApiExceptionHandler`, `application.yaml`, `application-local.yaml`, `docker/*.Dockerfile`, `pom.xml`, `SecurityConfig` | Distroless |

## 4. Medidas transversales presentes en el diseño

- **TLS por defecto hacia webhooks.** `WebhookUrlValidator` exige HTTPS salvo que el host esté en `WEBHOOK_ALLOWLIST` con `requireHttps=false` (solo perfil `local`) (RFC §9, §16). Cubre A02:2021 Cryptographic Failures.
- **Firma HMAC del webhook.** `WebhookSigner` calcula `event-signature = HMAC-SHA256(clave, event-timestamp + "." + cuerpo)`; la ventana de tolerancia de 5 minutos sobre el timestamp es documentada para el receptor. La clave nunca se devuelve completa por la API; cifrado en reposo y rotación son evoluciones (RFC §16).
- **Secretos fuera del repositorio.** `.env` y `deploy/local/keys/*.pem` ignorados por git (`.gitignore`); en AWS, clave pública y claves de firma en SSM (RFC §10, §12).
- **Contenedores sin root.** `docker/notifications.Dockerfile` y `docker/simulator.Dockerfile`: imagen final `eclipse-temurin:21-jre-alpine` con usuario `app` no root (RFC §12).
- **Dependencias gestionadas por el BOM de Spring Boot** (`spring-boot-starter-parent`, RFC §16).
- **Logs.** Hoy el único logging propio es `DeliveryScheduler` registrando errores del scheduler con SLF4J; no existe todavía la línea JSON estructurada por intento (`event_id`, `client_id`, `attempt_number`, `response_status`, `correlation_id`) que describen las secciones 10 y 16 del RFC. **Queda como evolución cercana**, no como mitigación ya implementada.
- **Sin inyección.** Spring Data JPA con parámetros enlazados (consultas derivadas y `@Query` parametrizadas en `DeliveryAttemptJpaRepository`, `NotificationEventJpaRepository`) y validación de entrada con Bean Validation (`ListRequest`) cubren A03:2021 Injection (RFC §7, §16).

## 5. Referencias

- OWASP API Security Top 10 (2023): <https://owasp.org/API-Security/editions/2023/en/0x11-t10/>
- OWASP Top 10 (2021): <https://owasp.org/Top10/>
