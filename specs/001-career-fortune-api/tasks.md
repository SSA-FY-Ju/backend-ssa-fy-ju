# Task List: Career Fortune & Consultation API

**Feature**: Career Fortune & Consultation API
**Date Generated**: 2026-04-10
**Status**: Ready for Implementation
**Total Tasks**: 49 (Enum definition + API documentation added)
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

---

## Overview

This task list breaks down the Career Fortune API implementation into 4 phases:
- **Phase 1 (Setup)**: Project initialization & configuration
- **Phase 2 (Foundational)**: Core infrastructure & utilities (blocking prerequisites)
- **Phase 3 (User Stories)**: Individual feature implementation in priority order
- **Phase 4 (Polish)**: Testing, documentation, integration

Each task is independently testable and includes file paths for clarity.

---

## Phase Dependencies

```
Phase 1 (Setup) ──┬─→ Phase 2 (Foundational) ──┬─→ Phase 3.1 (US1: Career Timing)
                  │                             │
                  │                             ├─→ Phase 3.2 (US2: Consultation)
                  │                             │
                  │                             ├─→ Phase 3.3 (US4: Feedback) [Parallel]
                  │                             │
                  │                             └─→ Phase 3.4 (US3: Company Compatibility)
                  │
                  └─→ Phase 4 (Polish & Integration)
```

**Implementation Strategy**: MVP-first approach
1. **US1 (Career Timing)** — Core value proposition, simplest integration
2. **US2 (Consultation)** — Revenue-critical, most complex external API
3. **US4 (Feedback)** — Optional but high-impact for product decisions (can run parallel with US2)
4. **US3 (Company Compatibility)** — Secondary feature (P2)

---

## Parallel Execution Opportunities

- **Setup Phase**: All tasks are independent (can run in parallel)
- **Foundational Phase**:
  - `T009` (Saju Data Service) and `T010` (Company Info Service) are independent from each other
  - `T011` (Exception handling) and `T012` (Response DTOs) are independent
- **User Story Phases**:
  - US1 and US2 can be developed in parallel after foundational phase
  - US4 (Feedback) can start after US1 basics are in place (needs SajuResult FK)
  - US3 depends on core structures but can be parallel to US1/US2

---

## Phase 1: Setup (Project Initialization)

- [v] T001 Create project package structure in `SSAju/src/main/java/ssafy/SSAju/`
  - Create directories: `career/`, `dto/`, `controller/`, `service/`, `repository/`, `exception/`, `handler/`, `config/`
  - File: `SSAju/src/main/java/ssafy/SSAju/` (directory structure)

- [v] T002 Initialize Spring Boot application class (`SSAjuApplication.java`)
  - File: `SSAju/src/main/java/ssafy/SSAju/SSAjuApplication.java`

- [v] T003 Configure `application.yaml` with database, external API URLs, and timeouts
  - Include: MySQL datasource, FastAPI URL, OpenAI API key (env var), public data API config
  - File: `SSAju/src/main/resources/application.yaml`

- [v] T004 Add Spring AI and WebClient dependencies to `build.gradle`
  - Add: Spring AI OpenAI starter, WebClient, MySQL driver, Lombok, Spring Validation
  - File: `SSAju/build.gradle`

- [v] T005 Create base exception hierarchy in `exception/` package
  - Create: `SajuException.java` (root), `InvalidSajuDataException.java`, `FastAPITimeoutException.java`, `OpenAIApiException.java`, `PublicDataApiException.java`, `DataAccessException.java`, `ExternalApiException.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/exception/*.java`

---

## Phase 2: Foundational Infrastructure (Prerequisites for all User Stories)

- [ ] T006 Implement WebClient configuration with timeout and retry policies
  - Configure WebClient bean with 3-5 second timeouts, exponential backoff for retries
  - File: `SSAju/src/main/java/ssafy/SSAju/config/WebClientConfig.java`

- [ ] T007 Implement Spring AI ChatClient configuration with OpenAI JSON Mode
  - Configure ChatClient bean for JSON structured outputs
  - File: `SSAju/src/main/java/ssafy/SSAju/config/ChatClientConfig.java`

