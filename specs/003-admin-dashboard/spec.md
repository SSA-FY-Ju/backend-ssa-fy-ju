# Feature Specification: 관리자 대시보드 및 모니터링 시스템

**Feature Branch**: `003-admin-dashboard`

**Created**: 2026-06-05

**Status**: Draft

**Input**: 관리자가 로그인 후 실시간으로 서비스 현황을 모니터링하고 유저/데이터를 관리하기 위한 4가지 통합 관리 화면

## User Scenarios & Testing *(mandatory)*

### User Story 0 - 관리자 로그인 (Priority: P0) ⚠️ 필수 선행

관리자는 ROLE=ADMIN 자격으로 로그인 폼에서 이메일과 비밀번호를 입력하여 인증받고,
JWT AccessToken을 발급받아 관리자 대시보드 및 모든 관리자 화면에 접근할 수 있습니다.

**Why this priority**: 모든 관리자 화면 접근의 필수 선행 조건. 이 기능이 완료되어야 다른 모든 관리자 기능 진행 가능

**Independent Test**: 관리자 계정(ROLE=ADMIN)으로 로그인 → AccessToken 발급 → /admin 페이지 접근 확인

**Acceptance Scenarios**:

1. **Given** 관리자 로그인 폼에 접근 **When** ADMIN 권한을 가진 계정으로 이메일/비밀번호 입력 후 제출 **Then** AccessToken(1시간 유효) 및 RefreshToken(7일 유효) 발급받고 프론트엔드가 /admin/dashboard로 이동

