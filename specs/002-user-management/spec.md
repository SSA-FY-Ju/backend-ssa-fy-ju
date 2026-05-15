# Feature Specification: User Management & Data Integration (사용자 관리 및 분석 데이터 통합)

**Feature Branch**: `002-user-management`
**Created**: 2026-05-14
**Status**: Draft
**Phase**: Phase 2
**Input**: User description: "통합 사용자 관리 (회원가입, 로그인, 로그아웃, 회원 탈퇴, 마이페이지) + 사주 분석, 관운 분석, 회사 궁합, 만족도 조사가 User와 명확하게 매핑되는 통합 데이터 관리"

---

## Overview

Phase 1에서 생성되는 **모든 분석 결과**(사주 분석, 취업 관운 분석, 회사 궁합 분석, 만족도 조사)가 **User와 명확하게 연결**되어, 마이페이지에서 통합 조회 및 관리가 가능하도록 하는 사용자 관리 시스템입니다.

---

## Clarifications

### Session 2026-05-14

- **Q: RefreshToken 발급 정책** → **A: 모든 로그인에서 항상 RefreshToken 발급** (조건부 "기억하기" 옵션 없음)
  - 단순화된 구현: 모든 로그인은 AccessToken(1시간) + RefreshToken(7일) 발급
  - User Story 4에서 "이 기기에서 기억하기" 옵션 제거

- **Q: RefreshToken 저장소** → **A: 클라이언트 localStorage + 서버 DB 모두 저장** (표준 JWT 패턴)
  - 클라이언트: localStorage에 토큰 저장 및 관리
  - 서버: RefreshToken을 `token_hash`로 DB 저장, 로그아웃 시 revoked_at 마크 처리

- **Q: User 계정 상태 Enum** → **A: "활성/비활성"만 유지** (브루트포스 방어 제거로 잠금 불필요)
  - 로그인 실패: 단순 에러 메시지만 표시
  - "잠금" 상태: Phase 2.x에서 재검토

- **Q: Phase 1 분석 결과와 User 매핑** → **A: 모든 분석 결과는 즉시 User와 매핑되어 저장**
  - 사주 분석 결과: SajuAnalysisResult (user_id FK 포함)
  - 관운 분석 결과: CareerFortuneResult (user_id FK 포함)
  - 회사 궁합 분석: CompanyCompatibilityResult (user_id FK 포함)
  - 만족도 조사: UserSatisfactionFeedback (user_id FK 포함)

- **Q: RefreshToken 저장 방식 보안 강화** → **A: HttpOnly, Secure 속성을 적용한 쿠키로 저장 (localStorage 제거)**
  - **문제**: localStorage는 자바스크립트(XSS 공격)로 접근 가능하여 7일 유효기간의 RefreshToken에는 부적합
  - **솔루션**:
    - **RefreshToken**: HttpOnly, Secure 속성의 HTTP-Only 쿠키로 클라이언트에 전달 (자동 전송, 자바스크립트 접근 불가)
    - **AccessToken**: 메모리 또는 localStorage에 저장 가능 (단기간만 유효하고, RefreshToken이 안전하면 전체 보안 수준 상승)
  - **이점**: RefreshToken의 탈취 위험 최소화, XSS 공격에 대한 강한 방어

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 회원가입 (Priority: P1)

신규 사용자가 이메일과 비밀번호로 계정을 생성하고, 기본 프로필 정보를 입력하여 서비스에 등록할 수 있습니다.

**Why this priority**: 모든 사용자가 서비스에 진입하는 기본 관문.

**Independent Test**: 신규 사용자가 유효한 이메일/비밀번호로 회원가입하고, 로그인 페이지로 리다이렉트됨을 검증합니다.

**Acceptance Scenarios**:

1. **Given** 사용자가 회원가입 폼에 접근, **When** 유효한 이메일, 비밀번호, 이름을 입력하고 이용약관 및 개인정보 수집 동의에 체크한 후 제출, **Then** 계정이 생성되고 로그인 페이지로 리다이렉트됨
2. **Given** 회원가입 완료된 사용자, **When** 로그인 페이지에서 가입한 이메일/비밀번호로 로그인, **Then** AccessToken과 RefreshToken 발급받음
3. **Given** 사용자가 회원가입 중, **When** 이미 등록된 이메일을 입력, **Then** "이미 가입된 이메일입니다" 에러 메시지 표시
4. **Given** 사용자가 회원가입 중, **When** 약한 비밀번호(8자 미만) 입력, **Then** 비밀번호 정책 설명 메시지 표시
5. **Given** 사용자가 회원가입 폼 작성 중, **When** 이용약관 또는 개인정보 수집 동의에 체크하지 않은 채 제출, **Then** "이용약관과 개인정보 수집 동의는 필수입니다" 에러 메시지 표시

---

### User Story 2 - 로그인 (Priority: P1)

등록된 사용자가 이메일과 비밀번호로 로그인하여, JWT 토큰을 발급받고 인증된 상태에서 서비스를 이용할 수 있습니다.

**Why this priority**: 핵심 기능. 인증 없이는 개인화 서비스 불가능.

