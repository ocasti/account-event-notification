# AI usage log

Tool: Claude Code (CLI). Model: Claude Fable 5.1 (`claude-fable-5-1`).
Each entry records the goal, the prompt in summary, what was produced, and what was accepted, changed or rejected. Screenshots live in `docs/ai/`.

## Session 1 — 2026-09-14 — Understanding the case and choosing the stack

- **Goal:** read the case description and the reference dataset; decide technology and hosting.
- **Prompts (summary):** "review both documents and explain what is asked"; "plan technology, services, and whether cloud is needed"; "check the AWS free tier before recommending Postgres".
- **Output:** requirement breakdown, stack proposal (Java 21, Spring Boot, Maven multi-module, PostgreSQL, ElasticMQ, WireMock, Prometheus, Grafana), free-tier cost analysis with sources.
- **Accepted:** local-only execution with Docker Compose; AWS kept as target design only.
- **Rejected:** deploying to AWS; Terraform; DynamoDB/Mongo as primary store.

## Session 2 — 2026-09-15 — Architecture RFC

- **Goal:** produce the design document with C4 diagrams, sequences, state machine, data model, capacity analysis, security.
- **Prompts (summary):** "draw the architecture and sequence diagrams"; "who calls whom? can the emitter run on localhost?"; "analyse as microservices in Docker; is this a Saga?"; "consult senior architect, DevOps, SRE and tech-lead roles as independent sub-agents on containers, builds and Terraform"; "consult Postgres, DynamoDB and distributed-systems roles on capacity"; "contrast with Cobre's public API docs"; "run independent cold reviewers on the RFC"; "verify only defects with literal quotes".
- **Output:** RFC published as an artifact (20 sections), three review rounds.
- **Accepted:** two processes from one image (api/worker); single data path with a continuous event simulator; retries with cycles; claim-with-lease concurrency; Cobre-aligned subscription, event schema and HMAC signature; OWASP API1/2/4/7/8 with 2021 mapping.
- **Changed after review:** removed inline first attempt; removed optimistic-lock column; lease covers the batch; conditional result write; NAT dropped as SSRF mitigation; partitioning stated as evolution with its precondition.
- **Rejected:** seeding the reference JSON into the database; Idempotency-Key header; replay of completed events (kept as evolution).
- **Verification protocol adopted:** reviewers may only report internal contradictions with literal quotes, uncovered requirements, or factual errors with a source; every quote is checked against the file before acting.

## Session 3 — 2026-09-15 — Repository bootstrap (step 0)

- **Goal:** create the repository skeleton: ignore rules, environment template, Makefile, preflight and token scripts, README, this log.
- **Prompts (summary):** "start step 0 with repo name account-event-notification".
- **Output:** files listed above; first commit.

## Session 4 — 2026-09-15 — Project structure without application code (steps 1 and 2)

- **Goal:** build the repository structure in parallel with one agent per area, without writing any Java source yet.
- **Prompts (summary):** three sub-agents launched in isolated git worktrees with an explicit write area each: "Compose stack and container configuration under docker/"; "Maven multi-module skeleton, wrapper and Dockerfiles, no .java files"; "GitHub Actions workflow and OWASP security report". Each had a mandatory verification list and committed to its own branch with attribution trailers; the branches were merged into main by the orchestrating session.
- **Output:** `compose.yaml` with profiles infra/app/observability and healthchecks; ElasticMQ queue and DLQ config; WireMock mappings failing EVT003/EVT005/EVT009; Prometheus scrape config; Grafana datasource and dashboard; root and module POMs; Maven wrapper; two multi-stage Dockerfiles; CI workflow; `docs/02-security.md`.
- **Verified:** five infrastructure containers healthy; WireMock returns 503 for the three failing ids and 200 otherwise; both queues listed; `./mvnw -DskipTests verify` succeeds with no sources; Spring Boot 4.1.1 resolves with Spring Cloud AWS 4.1.1; workflow YAML valid; every mitigation in the security report cites an RFC section.
- **Accepted from agents:** pinning ElasticMQ to 1.6.12 because 1.7 removed the statistics endpoint used by the healthcheck; Grafana on port 3001; `.dockerignore`.
- **Resolved by the orchestrator:** both the Maven and the Docker agent produced Dockerfiles; the Maven agent's version was kept because it was validated against the real POMs.
- **Rejected:** none.

## Session 5 — 2026-09-15 — Domain layer with TDD (step 3, part 1)

- **Goal:** build the domain module layer by layer: raw skeleton first, then value objects, then tests in red, then the minimal implementation to green.
- **Prompts (summary):** to a Haiku sub-agent, "create the raw class skeleton with these exact signatures, every method throwing UnsupportedOperationException, no logic"; then "add value objects EventId, ClientId, EventKey, WebhookUrl with constructor validation; write these test cases; run them red; implement until green", with the full case list (state transitions, replay cycle, retry delays 0/30 s/2 min/8 min/15 min, jitter bounds, subscription matching).
- **Output:** 14 classes in `domain/model`, `domain/policy`, `domain/exception`; 9 test classes, 89 tests.
- **Verified:** red phase failed with 56 errors from unimplemented methods and no compilation errors; green phase 89/89; no dependency outside the JDK in main; tests use only JUnit 5 and AssertJ.
- **Accepted:** implementation as delivered after reading the state machine, the aggregate transitions, the retry policy and the value objects.
- **Rejected:** none.

