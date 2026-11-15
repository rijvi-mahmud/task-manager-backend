# Task Manager backend

Java REST API for shared task boards, accounts, workspace membership, and task
assignment. This guide covers the implementation, API formats, database lifecycle,
operational settings, and verification commands. The React client lives in
[task-manager-frontend](https://github.com/rijvi-mahmud/task-manager-frontend).

## Technical specification

| Area | Implementation |
| --- | --- |
| Language/runtime | Java 17; Maven Wrapper |
| Framework | Spring Boot 4.1.2; Spring MVC |
| Authentication | Spring Security; server-side sessions stored by Spring Session JDBC |
| Persistence | Spring Data JPA/Hibernate; PostgreSQL 17 |
| Schema management | Flyway at application startup; Hibernate `ddl-auto=validate` |
| Migration generation | Development-only Liquibase 5.0.4 Maven plugin with Hibernate 7 extension |
| Validation | Jakarta Bean Validation on HTTP records; domain validation for service callers |
| API documentation | springdoc OpenAPI 3.1, Swagger UI |
| Rate limiting | Bucket4j 8.16.1 with Caffeine idle expiration; per backend instance |
| Logging | SLF4J/Logback, request correlation through MDC |
| Tests | JUnit, Mockito, MockMvc, PostgreSQL Testcontainers |

Exact dependencies are declared in [pom.xml](pom.xml); Spring Boot manages versions
unless the POM explicitly overrides them.

## Features covered

| Feature | Behavior |
| --- | --- |
| Registration | Create an account or claim an existing pending invitation; create a first workspace when needed |
| Sign-in/sign-out | Password authentication, session ID rotation, explicit context persistence, server-side logout |
| Password reset | Log-only reset links, SHA-256 token storage, 30-minute expiry, single-use completion, session revocation after commit |
| Workspaces | Create/list/open shared boards; owners rename/delete; deletion cascades to tasks and memberships |
| Membership | Owners add by email; new emails create pending accounts; members can leave; the last owner cannot leave |
| Tasks | Create, read, edit, move between statuses, assign/unassign, and delete |
| Permissions | Workspace membership on reads/writes; owners or task creators can delete tasks |
| Search/filtering | Status, priority, assignee, unassigned, text, and inclusive due-date bounds |
| Ordering/pagination | Server-side sort with ID tie-breakers; zero-based pages and bounded page sizes |
| Statistics | Workspace-wide total, per-status counts, and overdue count, independent of list filters |
| Concurrent editing | Full task updates require the last-read version; stale updates return `409` |
| Member removal | Unassign that person's tasks in the workspace and advance affected task versions |
| API feedback | Consistent success envelopes, RFC 9457 errors, field validation messages, request IDs |
| Operations | Request timing, authentication diagnostics, committed change logs, rate limiting, health endpoint |

No email server, email delivery integration, email verification flow, OAuth provider,
or MFA service is configured.

## Local setup

Requirements: JDK 17+, a reachable PostgreSQL 17 database, and network access for
uncached Maven dependencies. Docker is needed for the supplied integration tests
and is optional for development when using another PostgreSQL instance.

Start the optional local database:

```sh
docker compose up -d
```

Then:

```sh
cp .env.example .env
# Edit .env with your development database settings.
./mvnw spring-boot:run
```

Run the application with `backend` as the working directory: the optional `.env`
import is relative to that directory. Actual environment variables override file
values. Do not commit `.env`.

| URL | Purpose |
| --- | --- |
| `http://localhost:8080/api/v1` | Application API |
| `http://localhost:8080/swagger-ui.html` | Interactive API documentation |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8080/actuator/health` | Application/database health |

## Configuration contract

Settings live in [application.properties](src/main/resources/application.properties).
Local examples are in [.env.example](.env.example).

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | Required | PostgreSQL JDBC URL |
| `DB_USERNAME`, `DB_PASSWORD` | Required | Runtime database credentials |
| `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD` | Runtime credentials | Separate Flyway/schema-owner credentials |
| `DB_SCHEMA` | `taskmanager` | Application schema, Hibernate schema, and session-table namespace |
| `DB_APP_ROLE` | `taskmanager_app` | Runtime role granted data access by migration V4 |
| `DB_POOL_SIZE` | `5` | Maximum Hikari pool size |
| `APP_TIME_ZONE` | `UTC` | Business time zone used for calendar-day/overdue calculations |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated allowed browser origins |
| `FRONTEND_URL` | `http://localhost:5173` | Base URL of logged password-reset links |
| `SESSION_TIMEOUT` | `12h` | Session inactivity timeout |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` for HTTPS deployments |
| `AUTH_RATE_LIMIT_ENABLED` | `true` | Enable authentication throttling |
| `AUTH_RATE_LIMIT_WINDOW` | `1m` | Bucket refill interval and idle cache lifetime |
| `AUTH_ATTEMPTS_PER_IP` | `60` | Attempts per operation/IP/refill interval |
| `AUTH_ATTEMPTS_PER_ACCOUNT` | `10` | Attempts per operation/normalized email/refill interval |
| `AUTH_RATE_LIMIT_MAX_KEYS` | `10000` | Maximum cached identity/operation buckets |
| `APP_LOG_LEVEL` | `INFO` | Application logging level; `DEBUG` enables read diagnostics |
| `LOGGING_FILE_NAME` | Unset | Optional Spring Boot log file; console output is the default |

Use separate runtime and schema-owner roles when deploying with least privilege.
The dedicated schema keeps application tables separate from Supabase's `public`
schema; access still depends on the database's actual roles and grants.

## Package structure and responsibilities

```text
src/main/java/com/example/backend/
├── config/                 Security, CORS/versioning, time, OpenAPI, typed settings
├── common/
│   ├── api/                Response envelopes, metadata, pagination, error mapping
│   ├── exception/          Application exceptions
│   ├── observability/      Request logging and commit-only change logs
│   └── validation/         Shared value validation and email normalization
├── identity/
│   ├── api/                Authentication HTTP contracts and controllers
│   ├── command/            Registration, invitations, password-reset operations
│   ├── domain/             User/token entities and repositories
│   ├── query/              Current-user read service and view
│   └── security/           Principals, authentication, revocation, limiter adapter
├── workspace/
│   ├── api/                Workspace and membership HTTP contracts
│   ├── command/            Workspace/membership changes and registration listener
│   ├── domain/             Workspace/membership entities and write repositories
│   ├── query/              Workspace/member projections and read repositories
│   └── WorkspaceAccess     Shared workspace authorization policy
└── task/
    ├── api/                Task HTTP contracts and request records
    ├── command/            Task commands and membership-removal listener
    ├── domain/             Task entity, enums, write repository
    └── query/              Search criteria, sort policy, projections, read repository
```

Controllers translate HTTP into immutable commands and query calls. Transactional
command services authorize and mutate domain entities. Read-only query services
return projections rather than serializing JPA entities. Aggregates refer to other
aggregates by IDs; SQL foreign keys enforce those relationships. This is light
CQRS over one database, without a separate read store or event-sourcing system.

## ORM models and schema files

| Java ORM entity | PostgreSQL table | Responsibility |
| --- | --- | --- |
| `identity/domain/User.java` | `users` | Normalized email, display name, encoded password; null password means pending |
| `identity/domain/PasswordResetToken.java` | `password_reset_tokens` | Hashed reset grant, expiry and usage timestamps |
| `workspace/domain/Workspace.java` | `workspaces` | Shared board |
| `workspace/domain/WorkspaceMember.java` | `workspace_members` | Composite workspace/user key and `OWNER`/`MEMBER` role |
| `task/domain/Task.java` | `tasks` | Task content, assignment, creator/editor, version and timestamps |
| Spring Session JDBC | `spring_session`, `spring_session_attributes` | Persistent HTTP sessions; framework-owned model |

SQL migrations are in [src/main/resources/db/migration](src/main/resources/db/migration).
V1–V5 establish and harden the original board; V6 introduces accounts/workspaces
and remaps existing tasks; V7 adds JDBC sessions. Flyway records applied migrations
in `flyway_schema_history`. Never edit an already-applied migration.

### Generate a migration from entities

Export `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for a development database that
already has the committed Flyway migrations applied. Maven does not load Spring
Boot's `.env` import automatically. From `backend`:

```sh
./mvnw -Pmigration compile liquibase:diff -Dmigration.name=add_task_notes
```

The profile compares compiled Hibernate mappings to PostgreSQL and generates a
file such as `V20261115120000__add_task_notes.sql` in the migration directory.
Use snake_case descriptions. For another schema, add
`-Dmigration.schema=your_schema` matching `DB_SCHEMA`.

Review the generated SQL, commit it with the model changes, then start the app to
apply pending migrations through Flyway. `liquibase:diff` itself does not apply the
changes. The profile is a generation tool; Liquibase is not the runtime runner.

The configured comparison covers tables, columns and primary keys. Session tables
and Flyway history are excluded. Existing SQL-owned foreign keys, indexes, checks,
grants, and data transformations remain manually maintained. Renames may appear
as drop/add operations; required columns on populated tables may need a backfill.
Generation compares against the development database, not an EF Core-style model
snapshot, so unrelated database drift must be excluded during review.

## Authentication, authorization and concurrency

The browser sends the HttpOnly `SESSION` cookie automatically. The application
rotates an existing session ID on sign-in and explicitly saves the security
context. Cookies use `SameSite=Lax`; the frontend uses same-origin fetches.

Every `POST`, `PUT`, `PATCH`, and `DELETE`, including public authentication writes,
must echo the readable `XSRF-TOKEN` cookie in `X-XSRF-TOKEN`. Request
`GET /api/v1/auth/csrf` when the token cookie is absent. Successful login/logout
clear the previous CSRF token; fetch a fresh token before the next write.

| Operation | Policy |
| --- | --- |
| Read/write a workspace's tasks | Workspace member |
| Create a workspace | Signed-in user; creator becomes owner |
| Rename/delete workspace, add members | Workspace owner |
| Remove another member | Workspace owner |
| Leave a workspace | Self; last remaining owner cannot leave |
| Delete a task | Its creator or a workspace owner |
| Access another workspace as an outsider | `404` |
| Attempt an owner-only action as a member | `403` |

Passwords are encoded with Spring Security's delegating password encoder
(currently bcrypt). New passwords require at least eight Unicode code points,
are capped at 72 UTF-8 bytes, and cannot be all whitespace. Emails are normalized
to lowercase. Claiming an invitation locks the existing user row before checking
whether it was already claimed; concurrent new-account inserts are protected by
the unique email constraint.

Full task updates verify the supplied `version` and Hibernate also enforces
optimistic locking at flush. Status/assignment patches carry no client version;
a genuine overlapping database update can still conflict. Member removal locks
workspace/membership rows, bulk-unassigns tasks, and advances task versions.
Successful domain-change logs run after commit, so rolled-back writes do not
produce a success audit log.

## Team collaboration and update delivery

Workspace membership gives multiple accounts access to one shared set of tasks.
Every request resolves workspace permissions on the server; board data is not
stored separately per browser or tab. PostgreSQL transactions make committed
changes available to later reads. Task versions and membership-removal locks
protect the editing and assignment invariants described above.

The frontend discovers other clients' changes by polling the REST API every
15 seconds while relevant queries are observed in a visible tab, and by refetching
stale data when returning to a tab. The initiating client also revalidates affected
queries after its own writes. The backend exposes no WebSocket or SSE endpoint,
change subscription, presence indicator, or typing/drag broadcast.

Tabs in one browser profile share the session cookie. They have independent
frontend caches, and recheck the session on focus or after a protected-request
`401`. Separate browser profiles/private windows allow testing two accounts at
once. Password-reset completion revokes the affected account's persisted sessions
after commit; other clients discover that revocation on a subsequent session or
protected request.

Polling is a deliberate fit for this project's collaboration needs. Visible
changes are eventually refreshed rather than delivered instantly. If immediate
server-to-client notifications become a requirement, SSE or WebSockets would be
an additional feature, including workspace authorization and multi-instance event
delivery; neither is implied by the current transaction logging events.

## HTTP and JSON format specification

The canonical major-version prefix is `/api/v1`; JSON uses camelCase properties.
Enums are uppercase strings. IDs and versions are JSON numbers; dates are ISO
calendar strings (`yyyy-MM-dd`), and instants are UTC ISO 8601 strings.

A successful JSON response has exactly the transport fields `data` and `meta`:

```json
{
  "data": { "id": 1, "email": "ada@example.com", "displayName": "Ada" },
  "meta": { "requestId": "request-123", "timestamp": "2026-11-15T12:00:00Z" }
}
```

Lists put an array in `data`. Task pages put
`{ "items": [], "page": 0, "size": 25, "totalItems": 0, "totalPages": 0 }` in `data`.
Bodyless acknowledgements (`202`/`204`) do not contain an envelope. Created
resources return `201` with `Location`.

Errors use `application/problem+json` (RFC 9457), without a success envelope:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/v1/workspaces/1/tasks",
  "requestId": "request-123",
  "timestamp": "2026-11-15T12:00:00Z",
  "errors": { "title": "Title is required" }
}
```

`errors` is present for field validation. HTTP status determines success/failure:
`400` invalid input, `401` missing/invalid authentication, `403` forbidden/CSRF,
`404` unavailable resource, `409` conflicting state, `429` throttled authentication,
and `500` unexpected failure. Unexpected failures return a generic detail and log
the exception. `X-Request-ID` correlates responses and logs, including bodyless
responses; throttling also returns `Retry-After` in seconds.

### Endpoint catalogue

All paths below are relative to `/api/v1`.

| Method | Path | Request/purpose |
| --- | --- | --- |
| GET | `/auth/csrf` | Issue CSRF cookie; `204` |
| POST | `/auth/register` | `{email, displayName, password}`; register/sign in; `201` |
| POST | `/auth/login` | `{email, password}`; current user |
| POST | `/auth/logout` | End session; `204` |
| GET | `/auth/me` | Current user; `401` if signed out |
| POST | `/auth/password-reset` | `{email}`; log a link when eligible; `202`, subject to validation/throttling |
| POST | `/auth/password-reset/confirm` | `{token, newPassword}`; consume grant; `204` |
| GET / POST | `/workspaces` | List mine / create with `{name}` |
| GET / PUT / DELETE | `/workspaces/{workspaceId}` | Read / rename with `{name}` / delete |
| GET / POST | `/workspaces/{workspaceId}/members` | List / add with `{email}` |
| DELETE | `/workspaces/{workspaceId}/members/{userId}` | Remove or leave |
| GET / POST | `/workspaces/{workspaceId}/tasks` | Filtered page / create task |
| GET | `/workspaces/{workspaceId}/tasks/stats` | Unfiltered workspace counts |
| GET / PUT / DELETE | `/workspaces/{workspaceId}/tasks/{id}` | Read / full update / delete |
| PATCH | `/workspaces/{workspaceId}/tasks/{id}/status` | `{status}` |
| PATCH | `/workspaces/{workspaceId}/tasks/{id}/assignee` | `{assigneeId}`; null unassigns |

Create-task body: `{title, description, status, priority, dueDate, assigneeId}`.
Full update uses the same fields plus `version`. Title is required, maximum 120
characters; description is optional, maximum 1000. Status is `TODO`, `IN_PROGRESS`,
or `DONE`; priority is `LOW`, `MEDIUM`, or `HIGH`. Workspace/display names have a
maximum of 80 characters; email has a maximum of 120.

### Task query parameters

| Parameter | Allowed value/meaning |
| --- | --- |
| `status` | `TODO`, `IN_PROGRESS`, `DONE`; omitted means all |
| `priority` | `LOW`, `MEDIUM`, `HIGH`; omitted means all |
| `assigneeId` | User ID; membership is enforced when assigning |
| `unassigned` | Boolean, default false; cannot combine true with `assigneeId` |
| `q` | Case-insensitive literal substring in title/description; maximum 120 characters |
| `dueFrom`, `dueTo` | Optional inclusive dates in years 0001–9999 |
| `sort` | `NEWEST` (default), `DUE_DATE`, `PRIORITY` |
| `page` | Zero-based integer, default 0 |
| `size` | Integer 1–100, default 25 |

Filters combine with AND and run before pagination. Either date bound can be
omitted; undated tasks are excluded when a bound exists. Reversed or invalid dates
return `400`. Statistics deliberately ignore filters. The business clock determines
whether a task is overdue: due date before today and status not `DONE`.

## Logging and rate limiting

Request logs contain method, path, status and elapsed milliseconds. Successful
requests use INFO, client errors WARN, and server errors ERROR. A validated or
generated request ID is placed in MDC and restored after the request. Query
strings and bodies are excluded from routine request logs.

Authentication logs cover failed/confirmed sign-in, sign-out, throttling and session
revocation. Transaction events log identifier-only task/workspace/member changes
after commit. Debug read logs contain resource IDs rather than search text.
The intentional reset-link logger contains reset credentials; protect access to
that output. No email delivery occurs.

Bucket4j manages consumption and interval refill. Caffeine expires idle identity
buckets. The adapter adds operation/IP/account keys and rejects admission when the
cache is full, preserving active budgets. Limits are local to each application
instance. A multi-instance deployment requires shared or edge rate limiting;
behind a proxy, configure trusted remote-address handling deliberately.

## Testing, build and deployment

From `backend`:

```sh
./mvnw test
./mvnw package
./mvnw -Pmigration validate
```

Run focused tests with `./mvnw -Dtest=UserRegistrationTest test`; the real claim
race is covered by `UserRegistrationConcurrencyTest` and requires Docker/PostgreSQL.
API, session-cookie, migration/schema, and transaction integration tests use
Testcontainers. Unit tests and MVC slices do not need Docker, but all builds need
resolved Maven dependencies.

For deployment, run the packaged application with database settings supplied by
the environment. Use HTTPS, secure cookies, the correct frontend URL, and a
same-origin reverse proxy serving the SPA and `/api`. Flyway applies pending
migrations before Hibernate validates the schema. Expose only the intended health
endpoint and restrict access to reset-link logs.

Current verification: the two focused registration unit tests passed using cached
dependencies, and the registration/concurrency test sources compiled. Maven
profile/model validation has also passed. However, the new
Bucket4j/Caffeine and Liquibase artifacts could not be downloaded in the restricted
workspace. Full backend compilation, new dependency-backed tests, real PostgreSQL
locking/session checks, and migration generation still require verification in an
environment with Maven Central access and Docker. This is not a production-readiness
or complete accessibility/security certification.

## Implementation conventions and deliberate limits

Use lowercase Java packages, PascalCase classes/records, camelCase methods/fields,
and UPPER_SNAKE_CASE constants. Place shared configuration in `config`, common
value rules in `common/validation`, and feature rules in the feature. Prefer
constructor injection, typed request/view records, parameterized queries and
service-level transactions. Keep credentials out of record `toString()` output.

The project intentionally has no email ownership verification, social login, MFA,
push updates, or manual ordering inside a task column. Offset pagination is not a
snapshot of a board changing concurrently; clients deduplicate displayed cards.
SQL-only constraints and migration backfills need explicit review. These are scope
and deployment limits, separate from the registration and stale-session findings
addressed by the current fixes.

## Official references

- [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Spring Security SPA CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)
- [Hibernate locking](https://docs.hibernate.org/orm/7.4/userguide/html_single/#locking)
- [Liquibase Hibernate compatibility](https://github.com/liquibase/liquibase-hibernate)
- [Liquibase schema diff generation](https://docs.liquibase.com/community/reference-guide-5-0/database-inspection-change-tracking-and-utility-commands/diff-changelog)
- [Bucket4j reference](https://bucket4j.com/8.16.1/toc.html)
- [Caffeine eviction](https://github.com/ben-manes/caffeine/wiki/Eviction)
- [Spring Boot logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
