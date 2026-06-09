# Implementation Plan: 관리자 대시보드 및 모니터링 시스템

**Branch**: `003-admin-dashboard` | **Date**: 2026-06-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-admin-dashboard/spec.md`

## Summary

관리자용 웹 기반 모니터링 시스템으로, 4개 화면(대시보드, 유저 관리, 분석 기록, 피드백 관리)을 제공합니다.

**주요 기능:**
- P1: 대시보드 (일일 분석 현황), 유저 검색 및 프로필 조회 기능
- P2: 분석 기록 조회 및 일일 제한 수동 초기화, 피드백 통계 및 분석 결과 연관 링크

**기술 접근:** Thymeleaf 기반 SSR, Spring Boot REST API + 관리자 화면, 기존 엔티티 재사용, 페이지네이션으로 대량 데이터 처리

---

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.0.5

**Primary Dependencies**:
- Spring Data JPA (ORM)
- Thymeleaf (SSR 템플릿)
- Spring Web MVC
- MySQL Driver (DB 연결)

**Storage**: MySQL (기존 구현 기반, 추가 마이그레이션 불필요)

**Testing**: JUnit 5 (Mockito), Spring Boot Test, Integration Tests

**Target Platform**: Linux server (Spring Boot embedded Tomcat)

**Project Type**: Web application (MVC 아키텍처)

**Performance Goals**:
- 대시보드 로드: 5초 이내 (SC-001)
- 유저 검색(1000 레코드): 2초 이내 (SC-002)
- 전체 화면 응답: 3초 이내 (SC-006)

**Constraints**:
- Asia/Seoul 타임존 필수 (기존 구현 기반)
- UTF-8 인코딩 (한글 포함 텍스트 처리)
- 페이지네이션: 기본 20-50개 항목/페이지
- 데이터 조회 범위: 최근 30일 (대량 데이터 방지)

**Scale/Scope**:
- 4개 관리자 화면 (대시보드, 유저, 분석, 피드백)
- 13개 기능 요구사항
- 기존 4개 엔티티 활용 (SajuAnalysis, User, DailyApiUsage, UserSatisfactionFeedback)

---

## Constitution Check

**Gate 1**: 명세서 검증
- ✅ 모든 스토리, 요구사항, 성공 기준 완료
- ✅ 모호성 제거 완료 (Clarifications 섹션)
- ✅ Ban 기능 제거로 도메인 설계 정합성 확보

**Gate 2**: 아키텍처 적합성
- ✅ Spring Boot MVC 패턴 적합
- ✅ 기존 엔티티와 통합 가능 (Phase 2 기반)
- ✅ 타임리프로 소규모 UI 렌더링 가능
- ✅ REST API (조회 + 관리자 조정 POST) 필요 (일일 사용량 조정은 필수 기능)

**Gate 3**: 기술 스택 검증
- ✅ Java 21 / Spring Boot 4.0.5 기존 프로젝트
- ✅ MySQL / JPA 기존 구현
- ✅ 별도 의존성 추가 최소화

---

## Project Structure

### Documentation (this feature)

```text
specs/003-admin-dashboard/
├── spec.md                  # Feature specification ✅
├── plan.md                  # This file (implementation plan)
├── research.md              # Phase 0 output (dependency/pattern research)
├── data-model.md            # Phase 1 output (entity extensions, queries)
├── quickstart.md            # Phase 1 output (setup & local dev guide)
├── contracts/               # Phase 1 output (API endpoints, Thymeleaf views)
│   ├── admin-dashboard-api.md
│   └── admin-views.md
└── tasks.md                 # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
src/main/java/ssafy/SSAju/
├── admin/                          # 관리자 기능 모듈 (신규)
│   ├── controller/
│   │   ├── AdminDashboardController.java      # 대시보드
│   │   ├── AdminUserManagementController.java # 유저 관리
│   │   ├── AdminAnalyticsController.java      # 분석 기록
│   │   ├── AdminUsageAdjustmentController.java # 일일 사용량 조정
│   │   └── AdminFeedbackController.java       # 피드백 관리
│   ├── service/
│   │   ├── AdminDashboardService.java
│   │   ├── AdminUserService.java
│   │   ├── AdminAnalyticsService.java
│   │   ├── AdminUsageAdjustmentService.java
│   │   └── AdminFeedbackService.java
│   ├── dto/
│   │   ├── DashboardDTO.java
│   │   ├── UserSearchDTO.java
│   │   ├── AnalyticsListDTO.java
│   │   ├── AnalyticsDetailDTO.java
│   │   ├── FeedbackListDTO.java
│   │   └── FeedbackStatDTO.java
│   ├── repository/                 # Custom queries (기존 엔티티 활용)
│   │   ├── AdminAnalyticsQueryRepository.java
│   │   ├── AdminFeedbackQueryRepository.java
│   │   ├── AdminUserQueryRepository.java
│   │   └── AdminDailyUsageQueryRepository.java
│   └── validation/
│       └── [validation utilities]
│
├── [existing modules...]
│   ├── user/
│   ├── career/
│   ├── config/
│   ├── controller/
│   ├── service/
│   ├── dto/
│   ├── repository/
│   └── ...
│
└── config/
    ├── AdminSecurityConfig.java    # 관리자 권한 설정
    └── [existing configs...]

