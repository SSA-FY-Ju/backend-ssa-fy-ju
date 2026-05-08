# Feature Specification: Career Fortune & Consultation API

**Feature Branch**: `feat/career-fortune-api`
**Created**: 2026-04-10
**Status**: Draft
**Input**: SSAju career consultation service specification with technical architecture

## Overview

SSAju는 사주 명리학의 관성(정관/편관) 데이터를 활용해 취업 준비생에게 최적의 직무, 합격 시기, 기업 궁합을 제안하는 커리어 특화 백엔드 서비스입니다. 사용자 생년월일시(四柱: 연월일시)를 입력받아 3가지 핵심 기능을 제공합니다:

1. **관운 기반 합격 시기 분석**: 생년월일시를 기반으로 정관/편관 흐름 및 지장간(地藏干) 분석을 통해 상/하반기 취업 유리 시기 예측
2. **AI 커리어 컨설팅**: 오행, 십신(十神), 지장간(地藏干) 분포로 추천 산업군(3~5개), 면접 전략, 강점 분석
3. **기업/직무 궁합**: 사용자 생년월일시와 기업 설립일 사주 대조로 궁합 점수 및 추천 포지션

## Clarifications

### Session 2026-04-10 (Initial)

- Q: REST API 엔드포인트 경로는 어떻게 정의할 것인가? → A: Resource-based 경로 사용 (`POST /api/career/timing`, `POST /api/career/consultation`, `POST /api/company/compatibility`)
- Q: 불완전한 날짜 입력(월/일 미상)을 어떻게 처리할 것인가? → A: 완전한 YYYY-MM-DD 형식만 허용, 불완전하면 400 Bad Request 반환
- Q: SajuResult와 CareerConsultation의 관계 + 기업 정보 저장 방식은? → A: SajuResult 재사용 + 기업은 요청마다 공공데이터API 조회 (CompanyCompatibility는 계산 결과만 저장)
- Q: H1/H2 판정 로직은 어디서 구현할 것인가? → A: FastAPI는 만세력 원시 데이터만 반환, Spring에서 십신 계산 및 관운 분석으로 H1/H2 판정
- Q: OpenAI API 호출 시 응답 구조화 및 구현 방식은? → A: JSON Mode 사용 + Spring AI 도입으로 타입 안전 JSON 매핑 및 에러 처리 자동화

### Session 2026-04-10 (Clarification Phase)

- Q: Phase 1에서 API 인증 정책은? → A: 인증 없이 모든 API 공개 제공. Phase 2에서 JWT 기반 인증 추가 예정.
- Q: OpenAI 호출 빈도 제어 정책은? → A: Phase 1에서는 제한 없음. Phase 2에서 사용자당 일일 한도 도입 예정.
- Q: 사주 결과 및 컨설팅 기록 보관 정책은? → A: Phase 1에서는 무제한 보관. Phase 2에서 보관 정책 수립 예정.
- Q: 서비스 신뢰도(Uptime SLA) 목표는? → A: Phase 1에서는 Best Effort 운영. Phase 2에서 SLA 수립 예정.
- Q: 사용자 만족도 피드백 방식은? → A: 이진 평가(만족함/만족하지 않음) + 선택적 상세 의견(최대 500자) 수집. Phase 2에서 의견 기반 통계 시각화.
- Q: UserSatisfactionFeedback의 분석 결과 추적 방식은? → A: SajuResultId FK + feedbackType(ENUM) + satisfactionStatus(ENUM) + feedbackContent(VARCHAR(500), 선택사항) 저장. 상세 의견으로 피드백 분석 가능.

### Session 2026-04-24 (Birth Time Clarification)

- Q: 태어난 시간(Birth Time)은 필수 입력인가, 선택 입력인가? → A: **필수 입력**. 사주 명리학에서 정확한 분석을 위해 생년월일시(四柱) 모두 필요.
- Q: 태어난 시간의 입력 형식은? → A: **HH:mm (24시간 형식)**. 예: 14:30, 09:00. 분(minute) 단위까지 지원하여 정밀도 향상.
- Q: 관운 분석(User Story 1)에서 birth_time은 어떻게 처리할 것인가? → A: **생년월일시 모두 사용**. FastAPI로 완전한 생년월일시(YYYY-MM-DD HH:mm)를 전송하여 가장 정확한 사주 계산 제공.

### Session 2026-04-30 (1-Call Design & Implementation Validation)