**Independent Test**: 사용자가 올바른 이메일/비밀번호로 로그인하면, AccessToken(1시간)과 RefreshToken(7일)을 받음을 검증합니다.

**Acceptance Scenarios**:

1. **Given** 등록된 사용자가 로그인 폼에 접근, **When** 올바른 이메일과 비밀번호를 입력, **Then** AccessToken과 RefreshToken 발급받고 홈 페이지로 이동
2. **Given** 로그인한 사용자, **When** AccessToken을 Authorization 헤더에 포함하여 API 호출, **Then** 요청 성공
3. **Given** 사용자가 로그인을 시도, **When** 잘못된 비밀번호를 입력, **Then** "이메일 또는 비밀번호가 일치하지 않습니다" 에러 표시
4. **Given** AccessToken이 만료된 사용자, **When** RefreshToken으로 새 AccessToken 요청, **Then** 새 AccessToken 발급받음

---

### User Story 3 - 로그아웃 (Priority: P1)

로그인한 사용자가 로그아웃 버튼을 클릭하여 현재 세션을 종료하고 안전하게 로그인 페이지로 이동합니다.

**Why this priority**: 보안 필수 기능. 공유 기기 사용 후 계정 보호.

**Independent Test**: 사용자가 로그인 → 로그아웃 클릭 → 이전 토큰 무효화 → 로그인 페이지 이동을 검증합니다.

**Acceptance Scenarios**:

1. **Given** 로그인한 사용자가 상단 메뉴의 로그아웃 버튼 클릭, **When** 로그아웃 요청 전송, **Then** 세션 종료 및 로그인 페이지로 이동
2. **Given** 사용자가 방금 로그아웃, **When** 이전 토큰을 사용해 API 요청 시도, **Then** "인증 토큰이 유효하지 않습니다" (401) 에러 반환

---

### User Story 4 - 로그인 상태 유지 (Priority: P2)

사용자가 로그인하면, RefreshToken을 통해 AccessToken을 자동 갱신하여 장기간 로그인 상태를 유지할 수 있습니다.

**Why this priority**: 사용자 편의성 향상. 보안적으로 RefreshToken 패턴으로 관리.

**Independent Test**: 사용자가 로그인 → RefreshToken 받음 → AccessToken 만료 후 자동 갱신을 검증합니다.

**Acceptance Scenarios**:

1. **Given** 사용자가 로그인 성공, **When** 토큰 발급, **Then** AccessToken(1시간)과 RefreshToken(7일) 발급받음
2. **Given** AccessToken 만료 후 API 호출 시도, **When** 클라이언트가 RefreshToken으로 갱신 요청, **Then** 새 AccessToken 즉시 발급

---

### User Story 5 - 일일 API 요청 제한 (Priority: P2)

사용자가 사주 분석 등 주요 기능을 **하루에 최대 3회**만 사용할 수 있도록 제한되며, 제한 도달 시 명확한 안내 메시지를 받습니다.

**Why this priority**: 서비스 지속 가능성. 공정한 리소스 분배.

**Independent Test**: 사용자가 로그인 후 분석 API를 3회 호출 성공 → 4번째 요청 시 429 에러를 받음을 검증합니다.

**Acceptance Scenarios**:

1. **Given** 인증된 사용자가 사주 분석 API 호출, **When** 당일 1-3회 요청, **Then** 정상 응답
2. **Given** 3회 사용 완료, **When** 네 번째 분석 요청, **Then** HTTP 429 에러 + "하루 3회 제한" 메시지
3. **Given** KST 자정(한국 시간 밤 12시) 경과, **When** 다음 날 API 호출, **Then** 제한 초기화 및 새 3회 사용 가능
4. **Given** 제한 제외 API (로그인, 피드백) 호출, **When** 요청 전송, **Then** 제한 없이 성공

---

### User Story 6 - 회원 탈퇴 (Priority: P2)

로그인한 사용자가 계정 설정에서 회원 탈퇴를 요청할 수 있으며, 비밀번호 재확인 후 계정과 관련 데이터가 삭제됩니다.

**Why this priority**: 사용자 자율성 존중. 규정 준수. 사용자 신뢰 향상.

**Independent Test**: 사용자가 탈퇴 → 비밀번호 확인 → 계정 삭제 & 자동 로그아웃을 검증합니다.

**Acceptance Scenarios**:

1. **Given** 로그인한 사용자가 계정 설정에 접근, **When** "회원 탈퇴" 버튼 클릭, **Then** 탈퇴 확인 모달 표시
2. **Given** 탈퇴 확인 모달 표시됨, **When** 비밀번호 입력 후 "계정 탈퇴" 최종 확인, **Then** 계정 삭제 + 로그인 페이지로 리다이렉트
3. **Given** 탈퇴된 사용자, **When** 이전 이메일/비밀번호로 로그인 시도, **Then** "이메일 또는 비밀번호가 일치하지 않습니다" 에러 (User Enumeration 방지)

---

### User Story 7 - 마이페이지 (통합 분석 데이터 관리) (Priority: P2)