src/main/resources/templates/admin/   # Thymeleaf 템플릿 (신규)
├── layout/
│   ├── admin-header.html
│   ├── admin-sidebar.html
│   └── admin-footer.html
├── dashboard.html
├── user-management.html
├── analytics-history.html
└── feedback-management.html

src/test/java/ssafy/SSAju/admin/
├── controller/
│   ├── AdminDashboardControllerTest.java
│   ├── AdminUserManagementControllerTest.java
│   ├── AdminAnalyticsControllerTest.java
│   └── AdminFeedbackControllerTest.java
├── service/
│   ├── AdminDashboardServiceTest.java
│   ├── AdminUserServiceTest.java
│   ├── AdminAnalyticsServiceTest.java
│   ├── AdminUsageAdjustmentServiceTest.java
│   └── AdminFeedbackServiceTest.java
└── integration/
    └── AdminDashboardIntegrationTest.java
```

---

## Phase 0: Research & Unknowns

### Identified Research Areas

**1. Soft Delete & 이메일 마스킹 구현**
- Status 필드와 DeletedAt 필드의 JPA 쿼리 방식
- 마스킹된 이메일 생성 로직 (예: deleted_123_user@masked.local)
- 기존 User 엔티티 구조 확인 필요

**2. 일일 제한 로직 (DailyApiUsage)**
- 자정 리셋 메커니즘 (스케줄러 vs 조회 시점 계산)
- Asia/Seoul 타임존 적용 방식
- 기존 DailyApiUsage 테이블 스키마 확인

**3. 대시보드 성능 최적화 (5초 목표)**
- SajuAnalysis 테이블 인덱싱 전략 (CreatedAt, AnalysisType)
- 집계 쿼리 (COUNT, GROUP BY) 최적화
- 조인 복잡도 (User, SajuAnalysis, UserSatisfactionFeedback)

**4. JSON 데이터 렌더링**
- Thymeleaf에서 JSON 문자열 Escape 처리
- 한글 특수 문자 UTF-8 인코딩 보장 (DB 설정 확인)

**5. 페이지네이션 & 정렬**
- Spring Data Page<T> 활용
- Thymeleaf 템플릿에서 Page 객체 처리
- QueryDSL 또는 JPA Criteria API 선택

---

## Phase 1: Design & Contracts

### 1. Data Model

**User 엔티티**
- Fields: id, email, name, status, createdAt, deletedAt
- Validation: Status IN (ACTIVE, INACTIVE)
- Index: idx_deleted_at (Soft Delete 필터링 성능)
- Masking 처리: softDelete() 메서드에서 email 필드를 deleted_{userId}_{epochSecond}@deleted.local 형식으로 DB에 자동 적용
  - **중요**: 원본 이메일은 DB에 저장되지 않음 (완전히 마스킹된 값으로 덮어씀)
  - 탈퇴 유저의 status=INACTIVE이므로 프론트엔드에서 status로 탈퇴 여부 판단

**SajuAnalysis 엔티티 (기존)**
- Fields: id, userId, createdAt, jsonData, analysisType
- Index: idx_user_created_at, idx_analysis_type_created_at (대시보드 집계)
- Constraint: analysisType IN ('SAJU', 'GWANWUN', 'GUNG_HAP')

**UserSatisfactionFeedback 엔티티 (기존)**
- Fields: id, userId, sajuAnalysisId, satisfactionScore, feedbackContent, createdAt
- Index: idx_user_created_at (피드백 목록 조회)
- Constraint: satisfactionScore BETWEEN 1 AND 5

**DailyApiUsage 엔티티 (기존)**
- Fields: id, userId, date, usageCount
- Unique: (userId, date)
- Query: Reset at midnight Asia/Seoul time

### 2. API Contracts (관리자 전용 REST)

**대시보드 API**
```http
GET /admin/dashboard
Response: {
  totalAnalysis: int,
  analysisTypeBreakdown: { SAJU: int, GWANWUN: int, GUNG_HAP: int },
  dailyLimitExhaustedCount: int,
  feedbackSummary: { satisfiedCount: int, unsatisfiedCount: int, unviewedCount: int }
}
```

**유저 관리 API (조회만)**
```http
GET /admin/users?email=...&name=...&joinDate=...&status=ACTIVE|INACTIVE
Response: Page<UserSearchDTO>

