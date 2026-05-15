# Implementation Plan: User Management & Data Integration

**Feature**: User Management (사용자 관리 및 분석 데이터 통합)
**Spec**: `/specs/002-user-management/spec.md`
**Created**: 2026-05-14
**Phase**: Phase 2 (Phase 1 의존)

---

## 1. Technical Context

### Project Architecture
- **Backend**: Java 21 / Spring Boot 4.0.5
- **Database**: MySQL (기존)
- **Authentication**: JWT (AccessToken + RefreshToken)
- **Token Storage**: RefreshToken은 HttpOnly, Secure 쿠키; AccessToken은 메모리/localStorage
- **API Pattern**: REST (Spring Web)

### Key Technologies
- **Security**: Spring Security (PasswordEncoder - BCrypt), JWT
- **ORM**: JPA/Hibernate
- **Validation**: Spring Validation
- **HTTP**: Spring Web, Set-Cookie headers
- **Logging**: Spring AOP (감시 로그)

### Dependencies
- **Phase 1 (Career Fortune API)**: SajuAnalysisResult, CareerFortuneResult, CompanyCompatibilityResult 생성
  - Phase 2는 이 분석 결과들을 User와 자동 매핑
  - Phase 1이 user_id를 포함하여 저장해야 함 (협력 필요)

---

## 2. Implementation Phases

### Phase 0: Setup & Research

#### 0.1 기술 검증
- [ ] Spring Security 구성 (JWT, 커스텀 필터, PasswordEncoder)
- [ ] HttpOnly 쿠키 설정 (Spring Web)
- [ ] Spring Security PasswordEncoder (BCrypt) 비밀번호 해싱
  - PasswordEncoder.encode(plainPassword) 저장
  - PasswordEncoder.matches(plainPassword, hash) 검증
- [ ] JPA Soft Delete 패턴 (@Where 어노테이션)

#### 0.2 의존성 확인
- [ ] Phase 1 API에서 user_id 자동 설정 방식 확인
- [ ] 기존 SajuResult 엔티티 구조 확인
- [ ] 데이터베이스 마이그레이션 전략

**Output**: research.md (기술 검증 완료)

---

### Phase 1: Data Model & API Design

#### 1.1 데이터 모델 (data-model.md)

**User Entity**
- PK: id (Long)
- email (String, UNIQUE, NOT NULL)
- password_hash (String) ← **Spring Security PasswordEncoder (BCrypt)**로 해싱된 값
  - 저장: PasswordEncoder.encode(plainPassword)
  - 검증: PasswordEncoder.matches(plainPassword, passwordHash)
  - 평문 비밀번호 저장 금지
- name (String)
- role (ENUM: USER, ADMIN) ← 기본값 USER
- status (ENUM: ACTIVE, INACTIVE)
- last_login_at (LocalDateTime)
- created_at (LocalDateTime)
- updated_at (LocalDateTime)
- deleted_at (LocalDateTime, nullable) ← Soft Delete
- **terms_agreed_at (LocalDateTime, NOT NULL)** ← 이용약관 동의 시점
- **privacy_agreed_at (LocalDateTime, NOT NULL)** ← 개인정보 수집/이용 동의 시점

**RefreshToken Entity**
- PK: id (Long)
- user_id (FK to User)
- token_hash (String, UNIQUE)
- expires_at (LocalDateTime)
- revoked_at (LocalDateTime, nullable)
- created_at (LocalDateTime)

**LoginAttempt Entity**
- PK: id (Long)
- email (String)
- success (Boolean)
- **failure_reason (ENUM: SUCCESS, INVALID_EMAIL, WRONG_PASSWORD, UNKNOWN)** ← 실패 사유 (내부 보안 감시용)
  - 클라이언트 응답은 "이메일 또는 비밀번호가 일치하지 않습니다"로 통일 (User Enumeration 방지)
  - 내부 로깅은 failure_reason에 상세히 기록
