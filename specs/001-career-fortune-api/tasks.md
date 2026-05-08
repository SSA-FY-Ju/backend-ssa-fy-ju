# Task List: Career Fortune & Consultation API

**Feature**: Career Fortune & Consultation API
**Date Generated**: 2026-04-27
**Status**: Ready for Implementation (Phase 3-Enhancement Added)
**Total Tasks**: 91 (Phase 1~2: 14 tasks + Phase 3.1-3.2: 17 tasks + Phase 3-Refactor: 11 tasks + Phase 3-Enhancement: 27 tasks + Phase 3-Refactor-3: 12 tasks + Phase 3.3: 7 tasks + Phase 4: 3 tasks)
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

- [v] T006 Implement WebClient configuration with timeout and retry policies
  - Configure WebClient bean with 3-5 second timeouts, exponential backoff for retries
  - Note: `buildHttpClient()` 메서드에 `timeoutSeconds <= 0` 유효성 검증은 Phase 3에서 서비스 통합 시 추가 (현재 TODO 주석으로 표기)
  - File: `SSAju/src/main/java/ssafy/SSAju/config/WebClientConfig.java`

- [v] T007 Implement Spring AI ChatClient configuration with OpenAI JSON Mode
  - Configure ChatClient bean for JSON structured outputs
  - Note: JSON Mode 동작 검증 및 에러 처리는 Phase 3.2 (ConsultationService)에서 추가 구현
  - File: `SSAju/src/main/java/ssafy/SSAju/config/ChatClientConfig.java`

- [v] T008 Create global exception handler using `@RestControllerAdvice`
  - Handle: InvalidSajuDataException, FastAPITimeoutException, OpenAIApiException, PublicDataApiException, DataAccessException
  - Note: OpenAIApiException 세분화 (401/429/5xx 구분)는 Phase 3.2 (ConsultationService)에서 구현
  - File: `SSAju/src/main/java/ssafy/SSAju/handler/SajuGlobalExceptionHandler.java`

- [v] T009 [P] Create base entities: `UserProfile` and `SajuResult` in `career/entity/`
  - Implement UserProfile: birthDate (LocalDate, @NotNull), birthTime (LocalTime, @NotNull, HH:mm format), createdAt (@CreatedDate, LocalDateTime), updatedAt (@LastModifiedDate, LocalDateTime). **Add UNIQUE(birthDate, birthTime) constraint**
  - Implement SajuResult: fullSajuData (Map<String, Object>, FastAPI 응답 저장 - Phase 3-Refactor-3에서 SajuFullData 엔티티로 정규화), hiddenStems (Map<String, List<String>>, 지지별 지장간 저장 - Phase 3-Refactor에서 HiddenStemData 엔티티로 정규화), tenGodDistribution (Map<String, Integer> - Phase 3-Refactor에서 TenGodData 엔티티로 정규화), careerFortune (Map<String, Object> - Phase 3-Refactor에서 CareerFortune 엔티티로 정규화), createdAt (@CreatedDate), updatedAt (@LastModifiedDate). Link to UserProfile (1:1, FetchType.LAZY)
  - Use: @Getter, @NoArgsConstructor(access=PROTECTED), @Builder, FetchType.LAZY for relationships
  - **Timestamp 처리**: `@CreatedDate` (@Column(nullable=false, updatable=false)) + `@LastModifiedDate` 사용. @PreUpdate 사용 금지. JPA Auditing 활성화 필수 (@EnableJpaAuditing in @Configuration).
  - **JSON 컬럼 처리 (Spring Boot 4.x Hibernate 7.2.7)**: @JdbcTypeCode 대신 @Convert + custom AttributeConverter 사용 (Jackson 3.x 호환성)
    - ObjectMapConverter: Map<String, Object> 직렬화/역직렬화 (fullSajuData, careerFortune용)
    - StringListMapConverter: Map<String, List<String>> 직렬화/역직렬화 (hiddenStems용 - Phase 3-Refactor까지만 사용)
    - IntegerMapConverter: Map<String, Integer> 직렬화/역직렬화 (tenGodDistribution용 - Phase 3-Refactor까지만 사용)
  - Note: JSON 저장은 임시. Phase 3-Refactor에서 TenGodData, HiddenStemData, CareerFortune 엔티티로 정규화. Phase 3-Refactor-3에서 fullSajuData → SajuFullData 엔티티로 최종 정규화.
  - Note: hiddenStems 구조 예시: `{"子": ["癸"], "丑": ["癸", "辛", "己"], ...}` (임시 저장 형식, Phase 3-Refactor에서 HiddenStemData 엔티티로 변환)
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/UserProfile.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/SajuResult.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/converter/ObjectMapConverter.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/converter/StringListMapConverter.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/converter/IntegerMapConverter.java`

- [v] T010 [P] Create repositories: `UserProfileRepository` and `SajuResultRepository` in `repository/`
  - Implement: Spring Data JPA repositories with custom query methods (e.g., findByBirthDate, findLatestByUserProfileId)
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/UserProfileRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/SajuResultRepository.java`

- [v] T011 [P] Create base DTO classes in `dto/request/` and `dto/response/`
  - Create: `ApiResponse.java` (generic wrapper), `ErrorInfo.java` (error details)
  - Use: Java record type for all DTOs
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/ApiResponse.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/ErrorInfo.java`

- [v] T012 [P] Create external API response DTOs in `dto/external/`
  - Create: `FastAPIResponse.java` (camelCase 필드: heavenlyStems, earthlyBranches, fiveElements, yearPillar, monthPillar, dayPillar, hourPillar, birthTime, birthDate, solarCorrection), `CareerAdviceResponse.java` (for OpenAI JSON Mode)
  - FastAPIResponse fields:
    ```java
    public record FastAPIResponse(
        List<String> heavenlyStems,
        List<String> earthlyBranches,
        Map<String, Integer> fiveElements,
        String yearPillar, monthPillar, dayPillar, hourPillar,
        String birthTime,
        String birthDate,
        Map<String, Object> solarCorrection
    ) {}
    ```
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/external/FastAPIResponse.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/external/CareerAdviceResponse.java`

- [v] T013 [P] Define enums for feedback and satisfaction tracking
  - Create: `FeedbackType.java` (CAREER_TIMING, CONSULTATION, COMPATIBILITY), `SatisfactionStatus.java` (SATISFIED, DISSATISFIED)
  - File: `SSAju/src/main/java/ssafy/SSAju/career/enums/FeedbackType.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/enums/SatisfactionStatus.java`

- [v] T013-2 [P] Create utility classes for saju calculations
  - Create: `TenGodCalculator.java` (十神 computation from heavenlyStems)
  - Create: `HiddenStemCalculator.java` (地藏干 computation from earthlyBranches, returns Map<String, List<String>>)
  - Create: `CareerFortuneAnalyzer.java` (H1/H2 logic using both TenGod and HiddenStem data)
    - 관성 점수 산정: 정관·편관 가점(×20), 식신·상관 감점(×15), 비겁 2개↑ 감점(×5)
    - 지장간 보정: 정관·편관(+5), 식신·상관(-3)
  - Create: `CompatibilityScoreCalculator.java` (compatibility score using both calculators)
    - 입력 검증 필수: userHiddenStems/userDayMaster/companyHiddenStems/companyDayMaster null·blank 체크 → IllegalArgumentException
  - Note: HiddenStemCalculator must be used together with TenGodCalculator for accurate 오행 분포 calculation
  - File: `SSAju/src/main/java/ssafy/SSAju/career/util/TenGodCalculator.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/util/HiddenStemCalculator.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/util/CareerFortuneAnalyzer.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/util/CompatibilityScoreCalculator.java`

---

## Phase 3: User Stories (Feature Implementation)

### User Story 1: Career Timing Analysis (Priority P1)

**Goal**: Users can input birthDate and receive career timing analysis (H1/H2 prediction).
**Independent Test**: birthDate → FastAPI → Saju calculation → H1/H2 response (no other stories required)
**Expected Outcome**: Timing prediction API working end-to-end, stored in DB

- [v] T014 [US1] [P] Create `CareerTimingRequest` DTO in `dto/request/`
  - Fields: `birthDate` (LocalDate, @NotNull, YYYY-MM-DD), `birthTime` (LocalTime, @NotNull, HH:mm format)
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/request/CareerTimingRequest.java`

- [v] T015 [US1] [P] Create `CareerTimingResponse` DTO in `dto/response/`
  - Fields: `favoredPeriod` (String: "H1"/"H2"), `confidenceScore` (0-100), `reasoning`
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/CareerTimingResponse.java`

- [v] T016 [US1] Create `SajuDataService` in `service/`
  - Method: `fetchSajuFromFastAPI(LocalDate birthDate, LocalTime birthTime)` → calls FastAPI with complete birth date-time in request body ({"birthDate": "YYYY-MM-DD", "birthTime": "HH:mm"}) with retry logic
  - FastAPI URI: `POST /api/saju/calculate`
  - Response: `FastAPIResponse` (camelCase: heavenlyStems, earthlyBranches, fiveElements, yearPillar, monthPillar, dayPillar, hourPillar, birthTime, birthDate, solarCorrection)
  - Handles: ResourceAccessException (타임아웃/연결 실패) → FastAPITimeoutException, invalid response (heavenlyStems/earthlyBranches < 4 items) → InvalidSajuDataException, missing birthTime → InvalidSajuDataException
  - File: `SSAju/src/main/java/ssafy/SSAju/service/SajuDataService.java`

- [v] T017 [US1] Create `CareerFortuneService` in `service/`
  - Method: `analyzeCareerTiming(LocalDate birthDate, LocalTime birthTime)` → H1/H2 prediction with complete saju data including 지장간
  - Logic: Call `SajuDataService.fetchSajuFromFastAPI()` with birthDate + birthTime → Calculate HiddenStems via `HiddenStemCalculator` → Use `CareerFortuneAnalyzer` with both 십신 and 지장간 data for H1/H2 prediction
  - Stores: SajuResult in DB via `SajuResultRepository` (including birthTime, hiddenStems, tenGodDistribution)
  - Note: HiddenStemCalculator와 TenGodCalculator를 함께 사용하여 더 정확한 관운 분석 제공
  - File: `SSAju/src/main/java/ssafy/SSAju/service/CareerFortuneService.java`

- [v] T018 [US1] Create `CareerTimingController` in `controller/`
  - Endpoint: `POST /api/career/timing` with CareerTimingRequest (birthDate + birthTime, both required)
  - Handles: Request validation (@Valid on birthDate YYYY-MM-DD and birthTime HH:mm), calls `CareerFortuneService.analyzeCareerTiming()`, returns `ApiResponse<CareerTimingResponse>`
  - Validation: Reject requests with missing/malformed birthTime (400 Bad Request)
  - File: `SSAju/src/main/java/ssafy/SSAju/controller/CareerTimingController.java`