로그인한 사용자가 마이페이지에 접근하여, **최근 1년 범위의 분석 결과**(사주, 관운, 회사 궁합, 만족도 조사)를 통합 조회하고 관리할 수 있습니다.

**Why this priority**: 핵심 가치 제공. 사용자 데이터의 통합 관리 및 재활용.

**Independent Test**: 사용자가 마이페이지 접근 → 최근 1년 범위의 분석 결과 통합 목록 조회 → 특정 분석 클릭 → 전체 분석 결과 확인 및 재분석을 검증합니다.

**Acceptance Scenarios**:

1. **Given** 로그인한 사용자가 "마이페이지" 클릭, **When** 마이페이지 접근, **Then** 프로필 정보 + 통합 분석 데이터 목록(최근 1년 범위) 표시
2. **Given** 사주/관운/궁합 분석 기록(최근 1년)이 있는 사용자, **When** 마이페이지 접근, **Then** 분석 결과를 최신순으로 표시 (분석 유형, 대상 정보, 분석 일시 포함)
3. **Given** 분석 데이터 목록 표시됨, **When** 특정 분석 클릭, **Then** 해당 분석의 전체 결과 표시 (사주팔자, 관운, 궁합도, 상세 분석 등)
4. **Given** 이전 분석 결과 확인 중, **When** "재분석" 버튼 클릭, **Then** 현재 알고리즘으로 재분석 (일일 3회 제한에 포함)
5. **Given** 분석 기록이 없는 신규 사용자, **When** 마이페이지 접근, **Then** "분석 기록이 없습니다" 메시지 + 분석 시작 버튼
6. **Given** 마이페이지 접근, **When** 필터 옵션 사용 (분석 유형: 사주/관운/궁합), **Then** 선택한 유형의 결과만 표시

---

### Edge Cases

- 회원가입 중 네트워크 끊김 → 이메일 중복 확인 실패 처리
- 로그인 요청 중 데이터베이스 장애 → 적절한 에러 메시지 + 재시도 옵션
- 탈퇴 중 분석 중인 요청이 있는 경우 → 분석 완료 대기 또는 취소
- RefreshToken 만료 전 로그아웃 → RefreshToken 무효화 처리

---

## Requirements *(mandatory)*

### Functional Requirements

**회원가입**

- **FR-001**: 시스템은 이메일을 고유 식별자로 사용하여 사용자 계정을 생성할 수 있어야 함
- **FR-002**: 회원가입 시, 클라이언트는 평문 비밀번호를 HTTPS로 전송하고, 서버는 **Spring Security PasswordEncoder (BCrypt 알고리즘)**로 해싱하여 저장해야 함
  - 저장 방식: PasswordEncoder.encode(plainPassword) 사용
  - 검증 방식: PasswordEncoder.matches(plainPassword, storedHash) 사용
  - 요구사항: 평문 비밀번호는 절대 저장하지 않음
- **FR-003**: 시스템은 중복 이메일 가입을 방지해야 함
- **FR-004**: 시스템은 회원가입 시 비밀번호 정책을 강제해야 함 (최소 8자)
- **FR-004-1**: 시스템은 회원가입 시 이용약관 및 개인정보 수집/이용 동의를 받아야 함 (필수)
- **FR-004-2**: 시스템은 사용자가 이용약관 및 개인정보 수집/이용에 동의한 시점을 기록해야 함 (법적 증거 목적)

**로그인 & 토큰**

- **FR-005**: 사용자는 이메일과 비밀번호를 입력하여 로그인할 수 있어야 함
- **FR-006**: 로그인 성공 시, 시스템은 JWT 기반의 두 가지 토큰을 발급해야 함:
  - **AccessToken**: 유효기간 1시간, 모든 API 요청에 포함됨
  - **RefreshToken**: 유효기간 7일, AccessToken 만료 시 새 AccessToken 발급에 사용
- **FR-007**: 시스템은 모든 API 요청에서 AccessToken을 검증하여 인증 여부를 확인해야 함
- **FR-008**: 사용자는 RefreshToken을 사용하여 만료된 AccessToken을 갱신할 수 있어야 함

**로그아웃**

- **FR-010**: 사용자는 로그인 후 로그아웃할 수 있으며, 로그아웃 후 토큰은 무효화되어야 함
- **FR-011**: 로그아웃된 토큰으로 API 요청 시 401 에러 반환

**일일 API 제한 (Race Condition 방지)**

- **FR-012**: 각 인증된 사용자는 **하루(KST 자정 기준)에 3회**만 분석 API 요청 가능:
  - 사주 분석, 직업 상담, 호환성 분석, 관운 분석 등 포함
  - 로그인, 회원가입, 피드백은 제외 (무제한)
  - **동시성 요구사항**: 동일 사용자의 동시 요청(더블 클릭, 비동기 요청)에서도 정확히 3회만 허용
    - DB 레벨: `(user_id, usage_date)` 조합에 UNIQUE INDEX 필수
    - 애플리케이션 레벨: Atomic UPDATE + DataIntegrityViolationException 처리로 Race Condition 방지
