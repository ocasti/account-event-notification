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
