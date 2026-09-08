# Technical Deep Dive: Event-Driven Omnichannel Notification & Workflow Platform

*A senior architect's walkthrough of the system — architecture, module internals, DevOps posture, and the engineering trade-offs behind it.*

---

## 1. Executive Summary / Project Overview

### What it is

The platform is a centralized, event-driven system that does two things at once: it **ingests business events from external systems and turns them into reliable, multi-channel notifications**, and it lets a non-developer **compose those notifications into stateful, multi-step business workflows** through a visual builder — without writing code for each new scenario.

Concretely, it is a single Spring Boot backend (`ingestion-service`) fronted by an Angular administration console, with Apache Kafka as the backbone connecting ingestion, orchestration, and delivery.

### The problem it solves

Before a platform like this exists, notification logic in most systems accretes organically and badly:

- Each notification type gets hardcoded at its call site (order confirmation here, password reset there), duplicating channel-selection and formatting logic across the codebase.
- There's no single place enforcing user communication preferences (which channels a user allows) or quiet hours — every call site would have to remember to check.
- Multi-step business processes ("wait 24h, then check if the user responded, then escalate") either don't get built at all, or get built as one-off cron jobs and ad-hoc state machines that nobody can inspect visually or reuse.
- There's no unified, queryable audit trail of what was sent, to whom, on which channel, and why (or why not).
- Reliability is inconsistent: a synchronous call to a flaky email provider blocks the caller; there's no natural buffering for traffic spikes.

### The value proposition

The platform collapses all of this into three composable capabilities behind one ingestion point:

1. **A single, authenticated ingestion API** (`POST /api/v1/notifications/send`) that any client system can call, with built-in per-client rate limiting.
2. **A governed omnichannel delivery layer** (Email / SMS / Push) that centrally enforces user channel preferences and quiet hours, and produces a consistent, queryable delivery log — instead of every caller reimplementing that logic.
3. **A visual, event-triggered workflow engine** that lets a business user assemble Trigger → Notification → Wait → Gateway → End graphs, validated structurally before activation, and versioned safely so that in-flight executions are never corrupted by a later edit.

The system is instrumented and quality-gated as a production-grade artifact, not a prototype: Apache Kafka decouples ingestion from delivery so traffic bursts don't back-pressure callers, and the codebase carries **87.4% backend / 93.0% frontend automated test coverage**, verified continuously through SonarQube and GitHub Actions.

---

## 2. Architecture & Design Patterns

### 2.1 Event-Driven Architecture (EDA) as the organizing principle

Apache Kafka sits at the center of the system as the **event backbone**. Nothing downstream of ingestion is called synchronously: the ingestion controller validates and authenticates a request, publishes it to a topic, and returns `202 Accepted` immediately. Everything that happens next — channel routing, template resolution, provider dispatch, workflow triggering and progression — happens asynchronously, driven by Kafka consumers.

This buys the system two properties that matter operationally:

- **Load absorption.** A burst of 10,000 incoming events doesn't propagate backpressure to the calling systems; it just queues in Kafka and is drained at the rate the consumers can sustain.
- **Failure isolation.** If the SMS provider is down, SMS consumers back up (and eventually retry/DLQ — see §4), but Email and Push consumers, and the ingestion endpoint itself, are unaffected.

The main topics in play:

| Topic | Purpose |
|---|---|
| `notification.events` | Business events entering the workflow engine |
| `workflow.advance` | "Progress this execution" signal — published every time a workflow needs to move to its next node |
| Per-channel notification topics | Routed by channel (Email/SMS/Push) and priority (high/low), feeding the per-channel consumers |

### 2.2 "Microservices pattern" — a precise clarification

It's worth being exact here rather than reaching for the buzzword: **the backend is a single deployable Spring Boot application (`ingestion-service`), not a fleet of independently deployed microservices.** What it *does* borrow from the microservices world is the internal decomposition style that Kafka enables:

- Each responsibility (ingestion, rate limiting, workflow orchestration, per-channel delivery) is its own component with its own Kafka consumer group, so it can be reasoned about, tested, and scaled (via consumer concurrency) independently — *in principle* — even though today they all run inside one JVM process.
- Kafka's pub/sub model gives the same **temporal decoupling** and **failure isolation** benefits usually cited as microservices advantages, without paying the operational cost (service discovery, distributed tracing across process boundaries, network-partition handling) of an actually distributed topology.