- ip_address (VARCHAR(45)) ← **ClientIpUtil로 X-Forwarded-For 헤더 분석하여 실제 클라이언트 IP 추출**
- attempted_at (LocalDateTime)

**DailyApiUsage Entity** (Race Condition 방지)
- PK: id (Long)
- user_id (FK to User)
- request_count (Integer)
- usage_date (LocalDate) ← **KST 기준** (LocalDate.now(ZoneId.of("Asia/Seoul")))
- created_at (LocalDateTime)
- **UNIQUE 제약**: (user_id, usage_date) 조합은 반드시 유일
  - 동시성 버그 방지: 동시 INSERT 방지
  - DB 레벨 강제: UNIQUE INDEX 필수

**SajuAnalysisResult Entity** (Phase 1에서 생성)
- PK: id (Long)
- user_id (FK to User) ← Phase 2 매핑
- target_name (String)
- birth_date (LocalDate)
- birth_time (LocalTime, nullable)
- analysis_data (JSON)
- created_at (LocalDateTime)

**CareerFortuneResult Entity** (Phase 1에서 생성)
- PK: id (Long)
- user_id (FK to User) ← Phase 2 매핑
- target_name (String)
- birth_date (LocalDate)
- birth_time (LocalTime, nullable)
- analysis_data (JSON)
- created_at (LocalDateTime)

**CompanyCompatibilityResult Entity** (Phase 1에서 생성)
- PK: id (Long)
- user_id (FK to User) ← Phase 2 매핑
- target_name (String)
- target_birth_date (LocalDate)
- target_birth_time (LocalTime, nullable)
- company_name (String)
- company_founded_date (LocalDate)
- compatibility_score (Integer)
- analysis_data (JSON)
- created_at (LocalDateTime)

**UserSatisfactionFeedback Entity**
- PK: id (Long)
- user_id (FK to User)
- feedback_type (ENUM: SAJU, CAREER_FORTUNE, COMPANY_COMPATIBILITY)
- saju_result_id (FK, nullable)
- career_fortune_result_id (FK, nullable)
- company_compatibility_result_id (FK, nullable)
- satisfaction_status (ENUM)
- feedback_content (String, nullable)
- created_at (LocalDateTime)
- updated_at (LocalDateTime)

#### 1.2 REST API 계약 (contracts/api.md)

**Authentication APIs**

```
POST /api/auth/signup
Request:
  {
    "email": "user@example.com",
    "password": "SecurePass123!",
    "name": "홍길동",
    "termsAgreed": true,          // 이용약관 동의
    "privacyAgreed": true         // 개인정보 수집/이용 동의
  }
Response (201):
  {
    "message": "회원가입 완료. 로그인해주세요.",
    "redirectUrl": "/login"
  }

POST /api/auth/login
Request:
  {
    "email": "user@example.com",
    "password": "SecurePass123!"
  }
Response (200):
  {
    "accessToken": "eyJhbGc...",
    "accessTokenExpiresIn": 3600,
    "message": "로그인 성공"
  }
Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Max-Age=604800

POST /api/auth/logout
Headers: Authorization: Bearer <accessToken>
Response (200):
  { "message": "로그아웃 완료" }
Set-Cookie: refreshToken=; Max-Age=0; HttpOnly; Secure

POST /api/auth/refresh
Cookies: refreshToken=...
Response (200):
  {
    "accessToken": "eyJhbGc...",
    "accessTokenExpiresIn": 3600
  }
```

**User APIs**

