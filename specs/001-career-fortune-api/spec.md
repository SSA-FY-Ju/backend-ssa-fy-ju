# Feature Specification: Career Fortune & Consultation API

**Feature Branch**: `feat/career-fortune-api`
**Created**: 2026-04-10
**Status**: Draft
**Input**: SSAju career consultation service specification with technical architecture

## Overview

SSAju는 사주 명리학의 관성(정관/편관) 데이터를 활용해 취업 준비생에게 최적의 직무, 합격 시기, 기업 궁합을 제안하는 커리어 특화 백엔드 서비스입니다. 3가지 핵심 기능을 제공합니다:

1. **관운 기반 합격 시기 분석**: 정관/편관 흐름으로 상/하반기 취업 유리 시기 예측
2. **AI 커리어 컨설팅**: 오행과 십신 분포로 추천 산업군(3~5개), 면접 전략, 강점 분석
3. **기업/직무 궁합**: 사용자 사주와 기업 설립일 사주 대조로 궁합 점수 및 추천 포지션

## Clarifications

### Session 2026-04-10 (Initial)

- Q: REST API 엔드포인트 경로는 어떻게 정의할 것인가? → A: Resource-based 경로 사용 (`POST /api/career/timing`, `POST /api/career/consultation`, `POST /api/company/compatibility`)
- Q: 불완전한 날짜 입력(월/일 미상)을 어떻게 처리할 것인가? → A: 완전한 YYYY-MM-DD 형식만 허용, 불완전하면 400 Bad Request 반환
- Q: SajuResult와 CareerConsultation의 관계 + 기업 정보 저장 방식은? → A: SajuResult 재사용 + 기업은 요청마다 공공데이터API 조회 (CompanyCompatibility는 계산 결과만 저장)
- Q: H1/H2 판정 로직은 어디서 구현할 것인가? → A: FastAPI는 만세력 원시 데이터만 반환, Spring에서 십신 계산 및 관운 분석으로 H1/H2 판정
- Q: OpenAI API 호출 시 응답 구조화 및 구현 방식은? → A: JSON Mode 사용 + Spring AI 도입으로 타입 안전 JSON 매핑 및 에러 처리 자동화

### Session 2026-04-10 (Clarification Phase)

- Q: Phase 1에서 API 인증 정책은? → A: 인증 없이 모든 API 공개 제공. Phase 2에서 JWT 기반 인증 추가 예정.
- Q: OpenAI 호출 빈도 제어 정책은? → A: Phase 1에서는 제한 없음. Phase 2에서 사용자당 일일 한도(예: 하루 5회) 방식으로 제한 도입 예정.
- Q: 사용자 사주 결과 및 AI 컨설팅 기록 보관 정책은? → A: Phase 1에서는 무제한 보관. Phase 2에서 법적/운영 요건에 따라 보관 정책(예: 1년/6개월) 수립 예정.
- Q: 서비스 신뢰도(Uptime SLA) 목표는? → A: Phase 1에서는 SLA 정의 없음 (Best Effort 운영). Phase 2에서 실제 운영 경험을 바탕으로 SLA 수립 예정.
- Q: 사용자 만족도 피드백 방식은? → A: 사주 분석 완료 후 간단한 이진 평가(만족함/만족하지 않음)만 수집. Phase 1에서 수집 기능 구현, Phase 2에서 관리자 대시보드를 통한 통계 시각화.

### Session 2026-04-10 (Clarification Phase - Continued)

- Q: UserSatisfactionFeedback의 분석 결과 추적 방식은? → A: SajuResultId FK만 저장. 모든 분석(관운/컨설팅/궁합)이 SajuResult 기반이므로, SajuResult를 통해 추적 가능. feedbackType(ENUM)으로 어떤 분석의 피드백인지 명시.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Career Timing Analysis (Priority: P1)

취업 준비생이 생년월일을 입력하고 관운(官運) 데이터를 기반으로 상반기/하반기 중 취업에 유리한 시기를 파악합니다.

**Why this priority**: 핵심 가치 제안. 언제 집중적으로 취업 활동해야 하는지는 사주 기반 조언의 기초입니다.

**Independent Test**: 생년월일 입력 → FastAPI 연동으로 관운 계산 → 유리한 채용 시기 반환. 다른 기능 없이 독립적으로 동작합니다.

**Acceptance Scenarios**:

1. **Given** 완전한 생년월일(YYYY-MM-DD) 입력, **When** 관운 분석 요청, **Then** 유리한 채용 시기(H1/H2) + 신뢰도 점수 반환
2. **Given** 정관/편관 데이터 조회, **When** 상/하반기 단위 분석, **Then** 채용 확률 높은 시기와 이유 제시

---

### User Story 2 - AI Career Consulting (Priority: P1)

취업 준비생이 사주 데이터(천간/지지)를 제출하고 OpenAI API를 통한 AI 커리어 컨설턴트로부터 개인 맞춤 조언(추천 산업, 면접 전략, 강점)을 받습니다.

**Why this priority**: 직접 수익화 가능한 기능. 전문가 수준의 맞춤 조언으로 경쟁 차별화.

**Independent Test**: 사주 데이터 입력 → OpenAI API 호출 → 구조화된 권고사항(산업, 면접팁, 강점) 반환. P1/P3 없이도 완전히 동작합니다.

**Acceptance Scenarios**:

1. **Given** 완전한 사주 데이터(5 천간 + 12 지지 + 오행/십신 분포), **When** AI 커리어 컨설팅 요청, **Then** JSON 형식으로 3~5개 산업 추천 + 면접 전략 + 강점 분석 반환
2. **Given** 느린 외부 API(OpenAI), **When** Timeout 초과, **Then** 정중한 오류 메시지 + 재시도 안내 반환
3. **Given** 유효한 사주 데이터, **When** 컨설팅 응답, **Then** 타임스탬프 + AI 모델 버전 메타데이터 포함

---

### User Story 3 - Company & Job Fit Analysis (Priority: P2)

취업 준비생이 목표 기업의 설립일(사주)과 자신의 생년월일을 입력하여 궁합 점수(0~100)와 추천 직무를 얻습니다.

**Why this priority**: 보조 기능. 기업 선택 신뢰도를 높이지만, P1 기능(타이밍+컨설팅)이 핵심가치입니다.

**Independent Test**: 사용자 사주 + 기업 설립일 → 궁합 계산 → 호환성 점수 + 추천 직무 반환. P1/P2 불필요합니다.

**Acceptance Scenarios**:

1. **Given** 사용자 사주 + 기업 설립일(YYYY-MM-DD), **When** 호환성 분석, **Then** 호환성 점수(0~100) + 추천 직무 + 정렬 이유 반환
2. **Given** 유효한 두 사주 데이터, **When** 매칭 실행, **Then** 응답에 신뢰도 수준 포함

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

- **불완전한 날짜 입력**: 생년월일과 기업 설립일 모두 완전한 YYYY-MM-DD 형식 필수. 월/일 미상 시 400 Bad Request 반환 (상세 ErrorInfo 제시)
- **기업 설립일 조회 실패**: 공공데이터API 조회 실패 시 사용자에게 기업 설립일을 직접 입력하도록 요청 (폴백 시나리오)
- 외부 API(FastAPI, OpenAI, 공공데이터API) 일시 다운 상황 → Graceful Error 반환 + 재시도 안내
- 수천 명의 동시 요청 처리 → Connection Pool로 안정성 보장

## Technical Architecture

### System Communication Flow (Synchronous JSON)

```
┌─ Job Seeker ─┐
│   (Client)   │
└──────┬────────┘
       │ HTTP JSON
       ▼
┌─────────────────────────────────────┐
│     Spring Boot Backend (SSAju)     │
│  - 비즈니스 로직, 데이터 저장, API  │
│  - MySQL via Spring Data JPA       │
└──┬────────────────┬─────────────┬───┘
   │ WebClient      │ OpenAI      │ Public Data API
   │ (JSON)         │ API Key     │ (Company Info)
   ▼                ▼             ▼
┌──────────────┐  ┌────────────┐  ┌──────────────────────┐
│ FastAPI      │  │ OpenAI     │  │ Public Data Service  │
│ (Saju Calc)  │  │ (ChatGPT)  │  │ (Company Founding    │
│              │  │            │  │  Date Lookup)        │
└──────────────┘  └────────────┘  └──────────────────────┘
```

### Data Modeling & Entity Relationship

