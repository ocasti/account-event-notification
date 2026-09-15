# Análisis de seguridad: OWASP API Security Top 10

Vulnerabilidades del OWASP API Security Top 10 (2023) que afectan a la API y al worker definidos en `01-system-design.html` (el RFC), con su equivalente en el OWASP Top 10 (2021), la mitigación adoptada y su evolución.

## 1. Contexto de exposición

La API se consume públicamente por internet: tres endpoints REST (`GET /notification_events`, `GET /notification_events/{id}`, `POST /notification_events/{id}/replay`) protegidos con Bearer JWT (RFC §3, §16). En AWS entra por ALB con WAF (RFC §10).

El worker hace POST HTTPS a la URL que cada cliente definió en su suscripción (RFC §1, §16). Es a la vez objetivo (datos financieros de varios clientes en una misma base) y posible vector, porque hace peticiones salientes a destinos de terceros. El worker no recibe tráfico entrante; consume la cola por pull (RFC §1).

## 2. Vulnerabilidades

### 2.1 API1:2023 Broken Object Level Authorization (A01:2021 Broken Access Control)

**Cómo afectaría.** Los identificadores son legibles y secuenciales (`EVT005`). `CLIENT002`, con token válido, pide `GET /notification_events/EVT005`, que pertenece a `CLIENT003`, o dispara `POST .../EVT005/replay` y provoca una entrega al webhook ajeno.

**Mitigación adoptada.** El `client_id` nunca viaja como parámetro: sale del token y llega al caso de uso como `principal.clientId`. Los repositorios solo exponen búsquedas con cliente (`findByIdAndClientId`). Lo ajeno responde 404, no 403, para no confirmar que el recurso existe (RFC §7). El replay condiciona además el `UPDATE` a `client_id` y estado `FAILED` (RFC §8). Vive en el filtro JWT, controllers y puertos de salida; lo verifica un test de API con dos tokens (RFC §19).

**Evolución.** Ninguna pendiente; la garantía es estructural.

### 2.2 API2:2023 Broken Authentication (A07:2021 Identification and Authentication Failures)

**Cómo afectaría.** Un token sin expiración o con firma no verificada permite suplantar a cualquier cliente; un token aceptado en la query string queda en logs y proxies.

**Mitigación adoptada.** La API valida JWT RS256 solo con la clave pública del emisor: firma, expiración de 20 minutos y audiencia. Token únicamente en el header `Authorization`; inválido o ausente responde 401 sin detalle. La clave privada nunca está en la API ni en el repositorio (`docker/keys/` está ignorado por git); en AWS la clave pública vive en SSM (RFC §7, §10, §16). La emisión de tokens queda fuera del alcance porque ya existe en la plataforma (RFC §15).

**Evolución.** Claim de cliente y audiencia son configurables porque los claims del token real no están documentados; la validación contra el JWKS del emisor se verifica al integrar (RFC §16, §18).

### 2.3 API4:2023 Unrestricted Resource Consumption (A01:2021, según el mapeo del RFC)

**Cómo afectaría.** Un cliente pide páginas de un millón de filas, publica un webhook que retiene cada conexión hasta el timeout para agotar los workers, o encadena replays sobre el mismo evento.

**Mitigación adoptada.** Página máxima de 100 con paginación por keyset; timeouts de 3 s de conexión y 5 s de lectura sin seguir redirecciones; backoff con tope de 15 minutos y cinco intentos por ciclo; tope de filas por cliente en cada lote; replay solo desde `FAILED` con actualización condicional (RFC §5, §8, §14, §16). El POST corre fuera de la transacción de reclamo para no agotar el pool (RFC §13). El rate limiting por cliente vive en el ALB con WAF en AWS; en local no aplica (RFC §10, §16).

**Evolución.** Rate limiting en la aplicación, circuit breaker por suscripción, honrar `Retry-After` en 429 y lotes adaptativos (RFC §14, §18).

### 2.4 API7:2023 Server-Side Request Forgery (A10:2021 Server-Side Request Forgery)

**Cómo afectaría.** Una suscripción cuya URL apunte a `169.254.169.254` o a un host interno convierte al worker en un proxy firmado hacia la red de Cobre. Con DNS rebinding, el nombre resuelve a una IP pública al validar y a una privada al conectar.

