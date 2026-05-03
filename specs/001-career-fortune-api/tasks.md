# Task List: Career Fortune & Consultation API

**Feature**: Career Fortune & Consultation API
**Date Generated**: 2026-04-27
**Status**: Ready for Implementation (Updated with Hidden Stem Calculation)
**Total Tasks**: 50 (HiddenStemCalculator added to foundational phase)
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
  - Implement UserProfile: birthDate (LocalDate, @NotNull), birthTime (LocalTime, @NotNull, HH:mm format), timestamps (createdAt, updatedAt). **Add UNIQUE(birthDate, birthTime) constraint**
  - Implement SajuResult: fullSajuData (JSON from FastAPI), hiddenStems (Map<String, List<String>>, 지지별 지장간 저장), tenGodDistribution (JSON), careerFortune (JSON), timestamps. Link to UserProfile (1:1)
  - Use: @Getter, @NoArgsConstructor(access=PROTECTED), @Builder, FetchType.LAZY for relationships
  - **JSON 컬럼 처리 (Spring Boot 4.x Hibernate 7.2.7)**: @JdbcTypeCode 대신 @Convert + custom AttributeConverter 사용 (Jackson 3.x 호환성)
    - ObjectMapConverter: Map<String, Object> 직렬화/역직렬화
    - StringListMapConverter: Map<String, List<String>> 직렬화/역직렬화
    - IntegerMapConverter: Map<String, Integer> 직렬화/역직렬화
  - Note: hiddenStems 구조 예시: `{"子": ["癸"], "丑": ["癸", "辛", "己"], ...}`
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
  - Handles: TimeoutException → FastAPITimeoutException, invalid response (heavenlyStems/earthlyBranches < 4 items) → InvalidSajuDataException, missing birthTime → InvalidSajuDataException
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
  - **Massively Expanded** (Session 2026-04-30): 19개 필드 그룹 (16개 필드 그룹 + 3개 메타필드)
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
    - **OpenAI 분석 결과** (12 필드):
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
      6. OpenAI 호출 (ChatClient JSON Mode with 16+ field groups in schema)
      7. CareerConsultation 저장
      8. ConsultationResponse 반환 (19개 필드 모두 포함: 기본 조언 3 + 관운 분석 3 + 사주 프로필 1 + OpenAI 분석 12 필드)
  - **Transaction Management**: @Transactional 제거. FastAPI/OpenAI I/O는 트랜잭션 밖. 각 DB 작업은 개별 트랜잭션.
  - **OpenAI Prompt Enhancement**: buildPrompt() 메서드에 현재 연도(LocalDate.now().getYear()), 12개월 타임라인 요청, 16개 필드 그룹 명시 포함
  - **Helper Methods**:
    - `findOrCreateUserProfile()`: UNIQUE(birthDate, birthTime) 제약 활용, DIVE 처리
    - `findOrCreateSajuResult()`: 기존 SajuResult 재사용, 신규 생성 시 hiddenStems + tenGodDistribution 저장
    - `callOpenAI()`: ChatClient + JSON Mode로 CareerAdviceResponse 매핑 (16+ field groups)
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
  - **Expanded Response** (Session 2026-04-30): ApiResponse<ConsultationResponse> (19개 필드)
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
    1. Happy path: valid birthDate + birthTime → consultation with 19 fields (모든 필드 그룹 포함: industries/tips/strengths/version/favoredPeriod/confidenceScore/reasoning/sajuProfile/cautions/wealthStyle/longTermRoadmap/personalBranding/powerKeywords/mentalCare/environmentFit/workStyle/relationshipStrategy/careerTimeline)
    2. SajuResult 기존 존재 → 재사용하고 저장
    3. SajuResult 신규 생성 → 저장 (hiddenStems, tenGodDistribution 포함)
    4. OpenAI API 호출 실패 → OpenAIApiException
    5. OpenAI 응답 null → OpenAIApiException
    6. 모든 nested record 타입 검증 (CareerAdviceResponse.IndustryRecommendation, WealthStyle, LongTermRoadmap, 등)
  - Test Fixture: MOCK_SAJU (FastAPIResponse), MOCK_ADVICE (CareerAdviceResponse with 16+ field groups)
  - File: `SSAju/src/test/java/ssafy/SSAju/service/ConsultationServiceTest.java`

