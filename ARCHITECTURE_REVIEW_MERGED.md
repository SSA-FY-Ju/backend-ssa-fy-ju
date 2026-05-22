# 아키텍처 리뷰 — SSAju Backend (통합본)

> **초판 작성일**: 2026-05-20  
> **최종 갱신**: 2026-05-22  
> **기준**: SOLID, 계층형 아키텍처, JPA 최적화, 예외/동시성, 성능, 방어적 프로그래밍  
> **현황**: Phase 1 리팩토링 완료 (Critical 7/7 ✅, Major 2/10, Minor 3/11, TODO 0/4)  
> **정렬**: Critical → Major → Minor, 마지막에 TODO 전수 목록

---

## ⛔ Critical

---

### [C-1] `AuthService.login()` — `HttpServletResponse` 서비스 레이어 직접 주입 (DIP + SRP 위반)

**위치**: `AuthService.java`

**원인**: `login(LoginRequest, String clientIp, HttpServletResponse response)` 시그니처가 HTTP 인프라 객체를 서비스에 직접 받아 `CookieUtil.setRefreshTokenCookie(response, ...)`를 서비스 내부에서 호출.

- 서비스가 웹 레이어(`jakarta.servlet`)에 강결합 → 스케줄러·배치 등 비-웹 컨텍스트 재사용 불가
- 단위 테스트 시 `HttpServletResponse` mock 강제 필요

```java
// 개선: AuthService는 순수 비즈니스 결과만 반환
public AuthTokenPair login(LoginRequest request, String clientIp) {
    // ... 검증 로직 동일 ...
    return new AuthTokenPair(accessToken, refreshTokenValue, expiresIn);
}

// 쿠키 설정은 AuthController 책임
@PostMapping("/login")
public ResponseEntity<?> login(..., HttpServletResponse httpResponse) {
    AuthTokenPair pair = authService.login(request, clientIp);
    cookieUtil.setRefreshTokenCookie(httpResponse, pair.refreshToken());
    return ResponseEntity.ok(ApiResponse.success(new AuthTokenResponse(pair.accessToken(), pair.expiresIn())));
}
```

---

### [C-2] `SecurityConfig` — Filter 등록 순서 역방향 (보안 결함)

**위치**: `SecurityConfig.java`

**원인**:
```java
// 현재 잘못된 순서 → JwtAuthFilter가 ExceptionFilter 앞에 실행됨
.addFilterBefore(new JwtExceptionFilter(objectMapper), UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(new JwtAuthenticationFilter(jwtUtil), JwtExceptionFilter.class)
```
JWT 파싱 예외가 `JwtExceptionFilter`에 잡히지 않아 500 또는 빈 응답 반환.

```java
// 올바른 순서: ExceptionFilter → AuthFilter
.addFilterBefore(new JwtAuthenticationFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(new JwtExceptionFilter(objectMapper), JwtAuthenticationFilter.class)
```

---

### [C-3] `APIUsageInterceptor` + `SecurityConfig` — 미인증 사용자 외부 API 무제한 호출 (비용 폭발)

**위치**: `APIUsageInterceptor.java:38`, `SecurityConfig.java:73`

**원인**: `/api/career/**`가 `permitAll()`이고, 인터셉터는 인증된 사용자에게만 제한 적용. 미인증 요청은 조용히 통과(`return true`)하여 FastAPI·OpenAI 호출 무제한 발생.

```java
// SecurityConfig — 비용 API는 인증 필수
.requestMatchers("/api/career/consultation", "/api/career/timing", "/api/career/compatibility")
    .authenticated()

// APIUsageInterceptor — 미인증 시 401 반환
if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}}");
    return false;
}
```

---

### [C-4] `FeedbackService` — IDOR (Insecure Direct Object Reference)

**위치**: `FeedbackService.java:32`

**원인**: `/api/feedback/satisfaction`이 인증 없이 접근 가능하고, `sajuResultId`의 소유권 검증 없이 직접 조회. 공격자가 1~N ID 열거로 타인 분석 결과에 피드백 가능.

```java
// 개선 (Phase 2)
public SatisfactionFeedbackResponse saveFeedback(SatisfactionFeedbackRequest request, Long userId) {
    SajuResult sajuResult = sajuResultRepository
            .findByIdAndUserProfile_UserId(request.sajuResultId(), userId)  // 소유권 검증
            .orElseThrow(() -> new SajuResultNotFoundException(...));
}
```

---

### [C-5] `ConsultationService` — `CareerConsultation` 무제한 중복 저장

**위치**: `ConsultationService.java:83`

**원인**: `CompanyMatchingService`는 INSERT IGNORE + `completed` 플래그로 중복을 막지만, `ConsultationService`는 매 요청마다 무조건 `save()`. 동일 사용자가 10번 호출 → 10개의 `CareerConsultation` 누적.

```java
// 개선: 기존 consultation 재사용
Optional<CareerConsultation> existing = careerConsultationRepository
        .findTopBySajuResultOrderByGeneratedAtDesc(sajuResult);
if (existing.isPresent()) {
    return buildResponseFromExisting(existing.get(), ...);
}
```

---

## ⚠️ Major

---

### [M-1] `TokenValidationFilter` + `AuthService` — `hashToken()` SHA-256 중복 (DRY 위반)

**위치**: `AuthService.java`, `TokenValidationFilter.java`

**원인**: 완전히 동일한 SHA-256 해싱 로직이 두 클래스에 각각 private static으로 복사됨. 알고리즘 변경 시 두 곳 모두 수정 필요.

```java
// 신규: TokenHashUtil.java
public final class TokenHashUtil {
    private TokenHashUtil() {}
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
// AuthService, TokenValidationFilter 모두 TokenHashUtil.sha256(token) 사용
```

---

### [M-2] `CompanyMatchingService` — Repository 11개 직접 주입 + 캐시 경로 10 SELECT (SRP 위반)

**위치**: `CompanyMatchingService.java:64–80`, `CompanyMatchingService.java:235–295`