- **FR-013**: 4번째 분석 요청 시 **HTTP 429** 에러 반환, "하루 3회 제한" 메시지 포함
- **FR-014**: KST 자정(한국시간 밤 12시) 경계에서 제한 초기화

**회원 탈퇴**

- **FR-015**: 로그인한 사용자는 계정 설정에서 "회원 탈퇴" 옵션에 접근 가능
- **FR-016**: 탈퇴 전 비밀번호 재확인을 통해 본인 확인 후 탈퇴 진행
- **FR-017**: 탈퇴 시 **논리적 삭제(Soft Delete)** 방식으로 처리:
  - User 엔티티의 `deleted_at` 필드에 삭제 시간 기록
  - 개인정보(이름, 이메일 등) **마스킹 처리** (예: 이름 → "탈퇴한 사용자", 이메일 → "deleted_{userId}_{timestamp}@deleted.local")
    - 이메일은 고유성(UNIQUE)을 유지하기 위해 동적 형식으로 마스킹 (각 탈퇴 사용자마다 고유한 마스킹 이메일)
  - 데이터 통계 및 감시 로그는 유지 (합법성 보장)
- **FR-018**: 탈퇴 후 자동 로그아웃 및 로그인 페이지로 리다이렉트
- **FR-019**: 탈퇴된 계정으로는 로그인 불가 (status=inactive 또는 deleted_at!=null 확인)
- **FR-020**: 탈퇴된 사용자의 분석 결과(SajuAnalysisResult, CareerFortuneResult, CompanyCompatibilityResult)도 함께 마스킹 처리 또는 삭제

**마이페이지 - 통합 분석 데이터 관리**

- **FR-021**: 로그인한 모든 사용자는 마이페이지에 접근 가능
- **FR-022**: 마이페이지는 사용자 프로필 정보(이름, 가입일, 마지막 로그인) 표시
- **FR-023**: 마이페이지는 **통합 분석 히스토리** 목록 표시 (최신순 정렬, 페이지네이션, **최근 1년 범위**)
  - 사주 분석 결과 (SajuAnalysisResult)
  - 관운 분석 결과 (CareerFortuneResult)
  - 회사 궁합 분석 결과 (CompanyCompatibilityResult)
  - **범위**: created_at >= (현재 시간 - 1년) 조건으로 필터링하여 조회
- **FR-024**: 각 분석 항목은 분석 유형, 대상 정보(이름/생년월일), 분석 일시 포함
- **FR-025**: 분석 항목 클릭 시, 전체 분석 결과(사주팔자, 관운, 궁합도, 상세 분석 등) 조회 가능
- **FR-026**: 마이페이지에서 분석 유형별 필터링 가능 (사주/관운/궁합 등)
- **FR-027**: "재분석" 기능 제공 (일일 3회 제한에 포함)
- **FR-028**: 마이페이지 데이터는 읽기 전용 (편집/삭제 불가)
- **FR-029**: 사용자가 제출한 만족도 조사 결과도 분석 항목과 함께 표시 (해당 시 피드백 아이콘 표시)

**Phase 1 분석 결과와 User 매핑 (Phase 2 핵심)**

- **FR-030**: Phase 1 분석 API 요청 시, 인증된 User의 `user_id`를 자동으로 분석 결과에 포함하여 저장
  - 사주 분석 요청 → SajuAnalysisResult 생성 (user_id 자동 설정)
  - 관운 분석 요청 → CareerFortuneResult 생성 (user_id 자동 설정)
  - 회사 궁합 요청 → CompanyCompatibilityResult 생성 (user_id 자동 설정)
- **FR-031**: 모든 분석 결과는 생성 시점에 User와 매핑되어 마이페이지에서 즉시 조회 가능
- **FR-032**: 만족도 조사 요청 시, 분석 결과 ID와 함께 User ID를 저장하여 추적 가능
- **FR-033**: User 탈퇴 시, 해당 User의 모든 분석 결과(SajuAnalysisResult, CareerFortuneResult, CompanyCompatibilityResult, UserSatisfactionFeedback)는 마스킹 처리 또는 삭제

**보안 (Token & Data Protection)**

- **FR-034**: RefreshToken은 **HttpOnly, Secure 속성의 HTTP-Only 쿠키**로 클라이언트에 전달 (자동 전송, 자바스크립트 접근 불가)
  - XSS 공격으로부터 RefreshToken 보호
  - AccessToken이 탈취되더라도 새 AccessToken 생성 불가
- **FR-035**: AccessToken은 메모리 또는 localStorage에 저장 가능 (단기간 유효하므로 위험도 낮음)
- **FR-036**: User 탈퇴 시 **논리적 삭제(Soft Delete)** 방식으로 처리 (deleted_at 컬럼, 개인정보 마스킹)
  - 데이터 통계 유지, 감시 로그 활용 가능

**로깅 & 감시**

- **FR-037**: 시스템은 모든 인증 관련 이벤트(회원가입, 로그인, 로그아웃, 토큰 갱신) 로깅해야 함

### Key Entities

#### 기본 사용자 정보