- Q: /consultation 엔드포인트가 2-call 설계(먼저 /timing 호출 필수)인데, 이는 사용자 경험이 나쁘지 않은가? → A: **1-call 설계로 리팩토링 필요**. ConsultationService에서 내부적으로 FastAPI 호출, 십신/지장간 계산, 관운 분석을 모두 수행. 클라이언트는 birthDate + birthTime만 제공하면 완전한 컨설팅 결과(AI 조언 + 관운 분석) 수신 가능.
- Q: 응답 데이터 완전성: /consultation 응답에 favoredPeriod, confidenceScore, reasoning이 빠져있는데? → A: **응답 확장 필수**. ConsultationResponse에 **23개 필드 (기본 조언 3 + 관운 분석 3 + 사주 프로필 내부 6 + OpenAI 분석 10 + 메타데이터 1)** 포함하여 /timing 없이 단독 호출로 전체 정보 제공.
- Q: 트랜잭션 관리: FastAPI/OpenAI I/O 동안 DB 커넥션을 점유하면 Connection Pool 고갈 위험이 있지 않은가? → A: **트랜잭션 분리 필수**. ConsultationService에서 @Transactional 제거. FastAPI/OpenAI 호출은 트랜잭션 밖에서 수행. 각 DB 작업(UserProfile find/create, SajuResult find/create, CareerConsultation save)은 Repository의 @Transactional에 의해 개별 트랜잭션으로 실행. 네트워크 지연이 DB 커넥션을 점유하지 않음.
- Q: Jackson 호환성: PR 리뷰에서 불필요한 @JsonProperty/@JsonFormat 제거를 요청했는데? → A: **프로젝트는 `com.fasterxml.jackson.*` 패키지를 사용**. FastAPI 응답이 이미 camelCase이므로 Jackson이 자동 매핑함. @JsonProperty, @JsonFormat 등 불필요한 어노테이션은 추가하지 말 것. (빌드로 확인: tools.jackson.*은 존재하지 않음)
- Q: DataIntegrityViolationException 처리: 동시 다중 요청 시 같은 생년월일시 사용자가 두 번 생성될 수 있지 않은가? → A: **최적 "create or find" 패턴 적용**. SajuResult를 찾지 못하면 새로 생성 시도. UNIQUE 제약으로 인해 동시 생성 시 DIVE 발생하면, 호출 서비스에서 catch하여 다시 find 수행. 이미 생성된 결과를 재사용. 예외 전파 불필요, 로그만 기록 (warn level).

### Session 2026-04-27 (Data Uniqueness & Hidden Stem Calculation)

- Q: UserProfile에서 사용자를 식별하는 유니크 키는 무엇인가? → A: **생년월일시 조합 (birthDate + birthTime)이 유니크 키**. 같은 생년월일시를 가진 사용자는 동일한 사주 분석 결과를 공유하므로, 이를 중심으로 데이터를 구성. `UNIQUE(birthDate, birthTime)` 제약 추가.
- Q: 십신 외에 지장간(地藏干)을 어떻게 처리할 것인가? → A: **지장간 계산을 별도로 수행**. 각 지지(地支) 내 숨겨진 천간(地藏干)을 계산하여 십신 분석을 더 정확하게 구성. SajuResult의 `hiddenStems[]` 필드에 저장. FastAPI 응답에서 지장간 데이터를 수신하고, Spring에서 `TenGodCalculator`와 함께 `HiddenStemCalculator`를 통해 정확한 오행 분포 계산.
- Q: 지장간 계산이 AI 컨설팅과 기업 궁합에 어떻게 영향을 미치는가? → A: 오행 분포 계산 시 지장간을 포함하여 더 정확한 오행 비율 제공. OpenAI 프롬프트에 십신 + 지장간 분석 결과를 모두 포함하여 더 정밀한 커리어 컨설팅 제공. 기업 궁합도 마찬가지로 지장간 분석을 포함하여 신뢰도 향상.
- Q: FastAPI의 지장간(地藏干) 데이터 제공 여부는? → A: **FastAPI는 천간/지지/오행만 제공**. Spring 백엔드에서 `TenGodCalculator`와 `HiddenStemCalculator`를 통해 십신 및 지장간을 모두 계산. 이렇게 하면 Spring 단에서 모든 사주 계산을 통제 가능하고 FastAPI 변경에 영향받지 않음.
- Q: hiddenStems[] 필드의 정확한 데이터 구조는? → A: **`hiddenStems`는 `Map<String, List<String>>`** (JSON으로 저장). 예: `{"子": ["癸"], "丑": ["癸", "辛", "己"], "寅": ["甲", "丙", "戊"], ...}`. 각 지지(年月日時)별로 포함된 지장간들을 매핑하여 저장.
- Q: 기업 설립일시의 지장간 계산 필요성은? → A: **기업 설립일도 지장간 포함하여 계산**. 기업 설립시간이 미상일 경우 12:00으로 기본 설정하고, 해당 시주의 지장간도 계산. 사용자 궁합 분석과 동일한 수준의 정확성 유지.

### Session 2026-05-06 (REST 통신 최적화, 동시성 제어, 엔티티 설계)

- Q: FastAPI 호출 시 Reactive 의존성(WebClient)이 필수인가? → A: **아니오, RestClient 사용**. 동기식 호출에 WebClient의 무거운 Reactive 오버헤드 불필요. RestClient + Spring Retry로 간결하고 직관적인 구현 가능.
- Q: SajuResult 동시 Insert 경합(Race Condition) 처리는? → A: **JdbcTemplate INSERT IGNORE 활용**. DataIntegrityViolationException 대신 native query로 안전하게 처리. UNIQUE 제약 조건 활용으로 신뢰도 높음.
- Q: H2 테스트 DB는 MySQL과 호환 가능한가? → A: **H2 MySQL 모드 설정 필수**: `jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE`. 이를 통해 INSERT IGNORE, UNIQUE constraint 등 MySQL 문법 호환성 확보.
- Q: JPA 엔티티에서 equals/hashCode는 어떻게 구현할 것인가? → A: **ID 기준으로 직접 구현 필수**. Lombok @EqualsAndHashCode 사용 금지. 지연 로딩(Lazy Loading) 중 Proxy 객체 비교 시 정확성 보장. `@CreatedDate`/`@LastModifiedDate`로 timestamp 자동 관리.
- Q: Map<String, Integer> tenGodDistribution을 어떻게 관리할 것인가? → A: **TenGodDistribution 일급 컬렉션 객체로 래핑**. 의미가 명확해지고 비즈니스 로직을 객체 내부로 응집 가능. 마찬가지로 HiddenStems, FiveElements도 value object 생성.
- Q: Service 계층의 검증 로직이 비대화되면? → A: **전용 Validator 클래스 분리**. SajuValidator, RequestValidator, CompatibilityValidator 등으로 책임 분리. Service는 Validator 호출만 담당.