- [v] T019 [US1] Write unit tests for `CareerFortuneService` in `src/test/`
  - Test cases:
    1. Happy path: valid birthDate (YYYY-MM-DD) + birthTime (HH:mm) → H1/H2 prediction + SajuResult saved with hiddenStems/tenGodDistribution
    2. Existing user (same birthDate+birthTime) → UserProfile reused, no duplicate SajuResult
    3. Invalid heavenlyStems (< 4 items) → InvalidSajuDataException
    4. Invalid earthlyBranches (< 4 items) → InvalidSajuDataException
    5. FastAPI timeout → FastAPITimeoutException
  - Approach: @ExtendWith(MockitoExtension.class) with @Mock on SajuDataService, UserProfileRepository, SajuResultRepository. Real TenGodCalculator/HiddenStemCalculator/CareerFortuneAnalyzer 사용
  - Pattern: Given-When-Then with AssertJ
  - Note: FastAPIResponse fixture는 camelCase 필드 사용 (heavenlyStems, earthlyBranches 등)
  - File: `SSAju/src/test/java/ssafy/SSAju/service/CareerFortuneServiceTest.java`

- [v] T020 [US1] Write unit tests for `CareerTimingController` in `src/test/`
  - Test cases:
    1. Valid request (birthDate + birthTime both provided) → 200 OK with H1/H2 response
    2. Missing birthTime field → 400 Bad Request with error message "birthTime is required in HH:mm format"
    3. Invalid time format (only hour, no minutes) → 400 Bad Request
    4. Invalid date format → 400 Bad Request
    5. Empty body → 400 Bad Request
  - Approach: MockMvcBuilders.standaloneSetup(controller) + SajuGlobalExceptionHandler (Spring Boot 4.x에서 @WebMvcTest 미지원)
  - File: `SSAju/src/test/java/ssafy/SSAju/controller/CareerTimingControllerTest.java`

- [v] T021 [US1] Run all tests for US1 features
  - Command: `./gradlew test --tests "ssafy.SSAju.service.CareerFortuneServiceTest OR ssafy.SSAju.controller.CareerTimingControllerTest"`
  - Verify: BUILD SUCCESSFUL before committing

---

### User Story 2: AI Career Consulting (Priority P1)

**Goal**: Users receive AI-powered career consultation (recommended industries, interview tips, strengths).
**Independent Test**: Saju data → OpenAI JSON Mode → Structured advice response (requires US1 SajuResult)
**Expected Outcome**: Consultation endpoint working, storing CareerConsultation in DB
**Implementation Status**: ✅ **COMPLETED** (Commits 62ae1b3 + 657e77a, 2026-04-30)

#### Key Implementation Notes (Session 2026-04-30)

**1-Call Design Refactoring** (Commit 62ae1b3):
- ConsultationService: 내부적으로 FastAPI 호출, 십신/지장간 계산, 관운 분석 수행
- ConsultationRequest: 7개 필드 → 2개 필드 단순화 (birthDate + birthTime만 필요)
- ConsultationResponse 확장: favoredPeriod, confidenceScore, reasoning 추가
- 트랜잭션 분리: FastAPI/OpenAI I/O는 트랜잭션 밖에서 수행

**Response Data Completeness** (Commit 657e77a):
- ConsultationResponse에 3개 새 필드 추가: favoredPeriod (H1/H2), confidenceScore (0-100), reasoning
- 클라이언트는 단일 POST 요청으로 AI 조언 + 관운 분석 모두 수신
- SajuResult 자동 생성 (없을 경우): findOrCreateSajuResult() 메서드로 처리

---

- [v] T022 [US2] Create `CareerConsultation` entity in `career/entity/`
  - Fields: id, sajuResultId (FK), industries (JSON list), interviewTips (JSON list), strengths (JSON list), openaiModelVersion, generatedAt
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/CareerConsultation.java`

- [v] T023 [US2] [P] Create `CareerConsultationRepository` in `repository/`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/CareerConsultationRepository.java`

- [v] T024 [US2] [P] Create `ConsultationRequest` DTO in `dto/request/`
  - **Simplified** (Session 2026-04-30): 2개 필드만 필요
    - birthDate (LocalDate, @NotNull, YYYY-MM-DD format)
    - birthTime (LocalTime, @NotNull, HH:mm format)
  - **Before**: 7 필드 (heavenlyStems, earthlyBranches, fiveElements, hiddenStems, tenGodDistribution 포함)
  - **After**: 내부에서 모두 계산하므로 기본 정보만 입력
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/request/ConsultationRequest.java`

- [v] T025 [US2] [P] Create `ConsultationResponse` DTO in `dto/response/`
  - **Massively Expanded** (Session 2026-04-30): 23개 필드 (기본 조언 3 + 관운 분석 3 + 사주 프로필 내부 6 + OpenAI 분석 10 + 메타데이터 1)
    - **기본 AI 조언** (3 필드):
      - industries (List<CareerAdviceResponse.IndustryRecommendation> with name + reason)
      - interviewTips (List<String>)
      - strengths (List<String>)
    - **관운 분석** (3 필드):
      - favoredPeriod (String: "H1"/"H2")
      - confidenceScore (int: 0-100)
      - reasoning (String)
    - **사주 베이스 데이터** (1 필드):
      - sajuProfile (SajuProfile with dayMaster, dayMasterDescription, fiveElements, fiveElementsAnalysis, tenGodDistribution, keyTenGods)
    - **OpenAI 분석 결과** (10 필드):
      - cautions (List<String>)
      - wealthStyle (CareerAdviceResponse.WealthStyle: incomeSource, financialAdvice, investmentTendency, additionalIncome)
      - longTermRoadmap (CareerAdviceResponse.LongTermRoadmap: phase0to2years, phase3to5years, ultimateGoal, goalDescription)
      - personalBranding (CareerAdviceResponse.PersonalBranding: suitColor, impression, hairAndMakeup, brandingKeyword, taglineForResume)
      - powerKeywords (CareerAdviceResponse.PowerKeywords: keywords[], selectionGuide, usageTips[], avoidanceTip)
      - mentalCare (CareerAdviceResponse.MentalCare: stressVulnerability[], rechargeMethod[], mindsetMantra, emergencyTactic)
      - environmentFit (CareerAdviceResponse.EnvironmentFit: workVibe, companySize, colleagueType, conflictApproach, physicalEnv, culturalFit)
      - workStyle (CareerAdviceResponse.WorkStyle: preferredCompanyType, leadershipType, decisionMaking, conflictResolution)
      - relationshipStrategy (CareerAdviceResponse.RelationshipStrategy: socialStyle, networkingApproach, teamPosition, conflictResolution, careerNetworking)
      - careerTimeline (CareerAdviceResponse.CareerTimeline: year, months[], pivotPoints[], warningMonths[], warningDescription)
    - **메타정보** (1 필드):
      - openaiModelVersion (String)
  - **Nested Records Structure**:
    ```java
    record CareerAdviceResponse.IndustryRecommendation(String name, String reason, List<String> recommendedRoles)
    record CareerAdviceResponse.WealthStyle(String incomeSource, String financialAdvice, String investmentTendency, String additionalIncome)
    record CareerAdviceResponse.PhaseAdvice(String goal, String focus, String action)
    record CareerAdviceResponse.LongTermRoadmap(PhaseAdvice phase0to2years, PhaseAdvice phase3to5years, String ultimateGoal, String goalDescription)
    record CareerAdviceResponse.PersonalBranding(String suitColor, String impression, String hairAndMakeup, String brandingKeyword, String taglineForResume)
    record CareerAdviceResponse.PowerKeyword(String keyword, String element, String description, String usageExample, String context)
    record CareerAdviceResponse.PowerKeywords(List<PowerKeyword> keywords, String selectionGuide, List<String> usageTips, String avoidanceTip)
    record CareerAdviceResponse.MentalCare(List<String> stressVulnerability, List<String> rechargeMethod, String mindsetMantra, String emergencyTactic)
    record CareerAdviceResponse.EnvironmentFit(String workVibe, String companySize, String colleagueType, String conflictApproach, String physicalEnv, String culturalFit)
    record CareerAdviceResponse.WorkStyle(String preferredCompanyType, String leadershipType, String decisionMaking, String conflictResolution)
    record CareerAdviceResponse.RelationshipStrategy(String socialStyle, String networkingApproach, String teamPosition, String conflictResolution, String careerNetworking)
    record CareerAdviceResponse.MonthFortune(String type, String description)
    record CareerAdviceResponse.PivotPoint(String month, String type, int score, String description)
    record CareerAdviceResponse.CareerTimeline(int year, Map<String, MonthFortune> months, List<PivotPoint> pivotPoints, List<String> warningMonths, String warningDescription)
    record ConsultationResponse.SajuProfile(String dayMaster, String dayMasterDescription, Map<String, Integer> fiveElements, String fiveElementsAnalysis, Map<String, Integer> tenGodDistribution, List<String> keyTenGods)
    ```
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/ConsultationResponse.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/external/CareerAdviceResponse.java` (14개+ 중첩 record 타입 포함)

- [v] T026 [US2] Create `ConsultationService` in `service/`
  - **1-Call Design with Expanded Response** (Session 2026-04-30):
    - Method: `getCareerConsultation(ConsultationRequest)` → birthDate + birthTime만 입력받음
    - Logic: 
      1. SajuDataService.fetchSajuFromFastAPI(birthDate, birthTime) 호출
      2. TenGodCalculator + HiddenStemCalculator로 십신 + 지장간 계산
      3. CareerFortuneAnalyzer로 관운 분석 (favoredPeriod, confidenceScore, reasoning)
      4. findOrCreateUserProfile(birthDate, birthTime)
      5. findOrCreateSajuResult(userProfile, sajuData, tenGodDistribution, hiddenStems)
      6. OpenAI 호출 (ChatClient JSON Mode with 23개 필드 스키마)
      7. CareerConsultation 저장
      8. ConsultationResponse 반환 (23개 필드 모두 포함: 기본 조언 3 + 관운 분석 3 + 사주 프로필 내부 6 + OpenAI 분석 10 + 메타데이터 1)
  - **Transaction Management**: @Transactional 제거. FastAPI/OpenAI I/O는 트랜잭션 밖. 각 DB 작업은 개별 트랜잭션.
  - **OpenAI Prompt Enhancement**: buildPrompt() 메서드에 현재 연도(LocalDate.now().getYear()), 12개월 타임라인 요청, 23개 필드 명시 포함
  - **Helper Methods**:
    - `findOrCreateUserProfile()`: UNIQUE(birthDate, birthTime) 제약 활용, DIVE 처리
    - `findOrCreateSajuResult()`: 기존 SajuResult 재사용, 신규 생성 시 hiddenStems + tenGodDistribution 저장
    - `callOpenAI()`: ChatClient + JSON Mode로 CareerAdviceResponse 매핑 (23개 필드)
    - `buildReasoning()`: favoredPeriod + tenGodDistribution + dayMaster 종합 분석으로 근거 문자열 생성 (예: "정관 기운" 설명 포함)
    - `buildPrompt()`: 현재 연도, 12개월 타임라인, 십신 + 지장간 분석 결과를 모두 포함한 완전한 JSON 스키마 정의
    - `toObjectMap()`: FastAPIResponse → Map<String, Object> 변환
    - `toIndustriesMap()`: IndustryRecommendation → Map<String, String> 변환 (DB 저장용)
  - File: `SSAju/src/main/java/ssafy/SSAju/service/ConsultationService.java`