- [ ] T008 Create global exception handler using `@RestControllerAdvice`
  - Handle: InvalidSajuDataException, FastAPITimeoutException, OpenAIApiException, PublicDataApiException, DataAccessException
  - File: `SSAju/src/main/java/ssafy/SSAju/handler/SajuGlobalExceptionHandler.java`

- [ ] T009 [P] Create base entities: `UserProfile` and `SajuResult` in `career/entity/`
  - Implement UserProfile: birthDate (LocalDate, @NotNull), birthTime (LocalTime, @NotNull, HH:mm format), timestamps (createdAt, updatedAt)
  - Implement SajuResult: heavenlyStems[], earthlyBranches[], fiveElements, tenGods, careerFortune (JSON), birth_time, timestamps. Link to UserProfile (1:1)
  - Use: @Getter, @NoArgsConstructor(access=PROTECTED), @Builder, FetchType.LAZY for relationships, @JdbcTypeCode for JSON columns
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/UserProfile.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/SajuResult.java`

- [ ] T010 [P] Create repositories: `UserProfileRepository` and `SajuResultRepository` in `repository/`
  - Implement: Spring Data JPA repositories with custom query methods (e.g., findByBirthDate, findLatestByUserProfileId)
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/UserProfileRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/SajuResultRepository.java`

- [ ] T011 [P] Create base DTO classes in `dto/request/` and `dto/response/`
  - Create: `ApiResponse.java` (generic wrapper), `ErrorInfo.java` (error details)
  - Use: Java record type for all DTOs
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/ApiResponse.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/ErrorInfo.java`

- [ ] T012 [P] Create external API response DTOs in `dto/external/`
  - Create: `FastAPIResponse.java` (heavenlyStems, earthlyBranches, fiveElements, tenGods), `CareerAdviceResponse.java` (for OpenAI JSON Mode)
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/external/FastAPIResponse.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/external/CareerAdviceResponse.java`

- [ ] T013 [P] Define enums for feedback and satisfaction tracking
  - Create: `FeedbackType.java` (CAREER_TIMING, CONSULTATION, COMPATIBILITY), `SatisfactionStatus.java` (SATISFIED, DISSATISFIED)
  - File: `SSAju/src/main/java/ssafy/SSAju/career/enums/FeedbackType.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/enums/SatisfactionStatus.java`

- [ ] T013-2 [P] Create utility classes for saju calculations
  - Create: `TenGodCalculator.java` (十神 computation), `CareerFortuneAnalyzer.java` (H1/H2 logic), `CompatibilityScoreCalculator.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/util/TenGodCalculator.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/util/CareerFortuneAnalyzer.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/util/CompatibilityScoreCalculator.java`

---

## Phase 3: User Stories (Feature Implementation)

### User Story 1: Career Timing Analysis (Priority P1)

**Goal**: Users can input birthDate and receive career timing analysis (H1/H2 prediction).
**Independent Test**: birthDate → FastAPI → Saju calculation → H1/H2 response (no other stories required)
**Expected Outcome**: Timing prediction API working end-to-end, stored in DB

- [ ] T014 [US1] [P] Create `CareerTimingRequest` DTO in `dto/request/`
  - Fields: `birthDate` (LocalDate, @NotNull, YYYY-MM-DD), `birthTime` (LocalTime, @NotNull, HH:mm format)
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/request/CareerTimingRequest.java`

- [ ] T015 [US1] [P] Create `CareerTimingResponse` DTO in `dto/response/`
  - Fields: `favoredPeriod` (String: "H1"/"H2"), `confidenceScore` (0-100), `reasoning`
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/CareerTimingResponse.java`

- [ ] T016 [US1] Create `SajuDataService` in `service/`
  - Method: `fetchSajuFromFastAPI(LocalDate birthDate, LocalTime birthTime)` → calls FastAPI with complete birth date-time (YYYY-MM-DD HH:mm) with retry logic
  - Handles: TimeoutException → FastAPITimeoutException, invalid response → InvalidSajuDataException, missing time → InvalidSajuDataException
  - File: `SSAju/src/main/java/ssafy/SSAju/service/SajuDataService.java`