- **User**: 사용자 계정 정보
  - `id` (PK): 고유 사용자 ID
  - `email` (UNIQUE, NOT NULL): 로그인 이메일
  - `password_hash` (NOT NULL): **Spring Security PasswordEncoder (bcrypt)로 해싱된 비밀번호**
    - 저장: PasswordEncoder.encode(plainPassword) → bcrypt 해시값 저장
    - 검증: PasswordEncoder.matches(plainPassword, passwordHash) 사용
    - 평문 비밀번호는 절대 저장하지 않음
  - `name` (NOT NULL): 사용자 이름
  - `role` (ENUM): 사용자 권한 (USER, ADMIN) - 기본값: USER
  - `status` (ENUM): 활성/비활성 상태 (active/inactive)
  - `last_login_at` (TIMESTAMP): 마지막 로그인 시간
  - `terms_agreed_at` (TIMESTAMP, NOT NULL): 이용약관 동의 시점 (법적 증거)
  - `privacy_agreed_at` (TIMESTAMP, NOT NULL): 개인정보 수집/이용 동의 시점 (법적 증거)
  - `created_at` (TIMESTAMP): 계정 생성 시간
  - `updated_at` (TIMESTAMP): 마지막 수정 시간
  - `deleted_at` (TIMESTAMP, nullable): 논리적 삭제 시간 (소프트 삭제용, 데이터 통계 유지)

- **RefreshToken**: JWT RefreshToken 저장소
  - `id` (PK): 토큰 ID
  - `user_id` (FK): 사용자 ID
  - `token_hash` (UNIQUE, NOT NULL): RefreshToken 해시
  - `expires_at` (TIMESTAMP): 토큰 만료 시간 (7일)
  - `revoked_at` (TIMESTAMP): 로그아웃 시 취소 시간
  - `created_at` (TIMESTAMP): 생성 시간

#### 사용자 행동 추적

- **LoginAttempt**: 로그인 시도 기록 (보안 감시용)
  - `id` (PK): 기록 ID
  - `email` (NOT NULL): 로그인 시도 이메일
  - `success` (BOOLEAN): 성공 여부
  - `failure_reason` (ENUM: SUCCESS, INVALID_EMAIL, WRONG_PASSWORD, UNKNOWN): 실패 사유
    - **클라이언트 응답**: "이메일 또는 비밀번호가 일치하지 않습니다" (User Enumeration 방지)
    - **내부 로깅**: failure_reason에 상세히 기록 (CS 대응, 보안 분석용)
  - `ip_address` (VARCHAR(45)): 요청 IP (X-Forwarded-For 헤더 분석으로 실제 클라이언트 IP 추출)
  - `attempted_at` (TIMESTAMP): 시도 시간

- **DailyApiUsage**: 일일 API 요청 제한 추적 (Race Condition 방지)
  - `id` (PK): 기록 ID
  - `user_id` (FK): 사용자 ID
  - `request_count` (INT): 당일 요청 횟수 (최대 3)
  - `usage_date` (DATE): 사용 날짜 (KST 기준, LocalDate.now(ZoneId.of("Asia/Seoul"))로 계산)
  - `created_at` (TIMESTAMP): 생성 시간
  - **UNIQUE 제약**: `(user_id, usage_date)` 조합은 반드시 유일해야 함 (동시 INSERT 방지)
    - 동시성 버그 원인: 동시 요청 시 UPDATE 실패 → INSERT로 진행 → 중복 레코드 발생 → NonUniqueResultException
    - 해결책: DB 레벨에서 UNIQUE INDEX 강제 + 코드에서 DataIntegrityViolationException 처리

#### Phase 1 분석 결과 - User 매핑 (핵심)

**모든 분석 결과는 user_id를 포함하여 생성 시점에 User와 자동 매핑됩니다.**

- **SajuAnalysisResult**: 사주 분석 결과
  - `id` (PK): 분석 결과 ID
  - `user_id` (FK): 사용자 ID (Phase 1 요청 시 설정)
  - `target_name` (VARCHAR): 분석 대상 이름
  - `birth_date` (DATE): 생년월일
  - `birth_time` (TIME): 출생 시간 (nullable)
  - `analysis_data` (JSON): 사주 팔자, 오행 등 분석 데이터
  - `created_at` (TIMESTAMP): 분석 완료 시간
  - `updated_at` (TIMESTAMP): 마지막 수정 시간

- **CareerFortuneResult**: 취업 관운 분석 결과
  - `id` (PK): 분석 결과 ID
  - `user_id` (FK): 사용자 ID (Phase 1 요청 시 설정)
  - `target_name` (VARCHAR): 분석 대상 이름
  - `birth_date` (DATE): 생년월일
  - `birth_time` (TIME): 출생 시간 (nullable)
  - `analysis_data` (JSON): 관운, 직업 적성, 재운 등 분석 데이터
  - `created_at` (TIMESTAMP): 분석 완료 시간
  - `updated_at` (TIMESTAMP): 마지막 수정 시간