- [v] T027 [US2] Create `ConsultationController` in `controller/`
  - Endpoint: `POST /api/career/consultation` with ConsultationRequest (birthDate + birthTime, both required)
  - **Simplified Request** (Session 2026-04-30): JSON body에 birthDate, birthTime만 포함
    ```json
    {
      "birthDate": "1990-10-10",
      "birthTime": "14:30"
    }
    ```
  - **Expanded Response** (Session 2026-04-30): ApiResponse<ConsultationResponse> (23개 필드)
    - 기본 조언: industries, interviewTips, strengths
    - 관운: favoredPeriod, confidenceScore, reasoning
    - 사주: sajuProfile (dayMaster, dayMasterDescription, fiveElements, fiveElementsAnalysis, tenGodDistribution, keyTenGods)
    - OpenAI 분석: cautions, wealthStyle, longTermRoadmap, personalBranding, powerKeywords, mentalCare, environmentFit, workStyle, relationshipStrategy, careerTimeline
    - 메타: openaiModelVersion
  - Validation: @Valid 어노테이션으로 필수 필드 + 형식 검증 (birthDate YYYY-MM-DD, birthTime HH:mm)
  - File: `SSAju/src/main/java/ssafy/SSAju/controller/ConsultationController.java`

- [v] T028 [US2] Write unit tests for `ConsultationService` in `src/test/`
  - **Mock Setup** (Session 2026-04-30):
    - @Mock: ChatClient, SajuDataService, TenGodCalculator, HiddenStemCalculator, CareerFortuneAnalyzer, UserProfileRepository, SajuResultRepository, CareerConsultationRepository
  - Test cases:
    1. Happy path: valid birthDate + birthTime → consultation with 23 fields (모든 필드 포함: industries/tips/strengths/favoredPeriod/confidenceScore/reasoning/sajuProfile(6내부)/cautions/wealthStyle/longTermRoadmap/personalBranding/powerKeywords/mentalCare/environmentFit/workStyle/relationshipStrategy/careerTimeline/openaiModelVersion)
    2. SajuResult 기존 존재 → 재사용하고 저장
    3. SajuResult 신규 생성 → 저장 (hiddenStems, tenGodDistribution 포함)
    4. OpenAI API 호출 실패 → OpenAIApiException
    5. OpenAI 응답 null → OpenAIApiException
    6. 모든 nested record 타입 검증 (CareerAdviceResponse.IndustryRecommendation, WealthStyle, LongTermRoadmap, 등)
  - Test Fixture: MOCK_SAJU (FastAPIResponse), MOCK_ADVICE (CareerAdviceResponse with 23개 필드)
  - File: `SSAju/src/test/java/ssafy/SSAju/service/ConsultationServiceTest.java`

- [v] T029 [US2] Write unit tests for `ConsultationController` in `src/test/`
  - **Simplified Request Body** (Session 2026-04-30):
    ```json
    {
      "birthDate": "1990-10-10",
      "birthTime": "14:30"
    }
    ```
  - **Expanded Response** (Session 2026-04-30): 23개 필드 모두 포함 (기본 조언 3 + 관운 3 + 사주 프로필 내부 6 + OpenAI 분석 10 + 메타데이터 1)
  - Setup: MockMvcBuilders.standaloneSetup(controller) + SajuGlobalExceptionHandler
  - Test cases:
    1. Valid request (birthDate + birthTime) → 200 OK with 23-field response including all nested structures
    2. Missing birthTime → 400 Bad Request
    3. Invalid time format → 400 Bad Request
    4. Empty body → 400 Bad Request
    5. OpenAI timeout → 504 Gateway Timeout (via service exception)
    6. Verify response includes sajuProfile, all OpenAI field groups (wealthStyle, powerKeywords, careerTimeline 등)
  - File: `SSAju/src/test/java/ssafy/SSAju/controller/ConsultationControllerTest.java`

- [v] T030 [US2] Run all tests for US2 features
  - Command: `./gradlew test`
  - **Result**: BUILD SUCCESSFUL (all tests passed, 2026-04-30)
  - Verified: 전체 테스트 스위트 통과, CommitID 657e77a

---

## Phase 3-Refactor: Entity Normalization (JSON → Entities)

**Goal**: T030까지 구현된 JSON 저장 방식을 plan.md의 정규화된 엔티티 구조로 리팩토링.
**Scope**: 7개 새 엔티티 생성 (TenGodData, HiddenStemData, CareerFortune, Industry, InterviewTip, Strength)
**Strategy**: 기존 기능 유지 후 점진적 마이그레이션, 각 엔티티 생성 후 관련 Service/Repository 수정

### New Entities (JSON → Normalized Entities)

- [v] T031 [Refactor] Create `TenGodData` entity for normalizing SajuResult.tenGodDistribution
  - Fields: id, sajuResultId (FK, 1:1), tenGodName (String), score (Integer), createdAt
  - Purpose: SajuResult의 Map<String, Integer> tenGodDistribution을 엔티티로 저장
  - Link: 1:1 to SajuResult, FetchType.LAZY
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/TenGodData.java`

- [v] T032 [Refactor] [P] Create `HiddenStemData` entity for normalizing SajuResult.hiddenStems
  - Fields: id, sajuResultId (FK, 1:N), earthlyBranch (String, e.g., "子"), hiddenStem (String, e.g., "癸"), createdAt
  - Purpose: SajuResult의 Map<String, List<String>> hiddenStems를 정규화된 행으로 저장 (한 행 = 한 지지와 한 지장간)
  - Link: N:1 to SajuResult (multiple rows per SajuResult), FetchType.LAZY
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/HiddenStemData.java`

- [v] T033 [Refactor] Create `CareerFortune` entity for normalizing SajuResult.careerFortune
  - Fields: id, sajuResultId (FK, 1:1), favoredPeriod (String: "H1"/"H2"), confidenceScore (Integer, 0-100), reasoning (Text), createdAt
  - Purpose: SajuResult의 Map<String, Object> careerFortune을 엔티티로 저장
  - Link: 1:1 to SajuResult, FetchType.LAZY
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/CareerFortune.java`

- [v] T034 [Refactor] [P] Create `Industry` entity for normalizing CareerConsultation.industries
  - Fields: id, careerConsultationId (FK, 1:N), industryName (String), reason (Text), createdAt
  - Purpose: CareerConsultation의 JSON 리스트 industries를 엔티티로 저장
  - Link: N:1 to CareerConsultation, FetchType.LAZY
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/Industry.java`

- [v] T035 [Refactor] [P] Create `InterviewTip` entity for normalizing CareerConsultation.interviewTips
  - Fields: id, careerConsultationId (FK, 1:N), tipText (Text), createdAt
  - Purpose: CareerConsultation의 JSON 리스트 interviewTips를 엔티티로 저장
  - Link: N:1 to CareerConsultation, FetchType.LAZY
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/InterviewTip.java`

- [v] T036 [Refactor] [P] Create `Strength` entity for normalizing CareerConsultation.strengths
  - Fields: id, careerConsultationId (FK, 1:N), strengthText (Text), createdAt
  - Purpose: CareerConsultation의 JSON 리스트 strengths를 엔티티로 저장
  - Link: N:1 to CareerConsultation, FetchType.LAZY
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/Strength.java`

### New Repositories

- [v] T037 [Refactor] [P] Create repositories for new entities
  - Create: `TenGodDataRepository`, `HiddenStemDataRepository`, `CareerFortuneRepository`, `IndustryRepository`, `InterviewTipRepository`, `StrengthRepository`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/TenGodDataRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/HiddenStemDataRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/CareerFortuneRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/IndustryRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/InterviewTipRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/StrengthRepository.java`

### Service Updates for Entity-Based Storage

- [v] T038 [Refactor] Update `CareerFortuneService` to use TenGodData, HiddenStemData, CareerFortune entities
  - Modify: Save tenGodDistribution, hiddenStems, careerFortune as entities instead of JSON in SajuResult
  - Logic: After analyzing H1/H2, create TenGodData, HiddenStemData, CareerFortune entities and link to SajuResult
  - File: `SSAju/src/main/java/ssafy/SSAju/service/CareerFortuneService.java`

- [v] T039 [Refactor] Update `ConsultationService` to use Industry, InterviewTip, Strength entities
  - Modify: Save industries, interviewTips, strengths as entities instead of JSON in CareerConsultation
  - Logic: After OpenAI response, create Industry, InterviewTip, Strength entities and link to CareerConsultation
  - File: `SSAju/src/main/java/ssafy/SSAju/service/ConsultationService.java`

### Test Updates

- [v] T040 [Refactor] Update unit tests for entity normalization
  - Update `CareerFortuneServiceTest`: Verify TenGodData, HiddenStemData, CareerFortune entities created and linked
  - Update `ConsultationServiceTest`: Verify Industry, InterviewTip, Strength entities created and linked
  - File: `SSAju/src/test/java/ssafy/SSAju/service/CareerFortuneServiceTest.java`
  - File: `SSAju/src/test/java/ssafy/SSAju/service/ConsultationServiceTest.java`

- [v] T041 [Refactor] Run all refactored tests
  - Command: `./gradlew test`
  - Verify: All tests pass with normalized entity structure, no JSON storage in tables
  - Verify: Data persistence correctly links parent→child entities

---

### User Story 4: User Satisfaction Feedback (Priority P1)

**Goal**: After any saju analysis, users can provide simple binary satisfaction feedback (satisfied/dissatisfied).
**Independent Test**: Feedback submission → Stored in DB → Returns success response (runs parallel with US2)
**Expected Outcome**: Feedback collection API working, UserSatisfactionFeedback entity populated for Phase 2 dashboards

- [v] T042 [US4] Create `UserSatisfactionFeedback` entity in `career/entity/`
  - Fields: 
    - id (Long, PK)
    - sajuResultId (Long, FK to SajuResult, NOT NULL)
    - feedbackType (ENUM: CAREER_TIMING/CONSULTATION/COMPATIBILITY, NOT NULL)
    - satisfactionStatus (ENUM: SATISFIED/DISSATISFIED, NOT NULL)
    - feedbackContent (VARCHAR(500), nullable) - 사용자 상세 의견 (최대 500자)
    - createdAt (LocalDateTime)
  - Constraints: FK(sajuResultId), index(sajuResultId, createdAt)
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/UserSatisfactionFeedback.java`

- [v] T043 [US4] [P] Create `UserSatisfactionFeedbackRepository` in `repository/`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/UserSatisfactionFeedbackRepository.java`

- [v] T044 [US4] [P] Create `SatisfactionFeedbackRequest` DTO in `dto/request/`
  - Fields: 
    - sajuResultId (Long, @NotNull)
    - feedbackType (ENUM: CAREER_TIMING/CONSULTATION/COMPATIBILITY, @NotNull)
    - satisfactionStatus (ENUM: SATISFIED/DISSATISFIED, @NotNull)
    - feedbackContent (String, @Size(max=500), Optional) - 상세 의견 (최대 500자)
  - Validation: @NotNull on required fields, @Size on feedbackContent
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/request/SatisfactionFeedbackRequest.java`

