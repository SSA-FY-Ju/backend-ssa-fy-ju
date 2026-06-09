# SSAju Backend

> SSAFY 사주 기반 커리어 상담 서비스의 백엔드 (Java 21 / Spring Boot 4.0.5)

## 🚀 Quick Start

### 사전 요구사항
- Java 21
- Gradle 9.4.1 이상
- MySQL 8.0+

### 빌드 & 실행

```bash
# 프로젝트 루트: SSAju/

# 1️⃣ 빌드
./gradlew build

# 2️⃣ 실행
./gradlew bootRun

# 3️⃣ 테스트
./gradlew test
```

**실행 후**: `http://localhost:8080`에서 서비스 접근 가능

---

## 📦 기술 스택

| 계층 | 기술 |
|------|------|
| **백엔드 프레임워크** | Spring Boot 4.0.5 |
| **언어** | Java 21 |
| **ORM** | Spring Data JPA + Hibernate |
| **데이터베이스** | MySQL 8.0 |
| **보안** | Spring Security 7.0.4 + JWT (AccessToken/RefreshToken) |
| **템플릿 엔진** | Thymeleaf (SSR 관리자 페이지) |
| **테스팅** | JUnit 5 + Mockito + Testcontainers |
| **빌드 도구** | Gradle 9.4.1 |

---

## 🏗️ 프로젝트 구조

```
backend-ssa-fy-ju/
├── SSAju/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/ssafy/SSAju/
│   │   │   │   ├── admin/               # Phase 2-3: 관리자 모듈
│   │   │   │   │   ├── config/          # Security, Authentication 필터
│   │   │   │   │   ├── controller/      # 관리자 페이지 컨트롤러
│   │   │   │   │   ├── service/         # 관리자 비즈니스 로직
│   │   │   │   │   ├── dto/             # Data Transfer Objects
│   │   │   │   │   ├── repository/      # JPA/JdbcTemplate 쿼리
│   │   │   │   │   └── validation/      # 입력값 검증
│   │   │   │   ├── entity/              # JPA 엔티티
│   │   │   │   │   ├── User.java
│   │   │   │   │   ├── SajuAnalysis.java
│   │   │   │   │   ├── UserSatisfactionFeedback.java
│   │   │   │   │   ├── DailyApiUsage.java
│   │   │   │   │   ├── RefreshToken.java
│   │   │   │   │   └── enums/
│   │   │   │   ├── controller/          # REST API 컨트롤러
│   │   │   │   ├── service/             # 비즈니스 로직 서비스
│   │   │   │   ├── repository/          # Spring Data JPA Repository
│   │   │   │   ├── config/              # Spring 설정 (Security, DB, etc)
│   │   │   │   ├── filter/              # JWT 인증 필터
│   │   │   │   ├── exception/           # 커스텀 예외 클래스
│   │   │   │   ├── handler/             # 전역 예외 핸들러
│   │   │   │   ├── dto/                 # REST API DTO
│   │   │   │   └── util/                # 유틸리티 (JWT, ClientIp 등)
│   │   │   └── resources/
│   │   │       ├── templates/           # Thymeleaf 템플릿 (관리자 페이지)
│   │   │       │   └── admin/
│   │   │       │       ├── layout/      # 레이아웃 (header, sidebar, footer)
│   │   │       │       ├── login.html
│   │   │       │       └── dashboard.html (Phase 3)
│   │   │       ├── application.yaml     # 기본 설정
│   │   │       ├── application-admin.yaml
│   │   │       ├── application-local.yaml (로컬 개발용, .gitignore)
│   │   │       └── schema.sql           # 초기 DB 스키마
│   │   ├── test/
│   │   │   └── java/ssafy/SSAju/
│   │   │       ├── admin/               # 관리자 모듈 테스트
│   │   │       ├── service/             # 서비스 유닛 테스트
│   │   │       ├── controller/          # 컨트롤러 유닛 테스트
│   │   │       └── integration/         # 통합 테스트
│   ├── build.gradle                     # 의존성, 빌드 설정
│   └── gradlew                          # Gradle wrapper
├── CLAUDE.md                            # 개발 지침 (필독)
├── skills/                              # 개발 가이드 문서
│   ├── code-style-guide.md              # 코딩 스타일 (DTO record, 네이밍 등)
│   ├── git-workflow.md                  # Git 커밋 규칙 (Conventional Commits)
│   └── architecture-guide.md            # 아키텍처 가이드 (예외 처리 등)
└── specs/                               # 기능 명세
    ├── 001-career-fortune-api/          # Phase 1: 사주 분석 API
    ├── 002-user-management/             # Phase 2: 사용자 관리 시스템
    └── 003-admin-dashboard/             # Phase 3-4: 관리자 대시보드
        ├── spec.md                      # 요구사항 명세
        ├── plan.md                      # 구현 설계
        └── tasks.md                     # 구현 작업 목록
```

