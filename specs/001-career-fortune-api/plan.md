# Implementation Plan: Career Fortune & Consultation API

**Branch**: `main` (direct planning, no feature branch) | **Date**: 2026-04-10 | **Spec**: [specs/001-career-fortune-api/spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-career-fortune-api/spec.md` with 13 clarifications resolved (including birth_time requirement, 지장간 calculation role separation)

## Summary

SSAju 백엔드는 사주 명리학 데이터(만세력, 십신, 지장간, 관운)를 활용해 취업 준비생에게 4가지 맞춤 서비스를 제공합니다:

1. **관운 기반 채용 시기 분석** (P1): FastAPI로 만세력 조회 → Spring에서 십신/지장간 계산 → 관운 분석 → H1/H2 판정
2. **AI 커리어 컨설팅** (P1): Spring에서 십신+지장간 기반 오행 분포 계산 → Spring AI + OpenAI JSON Mode로 산업/면접팁/강점 제공
3. **기업/직무 궁합** (P2): 공공데이터API로 기업 설립일 조회 → 지장간 포함 사주 계산 → 궁합 계산 (시간 미상 시 12:00 기본값)
4. **사용자 만족도 피드백** (P1): 사주 분석 완료 후 만족도(만족함/만족하지 않음) 수집 → Phase 2 대시보드에서 시각화

**기술 접근**: REST API (Spring Boot 4.0.5) + MySQL (JPA) + 외부 API 통합 (FastAPI, OpenAI, 공공데이터). 
- **FastAPI 역할**: 천간/지지/오행만 제공 (기본 사주 데이터)
- **Spring 역할**: TenGodCalculator + HiddenStemCalculator로 십신 및 지장간 모두 계산 → 더 정확한 오행 분포 파악
- Spring AI로 OpenAI 호출을 타입 안전하게 처리.

---

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.0.5
**Primary Dependencies**: Spring Data JPA, Spring Web, Spring AI (ChatClient), Lombok, Spring Validation
**Storage**: MySQL 8.0+ (정규화된 엔티티 구조, JPA via application.yaml)
**Testing**: JUnit 5 + AssertJ (Given-When-Then 패턴)
**Target Platform**: Backend REST API (HTTP JSON)
**Project Type**: Web Service / Microservice (Spring Boot)
**Performance Goals**:
- 관운 분석: 5초 이내 (Controller → Service → DB, FastAPI 제외)
- AI 컨설팅: 15초 이내 (OpenAI 지연 + 재시도 포함)
- 기업 궁합: 8초 이내
- 동시 처리: 5,000명 사용자 (Connection Pool 기본값)

**아키텍처 원칙 (SRP 준수)**:
- **Service**: Orchestration only (흐름 제어, 외부 API 호출)
- **Analyzer**: 분석 로직 전담 (TenGodAnalyzer, HiddenStemAnalyzer, CareerFortuneAnalyzer)
- **Calculator**: 계산 로직 전담 (TenGodCalculator, HiddenStemCalculator, CompatibilityScoreCalculator)
- **Mapper**: DTO ↔ Entity 변환 (CareerConsultationMapper, SajuResultMapper 등)
- **Provider**: 재사용 가능한 데이터 조회/생성 + 설정/프롬프트 관리 (동시성 보정, 경량 데이터 접근 포함). 예: UserProfileProvider, SajuResultProvider, PromptProvider, ConfigProvider
- **Exception**: @RestControllerAdvice + 커스텀 예외 우선. 경계 어댑터(ConsultationOpenAICaller 등)가 외부 오류를 도메인 예외로 변환하는 제한적 try-catch는 허용. 비즈니스 예외를 무조건 삼키는 것 금지

**Key Technical Decisions** (Session 2026-04-30 + Service Layer Optimization + Phase 3-Enhancement + Phase 3-Refactor-3):

**핵심 기능 설계**:
- **1-Call API Design**: `/api/career/consultation` 엔드포인트가 내부적으로 모든 외부 API 호출 오케스트레이션 (FastAPI, OpenAI). 클라이언트는 birthDate + birthTime만 제공하고, 모든 계산(십신, 지장간, 관운 분석) 및 23개 필드의 완전한 AI 조언을 한 번의 요청으로 수신.
- **Expanded Response (23개 필드)**: ConsultationResponse는 23개 필드 포함: 기본 조언 3(industries, interviewTips, strengths) + 관운 분석 3(favoredPeriod, confidenceScore, reasoning) + 사주 프로필 내부 6(sajuProfile.dayMaster, dayMasterDescription, fiveElements, fiveElementsAnalysis, tenGodDistribution, keyTenGods) + OpenAI 분석 10(cautions, wealthStyle, longTermRoadmap, personalBranding, powerKeywords, mentalCare, environmentFit, workStyle, relationshipStrategy, careerTimeline) + 메타데이터 1(openaiModelVersion). OpenAI 프롬프트에 현재 연도, 12개월 타임라인, 모든 필드 그룹 포함.
- **Spring AI ChatClient**: OpenAI JSON Mode로 `CareerAdviceResponse` record에 자동 매핑. 23개 필드 모두 포함. 타입 안전성 + 에러 처리 자동화.

**성능 및 동시성 최적화**:
- **Transaction Separation**: ConsultationService에서 @Transactional 제거. FastAPI/OpenAI I/O는 트랜잭션 밖에서 수행. 각 DB 작업은 Repository의 @Transactional에 의해 개별 트랜잭션으로 실행. Network 지연이 Connection Pool을 점유하지 않음.
  - 목표: 5000명 동시 사용자 처리 (기본 Connection Pool로)
  - 결과: Connection Pool 고갈 방지, 응답 시간 15초 이내 달성 (OpenAI 8초 타임아웃 포함)
  - ⚠️ 원자성 조건: 여러 Repository에 걸친 쓰기가 모두 성공/실패해야 하는 경우에는 Service 계층 @Transactional 유지 필요 (단건 또는 독립적 DB 작업만 Repository @Transactional로 충분)

- **RestClient + Spring Retry** (Phase 1): WebClient의 무거운 Reactive 의존성 제거. 동기식 호출에 적합한 경량 RestClient 도입. Spring Retry로 지수 백오프 재시도 (1초, 2초, 4초).
  - 적용 대상: FastAPI (3초 타임아웃, 2회 재시도), 공공데이터API (5초 타임아웃, 1회 재시도)
  - 이점: Reactive 오버헤드 제거, 코드 간결성, Spring Retry와 자연스러운 결합

- **Race Condition 처리** (Phase 3-Enhancement): JdbcTemplate INSERT IGNORE 활용으로 안전한 동시 insert
  - 문제: 동일한 생년월일시를 가진 사용자 2명 이상 동시 요청 시 SajuResult 중복 insert 위험
  - 해결: UNIQUE 제약 조건 + INSERT IGNORE native query로 원자적 처리
  - 코드 위치: `SajuResultJdbcRepository.insertOrIgnore()`
  - 테스트: H2 MySQL 모드 (`jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE`)에서 검증

**데이터 모델링**:
- **JPA Auditing (@CreatedDate/@LastModifiedDate)**: 수동 @PreUpdate 제거. Spring Data JPA 자동 타임스탐프 관리로 일관성 보장.
- **Entity equals&hashCode ID 기준 구현**: Lombok @EqualsAndHashCode 금지. 지연 로딩(Lazy Loading) 중 Proxy 객체 비교 시 정확성 보장.
- **Value Objects (TenGodDistribution, HiddenStems, FiveElements)**: Map<String, Integer> 같은 원시 컬렉션 대신 일급 컬렉션으로 래핑. 데이터 의미 명확화, 비즈니스 로직 응집.
- **완전 정규화** (Phase 3-Refactor-3 예정): SajuResult.fullSajuData (LONGTEXT JSON) → SajuFullData (1:1 엔티티) 마이그레이션. **현재 Phase 1은 JSON 형식 유지**, Phase 3-Refactor-3에서 정규화 완료 예정.

**Service 계층 최적화**:
- **PromptProvider 분리**: ConsultationService의 buildPrompt 메서드를 별도 PromptProvider 컴포넌트로 외부화. 프롬프트 수정이 서비스 로직에 영향을 주지 않도록 캡슐화.
- **Analyzer 분리**: CareerFortuneAnalyzer, TenGodCalculator, HiddenStemCalculator는 분석만 담당. Service는 이들을 조합(Composition)하여 orchestration.
- **Mapper 분리**: CareerConsultationMapper, SajuMapper, SajuResultMapper 등이 DTO ↔ Entity 변환 담당. SajuResultMapper는 SajuFullData 변환 추가.
- **Domain Model 캡슐화**: 엔티티는 비즈니스 메서드(validate*, is*, build*)를 제공. Service는 getter로 필드 꺼내 로직을 짜지 말 것.
- **Validator 분리**: SajuValidator, RequestValidator, CompatibilityValidator 등으로 검증 로직 전문화. Service 책임 분리로 재사용성/테스트성 향상.

**Constraints**:
- Phase 1: Redis/Global 캐싱 금지 (도메인 로직 정확성 우선)
- 모든 엔티티: @Data/@ToString 금지, @Getter + @Builder 사용
- 모든 DTO: Java record 타입
- 모든 JPA 관계: FetchType.LAZY 명시 (N+1 방지)
- 모든 예외: @RestControllerAdvice 처리 (try-catch 금지)

**Scale/Scope**:
- **엔티티: 11개** (Phase 1 구현 완료):
  - 기본: UserProfile, SajuResult (**Phase 1: fullSajuData는 JSON 유지**)
  - Saju 분석: TenGodData, HiddenStemData, CareerFortune (3개, 모두 1:N 또는 1:1)
  - 컨설팅: CareerConsultation, Industry, InterviewTip, Strength (4개)
  - 호환성: CompanyCompatibility, RecommendedRole (2개)
  - 피드백: UserSatisfactionFeedback (1개)
  - **미포함**: SajuFullData (Phase 3-Refactor-3에서 추가 예정), User (Phase 2에서 추가)
- API 엔드포인트: 4개 (`/api/career/timing`, `/api/career/consultation`, `/api/company/compatibility`, `/api/feedback/satisfaction`)
- 외부 API 통합: 3개 (FastAPI, OpenAI, 공공데이터API)
- **계산 로직**: TenGodCalculator (십신), HiddenStemCalculator (지장간), CareerFortuneAnalyzer (관운), CompatibilityScoreCalculator (궁합)

---

## Constitution Check

**GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.**

**Constitution Compliance Review**:

| 원칙 | 요구사항 | 준수 상태 | 비고 |
|-----|--------|---------|------|
| **I. 기술 환경** | Java 21, Spring Boot 4.0.5, MySQL, Phase 1 캐싱 금지 | ✅ Clear | Spec에 명시됨 |
| **II. Java/JPA 표준** | @Data/@ToString 금지, record DTO, FetchType.LAZY, Optional 사용 | ✅ Clear | Spec에 모두 명시됨 |
| **III. 계층형 아키텍처** | Controller (HTTP만), Service (비즈니스 로직), Repository (DB만), GlobalExceptionHandler | ✅ Clear | Spec에 정의됨 |
| **IV. Test-Then-Commit** | 테스트 먼저, 커밋 전 `./gradlew test` 통과, Conventional Commits | ✅ Ready | 구현 단계에서 적용 |
| **V. 문서화 워크플로우** | spec.md는 진실의 원천, plan.md 추적 | ✅ Clear | 현재 plan.md 작성 중 |

**Gate Status**: ✅ **모든 게이트 통과 (Phase 0 진행 가능)**

---

## Project Structure

### Documentation (this feature)

```text
specs/001-career-fortune-api/
├── spec.md                  # Feature specification (완료, 5개 명확화 포함)
├── plan.md                  # This file - Implementation plan
├── research.md              # Phase 0 output (연구 및 아키텍처 검토)
├── data-model.md            # Phase 1 output (데이터 모델, 엔티티 정의)
├── contracts/               # Phase 1 output (API 요청/응답 스키마)
│   ├── career-timing.md
│   ├── career-consultation.md
│   └── company-compatibility.md
├── quickstart.md            # Phase 1 output (개발 시작 가이드)
├── checklists/
│   └── requirements.md       # 요구사항 체크리스트
└── tasks.md                 # Phase 2 output (작업 분해, /speckit.tasks 생성)
```

### Source Code (repository root)

```text
SSAju/
├── src/main/java/ssafy/SSAju/
│   │
│   ├── career/                      ← Phase 1: Career Fortune feature
│   │   ├── entity/
│   │   │   ├── UserProfile.java
│   │   │   ├── SajuResult.java
│   │   │   ├── TenGodData.java (1:N to SajuResult, 십신 분포 - 십신별 행 단위)
│   │   │   ├── HiddenStemData.java (1:N to SajuResult, 지지별 지장간)
│   │   │   ├── CareerFortune.java (1:1 to SajuResult, 관운 분석)
│   │   │   ├── CareerConsultation.java
│   │   │   ├── Industry.java (1:N to CareerConsultation, 추천 산업)
│   │   │   ├── InterviewTip.java (1:N to CareerConsultation, 면접 팁)
│   │   │   ├── Strength.java (1:N to CareerConsultation, 강점)
│   │   │   ├── CompanyCompatibility.java
│   │   │   ├── RecommendedRole.java (1:N to CompanyCompatibility, 추천 직무)
│   │   │   ├── UserSatisfactionFeedback.java
│   │   ├── util/
│   │   │   ├── TenGodCalculator.java (十神 계산)
│   │   │   ├── HiddenStemCalculator.java (地藏干 계산)
│   │   │   ├── CareerFortuneAnalyzer.java (H1/H2 판정)
│   │   │   └── CompatibilityScoreCalculator.java (궁합 점수)
│   │
│   ├── dto/
│   │   ├── request/
│   │   │   ├── CareerTimingRequest.java (birthDate + birthTime)
│   │   │   ├── ConsultationRequest.java (birthDate + birthTime)
│   │   │   ├── CompatibilityRequest.java (userBirthDate + userBirthTime + companyInfo)
│   │   │   └── SatisfactionFeedbackRequest.java
│   │   ├── response/
│   │   │   ├── CareerTimingResponse.java
│   │   │   ├── ConsultationResponse.java
│   │   │   ├── CompatibilityResponse.java
│   │   │   ├── SatisfactionFeedbackResponse.java
│   │   │   ├── ApiResponse.java
│   │   │   └── ErrorInfo.java
│   │   └── external/
│   │       ├── FastAPIResponse.java (만세력 응답)
│   │       └── CareerAdviceResponse.java (OpenAI JSON 응답)
│   │
│   ├── controller/
│   │   ├── CareerTimingController.java
│   │   ├── ConsultationController.java
│   │   ├── CompatibilityController.java
│   │   └── FeedbackController.java
│   │
│   ├── service/
│   │   ├── CareerFortuneService.java (관운 계산, 십신 분석)
│   │   ├── ConsultationService.java (OpenAI 통합)
│   │   ├── CompanyMatchingService.java (궁합 계산)
│   │   ├── FeedbackService.java (만족도 피드백 수집)
│   │   ├── SajuDataService.java (FastAPI 조회)
│   │   └── CompanyInfoService.java (공공데이터API 조회)
│   │
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── UserProfileRepository.java
│   │   ├── SajuResultRepository.java
│   │   ├── TenGodDataRepository.java (new)
│   │   ├── HiddenStemDataRepository.java (new)
│   │   ├── CareerFortuneRepository.java (new)
│   │   ├── CareerConsultationRepository.java
│   │   ├── IndustryRepository.java (new)
│   │   ├── InterviewTipRepository.java (new)
│   │   ├── StrengthRepository.java (new)
│   │   ├── CompanyCompatibilityRepository.java
│   │   ├── RecommendedRoleRepository.java (new)
│   │   └── UserSatisfactionFeedbackRepository.java
│   │
│   ├── exception/
│   │   ├── SajuException.java (root)
│   │   ├── InvalidSajuDataException.java
│   │   ├── FastAPITimeoutException.java
│   │   ├── OpenAIApiException.java
│   │   ├── PublicDataApiException.java
│   │   └── DataAccessException.java
│   │
│   ├── handler/
│   │   └── SajuGlobalExceptionHandler.java (@RestControllerAdvice)
│   │
│   ├── config/
│   │   ├── WebClientConfig.java (WebClient 빈, 타임아웃 설정)
│   │   └── ChatClientConfig.java (Spring AI ChatClient 빈)
│   │
│   └── SSAjuApplication.java
│
├── src/main/resources/
│   ├── application.yaml (DB, API 키, 외부 URL 설정)
│   └── templates/ (필요 시)
│
├── src/test/java/ssafy/SSAju/
│   ├── service/
│   │   ├── CareerFortuneServiceTest.java
│   │   ├── ConsultationServiceTest.java
│   │   ├── CompanyMatchingServiceTest.java
│   │   ├── FeedbackServiceTest.java
│   │   ├── SajuDataServiceTest.java
│   │   └── CompanyInfoServiceTest.java
│   │
│   ├── controller/
│   │   ├── CareerTimingControllerTest.java
│   │   ├── ConsultationControllerTest.java
│   │   ├── CompatibilityControllerTest.java
│   │   └── FeedbackControllerTest.java
│   │
│   ├── integration/
│   │   └── CareerApiIntegrationTest.java (전체 플로우 테스트)
│   │
│   └── unit/
│       ├── TenGodCalculatorTest.java (십신 계산 로직)
│       ├── CareerFortuneAnalyzerTest.java (관운 분석)
│       ├── CompatibilityScoreTest.java (궁합 점수 계산)
│       └── FeedbackValidationTest.java (만족도 피드백 검증)
│
└── build.gradle (Spring AI, WebClient 의존성 추가)
```

**Structure Decision**:
- **모듈화 전략 (Option C)**: 같은 Spring Boot 애플리케이션 내에서 논리적 패키지 분리
- **Phase 1** (지금 구현): `career/` 패키지에서 Career Fortune API 구현 (로그인 제외)
  - User 엔티티는 **제외** (추후 auth/ 패키지에서 구현)
  - **정규화된 11개 엔티티 포함** (JSON 저장 제거):
    - 기본: UserProfile, SajuResult
    - Saju 분석: TenGodData, HiddenStemData, CareerFortune
    - 컨설팅: CareerConsultation, Industry, InterviewTip, Strength
    - 호환성: CompanyCompatibility, RecommendedRole
    - 피드백: UserSatisfactionFeedback
- **Phase 2+** (나중): `auth/` 패키지 추가 시 User 엔티티 추가 및 통합
- **계층형 아키텍처**: Controller → Service → Repository 준수
- **기능 분리**: 각 핵심 기능마다 전용 Service (CareerFortuneService, ConsultationService, CompanyMatchingService)
- **외부 API**: 별도 Service (SajuDataService, CompanyInfoService)로 분리

---

## Complexity Tracking

> **Justification for architecture decisions**

| 설계 결정 | 필요 이유 | 더 단순한 대안과 거절 이유 |
|---------|---------|------------------------|
| **11개 정규화된 엔티티** (Phase 1, JSON 저장 제거) | UserProfile ↔ SajuResult (1:1), SajuResult ↔ TenGodData (1:N, 십신 - 십신별 행 단위), SajuResult ↔ CareerFortune (1:1, 관운), SajuResult ↔ HiddenStemData (1:N, 지장간), SajuResult ↔ CareerConsultation (1:N), CareerConsultation ↔ Industry/InterviewTip/Strength (1:N), UserProfile ↔ CompanyCompatibility (1:N), CompanyCompatibility ↔ RecommendedRole (1:N), SajuResult ↔ UserSatisfactionFeedback (1:N) | JSON 저장 방식 → N+1 쿼리, 데이터 정규화 부족, 재사용성 저하, 타입 안전성 낮음, 쿼리 최적화 어려움 |
| **4개 독립 Service** | 각 기능(관운, 컨설팅, 궁합, 피드백)이 독립적으로 테스트/배포 가능해야 함. P1/P2 우선순위 구분으로 점진적 개발 필요 | 단일 Service → 테스트 복잡도 증가, 변경 파급 범위 확대, 리팩토링 위험 |
| **TenGodCalculator + HiddenStemCalculator 분리** | 십신(十神)과 지장간(地藏干)은 별개의 계산 로직. 분리하면 각각 독립 테스트 가능, 재사용성 향상. 더 정확한 오행 분포 계산 가능 | 통합 Calculator → 로직 혼재, 테스트 복잡도 증대, 유지보수 어려움. 지장간 미포함 시 사주 분석 정확도 저하 |
| **Spring에서 십신+지장간 계산** | FastAPI는 기본 데이터(천간/지지/오행)만 제공 → Spring 단에서 모든 계산 담당하므로 FastAPI 변경에 영향받지 않음. 도메인 로직 통제 가능 | FastAPI에서 십신/지장간까지 계산 → FastAPI 변경 시 Spring도 영향, 통제 불가. 지장간 미포함 시 정확도 저하 |
| **Spring AI 도입** | OpenAI JSON Mode 자동 처리, 타입 안전 매핑, 재시도/타임아웃 관리 자동화 → 코드 간결성 + 신뢰성 | 수동 WebClient + JSON 파싱 → 보일러플레이트 증가, 에러 처리 복잡, 스키마 불일치 위험 |
| **공공데이터API 폴백** | 기업 설립일 자동 조회 실패 시 사용자 입력으로 전환 → 사용성 향상 | 조회 실패 시 500 Error 반환 → 사용자 경험 저하 |

---

## Phase 0: Research & Analysis

**Goal**: 설계 단계 진행 전 모든 기술적 불확실성 해결

### Research Tasks

1. **FastAPI 만세력 응답 스키마 검증**
   - Task: FastAPI 서버의 실제 응답 형식 확인 (天干, 地支, 五行, 十神 필드 정의)
   - Output: `research.md`에 `FastAPIResponse` 필드 목록 기록

2. **십신(十神) 계산 알고리즘 연구**
   - Task: 일간(日干)을 기준으로 월간(月干)을 분석하여 정관/편관/기타 십신 판정
   - Output: 십신 판정 로직 수식화 (`TenGodCalculator` 클래스 스켈레톤 작성)

2.5. **지장간(地藏干) 계산 알고리즘 연구**
   - Task: 각 지지(地支)에 숨겨진 천간(地藏干) 정의 및 계산 규칙. 예: 子→癸, 丑→癸/辛/己, 寅→甲/丙/戊 등
   - Output: 지장간 매핑 테이블 및 `HiddenStemCalculator` 로직. 십신과 함께 정확한 오행 분포 계산에 활용
   - 의존성: TenGodCalculator 완료 후 함께 사용

3. **관운 분석 및 H1/H2 판정 로직**
   - Task: 관성의 변화 주기(10년 대운), 현재 연도 간지, 관성 강도 분석 → H1/H2 예측 알고리즘
   - Output: 관운 판정 알고리즘 정의 (`CareerFortuneAnalyzer` 로직)

4. **공공데이터API 선택 및 스키마**
   - Task: 기업 설립일 조회 가능한 공공 API 조사 (국세청, 기타) 및 요청/응답 포맷
   - Output: 선택된 API명, URL, 필드 정의

5. **Spring AI ChatClient 설정 및 JSON Mode**
   - Task: Spring AI `ChatClient`로 OpenAI JSON Mode 구현, `CareerAdviceResponse` record 스키마
   - Output: ChatClient 빈 설정 (`ChatClientConfig.java`), JSON 스키마 정의

6. **JPA 정규화 엔티티 설계 및 관계 매핑**
   - Task: 정규화된 엔티티(TenGodData, HiddenStemData, CareerFortune, Industry, InterviewTip, Strength, RecommendedRole) 간의 관계 매핑 및 FetchType.LAZY 설정
   - Output: 엔티티 간 1:1, 1:N 관계 정의, 모든 관계에 FetchType.LAZY 명시

### Output: `research.md`

---

## Phase 1: Design & Contracts

**Prerequisites**: `research.md` 완료

### 1.1 Data Model Definition (`data-model.md`)

**정규화된 11개 엔티티 및 관계**:

```
[Phase 1: 현재 구현 - 11개 정규화된 엔티티 (JSON 저장 제거)]

UserProfile
├── id: Long (PK)
├── birthDate: LocalDate (NOT NULL, YYYY-MM-DD)
├── birthTime: LocalTime (NOT NULL, HH:mm format, 사주 명리학 정확성 위해 필수)
├── createdAt: LocalDateTime
├── updatedAt: LocalDateTime
├── UNIQUE(birthDate, birthTime): 같은 생년월일시를 가진 사용자는 동일한 사주 분석 결과 공유
├── (1:1) → SajuResult
├── (1:N) → CompanyCompatibility
└── Note: Phase 2에서 User.id (FK) 추가 예정

[Phase 2+: 추후 auth/ 패키지에서 구현]
User (로그인 정보 포함)
├── id: Long (PK)
├── email: String (UNIQUE, NOT NULL)
├── password: String (bcrypt, Phase 2 추가)
├── phone: String (UNIQUE)
├── role: String (enum, Phase 2 추가)
└── createdAt, updatedAt: LocalDateTime

SajuResult (1:1 to UserProfile) ← **현재 Phase 1 스키마**
├── id: Long (PK)
├── userProfileId: Long (FK to UserProfile, NOT NULL)
├── fullSajuData: LONGTEXT (FastAPI 원본 JSON - **현재 Phase 1 기준**, 정규화 전 임시 보관)
├── fetchedAt: LocalDateTime
├── (1:N) → TenGodData (십신 분포 - 각 십신별 행)
├── (1:1) → CareerFortune (관운 분석)
├── (1:N) → HiddenStemData (지지별 지장간 - 각 지장간별 행)
├── (1:N) → CareerConsultation (AI 컨설팅 기록)
└── (1:N) → UserSatisfactionFeedback (만족도 피드백)
※ **Phase 3-Refactor-3 변경 예정**: fullSajuData 필드 제거 + (1:1) → SajuFullData 관계 추가 (완전 정규화)

TenGodData (1:N to SajuResult, 십신 분포 - 행 단위 정규화)
├── id: Long (PK)
├── sajuResultId: Long (FK to SajuResult, NOT NULL)
├── tenGodName: String (십신 이름, e.g., "正官", "偏官", "正财", "偏财" 등)
├── score: Integer (해당 십신의 점수)
├── createdAt: LocalDateTime
└── **설계**: Map<"正官": 1, "偏官": 1> → 2개 행 (각 십신별 행 분리, 완전 정규화)

HiddenStemData (1:N to SajuResult, 지지별 지장간 - 행 단위 정규화)
├── id: Long (PK)
├── sajuResultId: Long (FK to SajuResult, NOT NULL)
├── earthlyBranch: String (지지, e.g., "子", "丑", "午", "戌" 등)
├── hiddenStem: String (해당 지지의 지장간, e.g., "癸", "辛", "己" 등 - 1개만)
├── createdAt: LocalDateTime
└── **설계**: "丑": ["癸", "辛", "己"] → 3개 행 (각 지장간별 행 분리, 완전 정규화)

SajuFullData (1:1 to SajuResult, Phase 3-Refactor-3 추가, FastAPI 원본 데이터 정규화)
├── id: Long (PK)
├── sajuResultId: Long (FK to SajuResult, NOT NULL, UNIQUE)
├── yearPillar, monthPillar, dayPillar, hourPillar: String (天干地支 조합)
├── dayMaster: String (일간)
├── dayMasterElement: String (일간의 오행)
├── fiveElements: JSON 또는 1:N (오행 분포)
├── solarCorrection: JSON (선택사항)
└── createdAt: LocalDateTime

CareerFortune (1:1 to SajuResult, 관운 분석)
├── id: Long (PK)
├── sajuResultId: Long (FK to SajuResult, NOT NULL, UNIQUE)
├── favoredPeriod: String (NOT NULL, "H1" or "H2")
├── confidenceScore: Integer (0-100, NOT NULL)
└── reasoning: TEXT (분석 근거)

CareerConsultation (1:N to SajuResult, AI 컨설팅 기록)
├── id: Long (PK)
├── sajuResultId: Long (FK to SajuResult, NOT NULL)
├── openaiModelVersion: String (e.g., "gpt-4-turbo")
├── generatedAt: LocalDateTime
├── (1:N) → Industry (추천 산업)
├── (1:N) → InterviewTip (면접 팁)
└── (1:N) → Strength (강점 분석)

Industry (1:N to CareerConsultation, 추천 산업)
├── id: Long (PK)
├── careerConsultationId: Long (FK to CareerConsultation, NOT NULL)
├── name: String (산업명, e.g., "금융/핀테크")
└── reason: TEXT (추천 이유)

InterviewTip (1:N to CareerConsultation, 면접 팁)
├── id: Long (PK)
├── careerConsultationId: Long (FK to CareerConsultation, NOT NULL)
└── content: TEXT (팁 내용)

Strength (1:N to CareerConsultation, 강점 분석)
├── id: Long (PK)
├── careerConsultationId: Long (FK to CareerConsultation, NOT NULL)
└── description: TEXT (강점 설명)

CompanyCompatibility (1:N to UserProfile, 기업 궁합)
├── id: Long (PK)
├── userProfileId: Long (FK to UserProfile, NOT NULL)
├── companyName: String (NOT NULL)
├── compatibilityScore: Integer (0-100, NOT NULL)
├── createdAt: LocalDateTime
├── (1:N) → RecommendedRole (추천 직무)
└── (복합 인덱스: userProfileId + companyName)

RecommendedRole (1:N to CompanyCompatibility, 추천 직무)
├── id: Long (PK)
├── compatibilityId: Long (FK to CompanyCompatibility, NOT NULL)
└── roleName: String (직무명)

UserSatisfactionFeedback (1:N to SajuResult, 만족도 피드백)
├── id: Long (PK)
├── sajuResultId: Long (FK to SajuResult, NOT NULL)
├── feedbackType: Enum (CAREER_TIMING/CONSULTATION/COMPATIBILITY, NOT NULL)
├── satisfactionStatus: Enum (SATISFIED/DISSATISFIED, NOT NULL)
├── feedbackContent: VARCHAR(500) (nullable) - 사용자 상세 의견, @Size(max=500) 제약
├── createdAt: LocalDateTime
└── (인덱스: sajuResultId + createdAt)
```

**Relationships & FetchType**:
- **Phase 1** (현재, 모든 관계에 FetchType.LAZY 명시):
  - UserProfile (1) ↔ SajuResult (1:1) via userProfileId, FetchType.LAZY
  - SajuResult (1) ↔ TenGodData (1:N) via sajuResultId, FetchType.LAZY
  - SajuResult (1) ↔ CareerFortune (1:1) via sajuResultId, FetchType.LAZY
  - SajuResult (1) ↔ HiddenStemData (1:N) via sajuResultId, FetchType.LAZY
  - SajuResult (1) ↔ CareerConsultation (1:N) via sajuResultId, FetchType.LAZY
  - CareerConsultation (1) ↔ Industry (1:N) via careerConsultationId, FetchType.LAZY
  - CareerConsultation (1) ↔ InterviewTip (1:N) via careerConsultationId, FetchType.LAZY
  - CareerConsultation (1) ↔ Strength (1:N) via careerConsultationId, FetchType.LAZY
  - UserProfile (1) ↔ CompanyCompatibility (1:N) via userProfileId, FetchType.LAZY
  - CompanyCompatibility (1) ↔ RecommendedRole (1:N) via compatibilityId, FetchType.LAZY
  - SajuResult (1) ↔ UserSatisfactionFeedback (1:N) via sajuResultId, FetchType.LAZY
- **Phase 2+** (추후):
  - User (1) ↔ UserProfile (1:1) via userId, FetchType.LAZY (auth/ 패키지에서 추가)

**Validation Rules** (Phase 1):
- `birthDate`: 과거 날짜, 현실적 범위 (1900-01-01 ~ 오늘)
- `birthTime`: HH:mm 형식 (00:00 ~ 23:59)
- `UNIQUE(birthDate, birthTime)`: 같은 생년월일시 조합은 중복 불가
- `compatibilityScore`: 0 ≤ score ≤ 100
- 모든 외래키: NOT NULL (엔티티 생성 시 필수)
- Note: `email` 검증은 Phase 2 User 엔티티에서 추가

---

### 1.2 API Contracts (`contracts/`)

#### `career-timing.md`

```
POST /api/career/timing
Content-Type: application/json

Request:
{
  "birthDate": "1990-10-10",  // YYYY-MM-DD format, required
  "birthTime": "14:30"        // HH:mm format (24-hour), required
}

Response (200 OK):
{
  "success": true,
  "data": {
    "favoredPeriod": "H1",           // "H1" (상반기) or "H2" (하반기)
    "confidenceScore": 75,           // 0-100
    "reasoning": "정관이 강하고 현재 대운이 상반기와 궁합..."
  },
  "error": null,
  "timestamp": 1712700000000
}

Error Response (400 Bad Request):
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_DATE_FORMAT",
    "message": "Birth date must be in YYYY-MM-DD format",
    "requestId": "req-12345-abc"
  },
  "timestamp": 1712700000000
}