- [ ] T017 [US1] Create `CareerFortuneService` in `service/`
  - Method: `analyzeCareerTiming(LocalDate birthDate, LocalTime birthTime)` → H1/H2 prediction with complete saju data
  - Logic: Call `SajuDataService.fetchSajuFromFastAPI()` with birthDate + birthTime, use `CareerFortuneAnalyzer` to process saju data
  - Stores: SajuResult in DB via `SajuResultRepository` (including birthTime)
  - File: `SSAju/src/main/java/ssafy/SSAju/service/CareerFortuneService.java`

- [ ] T018 [US1] Create `CareerTimingController` in `controller/`
  - Endpoint: `POST /api/career/timing` with CareerTimingRequest (birthDate + birthTime, both required)
  - Handles: Request validation (@Valid on birthDate YYYY-MM-DD and birthTime HH:mm), calls `CareerFortuneService.analyzeCareerTiming()`, returns `ApiResponse<CareerTimingResponse>`
  - Validation: Reject requests with missing/malformed birthTime (400 Bad Request)
  - File: `SSAju/src/main/java/ssafy/SSAju/controller/CareerTimingController.java`

- [ ] T019 [US1] Write unit tests for `CareerFortuneService` in `src/test/`
  - Test cases:
    1. Happy path: valid birthDate (YYYY-MM-DD) + birthTime (HH:mm) → H1/H2 prediction
    2. Missing birthTime → InvalidSajuDataException
    3. Invalid time format (HH, no mm) → InvalidSajuDataException
    4. FastAPI timeout → FastAPITimeoutException
    5. Null birthDate/birthTime → NullPointerException / ValidationException
  - Pattern: Given-When-Then with AssertJ
  - File: `SSAju/src/test/java/ssafy/SSAju/service/CareerFortuneServiceTest.java`

- [ ] T020 [US1] Write unit tests for `CareerTimingController` in `src/test/`
  - Test cases:
    1. Valid request (birthDate + birthTime both provided) → 200 OK with H1/H2 response
    2. Missing birthTime field → 400 Bad Request with error message "birthTime is required in HH:mm format"
    3. Invalid time format (only hour, no minutes) → 400 Bad Request
    4. Invalid date format → 400 Bad Request
    5. FastAPI timeout via service → 503 Service Unavailable
  - File: `SSAju/src/test/java/ssafy/SSAju/controller/CareerTimingControllerTest.java`

- [ ] T021 [US1] Run all tests for US1 features
  - Command: `./gradlew test --tests "ssafy.SSAju.service.CareerFortuneServiceTest OR ssafy.SSAju.controller.CareerTimingControllerTest"`
  - Verify: BUILD SUCCESSFUL before committing

---

### User Story 2: AI Career Consulting (Priority P1)

**Goal**: Users receive AI-powered career consultation (recommended industries, interview tips, strengths).
**Independent Test**: Saju data → OpenAI JSON Mode → Structured advice response (requires US1 SajuResult)
**Expected Outcome**: Consultation endpoint working, storing CareerConsultation in DB

- [ ] T022 [US2] Create `CareerConsultation` entity in `career/entity/`
  - Fields: id, sajuResultId (FK), industries (JSON list), interviewTips (JSON list), strengths (JSON list), openaiModelVersion, generatedAt
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/CareerConsultation.java`

- [ ] T023 [US2] [P] Create `CareerConsultationRepository` in `repository/`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/CareerConsultationRepository.java`

- [ ] T024 [US2] [P] Create `ConsultationRequest` DTO in `dto/request/`
  - Fields: birthDate (LocalDate, @NotNull), birthTime (LocalTime, @NotNull), heavenlyStems (List<String>), earthlyBranches (List<String>), fiveElements (Map<String, Integer>)
  - Validation: @NotNull on birthDate/birthTime, size checks for stems/branches (must be 4 each), fiveElements must sum correctly
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/request/ConsultationRequest.java`

- [ ] T025 [US2] [P] Create `ConsultationResponse` DTO in `dto/response/`
  - Fields: industries (List with name + reason), interviewTips (List), strengths (List), openaiModelVersion
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/ConsultationResponse.java`