- [v] T045 [US4] [P] Create `SatisfactionFeedbackResponse` DTO in `dto/response/`
  - Fields: 
    - feedbackId (Long)
    - createdAt (LocalDateTime)
    - feedbackContent (String, 제출한 상세 의견 에코백)
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/SatisfactionFeedbackResponse.java`

- [v] T046 [US4] Create `FeedbackService` in `service/`
  - Method: `saveFeedback(SatisfactionFeedbackRequest)` → validates SajuResult exists, stores feedback
  - Handles: SajuResult not found → throw custom exception, enum validation
  - File: `SSAju/src/main/java/ssafy/SSAju/service/FeedbackService.java`

- [v] T047 [US4] Create `FeedbackController` in `controller/`
  - Endpoint: `POST /api/feedback/satisfaction`
  - Handles: Request validation, calls `FeedbackService`, returns `ApiResponse<SatisfactionFeedbackResponse>`
  - File: `SSAju/src/main/java/ssafy/SSAju/controller/FeedbackController.java`

- [v] T048 [US4] Write unit tests for `FeedbackService` in `src/test/`
  - Test: Valid feedback saved, SajuResult not found → 404, invalid enum → 400, null handling
  - File: `SSAju/src/test/java/ssafy/SSAju/service/FeedbackServiceTest.java`

- [v] T049 [US4] Write unit tests for `FeedbackController` in `src/test/`
  - Test: Valid feedback → 200 OK, invalid type → 400, missing SajuResult → 404
  - File: `SSAju/src/test/java/ssafy/SSAju/controller/FeedbackControllerTest.java`

- [v] T050 [US4] Run all tests for US4 features
  - Command: `./gradlew test --tests "ssafy.SSAju.service.FeedbackServiceTest OR ssafy.SSAju.controller.FeedbackControllerTest"`
  - Verify: BUILD SUCCESSFUL before committing

---

## Phase 3-Enhancement: 통신, 동시성, 엔티티, 아키텍처 개선

**Goal**: T041(상수화) 완료 후, 4가지 핵심 리팩토링 수행
1. **외부 API 통신 최적화**: WebClient → RestClient로 전환, Spring Retry 도입
2. **동시성 제어 및 DB 최적화**: SajuResult 동시 Insert 경합 해결, H2 MySQL 모드 적용
3. **JPA 엔티티 설계 최적화**: @CreatedDate/@LastModifiedDate, equals&hashCode, 엔티티 상태 명확화
4. **객체 지향 및 아키텍처 개선**: 컬렉션 객체화, 검증 로직 분리

### 0. 📦 Cleanup & Optimization

- [ ] T030-1 [Refactor] Remove unnecessary Jackson @JsonProperty annotations from DTOs
  - **Rationale**: FastAPI 응답이 이미 camelCase이고, Java 필드명도 camelCase이므로 @JsonProperty 불필요. Jackson이 자동으로 매칭함.
  - Remove: FastAPIResponse.java의 모든 @JsonProperty 어노테이션
  - Result: DTO 간결화, Jackson import 제거 (의존성 불필요)
  - Files:
    - `SSAju/src/main/java/ssafy/SSAju/dto/external/FastAPIResponse.java`

### 1. ⚡ 외부 API 통신 최적화 (WebClient → RestClient) - Phase 1 핵심

**Rationale**: 동기식 호출에 Reactive 의존성 불필요. RestClient가 더 가볍고 직관적.

**주의**: 이 섹션은 Phase 1에서 즉시 진행해야 합니다. spec.md(L227~230) 및 plan.md에서 RestClient로 확정되었습니다.

- [ ] T051 [Enhancement] 의존성 추가: Spring Retry, RestClient
  - `build.gradle`에 다음 추가:
    ```gradle
    implementation 'org.springframework:spring-retry'
    implementation 'org.springframework.boot:spring-boot-starter-web'  // RestClient 포함
    ```
  - Remove: WebClient 기반 설정 (WebClientConfig.java 제거 또는 RestClient 설정으로 전환)
  - File: `SSAju/build.gradle`

- [ ] T052 [Enhancement] Create `FastApiRestClientConfig` in `config/`
  - RestClient bean 생성 (default timeout, SSL 설정 등)
  - @EnableRetry 어노테이션 추가 (Spring Retry 활성화, application.yaml 설정 불필요)
  - Exponential backoff 정책: 1초, 2초, 4초 (최대 3회, @Retryable 어노테이션 속성으로 제어)
  - Note: `spring.task.retry.*` 프로퍼티는 `ThreadPoolTask*` 설정용이므로 사용하지 말 것
  - File: `SSAju/src/main/java/ssafy/SSAju/config/FastApiRestClientConfig.java`

- [ ] T053 [Enhancement] Refactor `SajuDataService` to use RestClient
  - **Before**: WebClient + .block() 동기 처리
  - **After**: RestClient + Spring Retry (@Retryable)
  - Method: `fetchSajuFromFastAPI(LocalDate, LocalTime)` → RestClient 호출, 자동 재시도
  - Exception Handling (Spring RestClient 공식 문서 기준):
    - ResourceAccessException (타임아웃/연결 실패) → @Retryable 대상 (그대로 던지기)
    - RestClientResponseException 4xx (클라이언트 오류) → InvalidSajuDataException (재시도 안 함)
    - RestClientResponseException 5xx (서버 오류) → 그대로 던지기 (재시도 대상 유지)
  - File: `SSAju/src/main/java/ssafy/SSAju/service/SajuDataService.java`

- [ ] T054 [Enhancement] Refactor `ConsultationService` OpenAI RestClient 호출
  - **Option**: ChatClient 유지 vs. RestClient로 전환 (프롬프트 유연성 필요 시)
  - Spring Retry 적용: OpenAI 타임아웃 시 재시도 (최대 2회)
  - File: `SSAju/src/main/java/ssafy/SSAju/service/ConsultationService.java`

- [ ] T055 [Enhancement] 통신 테스트 업데이트
  - Update: SajuDataServiceTest (RestClient mock 대응)
  - Update: ConsultationServiceTest (RestClient mock 대응)
  - Verify: Spring Retry 동작 확인 (재시도 로그 검증)
  - File: `SSAju/src/test/java/ssafy/SSAju/service/SajuDataServiceTest.java`
  - File: `SSAju/src/test/java/ssafy/SSAju/service/ConsultationServiceTest.java`

### 2. 동시성 제어 및 DB 최적화

**Rationale**: DataIntegrityViolationException 대신 JdbcTemplate INSERT IGNORE 활용. H2 MySQL 모드로 프로덕션 환경 근접.

- [ ] T056 [Enhancement] Create `SajuResultJdbcRepository` (JdbcTemplate 기반)
  - Method: `insertOrIgnore(SajuResult)` - INSERT IGNORE 네이티브 쿼리
  - Returns: 1 (신규 삽입) 또는 0 (이미 존재)
  - UNIQUE constraint: user_profile_id 활용 (saju_result는 userProfile과 1:1 관계, birthDate/birthTime은 user_profile에 있음)
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/SajuResultJdbcRepository.java`

- [ ] T057 [Enhancement] Update `SajuDataService` to use INSERT IGNORE
  - **Before**: try-catch with DataIntegrityViolationException
  - **After**: `SajuResultJdbcRepository.insertOrIgnore()` 호출
  - Logic: SajuResult 존재 확인 → 없으면 insertOrIgnore → 엔티티 반환
  - File: `SSAju/src/main/java/ssafy/SSAju/service/SajuDataService.java`

- [ ] T058 [Enhancement] Configure H2 MySQL mode in test
  - `application-test.properties` (또는 `application-test.yaml`)에:
    ```properties
    spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE
    spring.datasource.driver-class-name=org.h2.Driver
    spring.h2.console.enabled=true
    spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
    ```
  - Verify: MySQL 문법 호환성 테스트 (예: INSERT IGNORE, UNIQUE constraint)
  - File: `SSAju/src/test/resources/application-test.yaml`

- [ ] T059 [Enhancement] Test race condition handling
  - Test: 동시에 동일한 (birthDate, birthTime) SajuResult 생성 → insertOrIgnore 검증
  - Verify: 한 번만 INSERT, 나머지는 무시
  - Tool: @ParameterizedTest + concurrent threads
  - File: `SSAju/src/test/java/ssafy/SSAju/repository/SajuResultJdbcRepositoryTest.java`

### 3. JPA 엔티티 설계 최적화

**Rationale**: @CreatedDate/@LastModifiedDate 자동 관리, equals&hashCode는 ID 기준 구현 (Proxy 안전성), 엔티티 상태 명확화.

- [ ] T060 [Enhancement] Enable JPA Auditing & implement equals/hashCode
  - **@EnableJpaAuditing** 추가: `SSAjuApplication.java` 또는 @Configuration 클래스
  - **모든 엔티티에 적용**:
    - Remove: `@PreUpdate` 어노테이션
    - Add: `@CreatedDate`, `@LastModifiedDate` (이미 T009에서 명시했으므로 구현만)
    - Implement: `equals(Object)`, `hashCode()` (ID 기준, Lombok @EqualsAndHashCode 제거)
  - **ID 기준 equals/hashCode 패턴**:
    ```java
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (!(o instanceof SajuResult)) return false;
        SajuResult that = (SajuResult) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    ```
  - Files: 
    - `SSAju/src/main/java/ssafy/SSAju/SSAjuApplication.java` (@EnableJpaAuditing 추가)
    - `SSAju/src/main/java/ssafy/SSAju/career/entity/*.java` (모든 엔티티 equals/hashCode 구현)

- [ ] T061 [Enhancement] Clarify entity state lifecycle
  - Document: 영속(Persistent), 준영속(Detached), 비영속(Transient) 상태 정확히 이해
  - Service 계층에서 엔티티 상태 명확히 (예: `repository.save()` 전/후 상태 변화)
  - **패턴**:
    ```java
    // Transient → Persistent
    SajuResult result = SajuResult.builder().birthDate(...).build();  // transient
    SajuResult saved = repository.save(result);  // persistent
    
    // Persistent → Detached (트랜잭션 종료 후)
    // Detached 상태에서 lazy loading 불가 → LazyInitializationException 위험
    ```
  - Document: `SSAju/src/main/java/ssafy/SSAju/career/entity/README.md` (엔티티 상태 가이드)

- [ ] T062 [Enhancement] Test equals/hashCode & lazy loading safety
  - Test: equals/hashCode가 Proxy 객체에서도 정상 동작하는지 검증
  - Test: equals(null), equals(different type) 엣지 케이스
  - Test: HashSet/HashMap에 엔티티 저장 후 조회 가능한지 확인
  - File: `SSAju/src/test/java/ssafy/SSAju/career/entity/EntityEqualsHashCodeTest.java`

### 4. 객체 지향 및 아키텍처 개선

**Rationale**: 컬렉션 객체화 (TenGodDistribution 같은 일급 객체), 검증 로직 분리 (서비스 책임 단순화).