**Key Entities**:
- **User**: 사용자 신원 및 연락처 (Phase 2)
- **UserProfile**: 생년월일, 사주 분석 결과 참조
- **SajuResult**: 사주 상세 정보 (천간, 지지, 오행, 십신, 관운 데이터, FastAPI 전체 응답)
  - **FastAPI 응답 포함**: year_pillar, month_pillar, day_pillar, hour_pillar, year_stem, year_branch, month_stem, month_branch, day_stem, day_branch, hour_stem, hour_branch, birth_time, solar_correction (city, longitude, utc_offset, etc.) 등
- **CareerConsultation**: AI 생성 권고사항 (산업, 면접팁, 강점, OpenAI 메타데이터). SajuResult 외래키로 참조하여 어떤 사주 데이터 기반 생성인지 추적
- **CompanyCompatibility**: 사용자 사주와 기업 궁합 점수 및 추천 직무 (기업 정보는 요청 시 공공데이터API로 조회, 설립일 미상 시 사용자 입력으로 폴백)
- **UserSatisfactionFeedback**: 사용자 만족도 피드백 (만족함/만족하지 않음). SajuResult와 연관되어 어떤 분석 결과에 대한 피드백인지 추적

**Mapping Strategy** (CLAUDE.md 준수):
- **불변 사주 개념 → Java Enum**: 천간(HeavenlyStem), 지지(EarthlyStem), 오행(FiveElement), 십신(TenGod)
- **동적/복잡 데이터 → MySQL JSON 컬럼** (@JdbcTypeCode): 십신 배치, 추천 산업 목록, 면접 전략 등
- **모든 연관관계 → FetchType.LAZY**:
  - UserProfile ↔ SajuResult (1:1)
  - User ↔ UserProfile (1:1)
  - SajuResult ↔ CareerConsultation (1:N) - CareerConsultation은 특정 SajuResult 기반으로 생성된 권고사항 추적
  - User ↔ CompanyCompatibility (1:N) - 기업 정보는 요청 시 공공데이터API 조회, CompanyCompatibility는 계산 결과만 저장
  - SajuResult ↔ UserSatisfactionFeedback (1:N) - 특정 사주 분석(관운/컨설팅/궁합) 결과에 대한 피드백 추적

### API Design & Response Format

**DTO & Record Types** (Java 21 표준):

모든 요청/응답 객체는 `record` 타입 사용:

```java
// Request example
public record CareerTimingRequest(LocalDate birthDate) { }

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
- **FastAPI 호출** (사주 계산): WebClient with 3-second timeout, exponential backoff retry (max 2 retries)
- **OpenAI API 호출** (커리어 컨설팅): Spring AI ChatClient + JSON Mode. 응답은 구조화된 JSON으로 자동 매핑 (Record 기반), 8-second timeout, exponential backoff retry (max 1 retry)
- **공공데이터API 호출** (기업 설립일 조회): WebClient with 5-second timeout, exponential backoff retry (max 1 retry). 조회 실패 시 사용자에게 기업 설립일을 직접 입력하도록 요청

### Layered Architecture (CLAUDE.md 준수)

| Layer | Responsibility | Examples |
|-------|----------------|----------|
| **Controller** | HTTP 요청/응답 처리, DTO↔Entity 변환 | CareerTimingController, ConsultationController |
| **Service** | 모든 비즈니스 로직 (데이터 변환, 외부 API 조율, 계산) | CareerFortuneService, ConsultationService, CompanyMatchingService |
| **Repository** | DB 접근만 담당 (Spring Data JPA) | UserRepository, SajuResultRepository, CompanyCompatibilityRepository |
| **Global Exception Handler** | @RestControllerAdvice로 모든 예외 처리 (try-catch 금지) | SajuGlobalExceptionHandler |

### Exception Handling Strategy

@RestControllerAdvice + 커스텀 예외 계층:

```
SajuException (root)
├── InvalidSajuDataException (입력 유효성)
├── FastAPITimeoutException (외부 API 지연)
├── OpenAIApiException (LLM 호출 실패)
└── DataAccessException (DB 오류)