So the accurate way to describe this to an audience: it's a **modular, event-driven monolith** — a single service internally organized as if it *could* be split into microservices along its Kafka topic boundaries, should the scaling requirements ever justify that operational cost. This is a legitimate and common architectural stepping stone, and it's more honest (and more impressive to a technical reviewer) than claiming a distributed microservices topology that isn't actually deployed.

### 2.3 Backend layering (Spring Boot 3.3.3, Java 17)

The backend follows a conventional layered structure, with the workflow engine as a parallel "vertical" cutting across it:

```
Controllers (REST)  →  Security (JWT / API-Key)
        ↓
Application Services  +  Workflow Execution Engine (NodeHandlers)
        ↓
Spring Data JPA Repositories  ←→  Kafka Producers / Consumers
        ↓
PostgreSQL                        External providers (SMTP, Twilio, FCM)
```

- **Spring Security + OAuth2 Resource Server** validates JWTs issued by Keycloak for every internal management endpoint (templates, workflows, dashboard, statistics), translating Keycloak's `realm_access.roles` into Spring Security authorities via a custom converter.
- **External ingestion** is authenticated separately, by a per-client API key (`ClientApp` entity), deliberately decoupled from the human-user JWT model — a client system isn't a "user."
- **Spring Data JPA** persists the domain model to PostgreSQL, using native `jsonb` columns for the two fields that are structurally variable — a workflow's graph definition and an execution's accumulated context — rather than forcing a rigid relational schema onto inherently dynamic data.

### 2.4 Frontend architecture (Angular 19, standalone components)

The console is built entirely on Angular's standalone-component model — there is no `NgModule` anywhere in the codebase. Routes are lazy-loaded per page via `loadComponent`, keeping the initial bundle lean. Authentication is handled by `keycloak-js`, with an `AuthInterceptor` that transparently attaches the bearer token to every outgoing HTTP call and a `provideAppInitializer` that blocks app bootstrap until authentication completes — so no page ever renders in a half-authenticated state.

The one specialized piece of frontend architecture worth calling out is the **workflow builder**, built on `ngx-vflow`, a graph-editing library that gives the drag-and-drop canvas, node positioning, and edge-drawing interactions for free, so the team's own code only had to implement the domain-specific parts: the five node types, their configuration panels, and the client-side mirror of the server's structural validation rules.

---

## 3. Core Modules Deep Dive

### 3.1 Ingestion Service

The entry point is `POST /api/v1/notifications/send`, authenticated by an `X-API-KEY` header resolved against the `ClientApp` entity.

**Rate limiting** is enforced per client via Redis:

- Each accepted request increments a counter key (`ratelimit:<clientId>`) with a 60-second TTL, comparing against that client's configured `rateLimitPerMinute`.
- The failure-mode decision here is deliberate and worth surfacing explicitly, because it's a real architectural trade-off, not an oversight:

```java
} catch (Exception e) {
    log.warn("Redis rate limiter fallback allowed due to error: {}", e.getMessage());
    return true; // Resilience fallback in case Redis connection fails
}
```

If Redis itself becomes unavailable, the limiter **fails open** — it allows the request rather than rejecting it. This is a conscious choice to prioritize *availability of the core ingestion path* over *strict enforcement of a secondary protection mechanism*: a Redis outage degrades the system to "unrate-limited" rather than "completely down." The trade-off is real (a Redis outage during an abuse attempt would let it through), and it's the kind of decision that should be an explicit, documented policy rather than an implicit accident — which, here, it is.

Once accepted, the event is routed to a Kafka topic selected by **channel** and **declared priority** (high/low), and the controller returns immediately. The actual delivery work happens entirely downstream, decoupled from the HTTP request/response cycle.

### 3.2 Omnichannel Notification Engine

Delivery is handled by three parallel, independent Kafka consumers — `EmailNotificationConsumer`, `SmsNotificationConsumer`, `PushNotificationConsumer` — each in its own consumer group (`notification-email-group`, etc.), so a slowdown or outage on one channel never throttles the others.

Before dispatching, every consumer applies the same two governance checks, in the same order:

**1. Channel opt-in**, checked against `UserPreference`:

```java
if (!pref.isEnabledEmail()) { /* suppress */ }   // EmailNotificationConsumer
if (!pref.isEnabledSms())   { /* suppress */ }   // SmsNotificationConsumer
if (!pref.isEnabledPush())  { /* suppress */ }   // PushNotificationConsumer
```

**2. Quiet hours**, checked against the same entity's `quietHoursStart` / `quietHoursEnd`:

```java
if (now.isAfter(pref.getQuietHoursStart()) && now.isBefore(pref.getQuietHoursEnd())) {
    if (!"HIGH".equalsIgnoreCase(event.getPriority())) {
        notificationLogService.record(..., STATUS_SUPPRESSED);
        return;
    }
}
```

Two behaviors are worth being precise about, since they're easy to assume incorrectly:

- **Quiet-hours suppression is a hard drop, not a deferral.** A low-priority notification that arrives inside a user's quiet window is logged as `SUPPRESSED` and never sent — it is *not* queued and re-attempted once quiet hours end. Only events explicitly marked `HIGH` priority bypass the check entirely.
- **There is no automatic cross-channel fallback in the delivery layer itself.** If a Push send fails, the system does not automatically retry via SMS or Email — the consumer logs `STATUS_FAILED` and stops. Cross-channel fallback *is* achievable, but only by design: a workflow author can build it explicitly, using a `Gateway` node that inspects the delivery outcome in the execution context and branches to a different `Notification` node on a different channel. This is an important distinction to make in any presentation of the system: "fallback between channels" is a **capability the workflow engine enables**, not a feature automatically applied to every notification.

Every dispatch attempt — delivered, failed, or suppressed — is written to a `NotificationLog` row, which is the data source for the Dashboard and Statistics screens. For emails specifically, an invisible tracking pixel is embedded on send, giving the platform its only real open-tracking signal; the Statistics page is explicit in the UI about *not* having click-tracking, rather than displaying a fabricated number.

### 3.3 Workflow Engine

This is the platform's most architecturally interesting module: a small, purpose-built execution engine for graphs authored visually in the browser.

**The building blocks.** A workflow is a graph of typed nodes — `TRIGGER`, `NOTIFICATION`, `WAIT`, `GATEWAY`, `END` — connected by edges. Execution is driven by a `NodeHandler` interface (`handle(execution, node, context) → outcome`), with one implementation per node type except `TRIGGER`, which is never dispatched through the interface — it only ever marks where an execution begins:

| Node type | Handler | Behavior |
|---|---|---|
| `NOTIFICATION` | `NotificationNodeHandler` | Resolves a template, interpolates the execution context into it, and dispatches — synchronously for Email (so the tracking pixel can be embedded before the message leaves the process), asynchronously via Kafka for SMS/Push. |
| `WAIT` | `WaitNodeHandler` | Computes `nextWakeAt` and returns `SUSPEND` — the *only* node type that can pause an execution. |
| `GATEWAY` | `GatewayNodeHandler` | Evaluates a condition against the execution context and returns `CONTINUE` down the "yes" or "no" branch. |
| `END` | `EndNodeHandler` | Always returns `COMPLETE`. |

**Structural validation before activation.** A workflow can't be switched from `DRAFT` to `ACTIVE` unless its graph passes a set of structural checks — the `WorkflowGraphValidator` — enforced server-side (and mirrored client-side for immediate UI feedback): exactly one `TRIGGER` node, every node reachable from it, exactly one outgoing edge for any node except `GATEWAY` (which requires precisely one "yes" and one "no" edge) and `END` (which must have none). This is the safety net that prevents a business user from publishing a graph that could dead-end, branch ambiguously, or infinite-loop.

**Copy-on-write versioning.** Editing an *active* workflow never mutates the row an in-flight execution is bound to. Instead, it creates a new row at `DRAFT` status, linked back via `parentWorkflowId`; the running execution keeps referencing the exact `workflowVersion` it started on. This is what makes "edit a live workflow without corrupting executions already in progress" possible at all — a naive in-place edit would be a data-integrity hazard the moment any execution was mid-flight.

**Execution loop.** An event on `notification.events` that matches an `ACTIVE` workflow's trigger type causes `WorkflowExecutionEngine.spawn()` to create a `WorkflowExecution` row (status `RUNNING`) and publish to `workflow.advance`. From there, `advance()` walks the graph node by node until it hits a `WAIT` (suspend), an `END` (complete), or exhausts the current synchronous chain. A workflow is never started manually by a user — only the engine, reacting to a matching event, creates executions; this is deliberate, since manual triggering would break the guarantee that every execution corresponds to a real business event.

The mechanics of resuming a suspended execution are covered in §5, since they're really a concurrency problem, not just a workflow-engine feature.

---

## 4. DevOps, Quality & Testing

### 4.1 Infrastructure as a single reproducible unit