---

## ✨ 주요 기능

### Phase 1: 사주 분석 API
- ✅ 사주 운세 분석 (SAJU)
- ✅ 관운 분석 (GWANWUN)
- ✅ 궁합 분석 (GUNG_HAP)
- ✅ 일일 API 사용량 제한 (3회/일)

### Phase 2: 사용자 관리 시스템
- ✅ 회원가입/로그인 (이메일 기반)
- ✅ 비밀번호 암호화 (Spring Security)
- ✅ JWT 인증 (AccessToken 1시간 + RefreshToken 7일)
- ✅ 역할 기반 접근 제어 (ROLE_USER, ROLE_ADMIN)
- ✅ 소프트 딜리트 (탈퇴 유저 데이터 보존)
- ✅ 사용자 만족도 피드백 수집

### Phase 3-4: 관리자 대시보드 (진행 중)
- ✅ 관리자 로그인 + 쿠키 기반 SSR 인증
- 🔄 대시보드 (분석 현황, 일일 한도 소진, 피드백 요약)
- 🔄 유저 관리 (검색, 필터, 상세 프로필)
- 🔄 분석 기록 (조회, JSON 검증, 일일 한도 초기화)
- 🔄 피드백 관리 (통계, 연관 분석 링크)

---

## 🔐 보안 & 환경 설정

### 민감 정보 관리

⚠️ **소스 코드에 절대 포함하면 안 됨:**
- API Key (OpenAI, FastAPI, 공공데이터 API)
- 데이터베이스 비밀번호
- 사용자 개인정보

✅ **올바른 방식:**
- 모든 민감 정보는 **환경변수** 사용: `${OPENAI_API_KEY}`, `${DB_PASSWORD}`
- `.gitignore`에 포함: `application-local.yaml`, `.env`, `*.properties`

### 로컬 개발 환경

**로컬 설정**은 `application-local.yaml`에서 관리:
```yaml
# application-local.yaml (Git에 커밋 금지)
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ssaju_dev
    username: root
    password: ${DB_PASSWORD}  # 환경변수 사용

jwt:
  access-token-expiration: 3600000   # 1시간 (ms)
  refresh-token-expiration: 604800000 # 7일 (ms)
```

각 개발자가 로컬에서 자신의 정보로 설정하세요.

---

## 📖 개발 가이드

### 필독 문서

| 문서 | 내용 |
|------|------|
| **[CLAUDE.md](./CLAUDE.md)** | 프로젝트 전체 지침 (필수!) |
| **[code-style-guide.md](./skills/code-style-guide.md)** | 코딩 스타일 & JPA 사용법 |
| **[git-workflow.md](./skills/git-workflow.md)** | Git & 커밋 규칙 (Conventional Commits) |
| **[architecture-guide.md](./skills/architecture-guide.md)** | 아키텍처 & 예외 처리 |

### 주요 개발 규칙

#### 1. Conventional Commits
```bash
feat: 새로운 기능
fix: 버그 수정
refactor: 코드 리팩토링
test: 테스트 추가
chore: 빌드, 의존성 등
```

모든 커밋은 `[Test Passed]` 푸터 포함:
```bash
git commit -m "feat: 기능 설명

설명 (선택사항)

[Test Passed]

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

#### 2. DTO는 record 타입 사용
```java
// ✅ 올바른 방식
public record UserLoginResponseDTO(String accessToken, String refreshToken, long expiresIn) {}