### Session 2026-05-06 (CodeRabbit PR #9 리뷰 반영)

- Q: 외부 API 통신 방식을 명확히 구분할 것인가? → A: **OpenAI는 Spring AI ChatClient, FastAPI/공공데이터API는 RestClient + Spring Retry로 명확 구분**. 스펙의 "External Communication" 섹션에서 클라이언트 선택 기준 추가.
- Q: feedbackContent 필드의 DB 제약은? → A: **VARCHAR(500) 명시**. Java @Size(max=500) 제약과 DB VARCHAR(500) 동기화. TextType이 아닌 VARCHAR로 수정하여 일관성 확보.
- Q: Phase 1 인증 정책은 어떻게 명시할 것인가? → A: **현재 상태 유지**. Assumptions 섹션에서 "Phase 1에서는 인증 없이 모든 API 공개 제공. 보안 검증 필요 시 Phase 2에서 구현" 명시 (이미 문서화됨).
- Q: 로깅 보안 정책은? → A: **민감 데이터 로깅 금지**. architecture-guide.md의 "로깅 정책" 섹션 참고. 본 스펙에서는 FR-013 업데이트: "외부 API 호출 로깅 시 전문 정보는 debug 레벨로 분리하고 운영 로그에서 개인정보 제거".
- Q: RestClient 예외 처리 방식을 명시할 것인가? → A: **@Retryable 대상 명확화**. FR-008 추가: "RestClientResponseException (4xx)는 재시도 안 함, 5xx는 재시도 함. 타임아웃/네트워크 오류(ResourceAccessException)는 지수 백오프로 재시도".

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Career Timing Analysis (Priority: P1)

취업 준비생이 생년월일을 입력하고 관운(官運) 데이터를 기반으로 상반기/하반기 중 취업에 유리한 시기를 파악합니다.

**Why this priority**: 핵심 가치 제안. 언제 집중적으로 취업 활동해야 하는지는 사주 기반 조언의 기초입니다.

**Independent Test**: 생년월일시(YYYY-MM-DD HH:mm) 입력 → FastAPI 연동으로 생년월일시 기반 사주 데이터 수신 → 관운 계산 → 유리한 채용 시기 반환. 다른 기능 없이 독립적으로 동작합니다.

**Acceptance Scenarios**:

1. **Given** 완전한 생년월일시(YYYY-MM-DD HH:mm) 입력, **When** 관운 분석 요청, **Then** 유리한 채용 시기(H1/H2) + 신뢰도 점수 반환
2. **Given** 정관/편관 데이터 조회, **When** 상/하반기 단위 분석, **Then** 채용 확률 높은 시기와 이유 제시
3. **Given** 부정확한 시간(시간만, 분 없음) 또는 미상 시, **When** 요청 제출, **Then** 400 Bad Request + "HH:mm 형식으로 정확한 시간 입력 필요" 오류 메시지 반환

---

### User Story 2 - AI Career Consulting (Priority: P1)

취업 준비생이 사주 데이터(천간/지지)를 제출하고 OpenAI API를 통한 AI 커리어 컨설턴트로부터 개인 맞춤 조언(추천 산업, 면접 전략, 강점, 관운 분석, 재무 스타일, 장기 로드맵, 개인 브랜딩, 파워 키워드, 정신 케어, 업무 환경, 업무 스타일, 인간관계 전략, 커리어 타임라인)을 받습니다.

**Why this priority**: 직접 수익화 가능한 기능. 전문가 수준의 맞춤 조언으로 경쟁 차별화.

**Independent Test**: 사주 데이터 입력 → OpenAI API 호출 → 구조화된 권고사항(23개 필드) 반환. P1/P3 없이도 완전히 동작합니다.

**Acceptance Scenarios**:

1. **Given** 완전한 사주 데이터 입력, **When** AI 커리어 컨설팅 요청, **Then** ConsultationResponse (23개 필드) 반환:

   **기본 조언 (3개 필드)**:
   - industries: List<IndustryRecommendation> (3~5개)
   - interviewTips: List<String> (3~5개)
   - strengths: List<String> (3~5개)

   **관운 분석 (3개 필드)**:
   - favoredPeriod: String ("H1" or "H2")
   - confidenceScore: Integer (0-100)
   - reasoning: String

   **사주 베이스 데이터 (1개 복합 필드, 6개 내부 필드)**:
   - sajuProfile.dayMaster: String
   - sajuProfile.dayMasterDescription: String
   - sajuProfile.fiveElements: Map<String, Integer>
   - sajuProfile.fiveElementsAnalysis: String
   - sajuProfile.tenGodDistribution: Map<String, Integer>
   - sajuProfile.keyTenGods: List<String>

   **OpenAI 분석 결과 (10개 필드)**:
   - cautions: List<String>
   - wealthStyle: WealthStyle (4개 내부 필드)
   - longTermRoadmap: LongTermRoadmap (4개 내부 필드)
   - personalBranding: PersonalBranding (5개 내부 필드)
   - powerKeywords: PowerKeywords (3개 내부 필드)
   - mentalCare: MentalCare (4개 내부 필드)
   - environmentFit: EnvironmentFit (6개 내부 필드)
   - workStyle: WorkStyle (4개 내부 필드)
   - relationshipStrategy: RelationshipStrategy (5개 내부 필드)
   - careerTimeline: CareerTimeline (5개 내부 필드)

   **메타데이터 (1개 필드)**:
   - openaiModelVersion: String