The entire runtime dependency graph — eight services — is defined in one `docker-compose.yml` and started with a single command, eliminating "works on my machine" drift between developer environments:

| Service | Image | Role |
|---|---|---|
| PostgreSQL | `postgres:15-alpine` | System of record (native `jsonb` for dynamic fields) |
| Redis | `redis:7-alpine` | Per-client rate-limit counters |
| Apache Kafka + Zookeeper | `confluentinc/cp-kafka:7.5.0` / `cp-zookeeper:7.5.0` | Event backbone |
| Kafka UI | `provectuslabs/kafka-ui` | Visual topic/partition/message inspection |
| Keycloak | `quay.io/keycloak/keycloak:24.0.2` | JWT/OIDC identity provider, realm auto-imported at startup |
| Mailpit | `axllent/mailpit` | SMTP sink for local dev — captures every outgoing email instead of delivering it |
| Prometheus | `prom/prometheus` | Scrapes `/actuator/prometheus` every 5s (Spring Boot Actuator + Micrometer) |

### 4.2 Quality gate: SonarQube, dual-project

The backend and frontend are analyzed as **two separate SonarQube projects** — `omnichannel-backend` and `omnichannel-frontend` — because their coverage instrumentation pipelines are entirely different toolchains feeding two different report formats:

- **Backend:** JUnit 5 + Mockito (plus `spring-kafka-test` for consumer/producer tests), instrumented by JaCoCo, reported to SonarQube via the Maven Sonar plugin as XML. **42 test classes, 255 `@Test` methods**, reaching **87.4% coverage** — including the workflow engine's node handlers and graph validator, not just trivially-covered DTOs/getters.
- **Frontend:** Jasmine + Karma, run headless in Chrome, instrumented by `karma-coverage`, reported as LCOV via the `sonar-scanner` CLI. **34 spec files, ~268 `it()` cases**, reaching **93.0% coverage** across page components, HTTP services, and cross-cutting concerns (the auth interceptor, route guards).

### 4.3 Continuous Integration and error resilience

GitHub Actions runs two independent jobs on every push/PR to `main` — a JDK 17 (Temurin) job running `mvn clean test`, and a Node 20 job running Angular tests in headless Chrome — so a regression in either stack blocks the merge without the two pipelines coupling to each other.

One detail worth surfacing under "quality," since it's a real reliability mechanism rather than a checkbox: the Kafka consumer side is wired with a `CommonErrorHandler` configured with a **dead-letter-queue recoverer and exponential backoff (capped at 10s total)**. A message that repeatedly fails processing is routed to a DLQ topic instead of blocking or endlessly retrying the partition — a "poison message" in one channel's queue can't stall that consumer group indefinitely, let alone the others.

---

## 5. Key Technical Challenges & Solutions

This section is deliberately the most candid one. A senior-architect-level explanation of a system isn't complete without naming the trade-offs made under real constraints — and this platform has several genuinely interesting ones.

### 5.1 Concurrency-safe resumption of waiting executions

**The problem:** many `WorkflowExecution` rows can be sitting in `WAITING` status at once, each with a `nextWakeAt` timestamp. A scheduler needs to periodically find the ones that are due and resume them — but if that scheduler ever runs with more than one thread or instance (which any horizontally-scaled or even just multi-threaded deployment implies), two workers could race to claim and resume the *same* execution, double-processing it.

**The solution actually implemented** is not a naive "load the row, check its status, save it back" pattern — that would have a race window between the read and the write. Instead, `WorkflowWaitScheduler.resumeDueExecutions()` claims due rows with a single atomic, conditional SQL statement:

```java
@Modifying
@Query(value = """
    UPDATE workflow_executions
    SET status = 'ADVANCING', version = version + 1
    WHERE status = 'WAITING' AND next_wake_at <= :now
    RETURNING id
    """, nativeQuery = true)
List<Long> claimDueWaitingExecutions(@Param("now") LocalDateTime now);
```

Because the `WHERE status = 'WAITING'` predicate and the `SET status = 'ADVANCING'` happen inside one database-level atomic statement, PostgreSQL itself serializes the race: whichever caller's `UPDATE` commits first wins the row, and every other concurrent caller's `UPDATE` simply matches zero rows for that ID. This is a stronger guarantee than the more commonly reached-for JPA `@Version` optimistic-locking pattern (load → compare version → save, retry on conflict) would give under contention, because there's no read-then-write gap for a second worker to land in. The `@Version` field on `WorkflowExecution` still exists and is incremented here too, acting as a second line of defense for any other code path that might update the same row through a normal `save()`.