Error Response (503 Service Unavailable):
{
  "success": false,
  "data": null,
  "error": {
    "code": "FASTAPI_TIMEOUT",
    "message": "Failed to fetch saju data after 2 retries. Please try again later.",
    "requestId": "req-12345-abc"
  },
  "timestamp": 1712700000000
}
```

#### `career-consultation.md`

```
POST /api/career/consultation
Content-Type: application/json

Request:
{
  "birthDate": "1990-10-10",     // YYYY-MM-DD format, required
  "birthTime": "14:30",          // HH:mm format (24-hour), required
  "heavenlyStems": ["庚", "丙", "己", "辛"],     // 4개 天干 (年月日時)
  "earthlyBranches": ["午", "戌", "未", "未"],   // 4개 地支 (年月日時)
  "fiveElements": {                             // 五行 분포
    "木": 1,
    "火": 2,
    "土": 1,
    "金": 2,
    "水": 2
  },
  "hiddenStems": {                              // 地藏干 분포 (지지별 숨겨진 천간)
    "午": ["丁", "己"],
    "戌": ["戊", "辛", "丁"],
    "未": ["己", "丁", "乙"]
  },
  "tenGodDistribution": {                       // 十神 분포
    "正官": 1,
    "偏官": 1,
    "正财": 1,
    "偏财": 1
  }
}