- **CompanyCompatibilityResult**: 회사 궁합 분석 결과
  - `id` (PK): 분석 결과 ID
  - `user_id` (FK): 사용자 ID (Phase 1 요청 시 설정)
  - `target_name` (VARCHAR): 분석 대상 이름
  - `target_birth_date` (DATE): 분석 대상 생년월일
  - `target_birth_time` (TIME): 출생 시간 (nullable)
  - `company_name` (VARCHAR): 회사명
  - `company_founded_date` (DATE): 회사 설립일
  - `compatibility_score` (INT): 궁합 점수 (0-100)
  - `analysis_data` (JSON): 궁합도, 상세 분석 데이터
  - `created_at` (TIMESTAMP): 분석 완료 시간
  - `updated_at` (TIMESTAMP): 마지막 수정 시간

- **UserSatisfactionFeedback**: 사용자 만족도 조사 (피드백)
  - `id` (PK): 피드백 기록 ID
  - `user_id` (FK): 사용자 ID
  - `feedback_type` (ENUM): 분석 유형 (SAJU/CAREER_FORTUNE/COMPANY_COMPATIBILITY)
  - `saju_result_id` (FK, nullable): 사주 분석 결과 ID (feedback_type=SAJU인 경우)
  - `career_fortune_result_id` (FK, nullable): 관운 분석 결과 ID (feedback_type=CAREER_FORTUNE인 경우)
  - `company_compatibility_result_id` (FK, nullable): 회사 궁합 분석 결과 ID (feedback_type=COMPANY_COMPATIBILITY인 경우)
  - `satisfaction_status` (ENUM): 만족 여부
  - `feedback_content` (TEXT): 피드백 상세 내용 (optional)
  - `created_at` (TIMESTAMP): 피드백 등록 시간
  - `updated_at` (TIMESTAMP): 마지막 수정 시간

#### 마이페이지용 통합 뷰 (Phase 2)

- **UserAnalysisHistory**: 마이페이지에서 조회하는 통합 분석 히스토리 (VIEW or 통합 조회 로직)
  - User의 모든 분석 결과(SajuAnalysisResult, CareerFortuneResult, CompanyCompatibilityResult)를 통합 조회
  - 최신순 정렬, 필터링, 페이지네이션 지원

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

#### 인증 & 계정 관리

- **SC-001**: 신규 사용자가 회원가입에서 로그인까지 2분 이내 완료
- **SC-002**: 로그인 요청 응답 시간 500ms 이하
- **SC-003**: AccessToken 갱신 요청 응답 시간 300ms 이하
- **SC-004**: 5,000명 동시 사용자가 로그인/API 요청 시 500ms 이하 응답 유지
- **SC-005**: 회원가입 성공률 95% 이상 (유효한 입력 기준)
- **SC-006**: 비밀번호 보안: Spring Security PasswordEncoder(BCrypt) 사용율 100%
  - 비밀번호 해싱 저장율: 100%
  - 평문 비밀번호 저장율: 0%
  - 검증 시 PasswordEncoder.matches() 사용율: 100%
- **SC-007**: AccessToken 유효기간 정확히 1시간, RefreshToken 정확히 7일
- **SC-008**: 로그아웃 후 토큰 즉시 무효화, 재사용 시 401 에러 반환
- **SC-009**: 일일 API 제한 정확히 작동 (3회 후 4번째는 429) **- Race Condition 안전**
  - 동시 요청에서도 정확히 3회만 허용 (더블 클릭, 비동기 요청 포함)
  - 동시성 테스트: 동일 사용자의 10개 동시 요청 시에도 3회 이상 요청 불가
  - DB UNIQUE INDEX `(user_id, usage_date)` 적용률: 100%
  - DataIntegrityViolationException 처리율: 100%
- **SC-010**: 회원 탈퇴 시 모든 데이터 삭제/익명화 100%

#### Phase 1 & Phase 2 데이터 통합 (핵심)

- **SC-011**: Phase 1 분석 API 요청 시, 분석 결과가 100% User와 매핑되어 저장됨
  - 사주 분석 결과의 user_id 설정율: 100%
  - 관운 분석 결과의 user_id 설정율: 100%
  - 회사 궁합 결과의 user_id 설정율: 100%

- **SC-012**: 마이페이지에서 최근 1년 범위의 분석 결과(사주/관운/궁합)가 통합 조회 가능
  - 최근 1년 범위 내 분석 결과 누락률: 0%
  - 분석 항목 로드 시간: 2초 이내
  - 분석 유형별 필터링 정확도: 100%

- **SC-013**: 사용자가 제출한 분석 결과가 마이페이지에서 평균 1초 이내에 조회 가능
  - 분석 완료 후 마이페이지에 반영되는 시간: 1초 이내

- **SC-014**: User 탈퇴 시, 해당 User의 모든 분석 결과 삭제/익명화 정확도: 100%
  - 분석 결과 잔여율: 0%
  - 만족도 조사 데이터 잔여율: 0%

#### 마이페이지 & 사용자 경험

- **SC-015**: 마이페이지 로드 시간 2초 이내
- **SC-016**: 재분석 요청 성공률 99% 이상
- **SC-017**: 분석 항목 클릭 후 상세 결과 표시까지의 시간 1초 이내

#### 보안 & 데이터 보호