### 5.2 At-least-once delivery, and where idempotency actually lives (and doesn't)

Kafka's delivery guarantee, as configured here, is the standard **at-least-once** semantic — and it's worth being precise about what that means for *this specific* codebase rather than reciting the generic Kafka guarantee:

- The three notification consumers (`Email`/`Sms`/`PushNotificationConsumer`) have **no deduplication check**. If a consumer crashes after sending an email but before its offset commit, Kafka's rebalance will redeliver that message to another consumer instance, and the email will be sent a second time. `NotificationLogService.record()` is a pure append-only audit write — it never queries "have I already logged this `eventId`?" before acting. For a system whose worst-case duplicate is "the user gets the same notification twice," this is a reasonable, deliberate trade-off — but it is a trade-off, and it would be the first thing to fix (via an idempotency-key check before dispatch, or a unique constraint on `(eventId, channel)`) if the platform ever needed to guarantee exactly-once semantics for something higher-stakes than a notification (a payment confirmation, say).
- The **workflow advance path is different, and is idempotent by design**: `WorkflowExecutionEngine.advance()` re-reads the execution's current status before doing any work and no-ops unless it's `RUNNING` or `ADVANCING`. Combined with the atomic claim query in §5.1, a redelivered `workflow.advance` message for an execution that's already been processed simply finds nothing to do. This asymmetry — dedupe-by-design on the state-machine path, at-least-once-with-no-dedupe on the notification-dispatch path — is exactly the kind of nuance that's easy to gloss over but important to understand and be able to explain: they're solving different problems (correctness of a state machine vs. best-effort message delivery) and were given different levels of rigor accordingly.

### 5.3 The dual-write problem between PostgreSQL and Kafka

**The problem, precisely:** in `WorkflowExecutionEngine.spawn()` and `advance()`, the sequence inside a single `@Transactional` method is: persist the execution's new state to PostgreSQL, *then* publish to `workflow.advance` on Kafka. These are two independent systems with no shared transaction coordinator between them. That means there is a real, if narrow, window where they can diverge: if the process crashes after the DB commit but before the Kafka send succeeds, the execution is durably `RUNNING` in the database but nothing will ever come along to advance it further. The inverse (Kafka send succeeds, DB transaction later rolls back) is also possible in principle.

**How this is actually handled today:** it isn't fully solved — and naming that plainly is more useful, for both understanding the system and presenting it credibly, than glossing over it. The textbook fix is a **transactional outbox pattern** (write the "event to publish" to an outbox table in the *same* database transaction as the state change, then have a separate relay process/CDC connector — e.g. Debezium — publish it to Kafka afterward, guaranteeing the two never diverge). That pattern is not present in the current implementation; the current implementation accepts the narrow inconsistency window in exchange for architectural simplicity. This is a legitimate engineering trade-off for a system at this stage and scale, and it's exactly the kind of item that belongs on a "next hardening steps" list — alongside the notification-path idempotency gap in §5.2 — before the platform would be ready for a workload where a lost or duplicated event has serious consequences.

### 5.4 Fail-open resilience under partial infrastructure failure

Two independent pieces of the system make the same underlying choice when a dependency degrades, and it's a coherent philosophy worth naming as one: **the Redis rate limiter fails open** (§3.1) rather than rejecting all traffic when Redis is unreachable, and **the Kafka consumer error handler routes poison messages to a DLQ with bounded backoff** rather than letting a single bad message stall a partition indefinitely (§4.3). Both decisions optimize for "the core system keeps functioning in degraded mode" over "the core system halts to guarantee strict correctness of a secondary concern." That's a defensible default for a notification platform (where the cost of an occasional over-limit request or a delayed poison message is low) — but it's a policy, and a system like this should be able to state it as one, rather than have it discovered by surprise during an incident review.

---

## Closing note

Taken together, these five sections describe a system that is genuinely well-architected for its actual scale and stage: a decoupled, Kafka-centered ingestion-and-orchestration platform with a validated, versioned workflow engine, backed by real automated-test discipline (87.4%/93.0% coverage, continuously verified) rather than aspirational claims. The honest naming of its current limits — no transactional outbox, no notification-path idempotency, unconfigured producer/consumer reliability knobs (`acks`, `enable.idempotence`, explicit `ack-mode`) — isn't a weakness in the system description; it's the difference between a marketing summary and something a senior engineer would actually sign off on, and it doubles as a ready-made technical roadmap for the platform's next iteration.