Response (200 OK):
{
  "success": true,
  "data": {
    // 기본 조언 (3개 필드 그룹)
    "industries": [
      {"name": "금융/핀테크", "reason": "오행 金 강세로 재무 관련 산업 적성"},
      {"name": "IT/소프트웨어", "reason": "오행 水 분포로 논리력 강함"},
      {"name": "제조업", "reason": "오행 金 과다로 정밀함 강점"}
    ],
    "interviewTips": [
      "일관성 있는 자기소개 준비 (정관 특성)",
      "데이터 기반 성과 사례 강조",
      "팀 협력 능력 어필"
    ],
    "strengths": [
      "분석력과 논리성",
      "책임감 있는 업무 추진",
      "원칙 준수"
    ],
    
    // 관운 분석 (3개 필드 그룹)
    "favoredPeriod": "H2",
    "confidenceScore": 85,
    "reasoning": "정관 기운과 오행 균형이 안정적이어서 신뢰도 높음",
    
    // 사주 베이스 데이터 (1개 필드 그룹, 내부 6개 필드)
    "sajuProfile": {
      "dayMaster": "丙",
      "dayMasterDescription": "리더십과 추진력 강함, 창의적 사고",
      "fiveElements": {"木": 2, "火": 3, "土": 1, "金": 2, "水": 2},
      "fiveElementsAnalysis": "火가 과도하면 성급할 수 있음. 水 보충 필요",
      "tenGodDistribution": {"正官": 1, "偏官": 1, "正財": 2, "偏財": 1, "正印": 1},
      "keyTenGods": ["正官", "正財"]
    },
    
    // OpenAI 분석 결과 (10개 필드 그룹)
    "cautions": [
      "상반기 급격한 결정 지양",
      "인간관계 신중함 필요"
    ],
    "wealthStyle": "안정적 자산 운용. 장기 투자에 강함",
    "longTermRoadmap": "3년 내 리더십 포지션 획득 목표. 중간 관리자로 경력 발전",
    "personalBranding": "신뢰와 책임감으로 포지셔닝. 전문성 강조",
    "powerKeywords": ["조직력", "추진력", "신뢰", "책임감"],
    "mentalCare": "스트레스 관리: 명상, 산책 권장. 주기적 휴식 필수",
    "environmentFit": "체계적이고 안정적인 조직 문화에 최적",
    "workStyle": "계획 기반 일처리. 팀 중심의 협업 선호",
    "relationshipStrategy": "신뢰 기반의 인간관계 형성. 멘토-멘티 관계 활성화",
    "careerTimeline": "25-35세 경력 성장기. 30대 초반 리더십 전환점",
    
    "openaiModelVersion": "gpt-4-turbo"
  },
  "error": null,
  "timestamp": 1712700000000
}