```
GET /api/mypage
Headers: Authorization: Bearer <accessToken>
Response (200):
  {
    "profile": {
      "id": 1,
      "name": "홍길동",
      "email": "user@example.com",
      "createdAt": "2026-05-14T10:00:00",
      "lastLoginAt": "2026-05-14T15:30:00"
    },
    "analyses": [
      {
        "id": 101,
        "type": "SAJU",
        "targetName": "홍길동",
        "birthDate": "1990-01-15",
        "analysisDate": "2026-05-14T10:30:00",
        "hasFeedback": true
      },
      ...
    ],
    "pagination": {
      "page": 1,
      "size": 20,
      "total": 45,
      "totalPages": 3
    }
  }

GET /api/mypage/analyses?type=SAJU&page=1&size=20
Headers: Authorization: Bearer <accessToken>
Response (200): (분석 결과 목록, 필터링/페이지네이션)

GET /api/mypage/analyses/{analysisId}
Headers: Authorization: Bearer <accessToken>
Response (200): (특정 분석 상세 결과 + 만족도 조사)

POST /api/mypage/reanalyze/{analysisId}
Headers: Authorization: Bearer <accessToken>
Request: (선택사항) { "birthTime": "14:30" }
Response (200 또는 429):
  {
    "message": "재분석 요청 완료",
    "newAnalysisId": 102
  }

DELETE /api/users/me
Headers: Authorization: Bearer <accessToken>
Request: { "password": "SecurePass123!" }
Response (200):
  { "message": "계정 삭제 완료. 로그인 페이지로 이동합니다." }
```

#### 1.3 빠른 시작 (quickstart.md)

**개발 환경 설정**
1. application-local.yaml에 DB 설정
2. 데이터베이스 마이그레이션 (User, RefreshToken 등 테이블)
3. JWT Secret Key 환경변수 설정
4. Spring Security 설정 클래스 작성

**테스트 흐름**
1. 회원가입 → User 생성
2. 로그인 → AccessToken + RefreshToken 쿠키 발급
3. API 호출 → AccessToken 검증
4. RefreshToken 갱신 → 새 AccessToken 발급
5. 로그아웃 → RefreshToken 무효화

---

### Phase 2: Implementation Strategy

#### 2.1 Authentication & Token Management (+ Compliance)
**담당**: Auth Controller + Token Service
- [ ] User 회원가입 (이메일 중복 확인, **Spring Security PasswordEncoder (BCrypt) 비밀번호 해싱**)
  - 방식: PasswordEncoder.encode(plainPassword) 저장
  - **약관 동의 검증**: termsAgreed && privacyAgreed 필드 확인 (필수)
  - **동의 기록**: terms_agreed_at, privacy_agreed_at에 현재 시간 저장 (법적 증거)
- [ ] User 로그인 (**PasswordEncoder.matches()로 비밀번호 검증**, AccessToken + RefreshToken 발급)
- [ ] Token 갱신 (RefreshToken으로 새 AccessToken 발급)
- [ ] Token 검증 필터 (Spring Security Filter)
- [ ] 로그아웃 (RefreshToken revoked_at 마크)

**HttpOnly 쿠키 설정**
```java
// CookieUtil.java
ResponseCookie cookie = ResponseCookie
    .from("refreshToken", tokenValue)
    .httpOnly(true)
    .secure(true)
    .sameSite("Strict")
    .maxAge(604800) // 7 days
    .build();
response.addHeader("Set-Cookie", cookie.toString());
```



#### 2.2 MyPage - 통합 분석 조회
**담당**: MyPageController + AnalysisService
- [ ] 사용자 프로필 조회
- [ ] SajuAnalysisResult + CareerFortuneResult + CompanyCompatibilityResult 통합 조회
- [ ] 분석 유형별 필터링
- [ ] 페이지네이션
- [ ] 만족도 조사 함께 표시 (LEFT JOIN)

**구현 전략** (권장: JdbcTemplate + Native Query)

ORM의 한계를 고려하여 다음 중 하나를 선택:

**Option A: JdbcTemplate + RowMapper** (권장 - 성능 최적)
```java
// UserAnalysisDto.java
@Data
public class UserAnalysisDto {
    private String type;  // SAJU, CAREER_FORTUNE, COMPANY_COMPATIBILITY
    private Long analysisId;
    private String targetName;
    private LocalDate birthDate;
    private LocalDateTime createdAt;
    private String satisfactionStatus;  // nullable
}

// AnalysisRepository.java (extends JdbcTemplate)
@Repository
public class AnalysisRepository {
    private final JdbcTemplate jdbcTemplate;

    public List<UserAnalysisDto> findAllUserAnalysesByUserId(Long userId, Pageable pageable) {
        // 마이페이지: 최근 1년 범위의 분석 결과만 조회 (spec 요구사항)
        String sql = """
            SELECT 'SAJU' as type, sar.id as analysisId, sar.target_name, sar.birth_date, sar.created_at, usf.satisfaction_status
            FROM saju_analysis_result sar
            LEFT JOIN user_satisfaction_feedback usf ON sar.id = usf.saju_result_id
            WHERE sar.user_id = ? AND sar.created_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)

            UNION ALL

            SELECT 'CAREER_FORTUNE', cfr.id, cfr.target_name, cfr.birth_date, cfr.created_at, usf.satisfaction_status
            FROM career_fortune_result cfr
            LEFT JOIN user_satisfaction_feedback usf ON cfr.id = usf.career_fortune_result_id
            WHERE cfr.user_id = ? AND cfr.created_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)

            UNION ALL

            SELECT 'COMPANY_COMPATIBILITY', ccr.id, ccr.target_name, ccr.target_birth_date as birth_date, ccr.created_at, usf.satisfaction_status
            FROM company_compatibility_result ccr
            LEFT JOIN user_satisfaction_feedback usf ON ccr.id = usf.company_compatibility_result_id
            WHERE ccr.user_id = ? AND ccr.created_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)

            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

        return jdbcTemplate.query(
            sql,
            new Object[]{userId, userId, userId, pageable.getPageSize(), pageable.getOffset()},
            (rs, rowNum) -> new UserAnalysisDto(
                rs.getString("type"),
                rs.getLong("analysisId"),
                rs.getString("targetName"),
                rs.getDate("birthDate").toLocalDate(),
                rs.getTimestamp("createdAt").toLocalDateTime(),
                rs.getString("satisfaction_status")
            )
        );
    }
}
```

**Option B: @Query(nativeQuery=true) + Interface Projection**
```java
public interface UserAnalysisProjection {
    String getType();
    Long getAnalysisId();
    String getTargetName();
    LocalDate getBirthDate();
    LocalDateTime getCreatedAt();
    String getSatisfactionStatus();
}

@Repository
public interface AnalysisRepository extends JpaRepository<SajuAnalysisResult, Long> {
    @Query(nativeQuery = true, value = """
        SELECT 'SAJU' as type, sar.id as analysisId, sar.target_name as targetName, ...
        FROM saju_analysis_result sar
        LEFT JOIN user_satisfaction_feedback usf ON sar.id = usf.saju_result_id
        WHERE sar.user_id = ? AND sar.created_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)

        UNION ALL
        ... (같은 방식으로 반복)

        ORDER BY createdAt DESC
        LIMIT ?1 OFFSET ?2
        """)
    Page<UserAnalysisProjection> findAllUserAnalyses(Long userId, Pageable pageable);
}
```

**성능 고려사항**
- JdbcTemplate (Option A): 가장 빠르고 가벼움, 영속성 컨텍스트 우회
- Native Query + Projection (Option B): 중간 성능, JPA 기능 활용 가능
- JPQL + 객체 병합: 피할 것 (복잡하고 느림)

#### 2.3 User 탈퇴 (Soft Delete + 마스킹)
**담당**: UserService + DeleteUserStrategy
- [ ] 비밀번호 재확인
- [ ] deleted_at 설정 (현재 시간)
- [ ] name → "탈퇴한 사용자"로 마스킹
- [ ] email → "deleted_{userId}_{timestamp}@deleted.local" 형태로 마스킹 (UNIQUE 제약 조건 충돌 방지, 각 탈퇴 사용자마다 고유한 값)
- [ ] 분석 결과도 함께 마스킹 처리
- [ ] RefreshToken revoked_at 마크
- [ ] 자동 로그아웃 (클라이언트에서 토큰 삭제)

