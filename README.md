# Banking Application — Spring Boot Microservices

A banking system built from scratch as independent Spring Boot microservices, with JWT-based authentication at the gateway layer and a Resilience4j circuit breaker protecting inter-service calls.

Built as a hands-on learning project — every line was written and understood service by service, not generated wholesale. See [What I Learned](#what-i-learned) for the concepts this project actually covers.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Services](#services)
- [Request Flow: JWT Authentication](#request-flow-jwt-authentication)
- [Request Flow: Money Transfer with Circuit Breaker](#request-flow-money-transfer-with-circuit-breaker)
- [Event-Driven Flow: Kafka Notifications](#event-driven-flow-kafka-notifications)
- [Circuit Breaker State Machine](#circuit-breaker-state-machine)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Running the Project](#running-the-project)
- [API Reference](#api-reference)
- [Database Design](#database-design)
- [Design Decisions Worth Knowing](#design-decisions-worth-knowing)
- [Defense-in-Depth Security](#defense-in-depth-security)
- [What I Learned](#what-i-learned)
- [Known Limitations & Next Steps](#known-limitations--next-steps)

---

## Overview

Instead of one monolithic Spring Boot app, this system is split into seven independently deployable services, each owning its own database (where it has one):

| Service | Responsibility | Port | Own Database |
|---|---|---|---|
| **discovery-server** | Service registry (Eureka) — every service registers here so others can find it by name instead of a hardcoded IP | 8761 | — |
| **config-server** | Centralized configuration — every service's `application.yml` (ports, DB credentials, JWT secret, Resilience4j thresholds) lives in one place and is fetched over HTTP at startup, instead of being duplicated across each service's own files | 8888 | — |
| **api-gateway** | Single entry point for all client traffic. Validates JWTs, routes requests to the right service | 8080 | — |
| **auth-service** | User registration/login, password hashing, JWT issuing | 8081 | `bankapp_auth` |
| **account-service** | Account creation, balance, debit/credit, optimistic locking | 8082 | `bankapp_account` |
| **transaction-service** | Deposits, withdrawals, transfers — calls account-service via Feign, protected by a circuit breaker, publishes events to Kafka | 8083 | `bankapp_transaction` |
| **notification-service** | Consumes transaction events from Kafka asynchronously and logs a notification message — decoupled entirely from the request/response cycle | 8084 | — |

**Why split it up at all?** In a monolith, a bug in "transactions" code can crash the whole application — including login. Here, if transaction-service goes down, people can still log in and check balances. Each service scales, fails, and deploys independently. That independence is also *why* microservices need things a monolith never worries about: service discovery, an API gateway, and resilience patterns like circuit breakers — because now services talk to each other over an unreliable network instead of a simple in-process method call.

---

## Architecture

```mermaid
graph TB
    Client["Client (Postman / Browser / Mobile App)"]

    subgraph Gateway Layer
        GW["API Gateway :8080<br/>JWT Validation + Routing"]
    end

    subgraph Registry_and_Config
        Eureka["Discovery Server :8761<br/>(Eureka)"]
        Config["Config Server :8888<br/>reads config-repo/ from GitHub"]
    end

    subgraph Services
        Auth["Auth Service :8081<br/>Register / Login / Issue JWT"]
        Acct["Account Service :8082<br/>Create / Balance / Debit / Credit"]
        Txn["Transaction Service :8083<br/>Deposit / Withdraw / Transfer<br/>+ Circuit Breaker"]
        Notif["Notification Service :8084<br/>Kafka Consumer"]
    end

    subgraph Messaging
        Kafka["Kafka :9092<br/>topic: transaction-events<br/>(Docker Compose)"]
    end

    subgraph Databases
        AuthDB[("bankapp_auth")]
        AcctDB[("bankapp_account")]
        TxnDB[("bankapp_transaction")]
    end

    Client -->|"1. All requests"| GW
    GW -->|"/api/auth/**"| Auth
    GW -->|"/api/accounts/**"| Acct
    GW -->|"/api/transactions/**"| Txn

    Auth -.->|register| Eureka
    Acct -.->|register| Eureka
    Txn -.->|register| Eureka
    Notif -.->|register| Eureka
    GW -.->|discover services| Eureka

    Auth -.->|"fetch config on startup"| Config
    Acct -.->|"fetch config on startup"| Config
    Txn -.->|"fetch config on startup"| Config
    Notif -.->|"fetch config on startup"| Config
    GW -.->|"fetch config on startup"| Config

    Txn -->|"Feign + @CircuitBreaker"| Acct
    Txn -->|"2. publish event (async)"| Kafka
    Kafka -->|"3. consume event"| Notif

    Auth --> AuthDB
    Acct --> AcctDB
    Txn --> TxnDB
```

**Solid arrows** = real request/event traffic. **Dashed arrows** = Eureka service discovery / Config Server lookups (registration, config fetch, and name resolution — not business data). Notice `transaction-service → Kafka → notification-service` is a one-way, fire-and-forget path — transaction-service never waits on notification-service, and the two are never directly connected.

---

## Services

### 1. Discovery Server (Eureka)
Every other service registers itself here on startup (`spring.application.name`, e.g. `account-service`, becomes `ACCOUNT-SERVICE` in the registry). The gateway and Feign clients look services up **by name**, never by hardcoded host:port — so a service can move, restart, or scale to multiple instances without anything else needing to change.

### 2. Config Server
Centralizes configuration that would otherwise be duplicated and error-prone to keep in sync — most importantly the `jwt.secret`, which **must** be identical between auth-service (which signs tokens) and api-gateway (which verifies them). Each service's full config lives as one YAML file (e.g. `auth-service.yml`) inside a `config-repo/` folder in this same GitHub repo. Config Server clones that repo (`spring.cloud.config.server.git.uri`, `search-paths: config-repo`) and serves each file over HTTP at `GET /{service-name}/default`. Every other service keeps only a minimal local `application.yml` — just its `spring.application.name` and `spring.config.import: optional:configserver:http://localhost:8888` — and fetches everything else from here at startup. The `optional:` prefix means a service still starts on its bare-minimum local config if Config Server happens to be unreachable, rather than failing outright.

### 3. API Gateway
The only service clients talk to directly. Built on Spring Cloud Gateway. A `JwtAuthenticationFilter` (a `GlobalFilter`) runs on every request:
- Requests to `/api/auth/register` and `/api/auth/login` pass through untouched (you can't require a token to get your first token).
- Every other request must carry `Authorization: Bearer <token>`. The filter validates the signature and expiry; on success it forwards the username downstream via an `X-User` header, so account-service and transaction-service know who's calling without re-parsing the JWT themselves.
- Routes requests to the right service by path prefix (`/api/auth/**`, `/api/accounts/**`, `/api/transactions/**`), resolving the target via Eureka (`lb://ACCOUNT-SERVICE`, not a fixed URL).

### 4. Auth Service
Handles `POST /api/auth/register` and `POST /api/auth/login`. Passwords are hashed with **BCrypt** before ever touching the database — the raw password is never stored. On success, issues a JWT signed with HMAC-SHA, containing the username (`sub`) and role as claims. Duplicate username/email checks return `409 Conflict`; failed login returns a **generic** `401` for both "user doesn't exist" and "wrong password," so the API can't be used to enumerate valid usernames.

### 5. Account Service
Owns account data — `POST /api/accounts` (create), `GET /api/accounts/{accountNumber}`, and internal `PUT /{accountNumber}/debit` / `/credit` endpoints (called by transaction-service, not customers directly). Balances use `BigDecimal`, never `double`/`float` — floating-point types can't exactly represent decimal fractions, and for money that's unacceptable. Every `Account` has a `@Version` field enabling **optimistic locking**: if two requests try to update the same account concurrently, the second one's stale-version update is rejected rather than silently overwriting the first (preventing a classic "lost update" bug).

### 6. Transaction Service
The most involved service. `POST /api/transactions/deposit|withdraw|transfer` each call account-service through a **Feign client**, wrapped in a **Resilience4j `@CircuitBreaker`**. Every attempt — successful or failed — is recorded as an immutable `Transaction` row (an audit log, never updated in place, only ever inserted). See the sections below for exactly how this works. After a successful transaction, it also **publishes an event to Kafka** (topic `transaction-events`) via a `KafkaTemplate` — asynchronously, fire-and-forget, so a slow or unavailable Kafka broker never delays or fails the actual money movement.

### 7. Notification Service
A pure Kafka consumer — no REST endpoints, no database. `@KafkaListener(topics = "transaction-events", groupId = "notification-group")` subscribes to the topic and, for every event, logs a human-readable message (e.g. *"Your account was credited with ₹500"*), standing in for what would be a real email/SMS send. It runs entirely decoupled from transaction-service — if notification-service is down for an hour, deposits/withdrawals/transfers keep working normally, and it simply catches up on the backlog once it's back, reading from wherever its consumer group's offset last stopped.

---

## Request Flow: JWT Authentication

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as API Gateway
    participant Auth as Auth Service
    participant Acct as Account Service

    Note over C,Auth: Step 1 — Get a token (no token required)
    C->>GW: POST /api/auth/login {username, password}
    GW->>Auth: forward (public route, no JWT check)
    Auth->>Auth: verify password (BCrypt.matches)
    Auth->>Auth: sign JWT (HMAC, shared secret)
    Auth-->>GW: 200 {token, username, role}
    GW-->>C: 200 {token, username, role}

    Note over C,Acct: Step 2 — Use the token on a protected call
    C->>GW: POST /api/accounts (Authorization: Bearer <token>)
    GW->>GW: JwtAuthenticationFilter validates signature + expiry
    alt token invalid or missing
        GW-->>C: 401 Unauthorized
    else token valid
        GW->>Acct: forward request + X-User header
        Acct-->>GW: 201 Created {account}
        GW-->>C: 201 Created {account}
    end
```

The core idea: login is how you prove identity *once*, with a password. The JWT it returns is what you present on every subsequent call, instead of resending credentials. The gateway checks the token on the way in; account-service and transaction-service *also* independently re-verify it on every request, rather than trusting that a request must have already passed through the gateway — see [Defense-in-Depth Security](#defense-in-depth-security) for why, and what that layered approach does and doesn't protect against.

---

## Request Flow: Money Transfer with Circuit Breaker

```mermaid
sequenceDiagram
    participant C as Client
    participant Txn as Transaction Service
    participant CB as Circuit Breaker
    participant Acct as Account Service

    C->>Txn: POST /api/transactions/transfer
    Txn->>CB: transfer() [@CircuitBreaker]

    alt circuit CLOSED (normal)
        CB->>Acct: debit(fromAccount) via Feign
        Acct-->>CB: 200 OK
        CB->>Acct: credit(toAccount) via Feign
        alt credit succeeds
            Acct-->>CB: 200 OK
            CB-->>Txn: save Transaction (SUCCESS)
            Txn-->>C: 200 OK
        else credit fails
            Acct-->>CB: error
            CB->>Acct: compensating credit(fromAccount) — refund sender
            CB-->>Txn: rethrow — triggers fallback
        end
    else circuit OPEN (account-service unhealthy)
        CB--xAcct: call short-circuited, no network attempt
        CB-->>Txn: transferFallback(request, throwable)
    end

    alt fallback: 404 from account-service
        Txn-->>C: 404 Not Found (account doesn't exist)
    else fallback: 400 from account-service
        Txn-->>C: 400 Bad Request (insufficient balance)
    else fallback: infrastructure failure
        Txn->>Txn: save Transaction (FAILED)
        Txn-->>C: 503 Service Unavailable
    end
```

**Two failure categories are deliberately handled differently:**
- **Business errors** (account not found, insufficient balance) return the correct `404`/`400` and are *excluded* from the circuit breaker's failure-rate calculation — a customer mistyping an account number shouldn't help trip the circuit for everyone else.
- **Infrastructure failures** (account-service down, slow, unreachable) return `503`, get logged as a `FAILED` transaction for audit purposes, and *do* count toward tripping the circuit.

**Compensating transaction:** if the debit succeeds but the credit leg fails, the code automatically re-credits the sender — a simple saga-style compensation so a partial failure doesn't leave money debited from one account and never credited anywhere.

---

## Event-Driven Flow: Kafka Notifications

```mermaid
sequenceDiagram
    participant C as Client
    participant Txn as Transaction Service
    participant K as Kafka (transaction-events)
    participant Notif as Notification Service

    C->>Txn: POST /api/transactions/deposit
    Txn->>Txn: debit/credit via Feign, save Transaction (SUCCESS)
    Txn-->>C: 200 OK (response sent immediately)
    Txn-)K: publish TransactionCompletedEvent (async, fire-and-forget)

    Note over Txn,K: Transaction service never waits on Kafka —<br/>publish failures are logged, not surfaced to the client

    K--)Notif: deliver event whenever consumer is ready
    Notif->>Notif: deserialize event, build message
    Notif->>Notif: log "NOTIFICATION -> account X: ..."
```

**Why this is asynchronous, not another Feign call:** unlike the transaction-service → account-service call (which *must* succeed for the money movement to be correct, hence the circuit breaker), a notification is inherently best-effort — nobody's balance should ever be blocked by a failed or slow notification. Kafka decouples the two completely: transaction-service publishes and immediately moves on, notification-service reads independently whenever it's able to, and if it's down entirely, messages simply queue up in the topic until it recovers — no retry logic, no fallback method, no circuit breaker needed on this path at all.

**Key design choices:**
- **Message key = `accountNumber`** — guarantees all events for one account stay strictly ordered within a partition, even though ordering across different accounts doesn't matter.
- **`ErrorHandlingDeserializer`** wraps the real deserializer on the consumer side — if a message can't be deserialized, the container logs it and moves on to the next message instead of retrying the same broken message forever.
- **`spring.json.use.type.headers: false`** — the consumer ignores the producer's embedded class name (`__TypeId__` header) and always deserializes into its own local `TransactionCompletedEvent` class. Necessary because each service keeps its own independent copy of the event DTO, in a different package, and by default Kafka's JSON deserializer expects the exact same fully-qualified class name on both ends.

---

## Circuit Breaker State Machine

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPEN: failure/slow-call rate ≥ 50% over last 10 calls (min. 5 calls)
    OPEN --> HALF_OPEN: after 10s wait
    HALF_OPEN --> CLOSED: trial calls succeed
    HALF_OPEN --> OPEN: trial calls fail
    CLOSED --> CLOSED: call succeeds normally

    note right of CLOSED
        Normal operation.
        Every call to account-service
        is actually attempted.
        Resilience4j silently tracks
        the last 10 outcomes.
    end note

    note right of OPEN
        Calls short-circuit immediately.
        No network attempt is made.
        Fallback method runs instantly
        instead of waiting for a timeout.
    end note

    note right of HALF_OPEN
        Lets 3 trial calls through
        to test if account-service
        has recovered.
    end note
```

Configuration (`transaction-service/application.yml`):

| Setting | Value | Meaning |
|---|---|---|
| `sliding-window-size` | 10 | Look at the last 10 calls to decide health |
| `minimum-number-of-calls` | 5 | Don't evaluate failure rate until at least 5 calls have happened |
| `failure-rate-threshold` | 50% | Open the circuit if ≥50% of the window failed |
| `slow-call-duration-threshold` | 2s | A call taking longer than this counts as "slow" |
| `slow-call-rate-threshold` | 50% | Open the circuit if ≥50% of calls are slow, even if they "succeed" |
| `wait-duration-in-open-state` | 10s | Stay OPEN this long before trying HALF_OPEN |
| `permitted-number-of-calls-in-half-open-state` | 3 | Trial calls allowed through in HALF_OPEN |

---

## Tech Stack

- **Java 21**, **Spring Boot 4.1.0**, **Spring Cloud 2025.1.2**
- **Spring Web** — REST controllers
- **Spring Data JPA** + **Hibernate** — persistence, one MySQL database per service
- **Spring Security** (auth-service only) — BCrypt password hashing
- **jjwt 0.12.6** — JWT signing/parsing
- **Spring Cloud Netflix Eureka** — service discovery
- **Spring Cloud Config Server** (git-backed) — centralized configuration, reading `config-repo/` from this repo on GitHub
- **Spring Cloud Gateway** — API gateway, reactive filter-based routing
- **Spring Cloud OpenFeign** — declarative HTTP client for service-to-service calls
- **Resilience4j** (via `spring-cloud-starter-circuitbreaker-resilience4j`) — circuit breaker
- **Spring Kafka** — event publishing/consuming, backed by **Apache Kafka** running via **Docker Compose** (Kafka + Zookeeper)
- **Spring Boot Actuator** — health/circuit-breaker state inspection
- **Lombok** — boilerplate reduction (`@Data`, `@Builder`, `@RequiredArgsConstructor`)
- **MySQL 8**

---

## Prerequisites

- Java 21
- Maven (or use the included `mvnw` wrapper)
- MySQL 8 running locally on `3306` (default credentials `root`/`root` — sourced from `config-repo/*.yml`, override there if different)
- Docker Desktop — for running Kafka + Zookeeper via the included `docker-compose.yml`
- Internet access on first config-server startup, to clone this repo's `config-repo/` folder from GitHub
- IntelliJ IDEA (or any IDE) — each service is a separate Maven module

## Running the Project

Databases are auto-created on first connection (`createDatabaseIfNotExist=true`) — no manual `CREATE DATABASE` needed. Configuration for every service (ports, DB credentials, JWT secret, Resilience4j thresholds, gateway routes) lives in [`config-repo/`](./config-repo) in this same repo, served by `config-server` at runtime — see [Config Server](#2-config-server) above.

**Start Kafka first**, from the repo root:
```bash
docker-compose up -d
docker ps   # confirm both "kafka" and "zookeeper" containers show Up
```

**Start order matters** — `config-server` and `discovery-server` must be up before anything that depends on them:

1. `discovery-server` — wait until `http://localhost:8761` loads
2. `config-server` — verify it's serving config, e.g. `http://localhost:8888/auth-service/default` should return JSON
3. `auth-service`
4. `account-service`
5. `transaction-service`
6. `notification-service`
7. `api-gateway`

Verify all six application services (auth, account, transaction, notification, gateway, and config-server itself) show up under "Instances currently registered with Eureka" at `http://localhost:8761`.

## API Reference

All client traffic goes through the gateway at `http://localhost:8080`.

| Method | Endpoint | Auth required | Description |
|---|---|---|---|
| POST | `/api/auth/register` | No | Create a user, returns a JWT |
| POST | `/api/auth/login` | No | Authenticate, returns a JWT |
| POST | `/api/accounts` | Yes | Open a new account |
| GET | `/api/accounts/{accountNumber}` | Yes | Fetch account details |
| GET | `/api/accounts/user/{username}` | Yes | List a user's accounts |
| POST | `/api/transactions/deposit` | Yes | Deposit into an account |
| POST | `/api/transactions/withdraw` | Yes | Withdraw from an account |
| POST | `/api/transactions/transfer` | Yes | Transfer between two accounts |

Example — register, then create an account:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"bhavesh","email":"bhavesh@example.com","password":"secret123"}'

curl -X POST http://localhost:8080/api/accounts \
  -H "Authorization: Bearer <token from above>" -H "Content-Type: application/json" \
  -d '{"ownerUsername":"bhavesh","accountType":"SAVINGS","initialDeposit":1000}'
```

### Postman Collection

A ready-to-import Postman collection and environment live in [`postman/`](./postman) — every endpoint in the API Reference table above, pre-built:

- `postman/Banking-App.postman_collection.json`
- `postman/Banking-App.postman_environment.json`

**To use:**
1. Import both files into Postman
2. Select the **Banking App** environment (top-right dropdown)
3. Run **Login User** once — its post-response script (`pm.environment.set("token", response.token)`) automatically saves the returned JWT into the environment's `token` variable
4. Every other protected request is already configured with **Authorization → Bearer Token → `{{token}}`**, so it authenticates automatically — no manual copy-pasting of tokens between requests. Just re-run Login when the token expires (1 hour, per `jwt.expiration-ms`).

## Database Design

Each service owns a completely separate MySQL database — no shared tables, no cross-database foreign keys:

- **`bankapp_auth.users`** — `id`, `username` (unique), `email` (unique), `password` (BCrypt hash), `role`, `created_at`
- **`bankapp_account.accounts`** — `id`, `account_number` (unique, generated), `owner_username` (soft reference, not a foreign key), `account_type`, `balance` (`DECIMAL(19,2)`), `status`, `version` (optimistic lock), `created_at`
- **`bankapp_transaction.transactions`** — `id`, `type`, `from_account_number`, `to_account_number`, `amount`, `status`, `failure_reason`, `timestamp` — an append-only audit log; rows are never updated, only inserted, including failed attempts

`accounts.owner_username` deliberately does **not** reference `users` as a real foreign key — it can't, since `users` lives in a different database owned by a different service. This is a genuine microservices tradeoff: giving up referential integrity in exchange for true service independence.

## Design Decisions Worth Knowing

- **`BigDecimal` for all money fields** — never `double`/`float`, which can't represent decimal fractions exactly and would introduce rounding errors unacceptable for currency.
- **Optimistic locking (`@Version`) on `Account`** — protects balances from lost updates when concurrent requests hit the same account, without the cost of pessimistic row-locking.
- **DTOs never expose entities directly** — every service has separate `@Entity` and `*Dto` classes, so internal schema changes don't automatically become breaking API changes.
- **DTOs are duplicated across services, deliberately** — transaction-service has its own copy of `AccountDto`/`BalanceUpdateRequest`, not a shared import from account-service. Services don't share code; they share an HTTP contract. This is the accepted cost of true independent deployability.
- **Centralized exception handling (`@RestControllerAdvice`)** — business exceptions (`AccountNotFoundException`, `InsufficientBalanceException`) are thrown from service-layer code with zero HTTP knowledge; a single `GlobalExceptionHandler` per service maps them to the correct status code, keeping controllers free of `try/catch`.
- **Generic error messages on login failure** — "invalid username or password" for both a nonexistent user and a wrong password, closing a username-enumeration side channel.
- **Two distinct credential types for two distinct trust relationships** — a customer JWT proves "who is this end user," checked on every customer-facing endpoint; a separate internal API key proves "is this call actually coming from transaction-service," checked only on account-service's `/debit`/`/credit`. A customer JWT is deliberately *not* accepted there — customers should never call those endpoints directly, only transaction-service should, after its own business logic runs.
- **Notifications are async and fire-and-forget, deliberately not another Feign call** — a slow or down notification-service should never delay or fail an actual deposit/withdrawal/transfer. Kafka enforces that decoupling structurally, rather than relying on careful try/catch and fallback logic the way the synchronous account-service calls need.

## Defense-in-Depth Security

Early in this project, only the gateway validated JWTs — account-service and transaction-service trusted that any request reaching them had already been checked. That's a real gap: each service was still directly reachable on its own port (`:8082`, `:8083`), completely bypassing the gateway, with zero authentication.

**What was actually fixed:**
- **account-service and transaction-service now independently re-verify every JWT themselves**, using the same shared secret as the gateway, via a servlet `Filter` (`JwtAuthenticationFilter` in each service) — not just trusting an `X-User` header that anyone could forge by hitting the service directly.
- **account-service's `/debit` and `/credit` are separately gated by an internal API key** (`X-Internal-Api-Key`), checked instead of a JWT on those two routes specifically, since no customer should ever call them directly — only transaction-service should, as part of fulfilling a deposit/withdraw/transfer. transaction-service attaches this header automatically to every outgoing Feign call via a `RequestInterceptor` bean — the calling code in `TransactionService` never has to know the header exists.

**What this does and doesn't solve — worth being precise about:**
- It closes *unauthenticated* direct access — hitting any service's port with no token, or a bad one, now correctly returns `401`/`403` everywhere, not just at the gateway.
- It does **not** prevent a request carrying a *valid* JWT from bypassing the gateway entirely and hitting a service directly — a token is portable proof of identity, and a downstream service re-verifying it has no way to know whether the request physically traveled through the gateway first or not.
- The only way to truly guarantee *all* traffic passes through the gateway is **network-level isolation** — in a real deployment, account-service and transaction-service simply wouldn't have public-facing ports at all, only reachable from a private network the gateway sits in front of. That's infrastructure (a VPC, Docker's internal network, Kubernetes namespacing), not application code, and isn't something meaningfully demonstrable running everything on one localhost machine — noted here as the actual production-grade fix, with the JWT re-validation and internal API key as the practical, honest defense-in-depth achievable at the code level.

## What I Learned

This project was built to understand — not just produce — the following:
- **Service discovery** with Eureka, and why hardcoded service URLs don't work in a system where instances can move/scale
- **JWT-based stateless authentication** — signing, validating, and the tamper-evident (not secret) nature of a JWT payload
- **Declarative HTTP clients with Feign**, and how they compare to manually building `RestTemplate` calls
- **Resilience4j circuit breakers** — the CLOSED/OPEN/HALF_OPEN state machine, sliding windows, slow-call detection, and the difference between failures that should and shouldn't count against a circuit's health
- **Compensating transactions** as a lightweight alternative to distributed transactions across services
- **Optimistic locking** as a concurrency-safety mechanism, and how it differs from pessimistic locking
- **Defense-in-depth authentication** — why "the gateway already checked it" is a dangerous assumption for downstream services to make, the difference between authenticating a user (JWT) and authenticating a calling service (internal API key), and the honest limits of what re-validating a token can and can't protect against without real network isolation
- **Event-driven architecture with Kafka** — topics, partitions, consumer groups, and why a message key matters for ordering; the concrete difference between a synchronous Feign call (needs a circuit breaker, caller waits) and an asynchronous Kafka publish (fire-and-forget, caller never waits); how multiple independent consumer groups could each read the same topic without any coordination or code changes to the producer
- Real debugging: a fallback-method name mismatch caught only by tracing the exact string Resilience4j looks up at runtime, a copy-paste field bug (`toAccountNumber` vs `fromAccountNumber`) caught by reasoning through what the data should actually mean, and — the hardest of the project — a chain of three compounding Kafka/config bugs (missing `spring-cloud-starter-config` causing a silent config fallback, a missing `spring-boot-starter-web` breaking Eureka's registration assumptions, and a cross-service JSON type-header mismatch causing an infinite deserialization retry loop) traced one real log line at a time

## Known Limitations & Next Steps

- **No refresh tokens** — only short-lived access tokens currently
- **Compensating transaction has a gap** — if the *reversal* credit call (after a failed transfer credit) itself fails, there's no durable retry; a production system would use an outbox pattern or saga orchestrator instead of a single best-effort inline attempt
- **No true network isolation** — account-service and transaction-service are still reachable on their own ports directly; a *valid* JWT presented straight to them still works, bypassing the gateway. See [Defense-in-Depth Security](#defense-in-depth-security) for what is and isn't mitigated at the code level, and why full isolation needs infrastructure (VPC/Docker network/Kubernetes namespacing), not more application code
- **Notifications are logged, not actually sent** — notification-service logs a message in place of a real email/SMS integration; the event-driven plumbing is real, the delivery channel is a stand-in
- **No distributed tracing** — would help once debugging cross-service call chains gets harder, especially now that a request can fan out into an async Kafka event on top of the synchronous call chain
- **Kafka has one partition** — `transaction-events` was allowed to auto-create with the broker's default partition count rather than an explicitly configured value; fine at this scale, but worth setting explicitly to actually demonstrate parallel consumption
- **No Docker Compose for the Spring services themselves** — Kafka + Zookeeper run via `docker-compose.yml`, but the 7 Spring Boot services + MySQL still require manually starting each one in order