Error Response (400 Bad Request):
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_SAJU_DATA",
    "message": "Saju data must include 4 heavenly stems and 4 earthly branches",
    "requestId": "req-12345-abc"
  },
  "timestamp": 1712700000000
}

Error Response (504 Gateway Timeout):
{
  "success": false,
  "data": null,
  "error": {
    "code": "OPENAI_API_TIMEOUT",
    "message": "OpenAI API request timed out after 8 seconds. Please try again.",
    "requestId": "req-12345-abc"
  },
  "timestamp": 1712700000000
}
```

#### `company-compatibility.md`

```
POST /api/company/compatibility
Content-Type: application/json

Request:
{
  "birthDate": "1990-10-10",              // 사용자 생년월일, required
  "birthTime": "14:30",                   // 사용자 태어난 시간 (HH:mm), required
  "companyName": "Samsung Electronics",   // 기업명 (공공데이터 조회용)
  "companyFoundingDate": "1938-01-13",   // (Optional) 기업 설립일, 조회 실패 시 사용자 입력
  "companyFoundingTime": "12:00"         // (Optional) 기업 설립 시간 (HH:mm), 미상 시 기본값 12:00으로 자동 설정
}

참고: 기업 설립일도 사용자 생년월일과 동일한 수준으로 지장간 포함하여 사주 계산. 설립 시간 미상 시 정오(12:00)로 기본 설정.