**Mitigación adoptada.** Validación en la aplicación al registrar y al entregar: solo HTTPS, resolución de DNS, rechazo de rangos privados, loopback y link-local, y conexión a la IP ya resuelta para cerrar el rebinding (Apache HttpClient 5 con resolutor propio detrás de `RestClient`), sin seguir redirecciones. En AWS, security group de egreso solo hacia internet e IMDSv2 con límite de un salto; el NAT Gateway no es mitigación. En perfil local, allowlist explícita (`WEBHOOK_ALLOWLIST`) para WireMock y receptores de demostración, con advertencia en log (RFC §10, §16). Las suscripciones se cargan solo por migración, sin CRUD, lo que reduce la superficie (RFC §16).

**Evolución.** Con el CRUD de suscripciones esta validación será la primera línea de defensa; la verificación de URL con challenge no está en alcance (RFC §11, §16).

### 2.5 API8:2023 Security Misconfiguration (A05:2021 Security Misconfiguration)

**Cómo afectaría.** Un Actuator público expone `/actuator/env` con credenciales; un stack trace revela entidades JPA y versiones; Swagger en producción documenta la superficie de ataque.

**Mitigación adoptada.** Actuator en puerto de gestión interno; errores con formato fijo sin detalle interno; Swagger solo en perfil local; contenedor sin root; dependencias gestionadas por el BOM de Spring Boot (RFC §16). El perfil de Spring apaga beans: en perfil `api` no hay listener SQS ni conexión a la cola (RFC §12). `.env.example` versionado, `.env` ignorado (RFC §12).

**Evolución.** Imágenes distroless y multi-arquitectura quedan fuera del alcance (RFC §12).

## 3. Tabla resumen

| API Security 2023 | Top 10 2021 | Mitigación adoptada | Componente | Evolución |
|---|---|---|---|---|
| API1 BOLA | A01 | `client_id` del JWT; repositorio siempre filtra por cliente; 404 | Filtro JWT, controllers, puertos | Ninguna |
| API2 Broken Authentication | A07 | RS256 con clave pública, `exp` 20 min, `aud`, solo header | Spring Security, SSM | Claims reales, JWKS |
| API4 Resource Consumption | A01 (RFC) | Página 100, keyset, timeouts, backoff, tope por cliente | Controllers, `RetryPolicy`, scheduler, ALB+WAF | Rate limiting en app, circuit breaker |
| API7 SSRF | A10 | Solo HTTPS, sin IP privadas, IP fija, sin redirecciones | `WebhookSender`, SG de egreso | CRUD de suscripciones |
| API8 Misconfiguration | A05 | Actuator interno, errores fijos, Swagger local, sin root, BOM | Configuración, Dockerfile | Distroless |

## 4. Medidas transversales presentes en el diseño

- **TLS obligatorio hacia webhooks.** La URL de suscripción exige HTTPS; en local solo la allowlist admite HTTP (RFC §9, §16). Cubre A02:2021 Cryptographic Failures.
- **Firma HMAC del webhook.** `event-signature = HMAC-SHA256(clave, event-timestamp + "." + cuerpo)`, con ventana de 5 minutos, para que el receptor verifique origen e integridad. La clave nunca se devuelve completa por la API; cifrado en reposo y rotación son evoluciones (RFC §16).
- **Secretos fuera del repositorio.** `.env` y `docker/keys/*.pem` ignorados por git; en AWS, clave pública y claves de firma en SSM (RFC §10, §12).
- **Contenedores sin root.** Imagen multi-stage sobre Temurin 21 con usuario no root (RFC §12).
- **Dependencias gestionadas por el BOM de Spring Boot** (RFC §16).
- **Logs sin datos sensibles.** La línea JSON por intento lleva solo `event_id`, `client_id`, `attempt_number`, `response_status` y `correlation_id`; ni el contenido del evento ni la clave de firma (RFC §10, §16).
- **Sin inyección.** JPA con parámetros enlazados y validación de entrada cubren A03:2021 Injection (RFC §7, §16).

## 5. Referencias

- OWASP API Security Top 10 (2023): <https://owasp.org/API-Security/editions/2023/en/0x11-t10/>
- OWASP Top 10 (2021): <https://owasp.org/Top10/>