- [ ] T026 [US2] Create `ConsultationService` in `service/`
  - Method: `getCareerConsultation(LocalDate birthDate, LocalTime birthTime, saju data)` → fetches saju via SajuDataService, calls OpenAI via ChatClient
  - Logic: Call `SajuDataService.fetchSajuFromFastAPI()` with birthDate + birthTime, use JSON Mode for structured output mapping
  - Handles: Timeout → OpenAIApiException, invalid response → InvalidSajuDataException, missing time → InvalidSajuDataException
  - Stores: CareerConsultation in DB (linked to SajuResult with birthTime)
  - File: `SSAju/src/main/java/ssafy/SSAju/service/ConsultationService.java`

- [ ] T027 [US2] Create `ConsultationController` in `controller/`
  - Endpoint: `POST /api/career/consultation` with ConsultationRequest (birthDate + birthTime both required, heavenlyStems/earthlyBranches/fiveElements)
  - Handles: Request validation (@Valid on all fields), calls `ConsultationService.getCareerConsultation()`, returns `ApiResponse<ConsultationResponse>`
  - Validation: Reject requests with missing birthTime or malformed HH:mm format (400 Bad Request)
  - File: `SSAju/src/main/java/ssafy/SSAju/controller/ConsultationController.java`

- [ ] T028 [US2] Write unit tests for `ConsultationService` in `src/test/`
  - Test cases:
    1. Valid saju data (birthDate + birthTime + stems/branches/fiveElements) → consultation returned with industries/tips/strengths
    2. Missing birthTime → InvalidSajuDataException
    3. Invalid stem count (not 4) → InvalidSajuDataException
    4. Invalid branch count (not 4) → InvalidSajuDataException
    5. OpenAI timeout → OpenAIApiException
    6. Null birthTime → NullPointerException / ValidationException
  - File: `SSAju/src/test/java/ssafy/SSAju/service/ConsultationServiceTest.java`

- [ ] T029 [US2] Write unit tests for `ConsultationController` in `src/test/`
  - Test cases:
    1. Valid request (birthDate + birthTime + stems/branches/fiveElements) → 200 OK with consultation response
    2. Missing birthTime field → 400 Bad Request
    3. Invalid time format → 400 Bad Request
    4. Invalid stem count or format → 400 Bad Request
    5. OpenAI timeout via service → 504 Gateway Timeout
  - File: `SSAju/src/test/java/ssafy/SSAju/controller/ConsultationControllerTest.java`

- [ ] T030 [US2] Run all tests for US2 features
  - Command: `./gradlew test --tests "ssafy.SSAju.service.ConsultationServiceTest OR ssafy.SSAju.controller.ConsultationControllerTest"`
  - Verify: BUILD SUCCESSFUL before committing

---

### User Story 4: User Satisfaction Feedback (Priority P1)

**Goal**: After any saju analysis, users can provide simple binary satisfaction feedback (satisfied/dissatisfied).
**Independent Test**: Feedback submission → Stored in DB → Returns success response (runs parallel with US2)
**Expected Outcome**: Feedback collection API working, UserSatisfactionFeedback entity populated for Phase 2 dashboards

- [ ] T031 [US4] Create `UserSatisfactionFeedback` entity in `career/entity/`
  - Fields: id, sajuResultId (FK), feedbackType (ENUM: CAREER_TIMING/CONSULTATION/COMPATIBILITY), satisfactionStatus (ENUM: SATISFIED/DISSATISFIED), createdAt
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/UserSatisfactionFeedback.java`

- [ ] T032 [US4] [P] Create `UserSatisfactionFeedbackRepository` in `repository/`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/UserSatisfactionFeedbackRepository.java`

- [ ] T033 [US4] [P] Create `SatisfactionFeedbackRequest` DTO in `dto/request/`
  - Fields: sajuResultId, feedbackType (ENUM), satisfactionStatus (ENUM)
  - Validation: @NotNull on all fields
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/request/SatisfactionFeedbackRequest.java`

- [ ] T034 [US4] [P] Create `SatisfactionFeedbackResponse` DTO in `dto/response/`
  - Fields: feedbackId, createdAt
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/SatisfactionFeedbackResponse.java`