Response (200 OK):
{
  "success": true,
  "data": {
    // 핵심 점수
    "compatibilityScore": 78,                    // 0-100
    "confidenceLevel": "HIGH",                   // LOW, MEDIUM, HIGH
    
    // 분석 근거
    "reasoning": "사용자의 정관(正官) 기운과 기업 설립일의 오행(金/水)이 강한 상호보완적 시너지를 냅니다. 특히 체계적인 시스템 안에서 능력을 발휘하는 데 유리한 명식입니다.",
    
    // 점수 투명성
    "scoreBreakdown": {
      "tenGodCompatibility": 82,                 // 십신 기반 궁합
      "fiveElementsMatch": 75,                   // 오행 기반 궁합
      "hiddenStemAlignment": 76,                 // 지장간 기반 궁합
      "leadershipFit": 80                        // 리더십 매칭도
    },
    
    // 직무별 맞춤 정보 (Array of Objects)
    "roleCompatibility": [
      {
        "roleName": "제조 관리자",
        "score": 85,
        "reason": "조직력 강점과 정확히 매치",
        "recommendation": "즉시 지원 권장"
      },
      {
        "roleName": "공급망 담당자",
        "score": 78,
        "reason": "체계성 우수하나 유연성 보완 필요",
        "recommendation": "관련 경험 어필 시 유리"
      },
      {
        "roleName": "R&D 리더",
        "score": 72,
        "reason": "기술력은 있으나 창의적 발산보다 관리형 리더십에 가까움",
        "recommendation": "실무 경력 축적 후 매니징 롤 지원 추천"
      }
    ],
    
    // 핵심 강점
    "synergies": [
      "정관 기운이 회사의 체계적 조직 문화와 부합",
      "오행 金 분포가 제조 및 IT 산업 특성과 일치",
      "지장간 분석 결과 장기 근속 시 안정성 매우 높음"
    ],
    
    // 주의 사항
    "cautions": [
      "회사의 급격한 조직 개편 시 적응 스트레스 예상",
      "상반기보다 하반기에 뚜렷한 성과 기대"
    ],
    
    // 월별 운세: 핵심 달 5개월만 (month는 정수형 1-12)
    "monthlyForecast": [
      {
        "month": 1,
        "score": 35,
        "type": "CAUTION",
        "label": "주의",
        "advice": "신입 채용 지원 자제, 이직 지양",
        "details": "기운 전환기, 현재 역량 강화에 집중할 시기"
      },
      {
        "month": 2,
        "score": 50,
        "type": "NORMAL",
        "label": "보통",
        "advice": "이력서 및 포트폴리오 정비",
        "details": "서서히 기운이 풀리는 시기, 실무 면접 대비 적기"
      },
      {
        "month": 3,
        "score": 95,
        "type": "LUCKY",
        "label": "최고조",
        "advice": "이 시기에 집중적으로 지원 권장",
        "details": "정관 기운이 정점, 면접관의 평가가 매우 호의적으로 작용함"
      },
      {
        "month": 6,
        "score": 88,
        "type": "LUCKY",
        "label": "매우 높음",
        "advice": "중요 면접 일정 잡기 좋음",
        "details": "오행의 균형이 가장 잘 맞는 시기"
      },
      {
        "month": 7,
        "score": 42,
        "type": "CAUTION",
        "label": "주의",
        "advice": "충분한 사전 준비 후 지원 필수",
        "details": "회사와의 에너지 충돌 가능성, 압박 면접 주의"
      }
    ],
    
    // 경력 발전 마일스톤
    "careerMilestones": {
      "immediate": {
        "period": "1-3개월",
        "action": "집중 채용 기간 대비 지원",
        "expectedOutcome": "서류 및 1차 면접 통과 가능성 80% 이상"
      },
      "shortTerm": {
        "period": "3-12개월",
        "action": "신규 팀 적응 및 업무 프로세스 파악",
        "expectedOutcome": "조기 적응 및 팀 내 핵심 실무자로 신뢰 구축"
      },
      "mediumTerm": {
        "period": "1-3년",
        "action": "주요 프로젝트 주도 및 성과 창출",
        "expectedOutcome": "빠른 인사 고과 인정 및 조기 진급 기회 확보"
      }
    }
  },
  "error": null,
  "timestamp": 1712700000000
}

