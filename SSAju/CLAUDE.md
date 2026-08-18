# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> This file covers the `SSAju/` Gradle project itself (the actual Java source tree). See the repo-root [`../CLAUDE.md`](../CLAUDE.md) for workflow/security rules that apply to the whole `backend-ssa-fy-ju` repo, and `../skills/*.md` for the full style/architecture/git guides — this file only summarizes what's needed to navigate and build the code.

## Commands

All commands run from this directory (`SSAju/`):

```bash
./gradlew build                                          # full build
./gradlew bootRun                                         # run locally (loads application-local.yaml automatically)
./gradlew test                                             # run all tests
./gradlew test --tests "ssafy.SSAju.service.CareerFortuneServiceTest"                              # single class
./gradlew test --tests "ssafy.SSAju.service.CareerFortuneServiceTest.shouldReturnCareerTimingWhenValidInput"  # single method
./gradlew test --tests "*ServiceTest"                       # by pattern
./gradlew test jacocoTestReport                            # tests + coverage
./gradlew clean build                                       # if build cache issues
```

Local dev needs `application-local.yaml` (gitignored, not committed) and a running MySQL 8; integration tests spin up MySQL via Testcontainers (needs Docker running). Unit tests use H2. Test source also has a `concurrency/` package (race-condition/performance tests) alongside the usual `service/controller/integration/admin`.

**Test-before-commit is mandatory** in this repo — see `../skills/git-workflow.md`. Never commit without `BUILD SUCCESSFUL`, and commit messages must end with a `[Test Passed]` footer.

## Architecture

Spring Boot 4.0.5 / Java 21 / Gradle 9.4.1. Entry point: `ssafy.SSAju.SsAjuApplication`. Spring AI is on a milestone release (`2.0.0-M4`, needs milestone/snapshot repos in `build.gradle`). Uses `spring-retry` + `spring-aspects` directly (not `spring-boot-starter-aop`) — see `build.gradle` comment for why. API docs via `springdoc-openapi` 3.0.3.

### Two parallel feature areas under `ssafy.SSAju`

- **`career/`** — saju fortune analysis, career consultation (OpenAI), company compatibility domain logic. Has its own `entity/enums/domain/converter/mapper/provider/util/validator/caller` sub-packages, but **no `controller/service/repository`** — those live at the top level (see below). `career/util/` holds domain calculators (`TenGodCalculator`, `HiddenStemCalculator`); `career/provider/` holds `PromptProvider`.
- **Top-level `controller/ service/ repository/ entity/ dto/ config/ filter/ exception/ handler/ util/ annotation/ aspect/ event/ security/`** — this is a mixed bag: both career-domain controllers/services/repositories (`CareerTimingController`, `CareerFortuneService`, etc.) **and** user-management (auth, JWT, profiles) live directly here side by side, not separated by domain. When looking for "where does X live," check top-level `controller/service/repository` first regardless of whether X is career or user-management related.
- **`admin/`** — server-rendered (Thymeleaf) admin dashboard, cookie/JWT-based session auth, separate from the JSON REST API. Has its own `config/ controller/ service/ dto/ repository/ validation/` — note admin controllers return views/templates (`resources/templates/admin/`), not JSON.

When adding code, decide first which of these three areas it belongs to — they have different auth mechanisms and response conventions (JSON API vs SSR HTML). Within the top-level packages, career vs. user-management is a naming/convention distinction only, not a package boundary.

### Cross-cutting infrastructure worth knowing about

- **`annotation/` + `aspect/AuditLoggingAspect.java`** — AOP-based audit logging (`@AuditLog`, `@AuditableRequest`), runs outside the transaction advisor (see recent commit history — ordering was deliberately fixed to run after tx commit).
- **`event/`** — Spring application events (e.g. `LoginAttemptEvent` + listener) for decoupling side effects (like login-failure tracking) from the main auth flow.
- **`security/`** — JWT plumbing separate from `config/SecurityConfig.java`: `JwtAuthenticationEntryPoint`, `JwtAccessDeniedHandler`, `JwtExceptionFilter`.
- **`config/`** also includes `AsyncConfig`, `ClockConfig` (inject `Clock` instead of `new Date()`/`LocalDateTime.now()` for testability), `JacksonConfig`, `WebMvcConfig`, `JpaAuditingConfig`, besides the security/REST-client configs below.

### Request flow & layering (all three areas)

`Controller → Service → Repository → DB`, strictly one-directional. Business logic belongs only in Service; Controllers are thin (HTTP + DTO↔Entity), Repositories are pure Spring Data JPA/JdbcTemplate. All exceptions are thrown as custom exceptions (never swallowed by try/catch) and handled centrally by `handler/SajuGlobalExceptionHandler.java` via `@RestControllerAdvice`. Full rules: `../skills/architecture-guide.md`.

### External integrations (career domain)

Three external systems, each behind `RestClient` + `@Retryable` (config beans in `config/FastApiRestClientConfig.java`, `config/PublicDataRestClientConfig.java`, `config/ChatClientConfig.java`, `config/RetryConfig.java`):