- [v] T029 [US2] Write unit tests for `ConsultationController` in `src/test/`
  - **Simplified Request Body** (Session 2026-04-30):
    ```json
    {
      "birthDate": "1990-10-10",
      "birthTime": "14:30"
    }
    ```
  - **Expanded Response** (Session 2026-04-30): 19개 필드 모두 포함 (기본 조언 3 + 관운 3 + 사주 프로필 1 + OpenAI 분석 12 필드)
  - Setup: MockMvcBuilders.standaloneSetup(controller) + SajuGlobalExceptionHandler
  - Test cases:
    1. Valid request (birthDate + birthTime) → 200 OK with 19-field response including all nested structures
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

- [ ] T042 [US4] Create `UserSatisfactionFeedback` entity in `career/entity/`
  - Fields: id, sajuResultId (FK), feedbackType (ENUM: CAREER_TIMING/CONSULTATION/COMPATIBILITY), satisfactionStatus (ENUM: SATISFIED/DISSATISFIED), createdAt
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/UserSatisfactionFeedback.java`

- [ ] T043 [US4] [P] Create `UserSatisfactionFeedbackRepository` in `repository/`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/UserSatisfactionFeedbackRepository.java`

- [ ] T044 [US4] [P] Create `SatisfactionFeedbackRequest` DTO in `dto/request/`
  - Fields: sajuResultId, feedbackType (ENUM), satisfactionStatus (ENUM)
  - Validation: @NotNull on all fields
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/request/SatisfactionFeedbackRequest.java`

- [ ] T045 [US4] [P] Create `SatisfactionFeedbackResponse` DTO in `dto/response/`
  - Fields: feedbackId, createdAt
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/SatisfactionFeedbackResponse.java`

- [ ] T046 [US4] Create `FeedbackService` in `service/`
  - Method: `saveFeedback(SatisfactionFeedbackRequest)` → validates SajuResult exists, stores feedback
  - Handles: SajuResult not found → throw custom exception, enum validation
  - File: `SSAju/src/main/java/ssafy/SSAju/service/FeedbackService.java`

- [ ] T047 [US4] Create `FeedbackController` in `controller/`
  - Endpoint: `POST /api/feedback/satisfaction`
  - Handles: Request validation, calls `FeedbackService`, returns `ApiResponse<SatisfactionFeedbackResponse>`
  - File: `SSAju/src/main/java/ssafy/SSAju/controller/FeedbackController.java`

- [ ] T048 [US4] Write unit tests for `FeedbackService` in `src/test/`
  - Test: Valid feedback saved, SajuResult not found → 404, invalid enum → 400, null handling
  - File: `SSAju/src/test/java/ssafy/SSAju/service/FeedbackServiceTest.java`

- [ ] T049 [US4] Write unit tests for `FeedbackController` in `src/test/`
  - Test: Valid feedback → 200 OK, invalid type → 400, missing SajuResult → 404
  - File: `SSAju/src/test/java/ssafy/SSAju/controller/FeedbackControllerTest.java`

- [ ] T050 [US4] Run all tests for US4 features
  - Command: `./gradlew test --tests "ssafy.SSAju.service.FeedbackServiceTest OR ssafy.SSAju.controller.FeedbackControllerTest"`
  - Verify: BUILD SUCCESSFUL before committing

---

### User Story 3: Company & Job Fit Analysis (Priority P2)

**Goal**: Users can analyze compatibility between their saju and target company founding date, receiving a score (0-100) and recommended roles.
**Independent Test**: User saju + company date → Compatibility calculation → Score + roles response (depends on core structures)
**Expected Outcome**: Compatibility endpoint working, CompanyCompatibility + RecommendedRole entities stored in DB

- [ ] T051 [US3] Create `CompanyCompatibility` and `RecommendedRole` entities in `career/entity/`
  - **CompanyCompatibility**: id, userProfileId (FK), companyName, compatibilityScore (0-100), createdAt. **No JSON 저장** — recommendedRoles는 별도 RecommendedRole 엔티티에 1:N 관계로 저장
  - **RecommendedRole**: id, companyCompatibilityId (FK), roleName (String), createdAt. **N:1 관계** to CompanyCompatibility
  - Use: @Getter, @NoArgsConstructor(access=PROTECTED), @Builder, FetchType.LAZY for relationships
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/CompanyCompatibility.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/career/entity/RecommendedRole.java`

- [ ] T052 [US3] [P] Create `CompanyCompatibilityRepository` and `RecommendedRoleRepository` in `repository/`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/CompanyCompatibilityRepository.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/repository/RecommendedRoleRepository.java`