2. **Given** 느린 외부 API(OpenAI), **When** Timeout 초과, **Then** 정중한 오류 메시지 + 재시도 안내 반환
3. **Given** 유효한 사주 데이터, **When** 컨설팅 응답, **Then** AI 모델 버전 메타데이터(openaiModelVersion) + 모든 23개 필드 포함 (타임스탬프는 ApiResponse wrapper의 `timestamp` 필드로 별도 제공)

---

### User Story 3 - Company & Job Fit Analysis (Priority: P2)

취업 준비생이 목표 기업의 설립일시(사주)와 자신의 생년월일시, 지원 직군을 입력하여 궁합 점수(0~100)와 직군 오행 분석, 추천 직무, 예상 면접 질문을 얻습니다.

**Why this priority**: 보조 기능. 기업 선택 신뢰도를 높이지만, P1 기능(타이밍+컨설팅)이 핵심가치입니다.

**Independent Test**: 사용자 사주 + 기업 설립일 + 지원 직군 → 궁합 계산 → 호환성 점수 + 직군 오행 분석 + 추천 직무 반환. P1/P2 불필요합니다.

**Acceptance Scenarios**:

1. **Given** 사용자 사주(YYYY-MM-DD HH:mm) + 지원 직군(category + detailName) + 기업 설립일(YYYY-MM-DD, 선택사항으로 시간 HH:mm 추가 가능), **When** 호환성 분석, **Then** 다음 필드 포함 JSON 반환:
   - requestContext: {companyName, targetRole: {category, detailName}} (프론트 응답 에코)
   - compatibilityScore: 0-100 정수
   - summary: 전체 궁합 한 줄 요약 텍스트
   - targetRoleAnalysis: {matchScore, synergy, warning} (직군 오행 분석, Service 계층에서 생성)
   - fiveElements: {userDistribution, companyDistribution, synergyDescription} (오행 분포 비교, Service 계층에서 생성)
   - analysisBreakdown: {characterMatch, potentialSynergy, longTermStability} (Service 계층에서 계산된 값)
   - actionableStrategy: {interviewKeywords[], weaknessDefense, bestTiming: {luckyDays[], preferredTime}} (Service 계층에서 생성)
   - expectedInterviewQuestions[]: [{question, intent}] (Service 계층에서 생성)
   - roleCompatibility[]: [{roleName, score, reason, tag}] (추천 직무 Array, score/reason/tag는 Service에서 계산)
   - monthlyForecast[]: [{month(1-12), score, status, advice}] (5개월만 포함, status: LUCKY/NORMAL/CAUTION)
   - cautions[]: 주의사항 배열
   (기본값 및 지장간 계산은 Clarifications 참고)
2. **Given** 유효한 두 사주 데이터 + 지원 직군, **When** 매칭 실행, **Then** targetRoleAnalysis에 직군 오행 기반 synergy/warning 포함
3. **Given** 기업 설립일시가 불완전할 경우(시간 정보 미상), **When** 요청 제출, **Then** 계속 진행 (기본값 12:00 적용, Clarifications 참고). 지장간은 완전 데이터로 계산
4. **Given** targetRole.category가 유효하지 않은 값, **When** 요청 제출, **Then** 400 Bad Request 반환

---

### User Story 4 - User Satisfaction Feedback (Priority: P1)

사주 분석(관운 분석, AI 컨설팅, 기업 궁합) 완료 후 취업 준비생이 서비스의 만족도를 평가합니다.

**Why this priority**: 서비스 품질 관리 기초. 사용자 만족도는 향후 기능 개선 및 관리자 의사결정의 핵심 지표입니다.

**Independent Test**: 사주 분석 API 응답 이후 → 만족도 피드백 API 호출 → 피드백 저장 완료. 기존 3가지 기능과 독립적으로 동작합니다.

**Acceptance Scenarios**:

1. **Given** 관운 분석 또는 AI 컨설팅 또는 기업 궁합 분석 완료, **When** 만족도 피드백 제출, **Then** 만족도(만족함/만족하지 않음) 저장 성공 반환
2. **Given** 유효한 만족도 피드백, **When** 저장, **Then** 피드백 타임스탐프 + 관련 분석 ID 함께 기록
3. **Given** 피드백이 제출되지 않은 경우, **When** 분석 완료, **Then** 나중에 별도 요청 없이 선택적 입력 가능

**Note**: Phase 1에서는 피드백 **수집만** 담당. 피드백 통계 시각화(대시보드)는 Phase 2에서 Admin Dashboard 구현 시 함께 진행.

---

### Edge Cases

- **불완전한 날짜/시간 입력**:
  - 사용자 생년월일시: **완전한 YYYY-MM-DD HH:mm 형식 필수**. 미상 시 400 Bad Request 반환 (상세 ErrorInfo 제시)
  - 기업 설립일: YYYY-MM-DD 최소 필수. 시간(HH:mm)은 선택사항. 시간 미상 시 기본값(12:00) 자동 적용