- [ ] T063 [Enhancement] Create value objects for collections
  - **TenGodDistribution**: `Map<String, Integer>` → 일급 컬렉션
    ```java
    public class TenGodDistribution {
        private final Map<String, Integer> distribution;
        
        public TenGodDistribution(Map<String, Integer> data) { ... }
        public Integer getScore(String tenGodName) { ... }
        public boolean hasHighConfidence(int threshold) { ... }
    }
    ```
  - **HiddenStems**: `Map<String, List<String>>` → 일급 컬렉션
    ```java
    public class HiddenStems {
        private final Map<String, List<String>> stems;
        
        public List<String> getByBranch(String branch) { ... }
        public boolean containsStem(String stem) { ... }
    }
    ```
  - **FiveElements**: `Map<String, Integer>` → 일급 컬렉션
  - File:
    - `SSAju/src/main/java/ssafy/SSAju/career/domain/TenGodDistribution.java`
    - `SSAju/src/main/java/ssafy/SSAju/career/domain/HiddenStems.java`
    - `SSAju/src/main/java/ssafy/SSAju/career/domain/FiveElements.java`

- [ ] T064 [Enhancement] Refactor services to use value objects
  - **CareerFortuneAnalyzer**: `Map<String, Integer>` → `TenGodDistribution` 사용
  - **HiddenStemCalculator**: `Map<String, List<String>>` → `HiddenStems` 사용
  - **CompatibilityScoreCalculator**: 두 객체 모두 사용
  - File:
    - `SSAju/src/main/java/ssafy/SSAju/career/util/CareerFortuneAnalyzer.java`
    - `SSAju/src/main/java/ssafy/SSAju/career/util/HiddenStemCalculator.java`
    - `SSAju/src/main/java/ssafy/SSAju/career/util/CompatibilityScoreCalculator.java`

- [ ] T065 [Enhancement] Create validation utility classes
  - **SajuValidator**: 사주 데이터 검증 (heavenlyStems, earthlyBranches 개수, 형식 등)
  - **RequestValidator**: DTO 검증 로직 (birthDate 범위, birthTime 형식 등)
  - **CompatibilityValidator**: 호환성 분석 입력 검증
  - Service에서는 이들 Validator 호출만 수행
  - File:
    - `SSAju/src/main/java/ssafy/SSAju/career/validator/SajuValidator.java`
    - `SSAju/src/main/java/ssafy/SSAju/career/validator/RequestValidator.java`
    - `SSAju/src/main/java/ssafy/SSAju/career/validator/CompatibilityValidator.java`

- [ ] T066 [Enhancement] Refactor services to use validators
  - **SajuDataService**: SajuValidator 사용 (FastAPI 응답 검증)
  - **CareerFortuneService**: RequestValidator 사용 (요청 검증)
  - **ConsultationService**: 두 validator 모두 사용
  - **CompanyMatchingService**: CompatibilityValidator 사용
  - File: 위 Service 파일들

- [ ] T067 [Enhancement] Update tests for value objects & validators
  - Test: TenGodDistribution, HiddenStems, FiveElements 객체 기능
  - Test: Validator 정상 동작 (valid/invalid 경우)
  - Test: Service가 Validator 호출하는지 검증
  - File:
    - `SSAju/src/test/java/ssafy/SSAju/career/domain/*Test.java`
    - `SSAju/src/test/java/ssafy/SSAju/career/validator/*Test.java`
    - Updated service tests

- [ ] T068 [Enhancement] Run full test suite and verify refactoring
  - Command: `./gradlew clean test`
  - Verify:
    1. ✅ RestClient 모든 통신 정상
    2. ✅ Race condition 처리 확인 (insertOrIgnore)
    3. ✅ H2 MySQL 모드 테스트 통과
    4. ✅ equals/hashCode Proxy 안전성
    5. ✅ 모든 검증 로직 분리 완료
    6. ✅ 컬렉션 객체화 완료
  - Coverage: >85% 목표

---

## Phase 3-Refactor-3: Advanced Normalization & Service Optimization

**Goal**: Phase 3-Enhancement 완료 후, 3가지 최적화 작업 수행
1. **fullSajuData 완전 정규화**: Map → SajuFullData 엔티티
2. **PromptProvider 분리**: ConsultationService의 buildPrompt 메서드 외부화
3. **모든 하드코딩 상수 제거**: 남은 magic number/string 추출

### SajuFullData Normalization

- [ ] T069 [Refactor] Create `SajuFullData` entity for normalizing SajuResult.fullSajuData
  - **Purpose**: SajuResult.fullSajuData (Map<String, Object>) 완전 정규화 → 객체 저장으로 타입 안전성 및 쿼리 성능 향상
  - **Fields**: 
    - id: Long (PK)
    - sajuResultId: Long (FK to SajuResult, NOT NULL, UNIQUE) 
    - yearPillar, monthPillar, dayPillar, hourPillar: String (天干地支 조합 문자열)
    - dayMaster: String (일간, 천간 1글자)
    - dayMasterElement: String (일간의 오행: 木火土金水 중 1개)
    - fiveElements: Map<String, Integer> (오행 분포, JSON 컬럼 유지 가능 또는 FiveElementData 1:N로 정규화 - 현재는 JSON 유지)
    - solarCorrection: Map<String, Object> (선택사항, JSON 컬럼 - 현재는 JSON 유지)
    - createdAt: LocalDateTime (@CreatedDate)
  - **관계**: 1:1 to SajuResult (mappedBy="sajuFullData"), FetchType.LAZY
  - **정규화 선택지**:
    - **Option A** (현재 선택): fiveElements, solarCorrection은 JSON 유지 (Map으로 저장, 변경 빈도 낮음)
    - **Option B** (완전 정규화): FiveElementData (1:N), SolarCorrectionData (1:N) 생성
  - **설계 원칙**: 변경 빈도 낮은 필드(fiveElements, solarCorrection)는 JSON 유지하여 쿼리 단순화. 자주 조회/필터링되는 필드(yearPillar, dayMaster 등)는 엔티티 필드로 저장
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/SajuFullData.java`

- [ ] T070 [Refactor] Create `SajuFullDataRepository` in `repository/`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/SajuFullDataRepository.java`