**원인**: 캐시 재사용 경로(`buildResponseFromExisting`)를 위해 읽기용 Repository 11개가 서비스에 직접 주입. 캐시 히트 시마다 10개 개별 SELECT 쿼리 실행.

```java
// 신규: CompatibilityChildReadService 분리
@Service
@RequiredArgsConstructor
public class CompatibilityChildReadService {
    // Repository 11개를 이곳으로 이동
    public CompatibilityResponse buildFromExisting(CompanyCompatibility saved, ...) { ... }
}

// CompanyCompatibilityRepository에 JOIN FETCH 추가
@Query("SELECT cc FROM CompanyCompatibility cc LEFT JOIN FETCH cc.targetRoleAnalysis WHERE cc.id = :id")
Optional<CompanyCompatibility> findWithChildrenById(@Param("id") Long id);
```

---

### [M-3] `CompatibilityChildSaveService` — `forEach` 루프 내 단건 `save()` (N번 DB 왕복)

**위치**: `CompatibilityChildSaveService.java:128–178`

**원인**: `saveInterviewQuestions`, `saveRoleCompatibilities`, `saveMonthlyForecasts`, `saveCautions`, 키워드/럭키데이 저장 모두 개별 `save()` 호출.

```java
// 개선: saveAll() 배치 처리
private void saveInterviewQuestions(CompanyCompatibility saved,
                                    List<CompatibilityAnalysisData.InterviewQuestion> questions) {
    List<ExpectedInterviewQuestion> entities = questions.stream()
            .map(q -> ExpectedInterviewQuestion.builder()
                    .companyCompatibility(saved).question(q.question()).intent(q.intent()).build())
            .toList();
    expectedInterviewQuestionRepository.saveAll(entities);
}
// 나머지 save() 호출도 동일하게 saveAll()로 변경
// application.yml: spring.jpa.properties.hibernate.jdbc.batch_size=50
```

---

### [M-4] `ConsultationService` + `CareerFortuneService` — 사주 계산 로직 중복 (OCP 위반)

**위치**: `ConsultationService.java:62–72`, `CareerFortuneService.java:51–63`

**원인**: `tenGodCalculator.calculate()`, `hiddenStemCalculator.calculate()`, `careerFortuneAnalyzer.analyzeFavoredPeriod()` 등 동일 흐름이 두 서비스에 복붙. 계산 로직 변경 시 두 곳 모두 수정 필요.

```java
// 신규: SajuAnalysisFacade (공통 계산 추출)
@Component
@RequiredArgsConstructor
public class SajuAnalysisFacade {
    public SajuAnalysisContext analyze(FastAPIResponse sajuData) {
        TenGodDistribution dist = tenGodCalculator.calculate(sajuData.heavenlyStems());
        HiddenStems stems = hiddenStemCalculator.calculate(sajuData.earthlyBranches());
        String dayMaster = sajuData.heavenlyStems().get(SajuPillarIndex.DAY_INDEX);
        String favoredPeriod = careerFortuneAnalyzer.analyzeFavoredPeriod(dist, stems, dayMaster, sajuData.earthlyBranches());
        int score = careerFortuneAnalyzer.calculateConfidenceScore(dist, stems, dayMaster);
        return new SajuAnalysisContext(dayMaster, dist, stems, favoredPeriod, score, careerFortuneAnalyzer.buildReasoning(favoredPeriod, dist));
    }
}
```

---

### [M-5] `CareerFortuneService` vs `ConsultationService` — `SajuResult` 관리 전략 충돌 (데이터 손실 위험)

**위치**: `CareerFortuneService.java:68–72`, `SajuResultWriteService.java:58–72`

**원인**:
- `CareerFortuneService` → `replaceForUserProfile()`: 기존 SajuResult **삭제 후 재생성**
- `ConsultationService` → `findOrCreate()`: 기존 SajuResult **유지**

동시 실행 시 `ConsultationService`가 참조하던 SajuResult(id=100)를 `CareerFortuneService`가 삭제 → FK 제약 위반 또는 고아 데이터 발생 가능.

```java
// 개선: replaceForUserProfile()에 CareerConsultation 삭제 추가
@Transactional
public void replaceForUserProfile(UserProfile userProfile, SajuResult newResult) {
    sajuResultRepository.findByUserProfile(userProfile).ifPresent(existing -> {
        Long existingId = existing.getId();
        careerConsultationRepository.deleteBySajuResultId(existingId); // 추가
        tenGodDataRepository.deleteBySajuResultId(existingId);
        hiddenStemDataRepository.deleteBySajuResultId(existingId);
        careerFortuneRepository.deleteBySajuResultId(existingId);
        sajuResultRepository.deleteByUserProfileJpql(userProfile);
    });
    sajuResultRepository.save(newResult);
}
```

---

### [M-6] `SajuDataService.recoverFetchSajuFromFastAPI()` — 타임아웃/5xx를 400으로 오분류

**위치**: `SajuDataService.java`

**원인**: 재시도 후 `ResourceAccessException`(타임아웃)과 `HttpServerErrorException`(5xx) 모두 `InvalidSajuDataException`으로 변환 → `GlobalExceptionHandler`가 400으로 처리. FastAPI 장애를 클라이언트 입력 오류로 잘못 응답.

```java
@Recover
public FastAPIResponse recoverFetchSajuFromFastAPI(RuntimeException ex, LocalDate birthDate, LocalTime birthTime) {
    if (ex instanceof ResourceAccessException) throw new FastAPITimeoutException("FastAPI 응답 시간 초과", ex); // → 503
    if (ex instanceof HttpServerErrorException) throw new ExternalApiException("FastAPI 서버 오류", ex);       // → 502
    throw new InvalidSajuDataException(..., ex); // → 400
}
```

---

### [M-7] `JwtUtil.getRefreshTokenExpiration()` — `LocalDateTime` 타임존 미고려

**위치**: `JwtUtil.java:70–72`

**원인**: `LocalDateTime.now()`는 JVM 기본 타임존 의존. 서버 타임존 변경 또는 다중 서버 혼재 시 `expiresAt` 값이 최대 9시간 차이나 RefreshToken 조기 만료/초과 유효 문제 발생.