## Session 6 — 2026-09-15 — Application layer with TDD (step 3, part 2)

- **Goal:** ports, commands, queries and use cases: raw skeleton, tests in red, minimal implementation, refactor in green.
- **Prompts (summary):** to a Haiku sub-agent, "create the raw skeleton with these exact port and use case signatures"; then "write these test cases with Mockito for the four ports and a fixed Clock, run red, implement to green" with the full semantics of each use case; then "refactor ProcessDueDeliveries: no nulls, a single executed-attempt builder, exhaustive switch over the sealed outcome".
- **Output:** 4 outbound port interfaces, 6 records, 5 use cases; 5 test classes, 17 tests.
- **Verified:** red phase 17 failures from unimplemented methods; green 17/17 with the 89 domain tests still passing; refactor diff touches only the implementation file and stays green.
- **Accepted:** implementation after reading register, replay and delivery processing.
- **Changed:** the first delivery-processing implementation used nulls, duplicated a twelve-argument constructor and chained instanceof checks; it was refactored under green tests before merging.

## Session 7 — 2026-09-15 — Parameter objects refactor

- **Goal:** remove long parameter lists from the core after a review comment: methods and logic constructors should take at most three arguments; anything larger becomes a parameter object.
- **Prompts (summary):** to a Haiku sub-agent, "introduce EventData, DeliveryResult, DeliveryClaim and DeliveryWorkerSettings; change these signatures; keep the 106 tests green adjusting only call sites; add tests for the new records".
- **Output:** four new records; `NotificationEvent.register/skipped`, `DeliveryAttemptRepository.claimDue` and the `ProcessDueDeliveries` constructor reduced; `DeliveryAttempt.executed(...)` replaces a twelve-argument reconstruction.
- **Verified:** 115 tests green; an automated scan shows the remaining signatures above three parameters are data records or dependency-injection constructors.
- **Accepted:** as delivered.

## Session 8 — 2026-09-15 — Infrastructure skeleton (step 3, part 3)

- **Goal:** raw Spring Boot skeleton of the infrastructure layer with correct stereotypes and no logic, plus the ArchUnit rules that enforce layer independence.
- **Prompts (summary):** two Haiku sub-agents in parallel on disjoint packages: "base, config, persistence, messaging, scheduler, metrics, resources, migrations and the architecture test" and "rest, security and webhook"; both with the exact class map, the rule "stereotypes over @Bean, constructor injection, at most four collaborators", and the `@UseCase` annotation approach so use cases become beans without depending on Spring.
- **Output:** 44 classes under `infrastructure`, 4 profile YAML files, 2 Flyway migrations, 1 architecture test; `@UseCase` in `application`.
- **Verified:** module compiles; ArchUnit passes on the merged tree (domain and application free of Spring, JPA and Jackson; entities only under persistence; rest does not touch persistence).
- **Changed by the orchestrator:** added Apache HttpClient 5 to the POM after the agent reported it was not transitive; corrected the Flyway placeholder syntax in the subscriptions migration.

## Session 9 — 2026-09-15 — Infrastructure layer with TDD (step 3, part 4)

- **Goal:** implement the Spring Boot adapters area by area with tests in red first: persistence, REST and security, webhook sender, messaging with scheduler and metrics, then the due-attempts gauge feeder.
- **Prompts (summary):** four Haiku sub-agents in parallel on disjoint packages, each with the exact test case list, the implementation semantics and the verification command; follow-up messages with the first `Caused by` from Surefire whenever an agent reported partial results.
- **Output:** repository adapters with keyset search, claim-with-lease and conditional result writes; JWT resource server validating a generated key file in tests; HMAC signer, SSRF validator with pinned DNS and the HTTP sender; SQS listener, scheduler and Micrometer metrics; `SearchCriteria` and `ListRequest` parameter objects. 113 infrastructure tests.
- **Verified:** every branch was re-run by the orchestrating session before merging; `mvnw verify` on main: 229 tests, 0 failures.
- **Corrections driven by the orchestrator:** entity status names had been renamed away from the domain; the claim query used the worker clock instead of the database clock; a custom `@ComponentScan` had disabled Spring Boot test slices; test security config had shadowed the production chain; the HTTPS rule had been placed in the domain value object; two persistence tests were wrong (a 2026 timestamp inside a 2024 range, and data colliding with the seeded subscriptions); the keyset cursor pointed at the wrong element and truncated to milliseconds.
- **Environment facts learned:** Docker runs under OrbStack, so Testcontainers needs `DOCKER_HOST=unix:///Users/omarcastiblanco/.orbstack/run/docker.sock`; Spring Boot 4 moved `@DataJpaTest` to `org.springframework.boot.data.jpa.test.autoconfigure`.
- **Rejected:** mocking beans from other packages to make a full-context test start; `@ConditionalOnMissingBean` in production config to accommodate a test; a snake_case record component to bind a query parameter.