**구현 패턴** (Hibernate 6.x 기준)
```java
@Entity
@Table(name = "user")
@SQLRestriction("deleted_at IS NULL")  // Hibernate 6.x: @Where 대신 @SQLRestriction 사용
// ⚠️ @SQLDelete 사용 금지: 마스킹된 필드값이 반영되지 않는 버그 발생
public class User {
    @Id
    private Long id;

    // ... 필드들
    private LocalDateTime deletedAt;
    private String name;
    private String email;
    private Role role;
    private UserStatus status;
}
```

**서비스 로직** (명시적 save() 활용)
```java
// UserService.java (또는 AuthService.deleteUser())
public void deleteUser(Long userId, String password) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException());

    // 비밀번호 재확인
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
        throw new InvalidPasswordException();
    }

    // ✅ 명시적 필드 수정 (JPA가 자동으로 UPDATE 생성)
    user.setName("탈퇴한 사용자");
    user.setEmail(String.format("deleted_%d_%d@deleted.local", userId, System.currentTimeMillis()));
    user.setStatus(UserStatus.INACTIVE);
    user.setDeletedAt(LocalDateTime.now());

    // ✅ 명시적 save() 호출: 위의 모든 변경사항이 DB에 반영됨
    userRepository.save(user);

    // RefreshToken 무효화
    refreshTokenRepository.findByUserId(userId)
        .forEach(token -> {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        });

    // 분석 결과 마스킹도 함께 처리
    maskUserAnalysisResults(userId);
}
```

**마스킹 및 쿼리 자동 필터링**:
- `@SQLRestriction("deleted_at IS NULL")`으로 모든 SELECT에서 탈퇴 사용자 자동 제외
- 탈퇴 후 로그인 시도 → "이메일 또는 비밀번호가 일치하지 않습니다" 에러 (User Enumeration 방지)

#### 2.4 로깅 & 보안

**2.5.1 EventPublisher 기반 비동기 로깅 (성능 최적화)**
- **패턴**: Spring EventPublisher + @EventListener (@Async)
  - AuthService에서 로그인 성공/실패 시 `LoginAttemptEvent` 발행
  - 별도의 `LoginAttemptEventListener`가 이벤트 구독하여 DB에 비동기 저장
  - **목적**: 로그인 API 응답 지연 방지 (네트워크 I/O가 응답 경로에서 제외)
  - failure_reason: INVALID_EMAIL, WRONG_PASSWORD, SUCCESS 등을 상세히 기록

```java
// Event 발행
public void login(LoginRequest req) {
    // ... 로그인 로직
    if (success) {
        eventPublisher.publishEvent(
            new LoginAttemptEvent(email, true, LoginFailureReason.SUCCESS, clientIp, LocalDateTime.now())
        );
    } else {
        eventPublisher.publishEvent(
            new LoginAttemptEvent(email, false, reason, clientIp, LocalDateTime.now())
        );
    }
}

// Event 수신 및 DB 저장
@EventListener
@Async
@Transactional
public void onLoginAttempt(LoginAttemptEvent event) {
    LoginAttempt attempt = new LoginAttempt();
    attempt.setEmail(event.getEmail());
    attempt.setSuccess(event.isSuccess());
    attempt.setFailureReason(event.getFailureReason());
    attempt.setIpAddress(event.getIpAddress());
    attempt.setAttemptedAt(event.getAttemptedAt());
    loginAttemptRepository.save(attempt);
}
```

**2.5.2 실제 클라이언트 IP 추출 (ClientIpUtil)**
- **패턴**: X-Forwarded-For 헤더 분석
  - Nginx, AWS ALB 등 리버스 프록시 환경에서 실제 클라이언트 IP 추출
  - 요청 헤더 우선순위: X-Forwarded-For → CF-Connecting-IP → X-Real-IP → request.getRemoteAddr()
- **적용**: LoginAttempt 저장 시 실제 클라이언트 IP를 ip_address에 기록

```java
public class ClientIpUtil {
    public static String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();  // 첫 번째 IP (클라이언트)
        }

        String cfConnecting = request.getHeader("CF-Connecting-IP");
        if (cfConnecting != null && !cfConnecting.isEmpty()) {
            return cfConnecting;
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }

        return request.getRemoteAddr();
    }
}
```