GET /admin/users/{id}
Response: { id, email, name, joinDate, status, deletedAt, totalAnalysisCount }
(주의: 탈퇴한 유저의 경우 email은 이미 마스킹된 형식(deleted_{userId}_{epochSecond}@deleted.local)으로 DB에 저장됨)
```

**분석 기록 API**
```http
GET /admin/analytics?type=SAJU|GWANWUN|GUNG_HAP&page=0&size=20
Response: Page<AnalyticsListDTO>

GET /admin/analytics/{id}
Response: { id, userId, analysisType, jsonData, createdAt }
```

**일일 제한 수동 조정 API (RESTful 설계)**
```http
POST /admin/daily-usages/users/{userId}/adjust
Body: { "action": "RESET" } 또는 { "action": "DECREMENT", "amount": 1 }
Response: { userId, date, usageCountBefore, usageCountAfter, action }
```

**피드백 관리 API**
```http
GET /admin/feedback?type=SAJU|GWANWUN|GUNG_HAP&page=0&size=20
Response: Page<FeedbackListDTO>

GET /admin/feedback/stats
Response: { satisCountBySajuType, averageScore, totalFeedbackCount }

GET /admin/feedback/{id}/analysis/{analysisId}
Response: { feedback, analysis }
```

### 3. Thymeleaf Views

**admin/dashboard.html**
- 4개 위젯: 분석 현황, 사용량 경고, 피드백 요약
- 수동 새로고침 버튼
- 반응형 디자인

**admin/user-management.html**
- 검색 폼 (email, name, joinDate range, status filter)
- 테이블 (페이지네이션, 정렬)
- 상세 프로필 모달 (분석 이용 내역)

**admin/analytics-history.html**
- 분석 기록 테이블 (type filter, date range, 페이지네이션)
- JSON 원문 뷰 (코드 블록, 인코딩)
- 사용량 초기화 폼 (리셋/차감 옵션, 확인 모달)

**admin/feedback-management.html**
- 만족도 통계 차트 (유형별 평균, 분포)
- 피드백 목록 테이블 (type filter, 정렬)
- 분석 결과 연관 링크

---

## Key Design Decisions

| 결정 | 선택사항 | 이유 |
|------|---------|------|
| **유저 관리** | 조회만 (Ban 제거) | 유저 자발적 탈퇴만 지원, 인증 도메인에서 매크로 방어 |
| **탈퇴 유저 필터링** | Soft Delete (deleted_at 필드) | 데이터 감사 추적, 마스킹된 이메일 사용 |
| **결제 기능** | v2 이상에서 구현 | 현재는 분석 이용 기록만 표시 |
| **일일 제한 초기화** | 리셋 & 차감 모두 | CS 대응 유연성 |
| **API 경로** | POST /admin/daily-usages/users/{userId}/adjust | 도메인 중심 설계 (DailyApiUsage 주체 리소스) |
| **렌더링 방식** | Thymeleaf SSR | 기존 프로젝트 스택 |
| **페이지네이션** | Spring Data Page | 기존 의존성 |
| **대시보드 자동 새로고침** | 제외 (수동) | 복잡도 감소, v1 scope |

---

## Risks & Mitigations

| 위험 | 영향 | 완화 전략 |
|------|------|---------|
| **대량 데이터 성능** | 대시보드 5초 목표 미달성 | 인덱싱 + 쿼리 최적화 + 데이터 범위 제한 |
| **타임존 버그** | 일일 제한 오류 | 테스트 (자정 근처), Asia/Seoul 명시 |
| **JSON 인코딩** | 특수 문자 깨짐 | UTF-8 인코딩 검증 테스트 |
| **기존 엔티티 변경** | 사이드 이펙트 | DB 마이그레이션 계획, 통합 테스트 |
| **관리자 접근 제어** | 보안 위협 | Spring Security ROLE_ADMIN 설정 |

---

**Status**: Ready for Phase 0-1 Documentation
**Next Command**: `/speckit-tasks` 실행 (Phase 2)