- **SC-018**: RefreshToken은 100% HttpOnly, Secure 쿠키로 전달됨 (localStorage 미사용)
  - XSS 공격으로부터의 보호율: 100%
  - HTTP-Only 속성 적용률: 100%

- **SC-019**: User 탈퇴 시 개인정보 마스킹 적용률: 100%
  - 이름 마스킹: "탈퇴한 사용자"로 변환
  - 이메일 마스킹: "deleted_{userId}_{timestamp}@deleted.local" 형식으로 고유성 유지하며 변환 (UNIQUE 제약 위반 0%)
  - deleted_at 필드에 삭제 시간 기록, 논리적 삭제 완벽 실행

- **SC-020**: 탈퇴된 계정 재로그인 차단율: 100% (deleted_at 확인)

#### 로깅 & 감시

- **SC-021**: 모든 인증 이벤트 감시 로그 기록, 조회 가능
- **SC-022**: User와 분석 결과 매핑 실패율: 0% (모든 분석이 정확한 User와 연결)

---

## Assumptions

### 기본 설정

- **대상 사용자**: 개인 사용자(B2C). 단체/기업 계정은 Phase 2.x 이상에서 고려
- **인증 방식**: JWT 토큰 기반 (AccessToken 1시간 + RefreshToken 7일)
- **토큰 발급**: 모든 로그인 시에 AccessToken과 RefreshToken 항상 발급 (회원가입 후에는 발급 안 함)
- **토큰 저장 (보안 강화)**:
  - **AccessToken**: 메모리 또는 localStorage에 저장 (단기간 유효하므로 용인)
  - **RefreshToken**: **HttpOnly, Secure 속성을 적용한 HTTP-Only 쿠키로 저장** (자동 전송, XSS 방어)
    - 서버 DB에도 token_hash 저장 (로그아웃 추적 및 토큰 검증용)
- **토큰 전송**:
  - **AccessToken**: HTTP Authorization 헤더 (Bearer scheme)
  - **RefreshToken**: HTTP-Only 쿠키 (자동 전송)
- **비밀번호 암호화**: Spring Security PasswordEncoder (BCrypt 알고리즘) 사용 (클라이언트: 평문 전송 via HTTPS)
  - 저장: PasswordEncoder.encode(plainPassword)로 bcrypt 해시값 저장
  - 검증: PasswordEncoder.matches(plainPassword, storedHash) 사용
  - 평문 비밀번호는 절대 저장하지 않음
- **비밀번호 정책**: 최소 8자, 대문자/소문자/숫자/특수문자 포함 권장 (필수 아님)
- **일일 API 제한**: KST 자정(한국시간 밤 12시) 기준 초기화 (한국 사용자 UX 최적화)
  - **동시성 처리**: (user_id, usage_date) UNIQUE INDEX + Atomic UPDATE + DataIntegrityViolationException 처리
    - 동시 요청 시에도 정확히 3회만 허용 (Race Condition 방지)
    - DB 레벨: UNIQUE INDEX 필수
    - 애플리케이션: SELECT → UPDATE 실패 → INSERT (with duplicate exception handling)
- **제한 제외 API**: 로그인, 회원가입, 로그아웃, 피드백 (무제한)
- **약관 동의 기록**: 회원가입 시 이용약관 및 개인정보 수집/이용 동의 필수, 동의 시점을 `terms_agreed_at`, `privacy_agreed_at`에 기록 (법적 분쟁 방지, 서비스 신뢰도 향상)
- **다중 로그인**: 한 계정이 여러 기기/브라우저에서 동시 로그인 허용
- **데이터베이스**: 기존 MySQL 사용

### Phase 1과 Phase 2의 협력 (핵심)

- **분석 결과 저장**: Phase 1의 모든 분석 API는 인증된 User로부터 오므로, 분석 결과 생성 시 자동으로 `user_id` 포함
  - SajuAnalysisResult (사주 분석)
  - CareerFortuneResult (관운 분석)
  - CompanyCompatibilityResult (회사 궁합 분석)
  - UserSatisfactionFeedback (만족도 조사 피드백)

- **마이페이지 통합 조회**: Phase 2에서는 User가 요청한 분석 결과를 통합 조회 및 관리 (**최근 1년 범위**)
  - 마이페이지에서 SajuAnalysisResult, CareerFortuneResult, CompanyCompatibilityResult를 통합 목록으로 표시
  - 조회 범위: created_at >= (현재 시간 - 1년) 조건으로 필터링
  - 분석 유형별 필터링 및 최신순 정렬
  - 각 분석의 상세 결과 및 만족도 조사 결과 함께 표시

- **재분석**: 마이페이지에서 "재분석" 요청 시, Phase 1 분석 API를 다시 호출 (일일 3회 제한에 포함)
  - 새로운 분석 결과가 생성되고, 자동으로 User와 매핑됨

- **데이터 삭제**: User 탈퇴 시, Phase 2에서 해당 User의 모든 분석 결과 삭제 또는 익명화
  - Phase 1에서 저장한 SajuAnalysisResult, CareerFortuneResult, CompanyCompatibilityResult도 함께 처리

### 인증 & 로깅 아키텍처