Error Response (404 Not Found):
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMPANY_NOT_FOUND",
    "message": "Company not found in public database. Please provide founding date.",
    "requestId": "req-12345-abc"
  },
  "timestamp": 1712700000000
}

Error Response (400 Bad Request - 재시도):
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Either provide companyName (for API lookup) or companyFoundingDate (manual input)",
    "requestId": "req-12345-abc"
  },
  "timestamp": 1712700000000
}
```

#### `user-feedback.md`

```
POST /api/feedback/satisfaction
Content-Type: application/json

Request:
{
  "sajuResultId": 123,                                    // SajuResult 엔티티 ID
  "feedbackType": "CAREER_TIMING",                       // CAREER_TIMING / CONSULTATION / COMPATIBILITY
  "satisfactionStatus": "SATISFIED",                     // SATISFIED / DISSATISFIED
  "feedbackContent": "분석 결과가 매우 정확했습니다. 다만 면접 팁이 좀 더 자세하면 좋을 것 같습니다."  // 선택사항, 최대 500자
}

Response (200 OK):
{
  "success": true,
  "data": {
    "feedbackId": 456,
    "createdAt": 1712700000000,
    "feedbackContent": "분석 결과가 매우 정확했습니다. 다만 면접 팁이 좀 더 자세하면 좋을 것 같습니다."
  },
  "error": null,
  "timestamp": 1712700000000
}