- [ ] T071 [Refactor] Update `SajuResult` to use normalized entities (SajuFullData, TenGodData, HiddenStemData, CareerFortune)
  - **Phase 3-Refactor 진행 상황**:
    - ✅ Phase 3.1-3.2: SajuResult.fullSajuData, tenGodDistribution, hiddenStems, careerFortune을 JSON (Map)으로 임시 저장
    - ⏭ Phase 3-Refactor: 다음 4가지를 엔티티로 정규화 (이 작업 이후):
      1. TenGodData: tenGodDistribution (Map<String, Integer>) → 1:N 엔티티 (각 십신별 1행)
      2. HiddenStemData: hiddenStems (Map<String, List<String>>) → 1:N 엔티티 (각 지지별 지장간 1행)
      3. CareerFortune: careerFortune (Map) → 1:1 엔티티 (favoredPeriod, confidenceScore, reasoning)
      4. SajuFullData: fullSajuData (Map) → 1:1 엔티티 (yearPillar, dayMaster 등)
  - **Modify SajuResult**:
    - Remove: fullSajuData (Map<String, Object> with @Convert)
    - Remove: tenGodDistribution (Map<String, Integer> with @Convert)
    - Remove: hiddenStems (Map<String, List<String>> with @Convert)
    - Remove: careerFortune (Map<String, Object> with @Convert)
    - Add: `@OneToOne(fetch = FetchType.LAZY) SajuFullData sajuFullData` (1:1, mappedBy="sajuResult")
    - Add: `@OneToOne(fetch = FetchType.LAZY) CareerFortune careerFortune` (1:1, mappedBy="sajuResult")
    - Add: `@OneToMany(fetch = FetchType.LAZY, mappedBy = "sajuResult") List<TenGodData> tenGodDataList` (1:N)
    - Add: `@OneToMany(fetch = FetchType.LAZY, mappedBy = "sajuResult") List<HiddenStemData> hiddenStemDataList` (1:N)
  - **Mapper 업데이트**: SajuResultMapper에서 FastAPIResponse → SajuFullData, TenGodData, HiddenStemData, CareerFortune 변환 로직 추가
    - `toSajuFullData(FastAPIResponse)`: yearPillar, dayMaster, fiveElements 등을 SajuFullData 엔티티로 매핑
    - `toTenGodDataList(Map<String, Integer>)`: tenGodDistribution을 List<TenGodData>로 변환 (각 십신별 1행)
    - `toHiddenStemDataList(Map<String, List<String>>)`: hiddenStems를 List<HiddenStemData>로 변환 (각 지지별 지장간별 행)
    - `toCareerFortune(Map)`: careerFortune 맵을 CareerFortune 엔티티로 변환
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/SajuResult.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/mapper/SajuResultMapper.java`

- [ ] T072 [Refactor] Update Service layer to save all normalized entities (TenGodData, HiddenStemData, CareerFortune, SajuFullData)
  - **Modify CareerFortuneService**:
    1. SajuDataService에서 FastAPI 응답 받음 (FastAPIResponse)
    2. SajuResultMapper를 이용하여 다음 4가지 변환:
       - `mapper.toSajuFullData(fastAPIResponse)` → SajuFullData 엔티티
       - `mapper.toTenGodDataList(tenGodDistribution)` → List<TenGodData>
       - `mapper.toHiddenStemDataList(hiddenStems)` → List<HiddenStemData>
       - `mapper.toCareerFortune(careerFortuneMap)` → CareerFortune 엔티티
    3. SajuResult 생성 시 이들 엔티티 함께 저장 (Repository.save(sajuResult) 호출하면 cascade=ALL이므로 자동 저장)
    4. 트랜잭션: FastAPI/OpenAI I/O는 트랜잭션 밖, 각 Repository.save는 개별 @Transactional
  - **Modify ConsultationService**:
    1. CareerFortuneService와 동일하게 SajuResultMapper 사용
    2. SajuResult 저장 시 SajuFullData, TenGodData, HiddenStemData, CareerFortune 모두 함께 저장
    3. CareerConsultation 저장 시 Industry, InterviewTip, Strength도 함께 저장
  - **Mapper 호출 패턴**:
    ```java
    SajuResult sajuResult = SajuResultMapper.toSajuResult(fastAPIResponse, userProfile);
    // 내부에서:
    // - sajuResult.fullSajuData = mapper.toSajuFullData(fastAPIResponse)
    // - sajuResult.tenGodDataList = mapper.toTenGodDataList(...)
    // - sajuResult.hiddenStemDataList = mapper.toHiddenStemDataList(...)
    // - sajuResult.careerFortune = mapper.toCareerFortune(...)
    sajuResultRepository.save(sajuResult);  // cascade=ALL로 자식도 저장
    ```
  - File: `SSAju/src/main/java/ssafy/SSAju/service/CareerFortuneService.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/service/ConsultationService.java`

- [ ] T073 [Refactor] Update tests for SajuFullData normalization
  - **Update**: CareerFortuneServiceTest, ConsultationServiceTest에서 SajuFullData 검증 추가
  - Test: SajuFullData 엔티티가 올바르게 생성되고 저장되는지 확인
  - File: `SSAju/src/test/java/ssafy/SSAju/service/CareerFortuneServiceTest.java`
  - File: `SSAju/src/test/java/ssafy/SSAju/service/ConsultationServiceTest.java`

- [ ] T074 [Refactor] Run all tests for SajuFullData refactoring
  - Command: `./gradlew test`
  - Verify: BUILD SUCCESSFUL, no SQL errors, SajuFullData 저장 확인

### PromptProvider Separation

- [ ] T075 [Refactor] Create `PromptProvider` component in `service/`
  - **Purpose**: ConsultationService의 buildPrompt 메서드 외부화
  - **Methods**:
    - `getCareerConsultationPrompt(SajuData sajuData, int currentYear, LocalDate birthDate, LocalTime birthTime)`: 
      - 사주 데이터(일간, 천간, 지지, 오행, 지장간, 십신) 포함
      - 현재 연도, 12개월 타임라인 포함
      - 23개 필드(기본 조언 3, 관운 분석 3, 사주 프로필 내부 6, OpenAI 분석 10, 메타데이터 1) 포함한 상세 프롬프트
      - JSON 스키마 정의 (careerTimeline.months 객체 형식 예시 포함)
    - `getCompanyCompatibilityPrompt(...)`: (Phase 3 추가 가능) 기업 궁합 분석용 프롬프트
  - **Properties**: 
    - `@Autowired private ConfigurationProperties` 또는 프로퍼티 파일에서 로드 가능
    - 또는 하드코딩하되, 프롬프트 변경 시 이 클래스만 수정하도록 캡슐화
  - **Test**: PromptProvider에 대한 단위 테스트 작성 (프롬프트 포함 필드 검증)
  - File: `SSAju/src/main/java/ssafy/SSAju/service/PromptProvider.java`
  - File: `SSAju/src/test/java/ssafy/SSAju/service/PromptProviderTest.java`

- [ ] T076 [Refactor] Update `ConsultationService` to use PromptProvider
  - **Modify**: `callOpenAI()` 또는 `getCareerConsultation()` 메서드에서 PromptProvider 호출
  - **Before**:
    ```java
    String prompt = buildPrompt(sajuData, currentYear);  // 서비스 내부
    CareerAdviceResponse response = chatClient.prompt().user(prompt).call().entity(...);
    ```
  - **After**:
    ```java
    String prompt = promptProvider.getCareerConsultationPrompt(sajuData, currentYear, ...);
    CareerAdviceResponse response = chatClient.prompt().user(prompt).call().entity(...);
    ```
  - **Remove**: ConsultationService.buildPrompt() 메서드 삭제 (PromptProvider로 이동)
  - File: `SSAju/src/main/java/ssafy/SSAju/service/ConsultationService.java`

- [ ] T077 [Refactor] Verify PromptProvider integration
  - Test: ConsultationServiceTest에서 PromptProvider mock 확인
  - Verify: 프롬프트 변경이 PromptProvider에만 영향을 미치는지 확인
  - File: `SSAju/src/test/java/ssafy/SSAju/service/ConsultationServiceTest.java`

### Final Constants Extraction & Cleanup

- [ ] T078 [Refactor] Extract remaining magic numbers and strings
  - **Scope**: 이전 T079~T075에서 놓친 모든 하드코딩된 값
  - **검색 대상**:
    - 숫자 리터럴: 0, 1, 2, 3, 4, 5, 8, 12, 25, 30, 35, 50, 75, 100, 1000, etc.
    - 문자열 리터럴: "H1", "H2", "정관", "편관", 월 이름 등
    - 날짜/시간: "12:00", "YYYY-MM-DD", "HH:mm"
  - **조치**:
    - 기존 상수 클래스에 추가 (ValidationConstants, CareerFortuneConstants 등)
    - 또는 새 상수 클래스 생성 (DateFormatConstants, DefaultTimeConstants)
  - **Verification**: grep으로 '숫자'와 '따옴표 문자열'의 직접 사용 검색
    ```bash
    grep -rn '[^a-zA-Z_]"[A-Z]' career/service/ | grep -v "ApiResponse\|ErrorInfo" | head -20
    grep -rn '[^a-zA-Z_][0-9]\+[^a-zA-Z_]' career/ | grep -v "LocalDate\|LocalTime\|Duration" | head -20
    ```
  - File: career/constants/*.java (기존 또는 신규)

- [ ] T079 [Refactor] Refactor all services and utilities to use final extracted constants
  - **Targets**:
    - SajuDataService.java: API 타임아웃, 엔드포인트 URL
    - ConsultationService.java: 현재 연도 기본값, 12개월, JSON 모드
    - CompanyMatchingService.java: 기본 설립 시간 "12:00", 호환성 범위
    - CareerFortuneAnalyzer.java: 신뢰도 임계값, H1/H2 판정 로직
    - GlobalExceptionHandler.java: HTTP 상태코드, 에러 메시지 (ErrorMessageConstants 사용)
    - All Calculators: 점수 계산 기준값 (TenGodConstants, HiddenStemConstants 사용)
  - Verification: 모든 magic number/string 제거 확인
  - File: Multiple service and util files

- [ ] T080 [Refactor] Run final test suite and verify constant extraction
  - Command: `./gradlew test`
  - Verify:
    1. 모든 테스트 통과
    2. 상수 미사용 코드 없음 (grep 재확인)
    3. 코드 스타일 준수 (Lombok, record, FetchType.LAZY 등)
    4. No JSON columns (fullSajuData 포함 모든 JSON 정규화됨)
  - **Final Checklist**:
    - ✅ SajuFullData 엔티티 저장 확인
    - ✅ PromptProvider 호출 확인
    - ✅ 모든 상수 사용 확인
    - ✅ 테스트 100% 통과
  - File: Multiple test files

---

### User Story 3: Company & Job Fit Analysis (Priority P2)

**Goal**: Users can analyze compatibility between their saju, target job role, and target company founding date, receiving a score (0-100), job role five-elements analysis, recommended roles, and actionable interview strategy.
**Independent Test**: User saju + targetRole + company date → Compatibility calculation → Score + targetRoleAnalysis + roles response (depends on core structures)
**Expected Outcome**: Compatibility endpoint working, CompanyCompatibility + RecommendedRole entities stored in DB. targetRoleAnalysis, fiveElements, analysisBreakdown, actionableStrategy, expectedInterviewQuestions는 Service에서 계산 후 응답에만 포함 (DB 미저장)

- [ ] T081 [US3] Create `CompanyCompatibility` and normalized child entities in `career/entity/`
  - **CompanyCompatibility** (루트 엔티티):
    - id (Long, PK), userProfileId (FK to UserProfile), companyName (String, NOT NULL)
    - targetRoleCategory (JobCategoryEnum, NOT NULL), targetRoleDetailName (String, optional)
    - compatibilityScore (Integer, 0-100, NOT NULL), summary (TEXT), createdAt (LocalDateTime)
    - **UNIQUE(userProfileId, companyName, targetRoleCategory)** — INSERT IGNORE로 중복 방지
    - 1:1 자식: TargetRoleAnalysis, FiveElementsAnalysis, AnalysisBreakdown, ActionableStrategy
    - 1:N 자식: ExpectedInterviewQuestion, RoleCompatibility, MonthlyForecast, Caution
  - **TargetRoleAnalysis** (1:1): id, compatibilityId (FK, UNIQUE), matchScore (Integer), synergy (TEXT), warning (TEXT)
  - **FiveElementsAnalysis** (1:1): id, compatibilityId (FK, UNIQUE), userDistribution (JSON), companyDistribution (JSON), synergyDescription (TEXT)
  - **AnalysisBreakdown** (1:1): id, compatibilityId (FK, UNIQUE), characterMatch (Integer), potentialSynergy (Integer), longTermStability (Integer)
  - **ActionableStrategy** (1:1): id, compatibilityId (FK, UNIQUE), interviewKeywords (JSON), weaknessDefense (TEXT), luckyDays (JSON), preferredTime (String)
  - **ExpectedInterviewQuestion** (1:N): id, compatibilityId (FK), question (TEXT), intent (TEXT)
  - **RoleCompatibility** (1:N): id, compatibilityId (FK), roleName (String), score (Integer), reason (TEXT), tag (String)
  - **MonthlyForecast** (1:N): id, compatibilityId (FK), month (Integer, 1-12), score (Integer), status (Enum: LUCKY/NORMAL/CAUTION), advice (TEXT)
  - **Caution** (1:N): id, compatibilityId (FK), content (TEXT)
  - Use: @Getter, @NoArgsConstructor(access=PROTECTED), @Builder, FetchType.LAZY for all relationships
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/CompanyCompatibility.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/TargetRoleAnalysis.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/FiveElementsAnalysis.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/AnalysisBreakdown.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/ActionableStrategy.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/ExpectedInterviewQuestion.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/RoleCompatibility.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/MonthlyForecast.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/Caution.java`

- [ ] T082 [US3] [P] Create repositories for all CompanyCompatibility entities in `repository/`
  - **CompanyCompatibilityRepository**: JPA repository + `findByUserProfileIdAndCompanyNameAndTargetRoleCategory()` 쿼리 메서드
  - **CompanyCompatibilityJdbcRepository**: JdbcTemplate 기반 INSERT IGNORE 구현
    - `insertOrIgnore(CompanyCompatibility)`: UNIQUE(userProfileId, companyName, targetRoleCategory) 활용
    - Returns: 1 (신규 삽입) 또는 0 (이미 존재)
  - 자식 엔티티 Repository: TargetRoleAnalysisRepository, FiveElementsAnalysisRepository, AnalysisBreakdownRepository, ActionableStrategyRepository, ExpectedInterviewQuestionRepository, RoleCompatibilityRepository, MonthlyForecastRepository, CautionRepository
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/CompanyCompatibilityRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/CompanyCompatibilityJdbcRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/TargetRoleAnalysisRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/FiveElementsAnalysisRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/AnalysisBreakdownRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/ActionableStrategyRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/ExpectedInterviewQuestionRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/RoleCompatibilityRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/MonthlyForecastRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/CautionRepository.java`

- [ ] T083 [US3] [P] Create `CompatibilityRequest` and `CompatibilityResponse` DTOs, and `JobCategoryEnum`
  - Request fields: userBirthDate (LocalDate, @NotNull), userBirthTime (LocalTime, optional), targetRole (TargetRoleRequest record: category(JobCategoryEnum, @NotNull), detailName(String, optional)), companyName (@NotNull), companyFoundingDate (LocalDate, optional), companyFoundingTime (LocalTime, optional, **defaults to 12:00 if missing**)
  - Response fields:
    - requestContext: {companyName, targetRole: {category, detailName}} (요청 에코)
    - compatibilityScore: 0-100 정수
    - summary: 전체 궁합 한 줄 요약 텍스트
    - targetRoleAnalysis: {matchScore, synergy, warning} (JobRoleAnalyzer에서 생성, DB 미저장)
    - fiveElements: {userDistribution, companyDistribution, synergyDescription} (Service에서 계산, DB 미저장)
    - analysisBreakdown: {characterMatch, potentialSynergy, longTermStability} (Service에서 계산)
    - actionableStrategy: {interviewKeywords[], weaknessDefense, bestTiming: {luckyDays[], preferredTime}} (Service에서 생성)
    - expectedInterviewQuestions[]: [{question, intent}] (Service에서 생성)
    - roleCompatibility[]: [{roleName, score, reason, tag}] (Array of Objects, score/reason/tag는 Service에서 계산)
    - monthlyForecast[]: [{month(1-12), score, status(LUCKY/NORMAL/CAUTION), advice}] - 5개 월만 포함
    - cautions[]: 주의사항 문자열 배열
  - JobCategoryEnum: TECH_BACKEND, TECH_FRONTEND, TECH_MOBILE, TECH_DATA, TECH_INFRA, FINANCE, MARKETING, HR, OPERATIONS, SALES, STRATEGY, RESEARCH. 각 항목은 primaryElement(String), secondaryElement(String) 보유
  - Note: 모든 분석 결과(targetRoleAnalysis, fiveElements, analysisBreakdown, actionableStrategy, expectedInterviewQuestions, roleCompatibility, monthlyForecast, cautions)를 정규화된 자식 엔티티로 DB 저장. CompanyCompatibility에 UNIQUE(userProfileId, companyName, targetRoleCategory) + INSERT IGNORE 패턴으로 중복 방지. companyFoundingTime 미상 시 자동으로 12:00으로 설정하고 지장간 포함 계산.
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/request/CompatibilityRequest.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/CompatibilityResponse.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/util/JobCategoryEnum.java`