#### EventPublisher 기반 비동기 로깅 (성능 최적화)

- **패턴**: Spring EventPublisher를 활용한 이벤트 기반 비동기 로깅
  - AuthService에서 로그인 성공/실패 시 `LoginAttemptEvent` 발행
  - 별도의 `@EventListener` (@Async)가 이벤트 구독하여 DB에 비동기 저장
  - **목적**: 로그인 API 응답 지연 방지 (네트워크 I/O가 응답 경로에서 제외)
  - **failure_reason**: INVALID_EMAIL, WRONG_PASSWORD, SUCCESS 등을 상세히 기록

#### 실제 클라이언트 IP 추출 (ClientIpUtil)

- **요구사항**: Nginx, AWS ALB 등 리버스 프록시 환경에서 실제 클라이언트 IP 추출
  - 로드밸런서를 거치면 `request.getRemoteAddr()`는 로드밸런서 IP만 반환
  - 실제 클라이언트 IP는 `X-Forwarded-For`, `CF-Connecting-IP`, `X-Real-IP` 헤더에 포함됨
- **구현 방식**: `ClientIpUtil.getClientIp(request)` 메서드
  - 헤더 순서대로 검사: X-Forwarded-For → CF-Connecting-IP → X-Real-IP → request.getRemoteAddr()
  - 첫 번째 유효한 IP 반환
- **적용 대상**: LoginAttempt 저장 시 실제 클라이언트 IP를 ip_address 필드에 기록

---

### 보안 & 데이터 보호

#### 토큰 저장 보안 (XSS 방어)

- **RefreshToken 저장**: **HttpOnly, Secure 속성의 HTTP-Only 쿠키** (권장사항 적용)
  - localStorage 사용 금지 (자바스크립트 XSS 공격에 취약)
  - 자동 전송되므로 자바스크립트 코드에서 접근 불가
  - 7일 유효기간동안 안전하게 보호됨

- **AccessToken 저장**: 메모리 또는 localStorage 가능
  - 1시간 유효기간이므로 탈취 위험도 낮음
  - RefreshToken이 안전하게 격리되어 있으면 전체 보안 수준이 크게 올라감

#### User 탈퇴 처리 (데이터 보호 & 통계 유지)

- **논리적 삭제(Soft Delete)** 방식 사용:
  - User 엔티티에 `deleted_at` 컬럼 추가
  - 실제 데이터는 삭제하지 않고 마크 처리
  - **중요**: @SQLDelete 어노테이션 사용 금지 (마스킹 내용이 반영되지 않는 기술적 문제)
    - JPA가 자동으로 생성한 DELETE 쿼리는 마스킹된 필드값을 무시함
    - **구현 방식**: 서비스 레이어에서 명시적으로 엔티티의 필드를 수정 후 save() 호출
    - 이 방식으로 PasswordEncoder, 이메일 마스킹, deleted_at 설정이 모두 올바르게 반영됨

- **개인정보 마스킹** (UNIQUE 제약 조건 충돌 방지):
  - 이름: "탈퇴한 사용자" 등으로 마스킹
  - 이메일: "deleted_{userId}_{timestamp}@deleted.local" 형태로 마스킹 (고유성 유지)
    - 동적 마스킹으로 여러 탈퇴 사용자도 각각 고유한 이메일 유지
    - UNIQUE 제약 조건 위반 방지
  - 비밀번호: 이미 해시되어 있으므로 추가 처리 불필요
  - status: INACTIVE로 설정

- **쿼리 필터링** (자동 제외):
  - 모든 SELECT 쿼리에서 `WHERE deleted_at IS NULL` 자동 적용 (JPA @SQLRestriction 사용)
  - 탈퇴 사용자 데이터는 조회되지 않음

- **장점**:
  - 데이터 통계 유지 (사용자 증감 추이, 분석 결과 데이터)
  - 감시 로그 활용 가능 (보안 감사, 규정 준수)
  - 필요시 데이터 복구 가능 (법적 분쟁 등)
  - 마스킹 및 상태 변경이 모두 정확히 반영됨

---

---

## Related Features & Dependencies

### 의존성 (Phase 2는 Phase 1 기반)

- **Phase 1 - Career Fortune API**: 사주/관운/궁합 분석 결과 생성
  - Phase 2는 Phase 1이 생성한 분석 결과(SajuAnalysisResult, CareerFortuneResult, CompanyCompatibilityResult)를 User와 매핑하여 관리
  - **중요**: Phase 1 분석 API가 생성하는 모든 결과에 user_id가 포함되어야 함

### 확장 기능 (Phase 2.x & Phase 3+)

- **Phase 2.x**:
  - 분석 결과 공유 기능 (다른 사용자와 공유)
  - 분석 결과 내보내기 (PDF, 이미지)
  - 분석 결과 알림 (새로운 분석 완료 시)
  - 사용자 선호도 기반 추천

- **Phase 3+**:
  - 비밀번호 재설정
  - 2FA (Two-Factor Authentication)
  - OAuth2 소셜 로그인
  - 역할 기반 접근 제어 (RBAC)
  - 계정 연계 (여러 계정 통합)