Error Response (400 Bad Request):
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_FEEDBACK_TYPE",
    "message": "feedbackType must be one of: CAREER_TIMING, CONSULTATION, COMPATIBILITY",
    "requestId": "req-12345-abc"
  },
  "timestamp": 1712700000000
}

Error Response (404 Not Found):
{
  "success": false,
  "data": null,
  "error": {
    "code": "SAJU_RESULT_NOT_FOUND",
    "message": "SajuResult with id 123 not found",
    "requestId": "req-12345-abc"
  },
  "timestamp": 1712700000000
}
```

---

### 1.3 Quickstart Guide (`quickstart.md`)

```markdown
# Career Fortune API - Quick Start Guide

## Prerequisites

- Java 21
- Spring Boot 4.0.5
- MySQL 8.0+
- API Keys: FastAPI 서버 URL, OpenAI API Key, 공공데이터API Key

## Setup

1. **Database Configuration**
   ```yaml
   # src/main/resources/application.yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/ssaju
       username: root
       password: ${DB_PASSWORD}
     jpa:
       hibernate:
         ddl-auto: validate

   saju:
     fastapi:
       url: ${FASTAPI_URL}
       timeout-seconds: 3
     openai:
       api-key: ${OPENAI_API_KEY}
       model: gpt-4-turbo
       timeout-seconds: 8
     public-data:
       url: ${PUBLIC_DATA_API_URL}
       api-key: ${PUBLIC_DATA_API_KEY}
       timeout-seconds: 5
   ```

2. **Dependencies**
   ```gradle
   // build.gradle
   dependencies {
     implementation 'org.springframework.boot:spring-boot-starter-web:4.0.5'
     implementation 'org.springframework.boot:spring-boot-starter-data-jpa:4.0.5'
     implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter:0.8.1'
     implementation 'mysql:mysql-connector-java:8.0.33'
     implementation 'org.projectlombok:lombok:1.18.30'
     ...
   }
   ```