```java
// 개선: Instant로 통일
public Instant getRefreshTokenExpiration() {
    return Instant.now().plusSeconds(refreshTokenExpirationMs / 1000);
}
// RefreshToken 엔티티 필드도 Instant 또는 OffsetDateTime으로 변경
```

---

### [M-8] `User.softDelete()` — `System.currentTimeMillis()` 직접 사용 (테스트 불가)

**위치**: `User.java:85`

**원인**: 이메일에 epoch millis 삽입으로 단위 테스트에서 결과 예측 불가. `deletedAt`은 `LocalDateTime`, 이메일은 millis로 단위 불일치.

```java
public void softDelete() {
    LocalDateTime now = LocalDateTime.now();
    this.name = "탈퇴한 사용자";
    this.email = "deleted_" + this.id + "_" + now.toEpochSecond(ZoneOffset.UTC) + "@deleted.local";
    this.status = UserStatus.INACTIVE;
    this.deletedAt = now;
}
```

---

## 💬 Minor

---

### [Mi-1] `AsyncConfig` — 스레드풀 미설정 (운영 시 OOM 위험)

**위치**: `AsyncConfig.java`

기본 `SimpleAsyncTaskExecutor`는 요청마다 새 스레드 생성. 대규모 트래픽 시 스레드 폭발.

```java
@Bean(name = "loginAuditExecutor")
public Executor loginAuditExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("login-audit-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
}
// @Async("loginAuditExecutor") 로 명시
```

---

### [Mi-2] `SajuGlobalExceptionHandler` — `AuthException` 전체가 `INVALID_CREDENTIALS` 단일 코드 반환

**위치**: `SajuGlobalExceptionHandler.java`

로그인 실패·약관 미동의·인증 정보 없음 모두 동일 에러코드. 클라이언트 분기 처리 불가.

**개선**: `AuthException`에 `ErrorCode` 필드 추가 또는 `ConsentRequiredException`·`UnauthenticatedException` 서브 분류.

---

### [Mi-3] `JwtAuthenticationFilter` — JWT 이중/삼중 파싱

**위치**: `JwtAuthenticationFilter.java`

`validateToken()` → `getUserIdFromToken()` → `getEmailFromToken()` 순서로 유효 토큰을 3회 파싱.

```java
// JwtUtil에 단일 메서드 추가
public Optional<Claims> validateAndParseClaims(String token) {
    try { return Optional.of(parseClaims(token)); }
    catch (JwtException | IllegalArgumentException e) { return Optional.empty(); }
}
// 필터에서 1회 파싱으로 userId, email 동시 추출
```

---

### [Mi-4] `SecurityConfig` — JWT Stateless + HTTP Basic 혼용

**위치**: `SecurityConfig.java`

`SessionCreationPolicy.STATELESS` + `httpBasic(Customizer.withDefaults())` 병행. BasicAuth 세션 생성 가능성.

**개선**: Swagger 보호는 `@Profile("local")` 분리 또는 Nginx IP 화이트리스트로 대체.

---

### [Mi-5] `ConsultationService` — 응답 조립 로직이 서비스에 직접 포함

**위치**: `ConsultationService.java:86–103`

`tenGodCharacteristics` Map 생성과 `SajuProfile` 조립 로직이 서비스 내에 있음. `ConsultationMapper`로 이동하면 서비스가 순수 오케스트레이션에만 집중 가능.

---

### [Mi-6] `SajuResultWriteService.isDuplicateKeyViolation()` — 예외 메시지 문자열 파싱 fragile

**위치**: `SajuResultWriteService.java:142–177`

`"Duplicate entry"`, `"Unique index or primary key violation"` 문자열 직접 비교. DB 드라이버 업그레이드 시 조용히 실패 가능.

```java
// 개선: 문자열 파싱 제거, SQLState + errorCode 조합만 유지
return ExceptionUtils.getThrowableList(ex).stream()
        .filter(SQLException.class::isInstance)
        .map(SQLException.class::cast)
        .anyMatch(s -> "23505".equals(s.getSQLState()) || s.getErrorCode() == 1062);
```

---

### [Mi-7] `CompanyMatchingService.buildResponseFromExisting()` — `orElse(null)` 사용

**위치**: `CompanyMatchingService.java:238–268`

`completed == true` 레코드는 모든 자식 존재가 보장되어야 하는데, 자식 없으면 `null` 반환 → 클라이언트에 불완전한 데이터 전달.

```java
// orElse(null) → orElseThrow()로 교체
.orElseThrow(() -> new DataAccessException("completed=true인데 TargetRoleAnalysis가 없음: id=" + saved.getId()))
```

---

### [Mi-8] `AnalysisResponseBuilder` — `LocalDate.now()` 직접 사용 (테스트 불가)

**위치**: `AnalysisResponseBuilder.java`

럭키데이 계산에 `LocalDate.now()` 직접 호출로 단위 테스트에서 결과 예측 불가.

```java
// Clock 주입으로 테스트 가능하게
@Component
public class AnalysisResponseBuilder {
    private final Clock clock;
    // @Bean Clock clock() { return Clock.systemDefaultZone(); }
    // 테스트: Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
    LocalDate today = LocalDate.now(clock);
}
```

---

### [Mi-9] `LoginAttemptEventListener` — 스택 트레이스 미포함 로깅

**위치**: `LoginAttemptEventListener.java:63`

```java
// 현재: 원인 추적 불가
log.error("로그인 시도 기록 저장 실패: {}", e.getMessage());

// 개선
log.error("로그인 시도 기록 저장 실패 (비동기 무시됨)", e);
```

---

## 📋 TODO / FIXME 전수 목록