- [ ] T084 [US3] Create `CompanyInfoService` in `service/`
  - Method: `lookupCompanyFoundingDate(companyName)` → calls public data API with fallback to manual input. If time not found, use default 12:00
  - Handles: API timeout → PublicDataApiException, company not found → inform user to provide founding date. If time missing → auto-set to 12:00
  - File: `SSAju/src/main/java/ssafy/SSAju/service/CompanyInfoService.java`

- [ ] T085 [US3] Create `CompanyMatchingService` and `JobRoleAnalyzer` in `service/` and `career/util/`
  - Method: `analyzeCompatibility(CompatibilityRequest request)` → compatibility score + all analysis results (DB 저장)
  - Logic:
    1. Fetch user saju via SajuDataService with userBirthDate + userBirthTime → Calculate user HiddenStems + TenGod + FiveElements
    2. Fetch company saju (with companyFoundingTime defaulting to 12:00 if missing) → Calculate company HiddenStems + TenGod
    3. Use `CompatibilityScoreCalculator` with both sets of data for accurate scoring
    4. Use `JobRoleAnalyzer.analyze(userFiveElements, request.targetRole().category())` → targetRoleAnalysis (matchScore, synergy, warning)
    5. Build fiveElements comparison, analysisBreakdown, actionableStrategy, expectedInterviewQuestions, roleCompatibility, monthlyForecast, cautions
    6. **INSERT IGNORE 패턴**: `CompanyCompatibilityJdbcRepository.insertOrIgnore()` → UNIQUE(userProfileId, companyName, targetRoleCategory) 중복 방지
    7. 기존 레코드 존재 시 조회하여 반환 (AI 비용 절감). 신규 삽입 시 모든 자식 엔티티 저장:
       - 1:1: TargetRoleAnalysis, FiveElementsAnalysis, AnalysisBreakdown, ActionableStrategy
       - 1:N: ExpectedInterviewQuestion[], RoleCompatibility[], MonthlyForecast[], Caution[]
  - JobRoleAnalyzer: @Component, `analyze(FiveElements userFiveElements, JobCategoryEnum category)` → targetRoleAnalysis 생성. JobCategoryEnum의 primaryElement/secondaryElement와 사용자 오행 분포를 비교하여 matchScore, synergy, warning 산출
  - Note: 기업 설립일도 사용자 사주와 동일한 수준으로 지장간 포함 계산. 시간 미상 시 정오(12:00)로 기본 설정. 동일 조합(userProfileId+companyName+targetRoleCategory) 재요청 시 기존 DB 결과 반환 (AI 재호출 없음)
  - File: `SSAju/src/main/java/ssafy/SSAju/service/CompanyMatchingService.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/util/JobRoleAnalyzer.java`

- [ ] T086 [US3] Create `CompatibilityController` in `controller/`
  - Endpoint: `POST /api/company/compatibility` with CompatibilityRequest (userBirthDate + targetRole.category required, userBirthTime optional, companyFoundingDate optional, companyFoundingTime optional)
  - Handles: Request validation (@Valid), validates targetRole.category is valid JobCategoryEnum (400 Bad Request if invalid), looks up company (with time fallback to 12:00), calculates compatibility, returns `ApiResponse<CompatibilityResponse>`
  - Validation: targetRole.category 유효성 검증 (400 INVALID_JOB_CATEGORY), companyName @NotNull
  - File: `SSAju/src/main/java/ssafy/SSAju/controller/CompatibilityController.java`

- [ ] T087 [US3] Write unit & integration tests for Company Compatibility
  - Test cases (JobRoleAnalyzer):
    1. Valid TECH_BACKEND category + 金 강세 user FiveElements → matchScore 높음, synergy 텍스트 포함
    2. Valid MARKETING category + 木/火 강세 user FiveElements → matchScore 및 synergy/warning 반환
    3. 사용자 오행 분포가 직군 오행과 상극(相剋) 관계 → warning 메시지 포함
  - Test cases (CompanyMatchingService):
    1. Valid compatibility analysis (user birthDate + birthTime + targetRole, company founding date + time) → compatibilityScore + targetRoleAnalysis + roles including 지장간-based analysis. DB에 CompanyCompatibility + 모든 자식 엔티티 저장 확인
    2. Company founding time missing → auto-default to 12:00 and calculate 지장간
    3. Company founding date missing but time provided → Invalid request (date required)
    4. 동일한 (userProfileId, companyName, targetRoleCategory) 재요청 → INSERT IGNORE로 기존 레코드 재사용. 자식 엔티티 중복 삽입 없음 검증
    5. 동시 요청 Race Condition: 동일 조합 2건 동시 요청 → CompanyCompatibility 1개만 생성, 나머지 무시 (insertOrIgnore 반환값 0 검증)
  - Test cases (CompatibilityController):
    1. Valid request (userBirthDate + targetRole.category + companyName + companyFoundingDate) → 200 OK with score/targetRoleAnalysis/roles
    2. Valid request (no companyFoundingTime) → 200 OK with score (company time defaults to 12:00)
    3. Invalid targetRole.category → 400 Bad Request (INVALID_JOB_CATEGORY)
    4. Missing targetRole.category → 400 Bad Request
    5. Company not found (with company founding date fallback) → score still calculated with 지장간 included
  - File: `SSAju/src/test/java/ssafy/SSAju/service/CompanyMatchingServiceTest.java`
  - File: `SSAju/src/test/java/ssafy/SSAju/controller/CompatibilityControllerTest.java`
  - File: `SSAju/src/test/java/ssafy/SSAju/unit/JobRoleAnalyzerTest.java`
  - File: `SSAju/src/test/java/ssafy/SSAju/repository/CompanyCompatibilityJdbcRepositoryTest.java` (INSERT IGNORE + Race Condition 검증)

---

## Phase 4: Polish & Integration

- [ ] T088 Generate Swagger/OpenAPI documentation
  - Add: `springdoc-openapi-starter-webmvc-ui` dependency to `build.gradle`
  - Configure: `@OpenAPIDefinition`, `@Info`, `@Server` annotations in `SSAjuApplication.java`
  - Add: `@Operation`, `@RequestBody`, `@ApiResponse` annotations to all controllers
  - Configure: `springdoc.swagger-ui.path=/swagger-ui.html` in `application.yaml`
  - File: `SSAju/build.gradle` (dependency), `SSAju/src/main/java/ssafy/SSAju/SSAjuApplication.java` (annotations)
  - File: All controller classes updated with OpenAPI annotations
  - Verify: Accessible at `http://localhost:8080/swagger-ui.html` after `./gradlew bootRun`

- [ ] T089 Write integration test for full Career API flow (all 4 endpoints)
  - Test:
    1. Create UserProfile with birthDate + birthTime
    2. POST /api/career/timing with birthDate + birthTime → Get H1/H2
    3. POST /api/career/consultation with birthDate + birthTime + saju data → Get advice
    4. POST /api/feedback/satisfaction with results → Save feedback
    5. POST /api/company/compatibility with birthDate + birthTime + company → Get compatibility score
  - Verify: Data persistence (normalized entities TenGodData, HiddenStemData, CareerFortune, Industry, InterviewTip, Strength, RecommendedRole), response consistency, birthTime required fields validated, error handling across flows, **no JSON stored in tables**
  - File: `SSAju/src/test/java/ssafy/SSAju/integration/CareerApiIntegrationTest.java`

- [ ] T090 Final verification: Run full test suite and validate coverage
  - Command: `./gradlew clean test`
  - Verify: 100% of Phase 1-3 tests pass, no warnings, coverage >80%
  - Verify: **All entities use normalized structure (no JSON columns)**
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
And:   SajuResult entity persisted in DB with birthDate, birthTime, hiddenStems (Map), tenGodDistribution
And:   FastAPI integration works with complete birth date-time (YYYY-MM-DD HH:mm) and timeout handling
And:   HiddenStemCalculator + TenGodCalculator 결과가 SajuResult에 저장되고 관운 분석에 사용됨
And:   Missing birthTime → 400 Bad Request with clear error message
```

### US2: AI Consultation (Complete MVP - Expanded to 19 Fields)
```text
Given: Valid birthDate (YYYY-MM-DD), birthTime (HH:mm, required, 24-hour format)
When:  POST /api/career/consultation with {"birthDate":"YYYY-MM-DD", "birthTime":"HH:mm"}
Then:  Response includes 23 fields across 5 groups:
       - 기본 조언: industries (3-5), interviewTips, strengths
       - 관운 분석: favoredPeriod (H1/H2), confidenceScore (0-100), reasoning (정관 기운 설명 포함)
       - 사주 데이터: sajuProfile (dayMaster, dayMasterDescription, fiveElements, fiveElementsAnalysis, tenGodDistribution, keyTenGods)
       - OpenAI 분석: cautions, wealthStyle (4 필드), longTermRoadmap (2 단계 + 목표), personalBranding (5 필드), powerKeywords (키워드 배열 + 선택가이드), mentalCare (취약점 + 충전방법), environmentFit (6 필드), workStyle (4 필드), relationshipStrategy (5 필드), careerTimeline (연도 + 12개월 + 전환점)
And:   CareerConsultation entity persisted in DB linked to SajuResult (with birthTime, hiddenStems, tenGodDistribution)
And:   Spring AI / OpenAI JSON Mode receives complete saju data including 지장간 for more accurate advice across all 23 fields
And:   OpenAI 프롬프트에 현재 연도, 12개월 타임라인, 십신(十神) + 지장간(地藏干) 분석 결과, 모든 23개 필드 요청 포함
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
Then:  Response includes compatibilityScore (0-100), recommendedRoles based on user + company 지장간 analysis
And:   Company founding time defaults to 12:00 if not provided, with 지장간 included in calculation
And:   Both user and company saju calculated with TenGod + HiddenStem for accurate compatibility scoring
And:   Fallback to manual company founding date if API lookup fails (time still defaults to 12:00)
And:   기업 설립일도 사용자 사주와 동일한 수준으로 지장간 포함하여 신뢰도 향상
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
| 지장간 계산 오류 | HiddenStemCalculator와 TenGodCalculator는 독립적으로 단위 테스트 + 통합 테스트 필수. 정확한 지장간 매핑 테이블 정의 및 검증 |
| 기업 설립시간 미상 처리 | 12:00 기본값 설정은 CompatibilityRequest에서 자동 처리. 시간대 오류 위험 최소화 |

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