3. **Build & Run**
   ```bash
   cd SSAju
   ./gradlew clean build
   ./gradlew bootRun
   ```

## API Examples

### 1. Career Timing Analysis

```bash
curl -X POST http://localhost:8080/api/career/timing \
  -H "Content-Type: application/json" \
  -d '{"birthDate":"1990-10-10", "birthTime":"14:30"}'
```

### 2. Career Consultation

```bash
curl -X POST http://localhost:8080/api/career/consultation \
  -H "Content-Type: application/json" \
  -d '{
    "birthDate": "1990-10-10",
    "birthTime": "14:30",
    "heavenlyStems": ["庚", "丙", "己", "辛"],
    "earthlyBranches": ["午", "戌", "未", "未"],
    "fiveElements": {"木":1, "火":2, "土":1, "金":2, "水":2},
    "hiddenStems": {"午": ["丁", "己"], "戌": ["戊", "辛", "丁"], "未": ["己", "丁", "乙"], "未": ["己", "丁", "乙"]},
    "tenGodDistribution": {"正官": 1, "偏官": 1, "正財": 1, "偏財": 1}
  }'
```

### 3. Company Compatibility

```bash
curl -X POST http://localhost:8080/api/company/compatibility \
  -H "Content-Type: application/json" \
  -d '{
    "birthDate": "1990-10-10",
    "birthTime": "14:30",
    "companyName": "Samsung Electronics",
    "companyFoundingDate": "1938-01-13"
  }'
```

### 4. User Satisfaction Feedback

```bash
curl -X POST http://localhost:8080/api/feedback/satisfaction \
  -H "Content-Type: application/json" \
  -d '{
    "sajuResultId": 123,
    "feedbackType": "CAREER_TIMING",
    "satisfactionStatus": "SATISFIED"
  }'
```

## Testing

```bash
# 전체 테스트
./gradlew test

# 특정 테스트 클래스
./gradlew test --tests "ssafy.SSAju.service.CareerFortuneServiceTest"

# 특정 테스트 메서드
./gradlew test --tests "ssafy.SSAju.service.CareerFortuneServiceTest.testH1FavoredAnalysis"
```

## Key Classes

- **Controller**: `CareerTimingController`, `ConsultationController`, `CompatibilityController`, `FeedbackController`
- **Service**: `CareerFortuneService`, `ConsultationService`, `CompanyMatchingService`, `FeedbackService`, `SajuDataService`, `CompanyInfoService`
- **Repository**: `UserRepository`, `UserProfileRepository`, `SajuResultRepository`, `CareerConsultationRepository`, `CompanyCompatibilityRepository`, `UserSatisfactionFeedbackRepository`
- **Exception Handler**: `SajuGlobalExceptionHandler`
- **Config**: `WebClientConfig`, `ChatClientConfig`

## Architecture

```
HTTP Request
    ↓
[Controller] → DTO validation
    ↓
[Service] → Business logic (十神 calculation, H1/H2 analysis, OpenAI call)
    ↓
[Repository] → DB query via JPA
    ↓
[HTTP Response] → ApiResponse<T> wrapper
```

## Common Issues

- **FASTAPI_TIMEOUT**: FastAPI 서버 응답 지연. 재시도 하거나 타임아웃 설정 조정.
- **OPENAI_API_TIMEOUT**: OpenAI API 응답 지연. 스로틀링이나 토큰 부족 확인.
- **DATABASE_CONNECTION_ERROR**: MySQL 연결 확인. `application.yaml`의 datasource 설정 검증.
- **INVALID_DATE_FORMAT**: 생년월일이 YYYY-MM-DD 형식이 아님.

```

---

### 1.4 Agent Context Update

Run the following to update the agent context for Claude:

```bash
.specify/scripts/bash/update-agent-context.sh claude
```

This will merge the Spring AI, FastAPI integration, and 십신/관운 계산 로직 정보를 agent context에 추가.

---

## Phase 1 Deliverables (생성 예정)

✅ `research.md` - 모든 기술적 불확실성 해결
✅ `data-model.md` - 6개 엔티티, 관계, 검증 규칙 정의
✅ `contracts/career-timing.md` - API 요청/응답 스키마
✅ `contracts/career-consultation.md` - API 요청/응답 스키마
✅ `contracts/company-compatibility.md` - API 요청/응답 스키마
✅ `contracts/user-feedback.md` - 사용자 만족도 피드백 API 스키마
✅ `quickstart.md` - 개발 시작 가이드, 설정, 예제

---

## 다음 단계

**Phase 2 (Task Generation)**: `/speckit.tasks` 명령으로 상세 작업 목록(tasks.md) 생성

```bash
/speckit.tasks
```

이 명령은:
1. FR (Functional Requirement) 별 작업 분해
2. 테스트 우선 작성 계획
3. Conventional Commits 대응
4. 체크리스트 생성

을 생성합니다.

---

## Notes for Implementation

- **Constitutional Compliance**: 모든 구현은 `/CLAUDE.md`의 Java/JPA 표준 및 Test-Then-Commit 프로토콜 준수
- **Documentation Workflow**: spec.md → plan.md → research.md → data-model.md → tasks.md → 구현 (진실의 원천은 항상 spec.md)
- **FastAPI-Spring 역할 분리**: FastAPI는 천간/지지/오행만 제공 (기본 사주 데이터). Spring은 TenGodCalculator + HiddenStemCalculator로 십신 및 지장간 모두 계산. 도메인 로직 통제 가능, FastAPI 변경에 영향 없음.
- **지장간 계산의 중요성**: 십신(十神)만으로 부족함. 지장간(地藏干)을 함께 계산해야 정확한 오행 분포를 파악 가능. AI 컨설팅, 기업 궁합 분석의 신뢰도 향상.
- **데이터 모델링**: UserProfile의 UNIQUE(birthDate, birthTime)으로 중복 데이터 방지. SajuResult에 hiddenStems(Map) 저장으로 재계산 불필요.
- **Test Strategy**: Given-When-Then 패턴 + AssertJ. 각 FR별 최소 2개 테스트 (Happy path + Error case). TenGodCalculator, HiddenStemCalculator는 각각 독립 단위 테스트 필수.
- **Layering**: Controller (얇음) → Service (두터움) → Repository (수동). 비즈니스 로직은 Service에만. 계산 로직(TenGod, HiddenStem, CareerFortune, CompatibilityScore)은 util/ 패키지에서 테스트 가능하게 관리.