| # | 파일 | 내용 | 누락 작업 | 해결 방향 |
|---|------|------|-----------|-----------|
| T-1 | `FeedbackController.java` | Phase 2 `@AuthenticationPrincipal` 미적용 | 피드백 제출자 신원 미기록 | `/api/feedback/**`를 `authenticated()`로 변경, `Long userId` 주입 |
| T-2 | `SajuGlobalExceptionHandler.java` | `OpenAIApiException` 상태코드별 분기 미구현 | 401/429/5xx 모두 504로 응답 | `OpenAIApiException`에 `statusCode` 필드 추가 후 핸들러 분기 |
| T-3 | `FeedbackService.java` | Phase 2 인증 연동 시 `findByIdAndUser()` 미변경 | IDOR 취약점 | `findByIdAndUserProfile_UserId()` 쿼리로 교체 |
| T-4 | `FeedbackService.java` | `UserSatisfactionFeedback.user` 미설정 | 피드백-사용자 연관관계 미완성 | `@ManyToOne` 관계 추가 및 저장 시 user 설정 |

---

## ✅ 잘 된 부분

- **INSERT IGNORE + `completed` 플래그 패턴** (`CompanyMatchingService`): Race Condition을 DB 레벨에서 차단하고, 불완전 캐시를 방어하는 설계가 정교함
- **트랜잭션 분리 원칙**: 외부 I/O 중 DB 커넥션 미점유 원칙을 일관 적용, `@Transactional(REQUIRES_NEW)` 적용 이유를 Javadoc으로 명시
- **예외 계층 설계**: 각 예외 타입별 적절한 HTTP 상태코드 반환 + `requestId`로 추적 가능
- **User Enumeration 방지**: 로그인 실패 시 동일 메시지 응답, 실패 원인은 비동기 이벤트로 기록
- **SHA-256 RefreshToken 해싱**: DB 저장 시 원본 토큰 대신 해시값 저장으로 유출 대비

---

## 전체 항목 진행 현황 (2026-05-22)

| 번호 | 중요도 | 위치 | 핵심 문제 | 상태 |
|------|--------|------|-----------|------|
| C-1 | Critical | `AuthService.login()` | HttpServletResponse 서비스 직접 주입 | ✅ |
| C-2 | Critical | `SecurityConfig` | Filter 등록 순서 역방향 | ✅ |
| C-3 | Critical | `APIUsageInterceptor` | 미인증 외부 API 무제한 호출 | ✅ |
| C-4 | Critical | `FeedbackService` | 소유권 미검증 (IDOR) | ✅ |
| C-5 | Critical | `ConsultationService` | CareerConsultation 무제한 중복 저장 | ✅ |
| C-6 | Critical | `SecurityConfig` | `/api/auth/check-email` permitAll 누락 | ✅ |
| C-7 | Critical | `ConsultationService` | @Transactional 부재로 고아 데이터 | ✅ |
| M-1 | Major | `AuthService` + `TokenValidationFilter` | hashToken() SHA-256 중복 | ⏳ |
| M-2 | Major | `CompanyMatchingService` | Repository 11개 직접 주입 + 10 SELECT | ⏳ |
| M-3 | Major | `CompatibilityChildSaveService` | forEach 단건 INSERT (N번 DB 왕복) | ⏳ |
| M-4 | Major | `ConsultationService` + `CareerFortuneService` | 사주 계산 로직 중복 | ⏳ |
| M-5 | Major | `CareerFortuneService` | SajuResult 전략 충돌 + 데이터 손실 | ⏳ |
| M-6 | Major | `SajuDataService` | 타임아웃/5xx → 400 오분류 | ⏳ |
| M-7 | Major | `JwtUtil` | LocalDateTime 타임존 미고려 | ⏳ |
| M-8 | Major | `User.softDelete()` | System.currentTimeMillis() 직접 사용 | ⏳ |
| M-9 | Major | `ConsultationService` | 캐시 히트 OpenAI 호출 생략 | ✅ |
| M-10 | Major | `UserService` | 죽은 코드 `associateSaju*` 제거 | ✅ |
| Mi-1 | Minor | `AsyncConfig` | 스레드풀 미설정 (OOM 위험) | ⏳ |
| Mi-2 | Minor | `SajuGlobalExceptionHandler` | AuthException 단일 에러코드 매핑 | ⏳ |
| Mi-3 | Minor | `JwtAuthenticationFilter` | JWT 삼중 파싱 | ⏳ |
| Mi-4 | Minor | `SecurityConfig` | Stateless + HTTP Basic 혼용 | ⏳ |
| Mi-5 | Minor | `ConsultationService` | 응답 조립 로직 서비스 내 포함 | ⏳ |
| Mi-6 | Minor | `SajuResultWriteService` | 예외 메시지 문자열 파싱 | ⏳ |
| Mi-7 | Minor | `CompanyMatchingService` | orElse(null) 사용 | ⏳ |
| Mi-8 | Minor | `AnalysisResponseBuilder` | LocalDate.now() 직접 사용 | ⏳ |
| Mi-9 | Minor | `LoginAttemptEventListener` | 스택 트레이스 미포함 로깅 | ⏳ |
| Mi-10 | Minor | `APIUsageInterceptor` | AnonymousToken 명시적 제외 | ✅ |
| Mi-11 | Minor | `AuthController` | Authorization 비표준 → 표준 헤더 | ✅ |
| Mi-12 | Minor | `UserService` | findBySajuResult_IdAndUser_Id() 변경 | ✅ |

---

## 🔧 리팩토링: SajuResult 공유 + 월별 중복 제거 (createdAt 범위 기반)