- **기업 설립일 조회 실패**: 공공데이터API 조회 실패 시 사용자에게 기업 설립일을 직접 입력하도록 요청 (폴백 시나리오). 이 경우에도 시간 형식 규칙 동일 적용
- 외부 API(FastAPI, OpenAI, 공공데이터API) 일시 다운 상황 → Graceful Error 반환 + 재시도 안내
- 수천 명의 동시 요청 처리 → Connection Pool로 안정성 보장

## Technical Architecture

**⚠️ 상세한 기술 아키텍처 설계는 [`plan.md`](./plan.md)에서 관리합니다.**

### 개요

- **시스템**: REST API (Spring Boot) ↔ MySQL + 외부 API (FastAPI, OpenAI, 공공데이터)
- **데이터 모델**: 정규화된 엔티티 구조 (phase별로 진화)
- **아키텍처**: 계층형 (Controller → Service → Repository)
- **예외 처리**: @RestControllerAdvice 중앙화

상세 내용은 다음을 참고하세요:
- **설계 결정 및 구현 전략**: [`plan.md#technical-context`](./plan.md) 참고
- **엔티티 설계 및 정규화**: [`plan.md#11-data-model-definition`](./plan.md) 참고
- **아키텍처 패턴**: [`../../../skills/architecture-guide.md`](../../../skills/architecture-guide.md) 참고

### API Design & Response Format

**DTO & Record Types** (Java 21 표준):

모든 요청/응답 객체는 `record` 타입 사용:

```java
// Request example
public record CareerTimingRequest(
    LocalDate birthDate,      // YYYY-MM-DD
    LocalTime birthTime       // HH:mm (24-hour format)
) { }

// Response example
public record CareerTimingResponse(
   
    String favoredPeriod,  // "H1" or "H2"
    int confidenceScore,   // 0-100
    String reasoning
) { }
```

**API Response Wrapper** (ApiResponse<T>):

모든 엔드포인트는 표준화된 응답 구조 사용:

```java
public record ApiResponse<T>(
    boolean success,
    T data,
    ErrorInfo error,
    long timestamp
) { }

public record ErrorInfo(
    String code,        // e.g., "INVALID_DATE_FORMAT", "EXTERNAL_API_TIMEOUT"
    String message,
    String requestId
) { }
```

**API Endpoints**:

모든 엔드포인트는 `/api/` 하위에 배치되며, resource-based 경로 컨벤션 준수:

| 엔드포인트 | 메서드 | 설명 | Request | Response |
|-----------|--------|------|---------|----------|
| `/api/career/timing` | POST | 관운 기반 채용 시기 분석 | `CareerTimingRequest` | `ApiResponse<CareerTimingResponse>` |
| `/api/career/consultation` | POST | AI 커리어 컨설팅 | `ConsultationRequest` | `ApiResponse<ConsultationResponse>` |
| `/api/company/compatibility` | POST | 기업/직무 궁합 분석 | `CompatibilityRequest` | `ApiResponse<CompatibilityResponse>` |
| `/api/feedback/satisfaction` | POST | 사용자 만족도 피드백 제출 | `SatisfactionFeedbackRequest` | `ApiResponse<SatisfactionFeedbackResponse>` |

**External Communication**:

**클라이언트 선택 기준**:
- **Spring AI ChatClient (OpenAI 전용)**: JSON Mode, 자동 타입 매핑, 응답 구조화. LLM 호출 특화.
- **RestClient + Spring Retry (나머지 외부 API)**: 동기식 경량 HTTP 클라이언트. Reactive 오버헤드 불필요.

**상세 구성**:
- **FastAPI 호출** (사주 계산): RestClient + Spring Retry with 3-second timeout, exponential backoff (1s, 2s, 4s... max 2 retries). 예외 처리: ResourceAccessException(네트워크/타임아웃) 재시도, RestClientResponseException(4xx) 재시도 안 함, 5xx 재시도.
- **OpenAI API 호출** (커리어 컨설팅): Spring AI ChatClient + JSON Mode. 응답은 구조화된 JSON으로 자동 매핑 (Record 기반), 8-second timeout, exponential backoff retry (max 1 retry)
- **공공데이터API 호출** (기업 설립일 조회): RestClient with 5-second timeout, exponential backoff retry (max 1 retry). 조회 실패 시 사용자에게 기업 설립일을 직접 입력하도록 요청

**참고**: RestClient + Spring Retry 상세 구현은 [`architecture-guide.md#restclient--spring-retry-패턴`](../../../skills/architecture-guide.md) 참고

### Layered Architecture

**⚠️ 계층형 아키텍처 설계는 [`architecture-guide.md#계층형-아키텍처-패턴`](../../../skills/architecture-guide.md)에서 관리합니다.**

본 spec에서는 기능 요구사항만 정의합니다. 아키텍처 패턴(Service 경량화, Analyzer 분리, Mapper 패턴 등)은 공용 가이드를 참고하세요.

### Exception Handling Strategy

**⚠️ 예외 처리 전략은 [`architecture-guide.md#예외-처리-원칙`](../../../skills/architecture-guide.md)에서 관리합니다.**