- [ ] T053 [US3] [P] Create `CompatibilityRequest` and `CompatibilityResponse` DTOs
  - Request fields: birthDate (LocalDate, @NotNull), birthTime (LocalTime, @NotNull), companyName (@NotNull), companyFoundingDate (LocalDate, optional), companyFoundingTime (LocalTime, optional, **defaults to 12:00 if missing**)
  - Response fields: compatibilityScore (0-100), confidenceLevel (LOW/MEDIUM/HIGH), recommendedRoles (List<String> - 클라이언트에 문자열 리스트로 반환), reasoning
  - Note: DB 내부에는 RecommendedRole 엔티티로 저장, 응답 시 roleName 리스트로 변환. companyFoundingTime 미상 시 자동으로 12:00으로 설정하고 지장간 포함 계산
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/request/CompatibilityRequest.java`
  - File: `SSAju/src/main/java/ssafy/SSAju/dto/response/CompatibilityResponse.java`

- [ ] T054 [US3] Create `CompanyInfoService` in `service/`
  - Method: `lookupCompanyFoundingDate(companyName)` → calls public data API with fallback to manual input. If time not found, use default 12:00
  - Handles: API timeout → PublicDataApiException, company not found → inform user to provide founding date. If time missing → auto-set to 12:00
  - File: `SSAju/src/main/java/ssafy/SSAju/service/CompanyInfoService.java`

- [ ] T055 [US3] Create `CompanyMatchingService` in `service/`
  - Method: `analyzeCompatibility(LocalDate userBirthDate, LocalTime userBirthTime, LocalDate companyFoundingDate, LocalTime companyFoundingTime)` → compatibility score + role recommendations with 지장간 calculation
  - Logic: Fetch user saju via SajuDataService with birthDate + birthTime → Calculate user HiddenStems + TenGod. Fetch company saju (with time defaulting to 12:00 if missing) → Calculate company HiddenStems + TenGod. Use `CompatibilityScoreCalculator` with both sets of data for accurate scoring. Save to CompanyCompatibility + RecommendedRole entities
  - Note: 기업 설립일도 사용자 사주와 동일한 수준으로 지장간 포함 계산. 시간 미상 시 정오(12:00)로 기본 설정. 추천 직무는 RecommendedRole 엔티티로 저장
  - File: `SSAju/src/main/java/ssafy/SSAju/service/CompanyMatchingService.java`

- [ ] T056 [US3] Create `CompatibilityController` in `controller/`
  - Endpoint: `POST /api/company/compatibility` with CompatibilityRequest (userBirthDate + userBirthTime required, companyFoundingDate optional, companyFoundingTime optional)
  - Handles: Request validation (@Valid), validates user birth time required, looks up company (with time fallback to 12:00), calculates compatibility, returns `ApiResponse<CompatibilityResponse>` (recommendedRoles는 엔티티에서 추출한 List<String>)
  - Validation: Reject requests with missing userBirthTime (400 Bad Request)
  - File: `SSAju/src/main/java/ssafy/SSAju/controller/CompatibilityController.java`

- [ ] T057 [US3] Write unit & integration tests for Company Compatibility
  - Test cases (CompanyMatchingService):
    1. Valid compatibility analysis (user birthDate + birthTime, company founding date + time) → compatibility score + roles including 지장간-based analysis
    2. Company founding time missing → auto-default to 12:00 and calculate 지장간
    3. Company founding date missing but time provided → Invalid request (date required)
    4. Missing user birthTime → InvalidSajuDataException
    5. Invalid user birthTime format → InvalidSajuDataException
  - Test cases (CompatibilityController):
    1. Valid request (user birthDate + birthTime + company founding date) → 200 OK with score/roles
    2. Valid request (user birthDate + birthTime + company, no company time) → 200 OK with score (company time defaults to 12:00)
    3. Missing user birthTime → 400 Bad Request
    4. Company not found (with company founding date fallback) → score still calculated with 지장간 included
    5. Invalid user time format → 400 Bad Request
  - File: `SSAju/src/test/java/ssafy/SSAju/service/CompanyMatchingServiceTest.java`
  - File: `SSAju/src/test/java/ssafy/SSAju/controller/CompatibilityControllerTest.java`

---

## Phase 4: Polish & Integration

- [ ] T058 Generate Swagger/OpenAPI documentation
  - Add: `springdoc-openapi-starter-webmvc-ui` dependency to `build.gradle`
  - Configure: `@OpenAPIDefinition`, `@Info`, `@Server` annotations in `SSAjuApplication.java`
  - Add: `@Operation`, `@RequestBody`, `@ApiResponse` annotations to all controllers
  - Configure: `springdoc.swagger-ui.path=/swagger-ui.html` in `application.yaml`
  - File: `SSAju/build.gradle` (dependency), `SSAju/src/main/java/ssafy/SSAju/SSAjuApplication.java` (annotations)
  - File: All controller classes updated with OpenAPI annotations
  - Verify: Accessible at `http://localhost:8080/swagger-ui.html` after `./gradlew bootRun`