**2.5.3 기타 감시 로깅**
**담당**: AuditLoggingAspect
- [ ] 회원가입 로깅
- [ ] Token 갱신 로깅
- [ ] 탈퇴 로깅

#### 2.5 Daily API Usage Tracking
**담당**: APIUsageInterceptor + DailyApiUsageService
- [ ] 분석 API 요청 시 DailyApiUsage 증가 (Atomic Update 사용)
- [ ] KST (Asia/Seoul) 자정 기준 제한 초기화 (한국 사용자 UX 최적화)
- [ ] 429 응답 + "하루 3회 제한" 메시지

**구현 전략** (권장: Atomic Update - 동시성 제어)

Pessimistic Lock 대신 **Atomic Update**를 사용하여 고성능 유지 (5,000명 동시 접속 목표):

```java
// DailyApiUsageService.java
@Service
public class DailyApiUsageService {
    private final DailyApiUsageRepository dailyApiUsageRepository;
    private final JdbcTemplate jdbcTemplate;

    public void checkAndIncrementDailyUsage(Long userId) throws DailyLimitExceededException {
        // KST (Asia/Seoul) 자정 기준으로 일일 제한 초기화 (한국 사용자 UX 개선)
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // Atomic Update: request_count < 3 조건에서만 증가
        String sql = """
            UPDATE daily_api_usage
            SET request_count = request_count + 1
            WHERE user_id = ?
              AND usage_date = ?
              AND request_count < 3
            """;

        int updateCount = jdbcTemplate.update(sql, userId, today);

        if (updateCount == 0) {
            // 1. 이미 3회를 초과했거나
            // 2. 오늘의 기록이 없거나 (새 사용자)

            try {
                DailyApiUsage usage = dailyApiUsageRepository.findByUserIdAndUsageDate(userId, today);

                if (usage != null && usage.getRequestCount() >= 3) {
                    // 이미 3회 도달
                    throw new DailyLimitExceededException("하루 3회 분석 제한에 도달했습니다.");
                } else {
                    // 신규 데이터 생성
                    usage = new DailyApiUsage(userId, today, 1);
                    dailyApiUsageRepository.save(usage);
                }
            } catch (DataIntegrityViolationException e) {
                // Race Condition: 동시 요청 시 다른 스레드가 먼저 INSERT했을 경우
                // UNIQUE 제약 위반 → 무시하고 조회 재시도
                DailyApiUsage usage = dailyApiUsageRepository.findByUserIdAndUsageDate(userId, today);
                if (usage != null && usage.getRequestCount() >= 3) {
                    throw new DailyLimitExceededException("하루 3회 분석 제한에 도달했습니다.");
                }
                // 그 외의 경우: 이미 INSERT되었으므로 정상 처리
            }
        }
        // updateCount > 0이면 성공 (락 없음, 매우 빠름)
    }
}

// APIUsageInterceptor.java (또는 @Before Advice)
@Component
public class APIUsageInterceptor extends HandlerInterceptorAdapter {
    private final DailyApiUsageService dailyApiUsageService;
    private static final List<String> LIMITED_API_PATHS = Arrays.asList(
        "/api/analysis/saju",
        "/api/analysis/career-fortune",
        "/api/analysis/company-compatibility",
        "/api/analysis/reanalyze"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (LIMITED_API_PATHS.stream().anyMatch(path::startsWith)) {
            try {
                Long userId = getCurrentUserId();  // SecurityContext에서 추출
                dailyApiUsageService.checkAndIncrementDailyUsage(userId);
            } catch (DailyLimitExceededException e) {
                response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);  // 429
                response.setContentType("application/json");
                response.getWriter().write("""
                    {"error": "daily_limit_exceeded", "message": "%s"}
                    """.formatted(e.getMessage()));
                return false;
            }
        }
        return true;
    }
}
```