| Phase | Task Count | Task ID Range | Focus | Parallel? |
|-------|-----------|-------|-------|-----------|
| Phase 1 (Setup) | 5 | T001~T005 | Project structure, config, exception handling | Yes (all) |
| Phase 2 (Foundational) | 9 | T006~T013, T013-2 | WebClient, ChatClient, base entities, repos, Enum definitions, TenGod + HiddenStem calculators | Yes (most) |
| Phase 3.1 (US1) | 8 | T014~T021 | Career timing feature with 지장간 calculation | Independent |
| Phase 3.2 (US2) | 9 | T022~T030 | Consultation feature with TenGod + HiddenStem analysis | Parallel with US4 |
| Phase 3-Refactor | 11 | T031~T041 | **Entity Normalization**: 6 new entities (TenGodData, HiddenStemData, CareerFortune, Industry, InterviewTip, Strength) + 6 repositories + 2 service updates + 2 test updates. Replaces JSON storage with normalized entities. | Yes (parallel entity creation) |
| Phase 3-Enhancement | 27 | T042~T050, T051~T068 | **(1) US4 Feedback (T042~T050)**: UserSatisfactionFeedback entity + repo + DTOs + service. **(2) Communication & Concurrency (T051~T068)**: RestClient, INSERT IGNORE, JPA Auditing, value objects, validators | Yes (T042~T050 and T051~T068 independent) |
| Phase 3-Refactor-3 | 12 | T069~T080 | **Advanced Normalization & Service Optimization**: (1) fullSajuData 완전 정규화 (SajuFullData 엔티티), (2) PromptProvider 분리, (3) 남은 모든 상수 추출 | Yes (parallel) |
| Phase 3.3 (US3) | 7 | T081~T087 | Company compatibility (P2) with RecommendedRole entity, 지장간 and 12:00 default | After core ready |
| Phase 4 (Polish) | 3 | T088~T090 | API documentation (Swagger), integration tests, final validation | After all stories |
| **TOTAL** | **91** | T001~T090 (+ T013-2) | Full MVP + Entity Normalization + Constants Extraction + PromptProvider + fullSajuData Normalization + 지장간 calculation + P2 foundation + Communication optimization + API docs | Strategic parallelism |

---

## REFERENCE: Constants Extraction & Code Refactoring Guide

> **Note**: This section provides implementation guidance for constant extraction and code refactoring. Constants are included in Phase 3-Refactor-3 (T069~T080) and Phase 3-Enhancement (T051~T068). No new Task IDs are defined below.

**Goal**: After completing Phase 3-Refactor, Phase 3-Enhancement, and Phase 3-Refactor-3, extract all hardcoded magic numbers and strings into constants/enums
**Scope**: 9 constant groups (15 total constant classes/enums)
**Strategy**: Organize constants by purpose (`career/enums/`, `career/constants/`), prioritize enums

### Constant Extraction Guide

**1. Career Fortune Analysis Constants** (CareerFortuneAnalyzer, HiddenStemCalculator)

**Implementation during T069~T080 (Phase 3-Refactor-3)**:

- Create `TenGodConstants` enum in `career/enums/`
  - 10개 십신 상수 정의: CHIEF_OFFICER (정관), SIDE_OFFICER (편관), CHIEF_WEALTH (정재), SIDE_WEALTH (편재), FOOD_GOD (식신), INJURING_OFFICER (상관), COMPARING_FRIEND (비견), ROBBING_WEALTH (겁재), CHIEF_SEAL (정인), SIDE_SEAL (편인)
  - 각 십신별 필드: name (한글명), symbol (기호), scoreModifier (가점/감점: 20, -15, -5, 0), isOfficer (관성 여부)
  - Helper method: fromName(String) - 십신 이름으로 상수 조회
  - 관운 분석용 점수 수정자: 정관/편관 +20, 식신/상관 -15, 비견/겁재 -5, 기타 0
  - File: `SSAju/src/main/java/ssafy/SSAju/career/enums/TenGodConstants.java`

- Create `HiddenStemConstants` enum in `career/enums/`
  - 지지별(子, 丑, 寅, ..., 亥) 지장간 정의: Map<String, List<String>>
  - 지장간 보정 점수: 정관·편관(+5), 식신·상관(-3)

- Create `CareerFortuneConstants` in `career/constants/`
  - 관운 신뢰도 임계값: CONFIDENCE_THRESHOLD_HIGH (75), MEDIUM (50), LOW (25) 등
  - H1/H2 판정 상수: FIRST_HALF ("H1"), SECOND_HALF ("H2")
  - 관성 점수 범위: MAX_CONFIDENCE (100), MIN_CONFIDENCE (0)

**2. API 통신 상수** (SajuDataService, ConsultationService, CompanyInfoService)

- Create `ApiTimeoutConstants` in `career/constants/`
  - FastAPI 타임아웃: FASTAPI_TIMEOUT_SECONDS (3), MAX_RETRIES (2)
  - OpenAI 타임아웃: OPENAI_TIMEOUT_SECONDS (8), MAX_RETRIES (1)
  - 공공데이터API 타임아웃: PUBLIC_DATA_TIMEOUT_SECONDS (5), MAX_RETRIES (1)

- Create `ApiEndpointConstants` in `career/constants/`
  - FastAPI 엔드포인트: SAJU_CALCULATE_ENDPOINT ("/api/saju/calculate")
  - OpenAI 모델: OPENAI_MODEL ("gpt-4o-mini")
  - 응답 형식: JSON_RESPONSE_FORMAT ("JSON_OBJECT")

**3. 데이터 검증 상수** (validators in Service/Controller)

- Create `ValidationConstants` in `career/constants/`
  - 천간/지지 개수: REQUIRED_HEAVENLY_STEMS (4), REQUIRED_EARTHLY_BRANCHES (4)
  - 생년월일 범위: EARLIEST_BIRTH_DATE ("1900-01-01")
  - 신뢰도 범위: MIN_SCORE (0), MAX_SCORE (100)

**4. 응답 메시지 상수** (GlobalExceptionHandler, Service)

- Create `ErrorMessageConstants` enum in `career/enums/`
  - 각 예외 타입별 메시지: INVALID_DATE_FORMAT, FASTAPI_TIMEOUT, OPENAI_API_TIMEOUT, COMPANY_NOT_FOUND 등
  - Error code 정의: "INVALID_SAJU_DATA", "EXTERNAL_API_TIMEOUT" 등

- Create `SuccessMessageConstants` enum in `career/enums/`
  - API 성공 메시지: "관운 분석 완료", "AI 커리어 컨설팅 완료" 등

**5. 기업 궁합 분석 상수** (CompatibilityScoreCalculator)

- Create `CompatibilityConstants` in `career/constants/`
  - 호환성 점수 범위: MIN_COMPATIBILITY (0), MAX_COMPATIBILITY (100)
  - 신뢰도 수준: CONFIDENCE_HIGH ("HIGH"), MEDIUM ("MEDIUM"), LOW ("LOW")
  - 기본 설립 시간: DEFAULT_FOUNDING_TIME ("12:00")

**6. 피드백 상수** (FeedbackService, UserSatisfactionFeedback)

- Create `FeedbackConstants` enum in `career/enums/` (optional: reference existing enums)
  - 피드백 타입: CAREER_TIMING, CONSULTATION, COMPATIBILITY (이미 FeedbackType.java에 존재)
  - 만족도 상태: SATISFIED, DISSATISFIED (이미 SatisfactionStatus.java에 존재)

**7. 프롬프트/설정 상수** (ConsultationService, PromptProvider)

- Create `PromptTemplateConstants` in `career/constants/`
  - OpenAI 프롬프트 템플릿 부분 (현재 연도, 타임라인 개월, 23개 필드 등)
  - JSON 스키마 필드명 정의 (일관성 유지)

**8. HTTP 응답 상수** (ApiResponse 형식)

- Create `ApiResponseConstants` in `career/constants/`
  - HTTP 상태 코드: OK (200), BAD_REQUEST (400), NOT_FOUND (404), SERVICE_UNAVAILABLE (503) 등
  - 응답 헤더: CONTENT_TYPE ("application/json")

**9. 데이터베이스 관련 상수** (JPA entity 및 Repository)

- Create `EntityConstants` in `career/constants/`
  - 테이블 이름 제약: UNIQUE_CONSTRAINT_NAMES, INDEX_NAMES 등
  - 기본값: DEFAULT_PAGE_SIZE (10), DEFAULT_OFFSET (0)
  - 날짜 형식: DATE_PATTERN ("yyyy-MM-dd"), TIME_PATTERN ("HH:mm")

### Code Refactoring Guidance

**During T069~T080 (Phase 3-Refactor-3):**
- Refactor all services and utilities to use extracted constants
  - **TenGodConstants 적용**: TenGodCalculator, CareerFortuneAnalyzer, CompatibilityScoreCalculator 등에서 십신 문자열 상수화
  - **Error & Success 메시지 적용**: SajuDataService, ConsultationService, CompanyMatchingService, GlobalExceptionHandler, Controllers 등에서 메시지 상수 사용
  - Verify constant extraction: `./gradlew test` 통과 확인
  - Verify: Code style follows code-style-guide.md (constants in enums/, all uses via constant names)

---

**Generated by `/speckit-tasks` on 2026-04-10**

**Version History & Task Count Correction:**
- 2026-04-27: Initial Phase 1~3.2 + Refactor structure (45 core tasks)
- 2026-05-02: Added Phase 3-Refactor (Entity Normalization, T031~T041, 11 tasks)
- 2026-05-03: Added US4 Feedback (T042~T050, 9 tasks). Total: 65
- 2026-05-04: Added Phase 3-Refactor-3 foundation planning (T069~T080, 12 tasks reserved)
- 2026-05-06: Added Phase 3-Enhancement (REST Communication, Concurrency, T051~T068, 18 tasks). Finalized task numbering: T001~T090 + T013-2
  - **Phase 1~2**: 14 tasks (T001~T005, T006~T013, T013-2)
  - **Phase 3.1~3.2**: 17 tasks (T014~T030)
  - **Phase 3-Refactor**: 11 tasks (T031~T041)
  - **Phase 3-Enhancement**: 27 tasks (T042~T050 US4 + T051~T068 REST/Concurrency)
  - **Phase 3-Refactor-3**: 12 tasks (T069~T080)
  - **Phase 3.3**: 7 tasks (T081~T087 US3)
  - **Phase 4**: 3 tasks (T088~T090)
  - **TOTAL**: 91 tasks (T001~T090 + T013-2)

**Status**: Task numbering finalized and corrected (2026-05-06). All Phase/Story/Task ID ranges now consistent and non-overlapping. Ready for implementation starting from Phase 1.