## Session 10 — 2026-09-15 — Application boot tests and build gate

- **Goal:** prove the application starts with its production configuration in both profiles against real Postgres and ElasticMQ, and make the integration tests part of the standard build.
- **Prompts (summary):** to a Haiku sub-agent, "write boot tests per profile; if production fails to start, do not patch production, report the cause"; follow-ups with the exact root cause after each run by the orchestrating session.
- **Output:** `boot/ApiProfileBootIT` (7 cases) and `boot/WorkerProfileBootIT` (5 cases); production fixes: webhook and JWT defaults in `application.yaml`, `@Autowired` on the validator's public constructor, worker settings available in every profile with scheduling only under `worker`, payload mapper on Jackson 3; Surefire now includes `*IT` classes.
- **Verified:** `mvnw verify` on main: 242 tests, 0 failures, integration tests included.
- **Lessons:** area tests in green did not guarantee the application started; the boot tests found four wiring defects. Spring considers every declared constructor, so a class with two needs an explicit `@Autowired`. Spring Boot 4 provides Jackson 3 (`tools.jackson`) beans.
- **Rejected:** replacing production beans with test substitutes to make the context start; attributing a failure to dynamic properties without evidence in the report.

## Session 11 — 2026-09-15 — Flat packages, CI reporting and coverage

- **Goal:** simplify the package structure after a review comment ("too nested"), and make the pipeline show test results and coverage.
- **Prompts (summary):** to a Sonnet sub-agent (chosen over Haiku because the move touched every file), "one package per adapter, a `worker` package for the worker process, git mv, rewrite package and import declarations, update ArchUnit, keep 242 tests green"; CI and POM edits made by the orchestrating session.
- **Output:** packages `domain`, `application.port`, `application.usecase`, `infrastructure.{config,persistence,rest,security,webhook,worker}`; two new ArchUnit rules isolating `worker` from `rest` and `security`; JaCoCo on every module; CI publishes Surefire results as a check and uploads reports and coverage as artifacts.
- **Verified:** 244 tests green locally and in CI with Testcontainers on the runner; coverage domain 92 %, application 93 %.
- **Rejected:** none.

## Session 12 — 2026-09-15 — Controller and webhook cleanup

- **Goal:** answer three review questions with code: no error handling in controllers, request objects that own their parsing, and an argument resolver for the authenticated client; plus two webhook defects found while reading the code.
- **Prompts (summary):** to a Haiku sub-agent, four commits with tests first: "ListRequest owns status parsing", "@AuthenticatedClient argument resolver", "serialize the payload record with the JsonMapper", "no timestamp header without a signature".
- **Output:** `ListRequest.status()`, `AuthenticatedClientArgumentResolver` registered through `WebMvcConfig`, controller signatures `(@AuthenticatedClient ClientId, ...)` with four collaborators and no try/catch, payload built from the record; 251 tests.
- **Verified:** full suite with Testcontainers re-run by the orchestrating session before merging; the agent had reported 200 because it ran without Docker.
- **Rejected:** none.

## Session 13 — 2026-09-15 — Simulator, first full start and delivery fixes

- **Goal:** build the event simulator, start the whole stack with `make up`, and fix what only a real container run reveals.
- **Prompts (summary):** to a Haiku sub-agent, the simulator in three commits (raw skeleton, tests in red, implementation) with the queue contract, the catalog loaded from the reference dataset, derived events, an SQS publisher, an emission scheduler and a manual endpoint; follow-ups to use Jackson 3, keep properties as a record and add the validation test. Then, after `make up`, two more agents: "random generator available in the JRE image" and "no HTTP client retries, parallel batch with virtual threads, configurable retry policy with a fast local profile".
- **Output:** `services/event-simulator` with 21 tests; both Docker images build from real code (445 MB and 362 MB); CI now builds the images; the stack starts with six healthy containers and events flow queue → worker → WireMock → API.
- **Defects found only at container start, all fixed in production code or configuration:** `RandomGenerator.getDefault()` needs a JDK module absent from the JRE image; the AWS region key was nested under the wrong YAML level (boot tests had masked it by setting the region explicitly); the HTTP client retried 503s on its own; the claimed batch was processed sequentially; `@EnableScheduling` made the executor ambiguous for the pure-Java use case, solved with `@Primary`.
- **Verified:** 256 tests green across the two services with Testcontainers; functional check against the running stack (statuses, webhook count, replay 202/409/404, 401 without token).
- **Rejected:** Jackson 2 `ObjectMapper` bean; properties class with setters; leaving the WireMock request journal disabled.