- [ ] T035 [US4] Create `FeedbackService` in `service/`
  - Method: `saveFeedback(SatisfactionFeedbackRequest)` → validates SajuResult exists, stores feedback
  - Handles: SajuResult not found → throw custom exception, enum validation
  - File: `SSAju/src/main/java/ssafy/SSAju/service/FeedbackService.java`

- [ ] T036 [US4] Create `FeedbackController` in `controller/`
  - Endpoint: `POST /api/feedback/satisfaction`
  - Handles: Request validation, calls `FeedbackService`, returns `ApiResponse<SatisfactionFeedbackResponse>`
  - File: `SSAju/src/main/java/ssafy/SSAju/controller/FeedbackController.java`

- [ ] T037 [US4] Write unit tests for `FeedbackService` in `src/test/`
  - Test: Valid feedback saved, SajuResult not found → 404, invalid enum → 400, null handling
  - File: `SSAju/src/test/java/ssafy/SSAju/service/FeedbackServiceTest.java`

- [ ] T038 [US4] Write unit tests for `FeedbackController` in `src/test/`
  - Test: Valid feedback → 200 OK, invalid type → 400, missing SajuResult → 404
  - File: `SSAju/src/test/java/ssafy/SSAju/controller/FeedbackControllerTest.java`

- [ ] T039 [US4] Run all tests for US4 features
  - Command: `./gradlew test --tests "ssafy.SSAju.service.FeedbackServiceTest OR ssafy.SSAju.controller.FeedbackControllerTest"`
  - Verify: BUILD SUCCESSFUL before committing

---

### User Story 3: Company & Job Fit Analysis (Priority P2)

**Goal**: Users can analyze compatibility between their saju and target company founding date, receiving a score (0-100) and recommended roles.
**Independent Test**: User saju + company date → Compatibility calculation → Score + roles response (depends on core structures)
**Expected Outcome**: Compatibility endpoint working, CompanyCompatibility stored in DB

- [ ] T040 [US3] Create `CompanyCompatibility` entity in `career/entity/`
  - Fields: id, userProfileId (FK), companyName, compatibilityScore (0-100), recommendedRoles (JSON list), createdAt
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/CompanyCompatibility.java`

- [ ] T041 [US3] [P] Create `CompanyCompatibilityRepository` in `repository/`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/CompanyCompatibilityRepository.java`