본 spec에서는 API 응답 형식만 정의합니다:
- 모든 에러는 `ApiResponse<Void>` + `ErrorInfo` (code, message, requestId) 형식으로 반환
- 구체적인 예외 계층, 처리 로직, GlobalExceptionHandler 구현은 공용 가이드를 참고하세요.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Controller는 HTTP 처리만 담당. DTO 입력 → Service 위임 → DTO 응답
- **FR-001-1**: Service 계층은 PromptProvider, Analyzer, Mapper를 조합(Composition)하여 orchestration만 수행. 프롬프트, 분석, 변환 로직은 각 컴포넌트에 위임
- **FR-001-2**: Domain Model (엔티티)은 비즈니스 메서드(validate*, is*, build*)를 제공하고, Service는 엔티티의 public 메서드만 호출. getter로 필드 꺼내 로직을 짜지 말 것
- **FR-002**: Service에서 생년월일시 검증 (YYYY-MM-DD HH:mm 형식, 현실적 범위). 시간이 미상일 경우 400 Bad Request + ErrorInfo 반환
- **FR-003**: Service는 생년월일시(YYYY-MM-DD HH:mm)를 FastAPI로 전송하여 만세력 데이터 수신 (천간, 지지, 오행 등 기본 정보만. 십신·지장간은 Spring에서 계산)
- **FR-004**: Service에서 십신(十神) 및 지장간(地藏干) 계산 및 관운 분석. 일간 기준 정관/편관 판정. (자세한 계산 방식은 Clarifications 참고)
- **FR-004-1**: 관운 강도와 현재 연도의 대운 주기를 분석하여 H1(상반기) vs H2(하반기) 중 취업 유리 시기 판정
- **FR-005**: Service에서 SajuResult를 MySQL에 저장 (Entity로 영속화)
- **FR-006**: Service에서 사주 데이터(천간, 지지, 오행, 십신, 지장간 분포)를 구조화하여 Spring AI ChatClient + JSON Mode로 OpenAI API 호출. 응답은 `CareerAdviceResponse` Record에 자동 매핑
- **FR-006-1**: OpenAI 프롬프트는 사주 정보 + 컨텍스트를 명확히 포함 (예: "다음 사주를 분석하여 추천 산업 3~5개, 면접 전략, 강점 분석을 JSON 형식으로 제공하세요")
- **FR-007**: `CareerAdviceResponse`의 필드(`industries`, `interviewTips`, `strengths`)를 정규화된 엔티티(Industry, InterviewTip, Strength)로 저장
- **FR-008**: Timeout/API 실패 시 @RestControllerAdvice로 처리 (try-catch 금지)
- **FR-008-1**: RestClient 외부 API 호출 시 @Retryable 적용. 예외 분류: ResourceAccessException(네트워크/타임아웃) 재시도 대상, RestClientResponseException의 4xx(클라이언트 오류) 재시도 안 함, 5xx(서버 오류) 재시도 함. (CodeRabbit PR #9 지적사항)
- **FR-009**: 기업 설립일을 공공데이터API로 조회. 조회 실패 시 사용자 입력으로 폴백하고, 기업 설립일과 사용자 사주를 비교하여 호환성 점수 계산 (0~100 범위)
- **FR-009-1**: CompatibilityRequest에 targetRole (category: JobCategoryEnum, detailName: String) 필드 포함. category는 필수, detailName은 선택사항(응답 에코 및 프롬프트 노출용 자유 텍스트)
- **FR-009-2**: JobCategoryEnum은 직군별 주요 오행(primaryElement)과 보조 오행(secondaryElement)을 포함. `JobRoleAnalyzer`가 사용자 오행 분포와 직군 오행을 비교하여 targetRoleAnalysis (matchScore, synergy, warning) 생성. 분석 결과(targetRoleAnalysis, fiveElements, analysisBreakdown, actionableStrategy, expectedInterviewQuestions, roleCompatibility, monthlyForecast, cautions) 전체를 정규화된 자식 엔티티로 DB에 저장. CompanyCompatibility에 UNIQUE(userProfileId, companyName, targetRoleCategory) 제약 + INSERT IGNORE 패턴으로 중복 방지
- **FR-010**: 모든 API 응답은 ApiResponse<T> 래퍼 사용 (success, data, error, timestamp)
- **FR-011**: 모든 엔티티는 @Getter + @NoArgsConstructor(access=PROTECTED) + @Builder 사용 (@Data/@ToString 금지)
- **FR-012**: 모든 연관관계는 FetchType.LAZY 명시 (N+1 문제 방지)
- **FR-013**: 외부 API 호출(FastAPI, OpenAI)을 모두 로깅 (요청, 응답, 지연시간, 에러). 단, 민감 정보(birthDate, 전문 API 응답)는 DEBUG 레벨로 분리하고 운영 로그에서 제거 (CodeRabbit PR #9 보안 권고)
- **FR-014**: 사용자 만족도 피드백 수집 API 구현. 요청 시 만족도(SATISFIED/DISSATISFIED) + 관련 분석 타입(CAREER_TIMING/CONSULTATION/COMPATIBILITY) + 선택적 상세 의견(최대 500자) 수신
- **FR-015**: 피드백 저장 시 UserSatisfactionFeedback 엔티티에 영속화 (SajuResult FK, feedbackType, 만족도 상태, 상세 의견, 타임스탐프)

### Key Entities & Database Schema (정규화된 구조)

**Phase 1 - Core Entities (현재 구현)**:

| Entity | Fields | Type | Constraints |
|--------|--------|------|-------------|
| **UserProfile** | id, birthDate, birthTime, createdAt, updatedAt | Long, LocalDate, LocalTime, LocalDateTime, LocalDateTime | UNIQUE(birthDate, birthTime) |
| **SajuResult** | id, userProfileId, fullSajuData, fetchedAt | Long, Long (FK), String (JSON), LocalDateTime | FK to UserProfile, 지지별로 정규화된 관계 |

**Phase 2 - Authentication Entities (추가 예정)**:

| Entity | Fields | Type | Constraints |
|--------|--------|------|-------------|
| **User** | id, email, phone, password, createdAt, updatedAt | Long, String, String, String (bcrypt), LocalDateTime, LocalDateTime | UNIQUE(email), bcrypt 암호화 |
| (UserProfile과 User의 1:1 관계 추가 예정) | | | |

**Saju Analysis Entities**:

| Entity | Fields | Type | Constraints |
|--------|--------|------|-------------|
| **TenGodData** | id, sajuResultId, tenGodName, score, createdAt | Long, Long (FK), String, Integer, LocalDateTime | FK to SajuResult (1:N) - 십신별 행 단위 정규화 |
| **HiddenStemData** | id, sajuResultId, earthlyBranch, hiddenStem, createdAt | Long, Long (FK), String, String, LocalDateTime | FK to SajuResult (1:N), 지지별 지장간 |
| **CareerFortune** | id, sajuResultId, favoredPeriod, confidenceScore, reasoning | Long, Long (FK, UNIQUE), String, Integer, String | FK to SajuResult (1:1, UNIQUE) |

**Career Consultation Entities**:

| Entity | Fields | Type | Constraints |
|--------|--------|------|-------------|
| **CareerConsultation** | id, sajuResultId, openaiModelVersion, generatedAt | Long, Long (FK), String, LocalDateTime | FK to SajuResult (1:N) |
| **Industry** | id, careerConsultationId, name, reason | Long, Long (FK), String, String | FK to CareerConsultation (1:N) |
| **InterviewTip** | id, careerConsultationId, content | Long, Long (FK), String | FK to CareerConsultation (1:N) |
| **Strength** | id, careerConsultationId, description | Long, Long (FK), String | FK to CareerConsultation (1:N) |

**Company Compatibility Entities (정규화 구조)**:

| Entity | Fields | Type | Constraints |
|--------|--------|------|-------------|
| **CompanyCompatibility** | id, userProfileId, companyName, targetRoleCategory, targetRoleDetailName, compatibilityScore, summary, createdAt | Long, Long (FK), String, JobCategoryEnum, String, Integer, String, LocalDateTime | FK to UserProfile (1:N), **UNIQUE(userProfileId, companyName, targetRoleCategory)** |
| **TargetRoleAnalysis** | id, compatibilityId, matchScore, synergy, warning | Long, Long (FK, UNIQUE), Integer, Text, Text | FK to CompanyCompatibility (1:1, UNIQUE) |
| **FiveElementsAnalysis** | id, compatibilityId, userDistribution, companyDistribution, synergyDescription | Long, Long (FK, UNIQUE), JSON, JSON, Text | FK to CompanyCompatibility (1:1, UNIQUE) |
| **AnalysisBreakdown** | id, compatibilityId, characterMatch, potentialSynergy, longTermStability | Long, Long (FK, UNIQUE), Integer, Integer, Integer | FK to CompanyCompatibility (1:1, UNIQUE) |
| **ActionableStrategy** | id, compatibilityId, interviewKeywords, weaknessDefense, luckyDays, preferredTime | Long, Long (FK, UNIQUE), JSON, Text, JSON, String | FK to CompanyCompatibility (1:1, UNIQUE) |
| **ExpectedInterviewQuestion** | id, compatibilityId, question, intent | Long, Long (FK), Text, Text | FK to CompanyCompatibility (1:N) |
| **RoleCompatibility** | id, compatibilityId, roleName, score, reason, tag | Long, Long (FK), String, Integer, Text, String | FK to CompanyCompatibility (1:N) |
| **MonthlyForecast** | id, compatibilityId, month, score, status, advice | Long, Long (FK), Integer, Integer, Enum(LUCKY/NORMAL/CAUTION), Text | FK to CompanyCompatibility (1:N) |
| **Caution** | id, compatibilityId, content | Long, Long (FK), Text | FK to CompanyCompatibility (1:N) |

**Feedback Entity**:

| Entity | Fields | Type | Constraints |
|--------|--------|------|-------------|
| **UserSatisfactionFeedback** | id, sajuResultId, feedbackType, satisfactionStatus, feedbackContent, createdAt | Long, Long (FK), Enum, Enum, String, LocalDateTime | FK to SajuResult (1:N), feedbackContent nullable (VARCHAR 500), index (sajuResultId, createdAt). 제약: feedbackContent @Size(max=500) (CodeRabbit PR #9 반영) |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 관운 분석 요청 완료까지 5초 이내 (FastAPI 지연 제외 = Controller → Service → DB)
- **SC-002**: AI 커리어 컨설팅 완료까지 15초 이내 (OpenAI 지연 + 재시도 포함)
- **SC-003**: 시스템이 동시 5,000명 사용자 처리 (Connection Pool 기본값으로)
- **SC-004**: 기업 궁합 계산 8초 이내 완료
- **SC-005**: 사용자가 생성된 권고사항을 "유용" 이상으로 평가 (90% 목표)
- **SC-006**: 외부 API 실패 시 0% 미처리 예외 (모두 GlobalExceptionHandler에서 처리)
- **SC-007**: DB 쿼리 N+1 이슈 0건 (FetchType.LAZY 강제)
- **SC-008**: 입력 유효성 검사 실패 시 400 Bad Request + 명확한 ErrorInfo
- **SC-009**: 만족도 피드백 저장 1초 이내 완료 (피드백 수집은 경량)

## Assumptions

- 생년월일시(YYYY-MM-DD HH:mm) 입력은 사용자 정확도를 신뢰 (검증은 형식만). 사주 명리학의 정확한 분석을 위해 분(minute) 단위까지 지원
- FastAPI 서비스는 정상 조건에서 <3초 응답
- OpenAI API는 <8초 응답
- 기업 설립일은 사용자 입력 또는 외부 DB 조회 (MVP에서는 사용자 제공)
- **오행/십신/지장간 계산 역할 분담**: FastAPI는 만세력 기본 데이터(천간, 지지, 오행)만 제공 → Spring 백엔드에서 `TenGodCalculator` + `HiddenStemCalculator`를 통해 십신(十神) 및 지장간(地藏干) 계산 → 더 정확한 오행 분포 도출
- 사용자는 취업 준비 전문직 (미성년 보호 불필요)
- **Phase 1에서 인증/인가 없음**: 모든 API 공개 제공. Phase 2에서 JWT 기반 인증 추가
- **Phase 1 OpenAI 호출 제한 없음**: 비용 관리는 환경 변수(API Key)로 수동 제어. Phase 2에서 사용자당 일일 한도(예: 5회/일) 방식으로 자동 제한 도입
- **Phase 1 데이터 보관 정책**: 무제한 보관. Phase 2에서 법적/운영 요건에 따라 보관 정책 수립
- **Phase 1 SLA 없음**: Best Effort 운영. Phase 2에서 실제 운영 경험을 바탕으로 Uptime 목표 수립
- 그레고리력 기준 (음력/대리력 미지원)

## Technology Stack & Dependencies

- **Spring Framework 버전**: Spring Boot 4.0.5
- **Spring AI**: ChatClient 기반 OpenAI 통합 (JSON Mode, 자동 재시도, 타입 안전 매핑)
- **외부 서비스 의존성**:
  - FastAPI (필수, 사주 계산)
  - OpenAI API (필수, 커리어 컨설팅)
  - 공공데이터API (기업 설립일 조회, 실패 시 사용자 입력 폴백)
  - 외부 서비스 다운 시 Graceful Error 반환 + 재시도 안내
- **데이터베이스**: MySQL 8.0+ (JSON 컬럼 지원 필수)

## Technology Constraints (Phase 1)

- **캐싱 금지**: Redis, In-Memory 전역 캐시 사용 금지. 도메인 로직 정확성 우선
- **외부 API 호출 방식**: OpenAI 호출은 Spring AI ChatClient 사용. 그 외 외부 API(FastAPI, 공공데이터API)는 RestClient + Spring Retry 사용

## Future Features (Phase 2+)

### User Authentication & Authorization

사용자 인증 및 권한 관리 시스템입니다. **Phase 2에서 추가**될 예정이며, 이를 기반으로 관리자 페이지 등의 보안이 필요한 기능들을 구현할 수 있습니다.

**주요 기능**:

1. **사용자 회원가입 및 로그인**
   - 이메일/비밀번호 기반 인증
   - JWT 토큰 기반 세션 관리
   - 비밀번호 암호화 (bcrypt)

2. **역할 기반 접근 제어 (RBAC)**
   - 일반 사용자 (USER): 커리어 상담 기능 접근
   - 관리자 (ADMIN): 관리자 대시보드 및 통계 접근

3. **보안 기능**
   - 토큰 갱신 (Refresh Token)
   - 로그아웃 및 세션 만료
   - 권한 없음 요청에 대한 403 Forbidden 응답

**구현 예상 시점**:
- Career Fortune API (Phase 1) 안정화 후
- Admin Dashboard 구현 전

**참고**: 현재 Phase 1에서는 인증 없이 모든 API를 공개합니다.

---

### Admin Dashboard & Usage Analytics

관리자 페이지는 서비스 이용 현황을 추적하고 통계 데이터를 시각화하는 기능입니다. **Phase 2에서 인증 시스템 구현 이후** 추가될 예정입니다.

**주요 기능**:

1. **사용자 및 API 호출 통계**
   - 일일/주간/월간 활성 사용자 수
   - 각 API 엔드포인트별 호출 횟수 및 성공/실패율
   - 평균 응답 시간 및 에러 분포

2. **서비스 이용 현황**
   - 관운 분석 요청 건수 및 선호 시기(H1 vs H2 분포)
   - AI 커리어 컨설팅 요청 건수 및 선호 산업군 통계
   - 기업 궁합 분석 요청 건수 및 평균 호환성 점수

3. **외부 API 성능 모니터링**
   - FastAPI 호출 지연 분포 및 타임아웃 발생률
   - OpenAI API 응답 시간 및 토큰 사용 현황
   - 공공데이터API 조회 성공률 및 폴백 빈도

4. **사용자 만족도 및 피드백**
   - 각 기능별(관운 분석/AI 컨설팅/기업 궁합) 만족도 비율 (만족함/만족하지 않음)
   - 만족도 추이 분석 (일일/주간/월간)
   - 시스템 오류 리포트 및 해결 현황

   **참고**: Phase 1에서 사용자 만족도 데이터는 UserSatisfactionFeedback 테이블에 저장되며, Phase 2에서 이 데이터를 시각화하여 관리자 대시보드에 표시합니다.

**구현 예상 시점**:
- 사용자 인증/인가 시스템 완성 후
- 서비스 기본 기능(P1/P2)의 안정화 이후

**참고**: 현재 Phase 1에서는 기본 서비스 기능 구현에 집중하며, 관리자 페이지 개발은 **별도 feature 명세(Phase 2)** 작성 후 진행됩니다.