1. **FastAPI** (`http://fastapi:8000/api/saju/calculate`) — computes only raw pillars/stems/branches/five-elements from birth date+time. Ten-gods and hidden-stems are deliberately *not* computed there — Spring owns that domain logic (`TenGodCalculator`, `HiddenStemCalculator` in `career/`), so FastAPI can change without touching domain correctness.
2. **OpenAI** (via Spring AI `ChatClient`, JSON-mode) — career consultation, returns a large nested-record response (`CareerAdviceResponse` + 14+ nested records). Prompt building is externalized to `PromptProvider`, never inlined in a Service.
3. **공공데이터 API** — company founding year lookup, falls back to manual user input if not found.

Retry rule (`RestClient` + `@Retryable`): 5xx and network/timeout exceptions must be re-thrown as-is so `@Retryable` can catch them; 4xx must be converted to a non-retryable domain exception (e.g. `InvalidSajuDataException`). Never convert 5xx into a custom exception — it breaks retry.

**`@Transactional` exception**: any Service method that calls an external API (FastAPI/OpenAI/공공데이터) must NOT be `@Transactional` — DB connections would be held open across slow network I/O. Individual DB operations get their own transaction at the Repository layer instead (trades atomicity for connection-pool safety; see `../skills/code-style-guide.md` "트랜잭션" section for the reasoning).

### Data normalization pattern (JSON column exception for AI/derived-analysis results)

The default is still: prefer a normalized child entity + repository over a JSON blob for query/index-ability and type safety (see `../skills/architecture-guide.md` "엔티티 정규화 패턴").

**Exception**: `SajuResult.tenGodHiddenStemAnalysis`, `CareerConsultation.resultJson`, and `CompanyCompatibility.resultJson` are stored as single `json` columns via domain-specific `AttributeConverter`s (`career/converter/TenGodHiddenStemConverter.java`, `ConsultationResultConverter.java`, `CompatibilityResultConverter.java`, all sharing `CareerJsonObjectMapperSupport`'s `ObjectMapper`), not as normalized child entities. This replaced ~24 direct child + several grandchild entities (see `../specs/006-career-json-migration/`). Reasons this domain deliberately deviates from the default:
- None of the sub-fields (ten-god scores, AI narrative sections, per-month forecasts, etc.) are ever queried, filtered, or indexed on individually — only the root entity is looked up (by user/profile/month), then the whole result is read back as one unit.
- `CareerConsultation`'s content comes from OpenAI and its shape evolves with prompt changes; a JSON column avoids a migration + child-entity + mapper change for every new AI response field.
- Normalized storage required multiple `REQUIRES_NEW` transactions per save (one root insert + N child-table inserts), which unnecessarily widened the critical section relevant to the planned distributed-lock work (`specs/004-redis-hardening-refactor`).

Identifying/lookup scalar columns (`consultationMonth`, `compatibilityMonth`, `companyName`, `targetRoleCategory`, `compatibilityScore`, `summary`, etc.) stay as plain columns on the root entity — only derived analysis *content* moves into the JSON column, never the columns used for querying or unique constraints.

When adding a new derived-analysis result elsewhere in `career/`, default back to a normalized child entity + repository unless the same conditions apply (write-once/read-whole access pattern, frequently-changing AI-generated shape, or child-table transaction fan-out you're trying to avoid) — the JSON-column approach is a deliberate exception for these three fields, not the new default.

### Entities & conventions worth knowing before writing new ones

- Lombok: `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder` only. Never `@Data` or `@ToString` on entities (bidirectional relations → infinite recursion).
- `equals`/`hashCode` are hand-written on ID only (never Lombok `@EqualsAndHashCode`) — needed for safe comparison of Hibernate lazy-loading proxies.
- All associations (`@OneToMany`/`@ManyToOne`/etc.) must specify `fetch = FetchType.LAZY` explicitly.
- Timestamps use `@CreatedDate`/`@LastModifiedDate` + `@EntityListeners(AuditingEntityListener.class)` (auditing enabled via `config/JpaAuditingConfig.java`), never manual `@PreUpdate`.
- Soft delete via `deleted_at` + `@SQLRestriction("deleted_at IS NULL")` where applicable (e.g. `User`) — not a universal pattern across all entities, but the convention to follow when an entity needs it.
- DTOs are always Java `record`s, never Lombok `@Data` classes.
- Domain constants: enums for cross-file business constants (`career/enums/`), `private static final` static classes for technical/config constants (timeouts, thresholds). No magic numbers/strings.

### Security

Two independent Spring Security configurations:
- `config/SecurityConfig.java` — JWT bearer auth for the JSON REST API (access token 1h / refresh token 7d), roles `ROLE_USER`/`ROLE_ADMIN`.
- `config/AdminSecurityConfig.java` + `admin/config/*` — cookie-based JWT auth for the Thymeleaf SSR admin dashboard (separate entry point/access-denied handlers, `AdminCookieJwtFilter`).

### Profiles

`application.yaml` (base) + `application-admin.yaml` (admin dashboard) + `application-local.yaml` (gitignored, per-developer — DB creds, JWT expirations). Local profile is active by default via `bootRun`.

## Feature specs

Design docs for each phase live under `../specs/`: `001-career-fortune-api`, `002-user-management`, `003-admin-dashboard` (each has `spec.md`/`plan.md`/`tasks.md`). Check the relevant spec before implementing a new feature in that area.