**성능 이점**
- **Pessimistic Lock 제거**: DB 락으로 인한 트랜잭션 대기 제거
- **Atomic Update**: DB 수준의 원자적 업데이트 (Race Condition 방지)
- **응답 시간**: < 100ms (락 대기 없음)
- **5,000명 동시 접속 가능**: 병목 현상 제거

**SQL 인덱스 최적화** (Race Condition 방지)
```sql
-- UNIQUE INDEX 추가 (필수: Race Condition 방지)
CREATE UNIQUE INDEX idx_daily_api_usage_user_date ON daily_api_usage(user_id, usage_date);

-- UNIQUE 제약 조건 추가 (대안: 테이블 생성 시)
ALTER TABLE daily_api_usage
ADD CONSTRAINT uk_user_date UNIQUE (user_id, usage_date);

-- UPDATE 성능 검증
EXPLAIN UPDATE daily_api_usage
SET request_count = request_count + 1
WHERE user_id = ? AND usage_date = ? AND request_count < 3;
```

**동시성 처리 메커니즘**:
1. **DB 레벨**: UNIQUE INDEX로 중복 INSERT 방지
2. **애플리케이션 레벨**: DataIntegrityViolationException catch → 재조회 후 처리

#### 2.6 Security Configuration
**담당**: SecurityConfig
- [ ] Spring Security 필터 체인
- [ ] JWT Token 검증 필터
- [ ] CORS 설정 (프론트엔드 도메인)
- [ ] CSRF 보호
- [ ] Rate Limiting (옵션, Phase 2.x)

---

## 3. Implementation Tasks

### Task 1: Entity & Repository 구성
```
- User, RefreshToken, LoginAttempt, DailyApiUsage 엔티티
- 각 Repository 작성
- Soft Delete 패턴 적용 (@SQLRestriction 사용, @SQLDelete 금지)
- Index 설정 (email, user_id, usage_date)
```

### Task 2: Authentication Service (+ Compliance)
```
- UserService: 회원가입, 로그인, 비밀번호 검증
  - 회원가입 시 termsAgreed, privacyAgreed 필드 검증
  - terms_agreed_at, privacy_agreed_at에 현재 시간 기록 (법적 증거 용)
- TokenService: AccessToken/RefreshToken 생성, 검증, 갱신
- TokenProvider: JWT 토큰 처리
```

### Task 3: REST API 구현
```
- AuthController: /api/auth/signup, /api/auth/login, /api/auth/logout, /api/auth/refresh
- UserController: /api/mypage, /api/mypage/analyses, /api/mypage/reanalyze, /api/users/me
- ErrorHandler: 예외 처리 (401, 429, 400 등)
```

### Task 4: Security & Filter
```
- SecurityConfig: Spring Security 설정
- JwtAuthenticationFilter: JWT 검증 필터
- CookieUtil: HttpOnly 쿠키 설정
- CORS, CSRF 설정
```

### Task 5: MyPage Integration
```
- AnalysisService: 통합 분석 조회 쿼리
- MyPageController: 프로필, 분석 목록, 필터링, 페이지네이션
- 만족도 조사 함께 표시 (LEFT JOIN)
```

### Task 6: User Withdrawal
```
- DeleteUserStrategy: Soft Delete + 마스킹 로직
- RefreshToken 무효화
- 분석 결과 마스킹
- 자동 로그아웃 (클라이언트 협력)
```

### Task 7: Daily API Usage Tracking (Atomic Update)
```
- APIUsageInterceptor: API 호출 시마다 체크
- DailyApiUsageService: Atomic Update로 카운트 증가 (DB 락 제거)
  UPDATE daily_api_usage SET request_count = request_count + 1
  WHERE user_id = ? AND usage_date = ? AND request_count < 3
- 429 응답 처리 (UpdateCount가 0인 경우)
- KST (Asia/Seoul) 자정 기준 초기화 (스케줄러, 한국 사용자 기준)
- 인덱스 최적화: idx_daily_api_usage_user_date
```

