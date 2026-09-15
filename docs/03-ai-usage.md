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