- [ ] T042 [US3] [P] Create `CompatibilityRequest` and `CompatibilityResponse` DTOs
  - Request fields: birthDate (LocalDate, @NotNull), birthTime (LocalTime, @NotNull), companyName (@NotNull), companyFoundingDate (LocalDate, optional), companyFoundingTime (LocalTime, optional, default 12:00)
  - Response fields: compatibilityScore (0-100), confidenceLevel (LOW/MEDIUM/HIGH), recommendedRoles, reasoning
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/request/CompatibilityRequest.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/CompatibilityResponse.java`

- [ ] T043 [US3] Create `CompanyInfoService` in `service/`
  - Method: `lookupCompanyFoundingDate(companyName)` → calls public data API with fallback to manual input. If time not found, use default 12:00
  - Handles: API timeout → PublicDataApiException, company not found → inform user to provide founding date. If time missing → auto-set to 12:00
  - File: `SSAju/src/main/java/ssafy/SSAju/service/CompanyInfoService.java`

- [ ] T044 [US3] Create `CompanyMatchingService` in `service/`
  - Method: `analyzeCompatibility(LocalDate userBirthDate, LocalTime userBirthTime, LocalDate companyFoundingDate, LocalTime companyFoundingTime)` → compatibility score + role recommendations
  - Logic: Fetch user saju via SajuDataService with birthDate + birthTime. Fetch/receive company saju (with time defaulting to 12:00 if missing). Use `CompatibilityScoreCalculator` for scoring
  - Stores: CompanyCompatibility in DB with both date-time information
  - File: `SSAju/src/main/java/ssafy/SSAju/service/CompanyMatchingService.java`

- [ ] T045 [US3] Create `CompatibilityController` in `controller/`
  - Endpoint: `POST /api/company/compatibility` with CompatibilityRequest (userBirthDate + userBirthTime required, companyFoundingDate optional, companyFoundingTime optional)
  - Handles: Request validation (@Valid), validates user birth time required, looks up company (with time fallback to 12:00), calculates compatibility, returns `ApiResponse<CompatibilityResponse>`
  - Validation: Reject requests with missing userBirthTime (400 Bad Request)
  - File: `SSAju/src/main/java/ssafy/SSAju/controller/CompatibilityController.java`

- [ ] T046 [US3] Write unit & integration tests for Company Compatibility
  - Test cases (CompanyMatchingService):
    1. Valid compatibility analysis (user birthDate + birthTime, company founding date) → compatibility score + roles
    2. Company founding time missing → default to 12:00
    3. Missing user birthTime → InvalidSajuDataException
    4. Invalid user birthTime format → InvalidSajuDataException
  - Test cases (CompatibilityController):
    1. Valid request (user birthDate + birthTime + company) → 200 OK with score/roles
    2. Missing user birthTime → 400 Bad Request
    3. Company not found (with company founding date fallback) → score still calculated
    4. Invalid user time format → 400 Bad Request
  - File: `SSAju/src/test/java/ssafy/SSAju/service/CompanyMatchingServiceTest.java`
  - File: `SSAju/src/test/java/ssafy/SSAju/controller/CompatibilityControllerTest.java`

---

## Phase 4: Polish & Integration

- [ ] T047 Generate Swagger/OpenAPI documentation
  - Add: `springdoc-openapi-starter-webmvc-ui` dependency to `build.gradle`
  - Configure: `@OpenAPIDefinition`, `@Info`, `@Server` annotations in `SSAjuApplication.java`
  - Add: `@Operation`, `@RequestBody`, `@ApiResponse` annotations to all controllers
  - Configure: `springdoc.swagger-ui.path=/swagger-ui.html` in `application.yaml`
  - File: `SSAju/build.gradle` (dependency), `SSAju/src/main/java/ssafy/SSAju/SSAjuApplication.java` (annotations)
  - File: All controller classes updated with OpenAPI annotations
  - Verify: Accessible at `http://localhost:8080/swagger-ui.html` after `./gradlew bootRun`

- [ ] T048 Write integration test for full Career API flow (all 4 endpoints)
  - Test:
    1. Create UserProfile with birthDate + birthTime
    2. POST /api/career/timing with birthDate + birthTime → Get H1/H2
    3. POST /api/career/consultation with birthDate + birthTime + saju data → Get advice
    4. POST /api/feedback/satisfaction with results → Save feedback
    5. POST /api/company/compatibility with birthDate + birthTime + company → Get compatibility score
  - Verify: Data persistence (SajuResult with birthTime), response consistency, birthTime required fields validated, error handling across flows
  - File: `SSAju/src/test/java/ssafy/SSAju/integration/CareerApiIntegrationTest.java`

- [ ] T049 Final verification: Run full test suite and validate coverage
  - Command: `./gradlew clean test`
  - Verify: 100% of Phase 1 tests pass, no warnings, coverage >80%
  - Document: Test summary in [tasks.md](./tasks.md) completion section

---

## Test-Then-Commit Checklist

**Before each commit, verify**:

- [ ] All related tests pass (`./gradlew test`)
- [ ] Commit message follows Conventional Commits format
- [ ] Commit includes `[Test Passed]` footer
- [ ] No uncommitted changes remain
- [ ] Code follows code-style-guide.md rules (Lombok, DTO records, FetchType.LAZY, no try-catch)

**Example commit**:
```
feat: Career Timing Analysis API endpoint

- Implement CareerFortuneService with H1/H2 prediction
- Create CareerTimingController with @Valid validation
- Add SajuDataService for FastAPI integration with retry logic
- Add CareerFortuneServiceTest and CareerTimingControllerTest

[Test Passed]
```

---

## Independent Test Criteria (MVP Scope)