### Task 8: Logging & Audit
```
- AuditLoggingAspect: 인증 관련 이벤트 로깅
- LoginAttemptEvent 기반 비동기 로깅 (EventPublisher)
- ClientIpUtil로 실제 IP 추출 및 저장
```

### Task 9: Phase 1 Integration
```
- SajuAnalysisResult 등에서 user_id 자동 설정
- Phase 1 API 수정 필요 (협력)
- 통합 테스트
```

### Task 10: Testing & Validation
```
- Unit Test: Service, Repository, Util
- Integration Test: API 엔드포인트
- Security Test: Token, 권한, CORS
- Performance Test: 동시 접속, 응답 시간
```

---

## 4. Success Metrics (Success Criteria 검증)

| 메트릭 | 목표 | 검증 방법 |
|--------|------|----------|
| 회원가입 → 로그인 시간 | 2분 이내 | 수동 테스트 |
| 로그인 응답 시간 | 500ms 이하 | JMeter |
| Token 갱신 응답 시간 | 300ms 이하 | JMeter |
| 동시 접속 5,000명 | 500ms 응답 | 부하 테스트 |
| 회원가입 성공률 | 95% | 통합 테스트 |
| PasswordEncoder (BCrypt) 사용률 | 100% | 코드 리뷰 |
| 비밀번호 평문 저장 | 0% | DB 검증 |
| RefreshToken HttpOnly | 100% | 브라우저 검증 |
| XSS 취약점 | 0% | 보안 검사 |
| User 탈퇴 마스킹 (이메일 고유성) | 100% | DB 검증 (UNIQUE 제약 미위반) |
| **일일 API 제한 (동시성)** | **Race Condition 0%** | **10개 동시 요청 테스트** |
| **UNIQUE INDEX 적용** | **100%** | **DB 스키마 검증** |
| 분석 결과 user_id 매핑 | 100% | 통합 테스트 |

---

## 5. 위험 요소 & 완화 전략

| 위험 | 영향 | 완화 전략 |
|------|------|----------|
| Phase 1에서 user_id 미포함 | 높음 | 조기에 협력, 테스트 강화 |
| Token 탈취 | 높음 | HttpOnly 쿠키, HTTPS 강제 |
| Soft Delete 마스킹 버그 | 중간 | @SQLDelete 사용 금지, 명시적 필드 수정 및 save() 호출, 단위 테스트 |
| 데이터베이스 마이그레이션 | 중간 | 사전 백업, 롤백 계획 |
| 동시성 문제 (DailyApiUsage) | 높음 | UNIQUE INDEX (user_id, usage_date) + Atomic UPDATE + DataIntegrityViolationException 처리 |
| RefreshToken 만료 처리 | 낮음 | 명확한 에러 메시지, 클라이언트 가이드 |

---

## 6. 일정 (Estimate)

| Phase | Task | 예상 기간 | 담당 |
|-------|------|----------|------|
| 0 | Entity & Repository | 1-2일 | Backend |
| 1 | Auth Service | 2-3일 | Backend |
| 1 | REST API | 2-3일 | Backend |
| 1 | Security Config | 1-2일 | Backend |
| 2 | MyPage Integration | 2-3일 | Backend |
| 2 | User Withdrawal | 1-2일 | Backend |
| 2 | Daily Usage Tracking | 1-2일 | Backend |
| 2 | Logging & Audit | 1일 | Backend |
| 3 | Phase 1 Integration | 1-2일 | Backend + Phase 1 Team |
| 4 | Testing & Validation | 2-3일 | QA + Backend |

**총 기간**: 14-24일 (병렬 진행 기준)

---

## 7. 다음 단계

1. **Tasks 생성**: `/speckit-tasks` 명령으로 actionable tasks 생성
2. **Code Review**: 각 Task별 PR 생성, 리뷰 진행
3. **Integration**: Phase 1과의 협력 확인
4. **Deployment**: Staging → Production 배포

---

**생성 날짜**: 2026-05-14
**최종 수정**: 2026-05-14