2. **Given** USER 권한의 계정 **When** 유효한 JWT 토큰으로 /admin/** 엔드포인트 접근 시도 **Then** 서버는 `403 Forbidden` 응답 반환
   - 에러코드: `AUTH-003`
   - 메시지: "접근 권한이 없습니다."
   - 이유: JWT 토큰은 정상이나 SecurityConfig의 @PreAuthorize("hasRole('ADMIN')")에서 차단

3. **Given** 인증받지 않은 사용자(JWT 토큰 없음) **When** /admin/** 페이지 직접 접근 시도 **Then** 서버는 `401 Unauthorized` 응답 반환
   - 에러코드: `AUTH-001`
   - 메시지: "인증이 필요합니다."
   - 책임 분리: 서버는 401만 반환, 프론트엔드에서 /admin/login으로 리다이렉트 처리

4. **Given** 로그인한 관리자 **When** 로그아웃 버튼 클릭 **Then** 다음이 순차적으로 실행
   - RefreshToken을 DB에서 revoked 처리 (삭제)
   - AccessToken은 클라이언트에서 삭제 (Stateless JWT이므로 서버는 처리 불가)
   - 프론트엔드가 /admin/login으로 리다이렉트

---

### User Story 1 - 관리자가 서비스 현황을 한눈에 파악 (Priority: P1)

관리자는 로그인 후 오늘의 서비스 상태를 즉시 파악할 수 있는 대시보드를 본다.
- 오늘 생성된 분석 건수 및 유형별 비율(사주/관운/궁합)
- 일일 제한(3회)을 모두 소진한 유저 수
- 유저 만족도 조사 결과 및 미확인 피드백 개수

**Why this priority**: 관리자의 일일 업무 시작 시 서비스 상태 파악은 필수. 서비스 건강도를 빠르게 감지하고 CS 대응 우선순위를 결정하는 핵심 지표입니다.

**Independent Test**: 대시보드 화면만 렌더링되면 오늘의 분석 현황을 실시간으로 모니터링할 수 있으며, CS 팀이 사용량 경고를 토대로 유저 제한 완화 여부를 판단할 수 있습니다.

**Acceptance Scenarios**:

1. **Given** 관리자가 로그인 후 대시보드에 접근 **When** 페이지 로드 **Then** 오늘의 분석 건수(총합) 및 사주/관운/궁합 유형별 건수를 표시
2. **Given** 대시보드 화면 **When** 새로고침 **Then** 최신 데이터로 업데이트되며, 일일 제한 모두 소진 유저 수를 표시
3. **Given** 대시보드 화면 **When** 피드백 요약 섹션 조회 **Then** 만족/불만족 비율 및 미확인 상세 피드백 개수를 표시

---

### User Story 2 - 관리자가 유저를 검색 및 관리 (Priority: P1)

관리자는 이메일, 이름, 가입일, 상태(활성/탈퇴)로 유저를 검색할 수 있으며,
특정 유저의 상세 프로필(기본 정보, 총 분석 횟수)을 조회할 수 있다.
또한 Soft Delete 처리된 탈퇴 유저의 마스킹된 이메일과 탈퇴 일시를 확인할 수 있다.

**Why this priority**: 유저 관리는 보안과 고객 관계 관리의 핵심. 악성 행동 차단 및 CS 문의 대응(예: 중복 가입 확인, 구독 이력 추적)에 필수적입니다.

**Independent Test**: 유저 목록/검색 화면과 상세 프로필 조회만으로도 유저를 식별·분류·관리할 수 있으며, Soft Delete 처리된 탈퇴 유저도 별도로 조회할 수 있습니다.

**Acceptance Scenarios**:

1. **Given** 유저 관리 페이지 **When** 이메일로 검색 **Then** 해당 유저 정보 표시 (이름, 가입일, 상태)
2. **Given** 검색 결과의 특정 유저 **When** 클릭 **Then** 상세 프로필 표시 (기본 정보, 총 분석 횟수)
3. **Given** 유저 목록 **When** 상태 필터를 '탈퇴'로 설정 **Then** Soft Delete된 유저만 표시 (마스킹된 이메일: deleted_123_..., 탈퇴 일시 포함)

---

### User Story 3 - 관리자가 전체 분석 기록을 모니터링 (Priority: P2)

관리자는 서비스 전체에서 일어난 분석 로그를 최신순으로 조회하고,
특정 분석 기록의 AI 생성 JSON 데이터(사주/관운/궁합)를 원문으로 확인할 수 있으며,
CS 문의 대응을 위해 특정 유저의 DailyApiUsage 카운트를 수동으로 차감/리셋할 수 있다.

**Why this priority**: 데이터 검증(JSON 규격, 텍스트 인코딩)과 CS 대응(일일 제한 수동 초기화)은 서비스 안정성과 고객 만족도를 높이는 중요한 기능입니다.

**Independent Test**: 분석 히스토리 조회와 데이터 확인 기능만으로도 AI 결과의 품질을 검증할 수 있으며, 일일 제한 초기화 기능으로 고객 불만을 해결할 수 있습니다.

**Acceptance Scenarios**:

1. **Given** 통합 분석 기록 관리 페이지 **When** 로드 **Then** 전체 분석 히스토리가 최신순으로 표시 (분석 유형, 유저, 생성 일시)
2. **Given** 분석 히스토리의 특정 기록 **When** 클릭 **Then** AI 생성 JSON 데이터를 원문으로 표시 (사주/관운/궁합 구조 확인 가능)
3. **Given** 분석 기록 상세 화면 **When** 텍스트 인코딩 확인 **Then** 한글 등 특수 문자가 올바르게 렌더링
4. **Given** 특정 유저의 분석 기록 **When** "일일 제한 수동 초기화" 버튼 클릭 **Then** 리셋(0으로) 또는 차감(N개) 옵션을 선택하고 저장하는 폼이 나타남

---

### User Story 4 - 관리자가 유저 피드백을 수집 및 분석 (Priority: P2)

관리자는 UserSatisfactionFeedback 엔티티를 통해 사주/관운/궁합별 만족도 점수 통계를 조회하고,
유저가 남긴 주관식 피드백(feedback_content)을 읽으면서,
특정 피드백에서 클릭 한 번으로 해당 유저가 받았던 실제 사주 분석 결과 화면으로 이동할 수 있다.

**Why this priority**: 피드백 수집 및 분석은 서비스 개선의 데이터 기반. 만족도 추이와 구체적인 불만 사항을 파악하여 제품 로드맵을 수립할 수 있습니다.

**Independent Test**: 피드백 목록과 만족도 통계 조회만으로도 유저 의견을 체계적으로 수집할 수 있으며, 매핑 링크로 실제 분석 결과와의 연관성을 파악할 수 있습니다.

**Acceptance Scenarios**:

1. **Given** 유저 피드백 관리 페이지 **When** 로드 **Then** 사주/관운/궁합별 만족도 점수와 응답 수 표시 (평균, 분포)
2. **Given** 피드백 목록 **When** 스크롤/필터 **Then** 유저가 남긴 주관식 피드백 표시 (내용, 작성자, 분석 유형)
3. **Given** 특정 피드백 **When** "분석 결과 보기" 링크 클릭 **Then** 해당 유저가 받았던 실제 사주 분석 결과 화면으로 이동

---

### Edge Cases

- 대시보드가 로드되는 동안 새로운 분석 기록이 생성되면 어떻게 표시되는가? (실시간 업데이트 vs 수동 새로고침)
- 일일 제한이 자정에 리셋될 때 대시보드가 이를 즉시 반영하는가?
- 탈퇴 유저(Soft Delete)의 마스킹된 이메일이 충돌할 수 있는가? (deleted_123_... 형식의 고유성 보장)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST 오늘 생성된 전체 분석 건수를 유형별(사주/관운/궁합)로 집계하여 대시보드에 표시
- **FR-002**: System MUST 현재 Asia/Seoul 시간 기준으로 일일 제한(3회)을 모두 소진한 유저 수를 계산하여 표시
- **FR-003**: System MUST UserSatisfactionFeedback 엔티티로부터 만족/불만족 비율 및 미확인 피드백 개수를 조회
- **FR-004**: System MUST 이메일, 이름, 가입일, 상태(활성/탈퇴)로 유저를 검색할 수 있는 필터링 기능 제공
- **FR-005**: System MUST 특정 유저의 상세 프로필(기본 정보, 총 분석 횟수)을 표시
- **FR-006**: System MUST Soft Delete된 탈퇴 유저를 별도로 필터링하여 마스킹된 이메일(deleted_{userId}_{epochSecond}@deleted.local 형식) 및 탈퇴 일시(deletedAt)를 관리자가 시각적으로 구분하여 표시
- **FR-007**: System MUST 서비스 전체 분석 히스토리를 최신순으로 조회하는 기능 제공
- **FR-008**: System MUST 특정 분석 기록의 AI 생성 JSON 데이터(사주/관운/궁합)를 원문으로 확인하는 기능 제공
- **FR-009**: System MUST JSON 데이터의 텍스트 인코딩이 올바른지 관리자가 시각적으로 검증할 수 있도록 한글 등 특수 문자를 올바르게 렌더링
- **FR-010**: System MUST 특정 유저의 DailyApiUsage 카운트를 수동으로 조정하는 인터페이스 제공 (CS용). 관리자는 두 가지 옵션 중 선택: (1) 완전 리셋(UsageCount를 0으로 설정), (2) 특정 개수만큼 차감 (단, usageCountAfter >= 0 보장). 현재 카운트보다 큰 차감을 요청할 시 카운트를 0으로 고정하고 조정된 값을 반환하며, 모든 조정은 감사 로그에 기록됨.
- **FR-011**: System MUST 사주/관운/궁합별 만족도 점수 통계(평균, 분포, 응답 수)를 조회 가능하게 함
- **FR-012**: System MUST 유저가 남긴 주관식 피드백(feedback_content)을 목록으로 표시하고 검색/필터 가능
- **FR-013**: System MUST 특정 피드백에서 클릭 한 번으로 해당 유저가 받았던 실제 사주 분석 결과 화면으로 이동하는 링크 제공

### Key Entities

- **SajuAnalysis** (코드: SajuResult): 사주 분석 기록 (ID, UserId, CreatedAt, JsonData, AnalysisType)
- **UserSatisfactionFeedback**: 만족도 피드백 (ID, UserId, SajuAnalysisId, SatisfactionScore, FeedbackContent, CreatedAt)
- **DailyApiUsage**: 일일 API 사용 현황 (ID, UserId, Date, UsageCount)
- **User**: 유저 정보 (ID, Email, Name, Status, CreatedAt, DeletedAt)

### Entity Mapping Reference (Spec vs. Codebase)

스펙 문서와 실제 코드베이스 간 엔티티명 매핑:

| Spec 문서 | 실제 코드베이스 | 필드 매핑 | 설명 |
|-----------|---------------|---------|------|
| **SajuAnalysis** | **SajuResult** | - | 사주 분석 결과 저장소 |
| id | id | PK | 분석 기록 ID |
| userId | userId | FK to User | 분석을 요청한 유저 |
| createdAt | fetchedAt | 타임스탐프 | 분석 생성/조회 시점 |
| jsonData | payload | JSON 문자열 | AI 생성 JSON 원문 |
| analysisType | analysisType | ENUM | SAJU, GWANWUN, GUNG_HAP |

**사용 원칙**:
- **API/DTO**: SajuAnalysis로 표현 (스펙 기준, 외부 계약)
- **Repository**: SajuResult 엔티티 사용 (코드 기준, 내부 구현)
- **구현자**: 이 매핑표를 참고하여 API 응답 시 SajuAnalysis 필드명 사용

## Clarifications

### Session 2026-06-05

- Q: 일일 제한 수동 초기화 시 차감과 리셋 중 어느 것을 지원하는가? → A: 둘 다 지원. 관리자가 선택 가능: 완전 리셋(0으로) 또는 특정 개수 차감.

## Assumptions

- **관리자 인증**: 관리자는 User Management (Phase 2)의 ROLE=ADMIN 기반 JWT 로그인을 통해 인증
  - User Management에서 로그인 시 AccessToken(1시간) + RefreshToken(7일) 발급
  - ROLE=ADMIN인 사용자만 /admin 페이지 및 관리자 API 접근 가능 (Spring Security @PreAuthorize)
  - ROLE != ADMIN 사용자가 접근 시 로그인 페이지로 리다이렉트
- **기존 엔티티 재사용**: SajuAnalysis, UserSatisfactionFeedback, DailyApiUsage 등 필요한 엔티티는 이미 구현되어 있거나 명세서에 정의됨
- **타임리프 기반 구현**: 관리자 페이지는 타임리프 템플릿으로 렌더링되며, 별도의 프론트엔드 프레임워크(React, Vue)는 사용하지 않음
- **실시간 데이터**: 대시보드의 분석 현황 데이터는 수동 새로고침 기반이며, WebSocket 등 실시간 푸시는 v1에서 제외
- **데이터 제한**: 분석 히스토리, 피드백은 페이지네이션 또는 기간 필터(최근 30일 등)로 조회 범위 제한
- **Soft Delete 처리**: 탈퇴 유저는 DB에서 완전히 삭제되지 않으며, deleted_at 필드와 마스킹된 이메일로 구분
- **API 호출 권한**: 관리자만 이 화면들에 접근 가능하며, 역할 기반 접근 제어(RBAC)는 Phase 2에서 구현
- **시스템 타임존**: 모든 시간 기준(일일 제한, 분석 건수 집계)은 Asia/Seoul 타임존을 사용하며, 기존 구현과 동일
- **성능 고려**: 대량의 분석 기록 조회 시 인덱싱 및 쿼리 최적화는 별도의 성능 개선 작업에서 다룸