// ❌ 피해야 할 방식
@Data
@Builder
public class UserLoginResponseDTO { ... }
```

#### 3. 예외 처리
- 커스텀 예외는 `AuthException`, `InvalidTokenException` 등 도메인별로 분류
- 전역 예외 핸들러 (`SajuGlobalExceptionHandler`) 경유
- HTTP 상태 코드 + 표준 에러 응답 형식

#### 4. 영속성 계층
- Spring Data JPA `Repository` 기반
- 복잡한 쿼리는 `@Query(nativeQuery=true)` 또는 JdbcTemplate 사용
- Soft Delete: `@SQLRestriction("deleted_at IS NULL")`

---

## 📊 데이터베이스 스키마

### 주요 엔티티

**users** (사용자)
- id (PK), email (UNIQUE), password_hash, name
- role (ENUM: USER, ADMIN), status (ENUM: ACTIVE, SUSPENDED, DELETED)
- terms_agreed_at, privacy_agreed_at, deleted_at (소프트 딜리트)

**saju_analyses** (분석 기록)
- id (PK), user_id (FK), analysis_type (ENUM: SAJU, GWANWUN, GUNG_HAP)
- result_json, created_at

**user_satisfaction_feedback** (만족도 피드백)
- id (PK), user_id (FK), analysis_id (FK), feedback_content
- satisfaction_status (ENUM: SATISFIED, UNSATISFIED), feedback_type, created_at

**daily_api_usages** (일일 API 사용량)
- id (PK), user_id (FK), usage_date, request_count
- UNIQUE: (user_id, usage_date)

**refresh_tokens** (리프레시 토큰)
- id (PK), user_id (FK), token_hash, expires_at, revoked_at

---

## 🧪 테스팅

### 테스트 실행
```bash
# 전체 테스트
./gradlew test

# 특정 테스트 클래스만
./gradlew test --tests "ssafy.SSAju.admin.controller.AdminLoginControllerTest"

# 테스트 + 커버리지
./gradlew test jacocoTestReport
```

### 테스트 구조

- **Unit Tests**: Mockito로 의존성 mock화
- **Integration Tests**: Testcontainers (MySQL) 사용
- **Controller Tests**: MockMvc 활용

---

## 🐛 트러블슈팅

### 로컬에서 빌드 실패
```bash
# 1. 빌드 캐시 삭제
./gradlew clean

# 2. 다시 빌드
./gradlew build
```

### Testcontainers MySQL 연결 실패
- Docker 데몬이 실행 중인지 확인
- `~/.docker/config.json` 권한 확인 (chmod 600)

### JWT 토큰 만료 에러
- `application-local.yaml`의 `jwt.access-token-expiration` 값 확인
- RefreshToken으로 재발급 요청 (POST `/api/auth/refresh`)

---

## 📝 커밋 히스토리

```
Phase 2 (User Management) ✅
├─ 사용자 회원가입/로그인
├─ JWT 인증 (Access + Refresh Token)
├─ 역할 기반 접근 제어 (ROLE_USER, ROLE_ADMIN)
└─ 소프트 딜리트 처리

Phase 3 (Admin Dashboard - 진행 중) 🔄
├─ 관리자 로그인 (US0) ✅
├─ 대시보드 (US1) 🔄
├─ 유저 관리 (US2) 🔄
├─ 분석 기록 (US3) 🔄
└─ 피드백 관리 (US4) 🔄
```

---

## 🤝 기여 가이드

1. **기능 개발 전** 명세서 & 계획 검토 (specs/003-admin-dashboard/)
2. **적절한 브랜치** 생성: `feat/기능명`, `fix/버그명`
3. **테스트 먼저** 작성 (Test-Driven Development 권장)
4. **Conventional Commits** 준수
5. **코드 스타일** 가이드 준수 (code-style-guide.md)
6. **PR 생성** 후 코드 리뷰 받기

---

## 📞 문의

프로젝트 관련 질문이나 버그 리포트는 GitHub Issues 또는 팀 Slack 채널을 통해 주세요.

---

**Last Updated**: 2026-06-09 | **Status**: Phase 2 완료, Phase 3 진행 중