### US1: Career Timing Analysis (Complete MVP)
```
Given: Valid birthDate (YYYY-MM-DD) and birthTime (HH:mm, 24-hour format)
When:  POST /api/career/timing with {"birthDate":"1990-10-10", "birthTime":"14:30"}
Then:  Response includes favoredPeriod (H1/H2), confidenceScore (0-100), reasoning
And:   SajuResult entity persisted in DB with both birthDate and birthTime
And:   FastAPI integration works with complete birth date-time (YYYY-MM-DD HH:mm) and timeout handling
And:   Missing birthTime → 400 Bad Request with clear error message
```

### US2: AI Consultation (Complete MVP)
```
Given: Valid birthDate (YYYY-MM-DD), birthTime (HH:mm), stems (4 values), branches (4 values), fiveElements
When:  POST /api/career/consultation with complete saju data including birthTime
Then:  Response includes industries (3-5), interviewTips, strengths
And:   CareerConsultation entity persisted in DB linked to SajuResult (with birthTime)
And:   Spring AI / OpenAI JSON Mode structured output works with complete saju (including hour stem/branch)
And:   Missing birthTime → 400 Bad Request with validation error
```

### US4: Feedback (Complete MVP)
```
Given: Existing SajuResult and valid feedback data
When:  POST /api/feedback/satisfaction
Then:  Feedback saved with timestamp
And:   Returns feedbackId and createdAt
And:   Feedback accessible for Phase 2 admin dashboard
```

### US3: Company Compatibility (P2 Deferred)
```
Given: Valid user birthDate (YYYY-MM-DD) and birthTime (HH:mm, required), company name, and optional company founding date
When:  POST /api/company/compatibility with {"birthDate":"1990-10-10", "birthTime":"14:30", "companyName":"Samsung", "companyFoundingDate":"1938-01-13"}
Then:  Response includes compatibilityScore (0-100), recommendedRoles
And:   Company founding time defaults to 12:00 if not provided
And:   Fallback to manual company founding date if API lookup fails (time still defaults to 12:00)
And:   Missing user birthTime → 400 Bad Request
```

---

## Known Risks & Mitigation

| Risk | Mitigation |
|------|-----------|
| FastAPI timeout / unavailability | Exponential backoff retry (2-3 attempts), 3s timeout, graceful degradation |
| OpenAI API slowness | 8s timeout, Spring AI automatic retry, inform user of delay |
| Database N+1 queries | FetchType.LAZY on all relationships (mandatory), eager loading only when needed |
| Invalid external API responses | Request validation, DTO mapping with clear error messages, schema validation |
| Circular entity references | No @ToString/@Data on entities, use explicit @Getter + @Builder |

---

## Next Steps (After Task Completion)

1. **Deploy to staging environment** and run load tests (5,000 concurrent users target)
2. **Monitor API performance** against goals:
   - Career timing: <5 seconds (FastAPI excluded)
   - Consultation: <15 seconds (OpenAI overhead)
   - Company compatibility: <8 seconds
3. **Phase 2 preparation**: User authentication (auth/ package), admin dashboard for feedback analytics
4. **Measure engagement**: Track feedback collection rate and satisfaction metrics

---

## Task Summary

| Phase | Task Count | Focus | Parallel? |
|-------|-----------|-------|-----------|
| Phase 1 (Setup) | 5 | Project structure, config, exception handling | Yes (all) |
| Phase 2 (Foundational) | 9 | WebClient, ChatClient, base entities, repos, Enum definitions, utilities | Yes (most) |
| Phase 3.1 (US1) | 8 | Career timing feature | Independent |
| Phase 3.2 (US2) | 9 | Consultation feature | Parallel with US4 |
| Phase 3.4 (US4) | 9 | Feedback feature | Parallel with US2 |
| Phase 3.3 (US3) | 7 | Company compatibility (P2) | After core ready |
| Phase 4 (Polish) | 3 | API documentation (Swagger), integration tests, final validation | After all stories |
| **TOTAL** | **49** | Full MVP + P2 foundation + API docs | Strategic parallelism |

---

**Generated by `/speckit-tasks` on 2026-04-10**
**Status**: Ready for implementation team