- [ ] T059 Write integration test for full Career API flow (all 4 endpoints)
  - Test:
    1. Create UserProfile with birthDate + birthTime
    2. POST /api/career/timing with birthDate + birthTime → Get H1/H2
    3. POST /api/career/consultation with birthDate + birthTime + saju data → Get advice
    4. POST /api/feedback/satisfaction with results → Save feedback
    5. POST /api/company/compatibility with birthDate + birthTime + company → Get compatibility score
  - Verify: Data persistence (normalized entities TenGodData, HiddenStemData, CareerFortune, Industry, InterviewTip, Strength, RecommendedRole), response consistency, birthTime required fields validated, error handling across flows, **no JSON stored in tables**
  - File: `SSAju/src/test/java/ssafy/SSAju/integration/CareerApiIntegrationTest.java`

- [ ] T060 Final verification: Run full test suite and validate coverage
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
Then:  Response includes 19 fields across 16 field groups:
       - 기본 조언: industries (3-5), interviewTips, strengths
       - 관운 분석: favoredPeriod (H1/H2), confidenceScore (0-100), reasoning (정관 기운 설명 포함)
       - 사주 데이터: sajuProfile (dayMaster, dayMasterDescription, fiveElements, fiveElementsAnalysis, tenGodDistribution, keyTenGods)
       - OpenAI 분석: cautions, wealthStyle (4 필드), longTermRoadmap (2 단계 + 목표), personalBranding (5 필드), powerKeywords (키워드 배열 + 선택가이드), mentalCare (취약점 + 충전방법), environmentFit (6 필드), workStyle (4 필드), relationshipStrategy (5 필드), careerTimeline (연도 + 12개월 + 전환점)
And:   CareerConsultation entity persisted in DB linked to SajuResult (with birthTime, hiddenStems, tenGodDistribution)
And:   Spring AI / OpenAI JSON Mode receives complete saju data including 지장간 for more accurate advice across all 16 field groups
And:   OpenAI 프롬프트에 현재 연도, 12개월 타임라인, 십신(十神) + 지장간(地藏干) 분석 결과, 모든 16개 필드 그룹 요청 포함
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

| Phase | Task Count | Focus | Parallel? |
|-------|-----------|-------|-----------|
| Phase 1 (Setup) | 5 | Project structure, config, exception handling | Yes (all) |
| Phase 2 (Foundational) | 10 | WebClient, ChatClient, base entities, repos, Enum definitions, TenGod + HiddenStem calculators | Yes (most) |
| Phase 3.1 (US1) | 8 | Career timing feature with 지장간 calculation | Independent |
| Phase 3.2 (US2) | 9 | Consultation feature with TenGod + HiddenStem analysis | Parallel with US4 |
| Phase 3-Refactor | 11 | **Entity Normalization**: 6 new entities (TenGodData, HiddenStemData, CareerFortune, Industry, InterviewTip, Strength) + 6 repositories + 2 service updates + 2 test updates. Replaces JSON storage with normalized entities. | Yes (parallel entity creation) |
| Phase 3.4 (US4) | 9 | Feedback feature | Parallel with US2 |
| Phase 3.3 (US3) | 7 | Company compatibility (P2) with RecommendedRole entity, 지장간 and 12:00 default | After core ready |
| Phase 4 (Polish) | 3 | API documentation (Swagger), integration tests, final validation | After all stories |
| **TOTAL** | **61** | Full MVP + Entity Normalization + 지장간 calculation + P2 foundation + API docs | Strategic parallelism |

---

**Generated by `/speckit-tasks` on 2026-04-10**
**Updated**: 2026-04-27 (Added HiddenStemCalculator, 지장간 calculation logic, company founding time 12:00 default)
**Updated**: 2026-05-02 (Added Phase 3-Refactor: Entity Normalization. Replaced JSON storage with 7 normalized entities. Total tasks: 61. T031~T041 for refactoring, T042~T057 for US4/US3, T058~T060 for Phase 4)
**Status**: Ready for implementation team with entity normalization workflow