→ GlobalExceptionHandler에서 catch
→ ApiResponse<T> with ErrorInfo 반환
```

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Controller는 HTTP 처리만 담당. DTO 입력 → Service 위임 → DTO 응답
- **FR-002**: Service에서 생년월일 검증 (YYYY-MM-DD 형식, 현실적 범위)
- **FR-003**: Service는 생년월일을 FastAPI로 전송하여 만세력 데이터 수신 (천간, 지지, 오행 등 기본 사주 정보)
- **FR-004**: Service에서 만세력 데이터를 기반으로 십신(十神) 계산. 일간을 기준으로 월간을 분석하여 정관(正官)/편관(偏官) 판정 및 관운 강도 평가
- **FR-004-1**: 관운 강도와 현재 연도의 대운 주기를 분석하여 H1(상반기) vs H2(하반기) 중 취업 유리 시기 판정
- **FR-005**: Service에서 SajuResult를 MySQL에 저장 (Entity로 영속화)
- **FR-006**: Service에서 사주 데이터(천간, 지지, 오행, 십신 분포)를 구조화하여 Spring AI ChatClient + JSON Mode로 OpenAI API 호출. 응답은 `CareerAdviceResponse` Record에 자동 매핑
- **FR-006-1**: OpenAI 프롬프트는 사주 정보 + 컨텍스트를 명확히 포함 (예: "다음 사주를 분석하여 추천 산업 3~5개, 면접 전략, 강점 분석을 JSON 형식으로 제공하세요")
- **FR-007**: `CareerAdviceResponse`의 필드(`industries`, `interviewTips`, `strengths`)를 JSON 컬럼에 저장
- **FR-008**: Timeout/API 실패 시 @RestControllerAdvice로 처리 (try-catch 금지)
- **FR-009**: 기업 설립일을 공공데이터API로 조회. 조회 실패 시 사용자 입력으로 폴백하고, 기업 설립일과 사용자 사주를 비교하여 호환성 점수 계산 (0~100 범위)
- **FR-010**: 모든 API 응답은 ApiResponse<T> 래퍼 사용 (success, data, error, timestamp)
- **FR-011**: 모든 엔티티는 @Getter + @NoArgsConstructor(access=PROTECTED) + @Builder 사용 (@Data/@ToString 금지)
- **FR-012**: 모든 연관관계는 FetchType.LAZY 명시 (N+1 문제 방지)
- **FR-013**: 외부 API 호출(FastAPI, OpenAI)을 모두 로깅 (요청, 응답, 지연시간, 에러)
- **FR-014**: 사용자 만족도 피드백 수집 API 구현. 요청 시 만족도(SATISFIED/DISSATISFIED) + 관련 분석 타입(CAREER_TIMING/CONSULTATION/COMPATIBILITY) 수신
- **FR-015**: 피드백 저장 시 UserSatisfactionFeedback 엔티티에 영속화 (SajuResult FK, feedbackType, 만족도 상태, 타임스탐프)

### Key Entities & Database Schema

| Entity | Fields | Type | Constraints |
|--------|--------|------|-------------|
| **User** | id, email, phone, createdAt | | PK, UNIQUE(email) |
| **UserProfile** | id, userId, birthDate, createdAt, updatedAt | | PK, FK to User, UNIQUE(userId) |
| **SajuResult** | id, userProfileId, heavenlyStems[], earthlyBranches[], fiveElements, tenGods, careerFortune, fetchedAt | Enums + JSON | PK, FK to UserProfile, @JdbcTypeCode for JSON |
| **CareerConsultation** | id, sajuResultId, industries[], interviewTips, strengths, openaiModelVersion, generatedAt | JSON columns | PK, FK to SajuResult, timestamp |
| **CompanyCompatibility** | id, userProfileId, companyName, compatibilityScore, recommendedRoles[], createdAt | INT + JSON | PK, FK to UserProfile, composite index on (userProfileId, companyName) |
| **UserSatisfactionFeedback** | id, sajuResultId, feedbackType, satisfactionStatus, createdAt | FK + Enum + ENUM | PK, FK to SajuResult, index on (sajuResultId, createdAt) |

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

- 생년월일 입력은 사용자 정확도를 신뢰 (검증은 형식만)
- FastAPI 서비스는 정상 조건에서 <3초 응답
- OpenAI API는 <8초 응답
- 기업 설립일은 사용자 입력 또는 외부 DB 조회 (MVP에서는 사용자 제공)
- 오행/십신 계산은 전적으로 FastAPI 담당 (Spring 백엔드는 결과만 수신/저장)
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
- **WebClient vs Spring AI**: OpenAI 호출은 Spring AI ChatClient 사용. 그 외 외부 API(FastAPI, 공공데이터API)는 WebClient 사용

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