> **작성일**: 2026-05-21  
> **상태**: Phase 2 ✅ **완료** (PR#21: C-5 커밋), Phase 1/3은 Phase 3 Minor로 미룸  
> **핵심 설계**: SajuResult는 생년월일 기반 공유 (N:M, 미래 계획), CareerConsultation/CompanyCompatibility는 createdAt/analyzedAt 범위로 월별 중복 제거

### 설계 개요: "버전 필드 불필요, createdAt으로 충분"

기존의 복잡한 `version` 필드 관리 대신, **이미 있는 타임스탬프(generatedAt, analyzedAt, createdAt)를 활용한 범위 쿼리**로 중복 제거:

```
같은 달(YYYY-MM) 범위 내 기존 레코드가 있으면 → 재사용
다른 달이면 → 새로 저장 (INSERT IGNORE 패턴 제거)
```

---

### 현재 문제점 분석

#### 1. SajuResult 중복 저장 (저장공간 낭비)

**현재 엔티티 구조** (`SajuResult.java`):
```java
@OneToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_profile_id", nullable = false)
private UserProfile userProfile;  // ← 1:1 고정, 중복 저장

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private User user;  // ← 불필요
```

**문제**:
- 같은 생년월일(1990-10-10, 14:30) 사용자 A, B가 각각 새로운 SajuResult 생성
- 동일한 사주 계산 결과가 DB에 중복 저장

---

#### 2. CareerConsultation 매달 변경 미반영 (C-5 이슈)

**현재 코드** (`ConsultationService.java:95`):
```java
CareerConsultation consultation = consultationMapper.buildConsultation(sajuResult, advice, modelVersion);
careerConsultationRepository.save(consultation);  // ← 매번 무조건 저장
```

**이미 있는 필드들**:
```java
@CreatedDate
@Column(name = "generated_at", nullable = false, updatable = false)
private LocalDateTime generatedAt;  // ← 이것으로 충분!

@Column(name = "openai_model_version")
private String openaiModelVersion;
```

**문제**: 같은 달 재요청이어도 새로 저장. 월이 바뀌면 12개월 타임라인 갱신되는데 version 필드로 추적하려는 시도는 복잡함

---

#### 3. CompanyCompatibility INSERT IGNORE 패턴 (경쟁 조건 복잡)

**현재 코드** (`CompanyMatchingService.java:167`):
```java
int inserted = companyCompatibilityJdbcRepository.insertOrIgnore(root);

if (inserted == 0) {
    // UNIQUE 제약 충돌 → 기존 레코드 재사용
    if (saved.isCompleted()) return buildResponseFromExisting(saved);
    // completed=false면 재계산 (불완전한 데이터 방어)
}
```

**문제**:
- INSERT IGNORE는 DB 레벨 제약에 의존 (명시성 약함)
- UNIQUE(user_id, company_name, target_role_category, version)에 의존하는데, version이 Integer(1,2,3...)로 혼란
- 동시성 처리를 위해 completed 플래그 필요 (복잡함)

**이미 있는 필드**:
```java
@Column(name = "analyzed_at", nullable = false, updatable = false)
private LocalDateTime analyzedAt;

@CreatedDate
@Column(name = "created_at", nullable = false, updatable = false)
private LocalDateTime createdAt;
```

---

### 간단한 개선 설계

#### Phase 1: SajuResult N:M 관계로 변경 (⏳ 계획, Phase 3 Minor로 미룸)

**상태**: 아직 구현 안 됨. 현재 SajuResult는 user_id 유지 (1:N 관계)

**목표**: 같은 생년월일 사용자들이 하나의 SajuResult 공유

1. **SajuResult 엔티티 수정**:
```java
// Before: 1:1 고정
@OneToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_profile_id", nullable = false)
private UserProfile userProfile;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private User user;

// After: N:M 공유
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
    name = "user_saju_result",
    joinColumns = @JoinColumn(name = "saju_result_id"),
    inverseJoinColumns = @JoinColumn(name = "user_profile_id")
)
private List<UserProfile> userProfiles = new ArrayList<>();
// user_profile_id, user_id FK 제거
```

2. **SajuResult에 UNIQUE 제약 추가** (생년월일 기반 공유):
```sql
ALTER TABLE saju_result 
ADD UNIQUE KEY uk_birth_datetime (birth_date, birth_time);
```

3. **Repository 쿼리**:
```java
@Repository
public interface SajuResultRepository extends JpaRepository<SajuResult, Long> {
    // 신규: 생년월일로 기존 SajuResult 조회
    Optional<SajuResult> findByBirthDateAndBirthTime(LocalDate birthDate, LocalTime birthTime);
}
```

4. **Service 로직** (`SajuResultProvider`):
```java
public SajuResult findOrCreate(UserProfile userProfile, LocalDate birthDate, 
                               LocalTime birthTime, SajuResult newResult) {
    // 1. 생년월일로 기존 SajuResult 조회
    Optional<SajuResult> existing = sajuResultRepository
        .findByBirthDateAndBirthTime(birthDate, birthTime);
    
    if (existing.isPresent()) {
        SajuResult result = existing.get();
        // 2. 사용자 추가 (N:M 관계 유지)
        if (!result.getUserProfiles().contains(userProfile)) {
            result.getUserProfiles().add(userProfile);
            sajuResultRepository.save(result);
        }
        return result;
    }
    
    // 3. 없으면 신규 생성
    SajuResult savedResult = sajuResultRepository.save(newResult);
    savedResult.getUserProfiles().add(userProfile);
    return sajuResultRepository.save(savedResult);
}
```

---

#### Phase 2: CareerConsultation 월별 중복 제거 (createdAt 범위) ✅ **완료 (PR#21 C-5)**

**상태**: 완료. 구현 내용: `existsBySajuResultAndGeneratedAtBetween()` 쿼리 추가, 월 중복 저장 방지

**목표**: generatedAt 범위로 같은 달 레코드 감지 → 재사용 / 다른 달 → 새로 저장

**핵심 아이디어**: `version` 필드 불필요. 범위 쿼리로 충분:
```java
// 해당 달의 시작~끝 범위 내에서 기존 레코드 조회
LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
LocalDateTime monthEnd = YearMonth.now().atEndOfMonth().atTime(23, 59, 59);

Optional<CareerConsultation> existing = careerConsultationRepository
    .findBySajuResultAndGeneratedAtBetween(sajuResult, monthStart, monthEnd);
```

1. **CareerConsultation 엔티티**: 변경 없음
```java
@CreatedDate
@Column(name = "generated_at", nullable = false, updatable = false)
private LocalDateTime generatedAt;

@Column(name = "openai_model_version")
private String openaiModelVersion;  // 유지 (모델명)
// version 필드 추가 불필요
```

2. **CareerConsultationRepository 쿼리 추가**:
```java
@Repository
public interface CareerConsultationRepository extends JpaRepository<CareerConsultation, Long> {
    // 신규: 같은 달 범위 내 consultation 조회
    Optional<CareerConsultation> findBySajuResultAndGeneratedAtBetween(
        SajuResult sajuResult, LocalDateTime startOfMonth, LocalDateTime endOfMonth);
}
```

3. **Service 로직** (`ConsultationService.java`) — **현재 PR#21 C-5 구현**:
```java
public ConsultationResponse getCareerConsultation(ConsultationRequest request, Long userId) {
    // ... 기존 사주 계산 로직 ...
    
    SajuResult sajuResult = sajuResultProvider.findOrCreate(userProfile, newResult);
    
    // OpenAI 호출 (항상 실행 — 풀 응답 필요)
    CareerAdviceResponse advice = openAICaller.call(sajuData, tenGodDistribution, hiddenStems, dayMaster);
    
    // 중복 저장 방지: 같은 달 record가 있으면 save 건너뜀
    CareerConsultation consultation = consultationMapper.buildConsultation(
        sajuResult, advice, modelVersion);
    
    LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
    LocalDateTime monthEnd = YearMonth.now().atEndOfMonth().atTime(23, 59, 59);
    
    if (!careerConsultationRepository.existsBySajuResultAndGeneratedAtBetween(
            sajuResult, monthStart, monthEnd)) {
        careerConsultationRepository.save(consultation);
    } else {
        log.info("이번 달 컨설팅 결과 이미 존재, 저장 건너뜀: sajuResultId={}", sajuResult.getId());
    }
    
    // 응답은 항상 freshly computed로 반환
    return buildResponse(...);  // OpenAI 결과 + 계산된 데이터 사용
}

private LocalDateTime getMonthStart() {
    return YearMonth.now().atDay(1).atStartOfDay();
}

private LocalDateTime getMonthEnd() {
    return YearMonth.now().atEndOfMonth().atTime(23, 59, 59);
}
```

**구현 상태**:
- ✅ 월 범위 쿼리: `existsBySajuResultAndGeneratedAtBetween()` 추가
- ✅ 중복 저장 방지: 같은 달 record 있으면 save 건너뜀
- ⏳ 완전한 캐싱 (buildResponseFromExisting): 아직 미구현 (전체 응답 필드를 DB에 저장 필요)

---

#### Phase 3: CompanyCompatibility 월별 중복 제거 (analyzedAt 범위, INSERT IGNORE 제거) (⏳ 계획, Phase 3 Major)

**상태**: 아직 구현 안 됨. INSERT IGNORE 패턴 유지 중

**목표**: analyzedAt 범위로 같은 달 레코드 감지 → 재사용 / 다른 달 → 새로 저장

**변경 사항**:

1. **CompanyCompatibility 엔티티**: version 필드 제거 또는 무시
```java
// Before (불필요함)
@Column(name = "version", nullable = false)
private Integer version;  // 1, 2, 3... 혼란스러움

// After: 범위 쿼리로 충분하므로 제거 가능 (또는 유지하되 사용하지 않음)

@Column(name = "analyzed_at", nullable = false, updatable = false)
private LocalDateTime analyzedAt;  // ← 이것으로 충분!
```

2. **CompanyCompatibilityRepository 쿼리 추가**:
```java
@Repository
public interface CompanyCompatibilityRepository extends JpaRepository<CompanyCompatibility, Long> {
    // 신규: 같은 달 범위 내 compatibility 조회
    Optional<CompanyCompatibility> findByUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndAnalyzedAtBetween(
        Long userProfileId, String companyName, JobCategoryEnum targetRole,
        LocalDateTime startOfMonth, LocalDateTime endOfMonth);
}
```

3. **Service 로직** (`CompanyMatchingService.java`, INSERT IGNORE 제거):
```java
public CompatibilityResponse analyzeCompatibility(CompatibilityRequest request, Long userId) {
    // ... 기존 사주 계산 로직 ...
    
    // 1. 같은 달 범위 계산
    LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
    LocalDateTime monthEnd = YearMonth.now().atEndOfMonth().atTime(23, 59, 59);
    
    // 2. 같은 달 호환성이 이미 있고 완료되었는가?
    Optional<CompanyCompatibility> existing = companyCompatibilityRepository
        .findByUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndAnalyzedAtBetween(
            userProfile.getId(), request.companyName(), 
            request.targetRole().category(), monthStart, monthEnd);
    
    if (existing.isPresent() && existing.get().isCompleted()) {
        log.info("같은 달 호환성 재사용 (캐시)");
        return buildResponseFromExisting(existing.get());  // DB에서 자식 로드
    }
    
    // 3. 다른 달 또는 신규 → 새로 저장
    CompanyCompatibility root = CompanyCompatibility.builder()
        .userProfile(userProfile)
        .user(user)
        .companyName(request.companyName())
        .targetRoleCategory(request.targetRole().category())
        .targetRoleDetailName(request.targetRole().detailName())
        .compatibilityScore(compatibilityScore)
        .summary(summary)
        .build();
    
    CompanyCompatibility saved = companyCompatibilityRepository.save(root);  // ← 직접 저장 (INSERT IGNORE 불필요)
    
    // 4. 자식 엔티티 저장 (원자적으로)
    childSaveService.saveAllAndMarkCompleted(saved, analysisData);
    
    log.info("새로운 호환성 분석 저장 (다른 달 또는 신규)");
    return buildNewResponse(saved, request, analysisData);
}

private LocalDateTime getMonthStart() {
    return YearMonth.now().atDay(1).atStartOfDay();
}

private LocalDateTime getMonthEnd() {
    return YearMonth.now().atEndOfMonth().atTime(23, 59, 59);
}
```

---

### 데이터 마이그레이션 (간단함)

```sql
-- 1. SajuResult N:M 관계 생성
CREATE TABLE user_saju_result (
    user_profile_id BIGINT NOT NULL,
    saju_result_id BIGINT NOT NULL,
    PRIMARY KEY (user_profile_id, saju_result_id),
    FOREIGN KEY (user_profile_id) REFERENCES user_profile(id),
    FOREIGN KEY (saju_result_id) REFERENCES saju_result(id)
);

-- 2. 기존 1:1 데이터 마이그레이션 → N:M
INSERT INTO user_saju_result (user_profile_id, saju_result_id)
SELECT DISTINCT sr.user_profile_id, sr.id
FROM saju_result sr
WHERE sr.user_profile_id IS NOT NULL;

-- 3. SajuResult FK 제거
ALTER TABLE saju_result 
DROP FOREIGN KEY fk_user_profile_id,
DROP FOREIGN KEY fk_user_id,
DROP COLUMN user_profile_id,
DROP COLUMN user_id;

-- 4. UNIQUE 제약 추가 (생년월일 기반 공유)
ALTER TABLE saju_result 
ADD UNIQUE KEY uk_birth_datetime (birth_date, birth_time);

-- 5. CompanyCompatibility version 필드 제거 (선택, UNIQUE 제약도 제거)
--    또는 무시하기 (기존 데이터 유지, 향후 사용 안 함)
--    제거하려면:
-- ALTER TABLE company_compatibility
-- DROP INDEX uk_user_company_role_version,
-- DROP COLUMN version;
--    무시하려면: 현재 상태 유지, Service 레벨에서만 범위 쿼리 사용
```

---

### 테스트 체크리스트

- [ ] **SajuResult 공유**
  - [ ] 같은 생년월일 사용자 2명: 동일 SajuResult 참조 (N:M)
  - [ ] 다른 생년월일: 별도 SajuResult 생성
  
- [ ] **CareerConsultation 월별 중복 제거**
  - [ ] 같은 달 재요청: 기존 consultation 재사용 (range query 동작)
  - [ ] 다음 달 요청: 새로운 consultation 생성 (OpenAI 호출)
  - [ ] 년도 변경: 자동 새 consultation (2026-12 → 2027-01)
  
- [ ] **CompanyCompatibility 월별 중복 제거**
  - [ ] 같은 달 재요청: 기존 compatibility 재사용 (completed=true)
  - [ ] 다음 달 요청: 새로운 compatibility 분석 (INSERT IGNORE 제거)
  - [ ] 동시 요청: 레이스 조건 없음 (범위 쿼리 + completed 플래그로 충분)
  
- [ ] **성능**
  - [ ] CareerConsultationRepository.findBySajuResultAndGeneratedAtBetween 인덱스 확인
  - [ ] CompanyCompatibilityRepository.findByUserProfile_IdAndCompanyNameAndTargetRoleCategoryAndAnalyzedAtBetween 인덱스 확인

---

### 구현 진행 상황 (2026-05-22 갱신)

| Phase | 상태 | 설명 | 진행률 |
|-------|------|------|--------|
| **Phase 1 리팩토링** | ✅ **완료** | Critical 7개 + Major 2개 (M-9, M-10) + Minor 3개 (Mi-10, Mi-11, Mi-12) | **12/32 (37.5%)** |
| Phase 2 | ✅ 완료 | CareerConsultation 월 중복 저장 방지 (PR#21 C-5) | - |
| Phase 3 | ⏳ 계획 | CompanyCompatibility 월 중복 제거 + INSERT IGNORE 제거 (Phase 3 Major) | - |

### Phase 1 리팩토링 완료 항목 (2026-05-22)

#### Critical (7/7 ✅)
| 항목 | 커밋 | 설명 |
|------|------|------|
| C-1 | `bdff6c9` | AuthService DIP/SRP (HttpServletResponse → Controller) |
| C-2 | `76c5129` | SecurityConfig Filter 순서 정정 |
| C-3 | `0b58033` | APIUsageInterceptor 미인증 401 |
| C-4 | `438c334` | FeedbackService IDOR (소유권 검증) |
| C-5 | `5d171ba` | ConsultationService 월 중복 저장 방지 |
| C-6 | `a90f967` | SecurityConfig `/api/auth/check-email` permitAll |
| C-7 | `a90f967` | ConsultationSaveService @Transactional 분리 |

#### Major (2/10 ✅)
| 항목 | 커밋 | 설명 |
|------|------|------|
| M-9 | `a90f967` | ConsultationService 캐시 히트 시 OpenAI 호출 생략 |
| M-10 | `a90f967` | UserService 죽은 코드 제거 (`associateSaju*` 메서드) |

#### Minor (3/11 ✅)
| 항목 | 커밋 | 설명 |
|------|------|------|
| Mi-10 | `a90f967` | APIUsageInterceptor AnonymousToken 명시적 제외 |
| Mi-11 | `e8d1686` | AuthController 표준 헤더 (Authorization + Refresh-Token 헤더) |
| Mi-12 | `a90f967` | UserService findBySajuResult_IdAndUser_Id() (userId 기반) |

### 향후 예상 효과 (전체 Phase 완료 시)

✅ **코드 단순성**: 복잡한 version 필드 제거, 이미 있는 타임스탐프 활용  
✅ **저장공간 절감**: 같은 생년월일 사용자들의 SajuResult 중복 제거 (Phase 1)  
✅ **API 비용 절감**: 같은 달 재요청 시 OpenAI/FastAPI 호출 제거 (Phase 2/3)  
✅ **월별 운세 정확성**: 월이 바뀔 때마다 자동 새 타임라인 (createdAt으로 명확함)  
✅ **INSERT IGNORE 제거**: 직관적인 범위 쿼리로 교체, 동시성 처리 간단해짐 (Phase 3)  
✅ **데이터 일관성**: N:M 공유 + 범위 쿼리로 무결성 보장 (Phase 1+2+3)

---

## 🔎 브랜치별 현황 리뷰 — `refactor/phase1-major-issues` (2026-05-22 갱신)

> **기준**: Phase 1 리팩토링 완료 (Critical 7개, Major 2개, Minor 3개)  
> **현재 HEAD**: `e8d1686` (Mi-11: 표준 헤더 변경)  
> **남은 항목**: Major 8개, Minor 6개, TODO 4개 (21개 항목)  

---

### ✅ Phase 1 완료된 항목 (12개)

| 항목 | 커밋 | 상태 |
|------|------|------|
| C-1: AuthService DIP/SRP | `bdff6c9` | ✅ `AuthTokenPair` record 반환, 쿠키 Controller로 이동 |
| C-2: Filter 순서 | `76c5129` | ✅ `JwtExceptionFilter → JwtAuthenticationFilter` 정정 |
| C-3: 미인증 API | `0b58033` | ✅ `APIUsageInterceptor` 미인증 401 반환 |
| C-4: FeedbackService IDOR | `438c334` | ✅ `findByIdAndUser_Id()` 소유권 검증 |
| C-5: Consultation 월 중복 | `5d171ba` | ✅ UNIQUE 제약 + `DataIntegrityViolationException` |
| C-6: check-email permitAll | `a90f967` | ✅ SecurityConfig `/api/auth/check-email` 추가 |
| C-7: @Transactional 분리 | `a90f967` | ✅ ConsultationSaveService 분리 |
| M-9: 캐시 히트 OpenAI 생략 | `a90f967` | ✅ `findBySajuResultAndConsultationMonth()` + 캐시 선조회 |
| M-10: 죽은 코드 제거 | `a90f967` | ✅ `associateSajuResultWithUser()` 등 삭제 |
| Mi-10: AnonymousToken 제외 | `a90f967` | ✅ `instanceof AnonymousAuthenticationToken` 명시적 체크 |
| Mi-11: 표준 헤더 | `e8d1686` | ✅ Authorization + Refresh-Token 헤더 방식 |
| Mi-12: findBySajuResult_IdAndUser_Id | `a90f967` | ✅ userId 기반 조회로 변경 |

---

## ⏳ **아직 남은 항목** (21개)

### Major (8개) — 2~3주 예상

| # | 항목 | 파일 | 문제 | 예상 난이도 |
|----|------|------|------|-----------|
| M-1 | SHA-256 중복 | AuthService + TokenValidationFilter | 동일 로직 두 곳 → TokenHashUtil 추출 | 낮음 |
| M-2 | Repository 11개 주입 | CompanyMatchingService | SRP 위반, N+1 쿼리 → CompatibilityChildReadService 분리 | 높음 |
| M-3 | N번 INSERT | CompatibilityChildSaveService | forEach 단건 save() → saveAll() 배치 처리 | 중간 |
| M-4 | 사주 계산 중복 | ConsultationService + CareerFortuneService | 동일 계산 로직 두 곳 → SajuAnalysisFacade 추출 | 중간 |
| M-5 | SajuResult 전략 충돌 | CareerFortuneService | replaceForUserProfile() 삭제 시 CareerConsultation 미삭제 | 높음 |
| M-6 | 타임아웃/5xx 오분류 | SajuDataService | ResourceAccessException → 400 → 503으로 변경 | 낮음 |
| M-7 | LocalDateTime 타임존 | JwtUtil | Instant 사용으로 통일 | 중간 |
| M-8 | System.currentTimeMillis() | User.softDelete() | LocalDateTime.now() 사용 | 낮음 |

### Minor (6개) — 2~3주 예상

| # | 항목 | 파일 | 문제 |
|----|------|------|------|
| Mi-1 | 스레드풀 미설정 | AsyncConfig | ThreadPoolTaskExecutor 설정 (코어/맥스/큐) |
| Mi-2 | 단일 에러코드 | SajuGlobalExceptionHandler | AuthException ErrorCode 필드 추가 |
| Mi-3 | JWT 삼중 파싱 | JwtAuthenticationFilter | validateAndParseClaims() 단일 파싱 |
| Mi-4 | Stateless + HTTP Basic | SecurityConfig | BasicAuth 제거 또는 @Profile 분리 |
| Mi-5 | 응답 조립 로직 | ConsultationService | buildResponse() → ConsultationMapper 이동 |
| Mi-6 | 예외 메시지 파싱 | SajuResultWriteService | SQLState 기반으로 변경 (DRY) |

### TODO (4개) — 1주 예상

| # | 파일 | 내용 | 해결 방향 |
|----|------|------|----------|
| T-1 | FeedbackController | @AuthenticationPrincipal 미적용 | Long userId 주입, authenticated() |
| T-2 | SajuGlobalExceptionHandler | OpenAIApiException 상태코드별 분기 | statusCode 필드 추가 |
| T-3 | FeedbackService | IDOR (소유권 미검증) | findByIdAndUserProfile_UserId() |
| T-4 | UserSatisfactionFeedback | user 필드 미설정 | @ManyToOne 관계 + 저장 시 설정 |

---

### 📋 요약 (2026-05-22)

| 카테고리 | 완료 | 남은 것 | 진행률 |
|---------|------|--------|--------|
| Critical | 7 | 0 | **100%** ✅ |
| Major | 2 | 8 | **20%** |
| Minor | 3 | 6 | **33%** |
| TODO | 0 | 4 | **0%** |
| **전체** | **12** | **18** | **40%** |

**다음 Step**: Major M-1 (SHA-256 중복)부터 시작 → 2~3주 소요 예상


---

### 남은 Major 8개 구현 계획

**1주차**: M-1 (SHA-256), M-6 (타임아웃), M-8 (softDelete)  
**2주차**: M-3 (배치 처리), M-4 (계산 중복), M-7 (타임존)  
**3주차**: M-2 (Repository 분리), M-5 (SajuResult 전략)

### 남은 Minor 6개 구현 계획

**1주차**: Mi-1 (스레드풀), Mi-2 (에러코드), Mi-3 (JWT 파싱)  
**2주차**: Mi-4 (BasicAuth), Mi-5 (응답 조립), Mi-6 (예외 파싱)

### TODO 4개 구현 계획

**1주차**: T-1 (FeedbackController), T-2 (OpenAIException), T-3 (IDOR), T-4 (user 필드)
