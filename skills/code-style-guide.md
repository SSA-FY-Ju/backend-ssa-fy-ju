# SSAju 코드 스타일 및 개발 규칙

## 📦 패키지 구조

SSAju는 모듈식 아키텍처로 설계:

```
ssafy.SSAju/
├── career/              # Phase 1: 관운 분석, AI 상담, 기업 궁합, 피드백
│   ├── controller/      # HTTP 요청 처리
│   ├── service/         # 비즈니스 로직
│   ├── repository/      # DB 접근
│   └── entity/          # JPA 엔티티
├── dto/
│   ├── request/         # 요청 DTO (record 타입)
│   ├── response/        # 응답 DTO (record 타입)
│   └── external/        # 외부 API DTO
├── exception/           # 커스텀 예외 클래스들
├── handler/             # @RestControllerAdvice로 전역 예외 처리
├── config/              # 설정 클래스 (WebClient, ChatClient 등)
└── auth/                # Phase 2: 인증/인가 (나중에 추가)
```

**핵심 원칙**:
- Phase 1: `career/` 패키지만 구현 (auth 제외)
- 각 계층 간 명확한 책임 분리
- 외부 API 호출은 Service 계층에서만 담당

---

## Lombok 사용 규칙

엔티티(Entity) 클래스에는 무한 루프(순환 참조) 방지를 위해 `@Data` 및 `@ToString` 사용을 금지합니다.

**필수**:
- `@Getter`: 필드 접근을 위한 게터 메서드 자동 생성
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`: JPA 기본 생성자 필요
- `@Builder`: 객체 생성 시 사용

**금지**:
- `@Data`: 모든 필드 접근 + toString() 자동화 (순환 참조 위험)
- `@ToString`: 양방향 관계에서 무한 재귀 위험

## JPA 타임스탐프 관리 (@CreatedDate/@LastModifiedDate)

모든 엔티티의 생성/수정 시간은 **Spring Data JPA 어노테이션** 사용 (수동 `@PreUpdate` 금지):

```java
// 좋음
@Entity
@EntityListeners(AuditingEntityListener.class)  // 필수: 없으면 @CreatedDate/@LastModifiedDate 동작 안 함
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate birthDate;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

// 필수 설정: @Configuration에서 @EnableJpaAuditing 활성화
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}

// 나쁨
@Entity
public class UserProfile {
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();  // 수동 관리 (금지)
    }
}
```

**이점**:
- ✅ 타임스탐프 자동 관리 (개발자 개입 최소화)
- ✅ createdAt은 업데이트 불가 (`updatable=false`)
- ✅ 모든 엔티티에서 일관된 방식

## 엔티티 equals & hashCode (ID 기준 구현)

**필수**: 모든 엔티티는 ID를 기준으로 직접 구현. Lombok @EqualsAndHashCode 금지.

**이유**: 지연 로딩(Lazy Loading)으로 인한 Proxy 객체 비교 시 안전성 보장. Proxy 객체는 실제 객체와 다른 hashCode를 가질 수 있으므로 Lombok이 생성한 equals/hashCode가 정확하지 않을 수 있음.

```java
// 좋음: ID 기준
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class SajuResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private LocalDate birthDate;
    // ... 기타 필드
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (!(o instanceof SajuResult)) return false;
        SajuResult that = (SajuResult) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

// 나쁨: Lombok @EqualsAndHashCode (Proxy 비교 시 위험)
@Entity
@EqualsAndHashCode
public class SajuResult {
    @Id
    private Long id;
    private LocalDate birthDate;
    // ... 기타 필드 모두 포함 (Proxy 로딩 시 문제)
}
```

**HashSet/HashMap 안전성**:
```java
// Proxy 객체 비교 테스트
SajuResult proxy = sessionFactory.openSession()
    .getReference(SajuResult.class, 1L);  // Proxy 객체 (실제 데이터 로드 안 됨)

Set<SajuResult> set = new HashSet<>();
set.add(proxy);
assertTrue(set.contains(proxy));  // ID 기준이면 true, 다른 필드 기준이면 false
```

## Value Objects (컬렉션 객체화)

Map, List 같은 원시 컬렉션을 전용 객체로 래핑하여 의미를 명확히 합니다.

```java
// 나쁨: 원시 컬렉션 반환
public Map<String, Integer> calculateTenGodDistribution(List<String> heavenlyStems) {
    Map<String, Integer> result = new HashMap<>();
    // ... 계산
    return result;  // 의미가 불명확
}

// 좋음: Value Object로 래핑
public TenGodDistribution calculateTenGodDistribution(List<String> heavenlyStems) {
    Map<String, Integer> data = new HashMap<>();
    // ... 계산
    return new TenGodDistribution(data);  // 의미 명확
}

// TenGodDistribution.java (일급 컬렉션)
public class TenGodDistribution {
    private final Map<String, Integer> distribution;
    
    public TenGodDistribution(Map<String, Integer> data) {
        this.distribution = Collections.unmodifiableMap(new HashMap<>(data));
    }
    
    public Integer getScore(String tenGodName) {
        return distribution.getOrDefault(tenGodName, 0);
    }
    
    public boolean hasHighConfidence(int threshold) {
        return distribution.values().stream()
            .mapToInt(Integer::intValue)
            .sum() >= threshold;
    }
    
    public Map<String, Integer> asMap() {
        return distribution;
    }
}
```

**이점**:
- ✅ 데이터의 의미가 명확해짐 (Map<String, Integer> vs TenGodDistribution)
- ✅ 비즈니스 로직을 객체 내부로 응집 (getScore, hasHighConfidence 등)
- ✅ 컬렉션 불변성 보장 (unmodifiableMap)
- ✅ 타입 안전성 향상

## Validator 분리 (검증 로직 전문화)

Service 계층의 책임을 줄이기 위해 검증 로직을 전용 Validator 클래스로 분리합니다.

```java
// 나쁨: Service 내부에서 검증
@Service
public class ConsultationService {
    public ConsultationResponse getConsultation(ConsultationRequest req) {
        // 검증 로직 (Service 책임 비대화)
        if (req.birthDate() == null) {
            throw new InvalidArgumentException("birthDate required");
        }
        if (req.birthTime() == null) {
            throw new InvalidArgumentException("birthTime required");
        }
        // ... 추가 검증
        // ... 실제 비즈니스 로직 (뒤로 밀려남)
    }
}

// 좋음: Validator 분리
@Component
public class RequestValidator {
    public void validateConsultationRequest(ConsultationRequest req) {
        if (req.birthDate() == null) {
            throw new InvalidArgumentException("birthDate required");
        }
        if (req.birthTime() == null) {
            throw new InvalidArgumentException("birthTime required");
        }
        // ... 추가 검증
    }
}

@Service
public class ConsultationService {
    private final RequestValidator validator;
    
    public ConsultationResponse getConsultation(ConsultationRequest req) {
        validator.validateConsultationRequest(req);  // 검증 위임
        // ... 실제 비즈니스 로직만 집중
    }
}
```

**분리 대상**:
- **SajuValidator**: `validateSajuData(heavenlyStems, earthlyBranches)` - FastAPI 응답 검증
- **RequestValidator**: `validateBirthDate(LocalDate)`, `validateBirthTime(LocalTime)` - 요청 DTO 검증
- **CompatibilityValidator**: `validateCompatibilityRequest(CompatibilityRequest)` - 호환성 분석 요청 검증

## DTO (Data Transfer Object)

Java 21 환경에서는 Request/Response DTO를 반드시 **`record` 타입**으로 작성합니다.

```java
// 좋음
public record UserRequest(String email, LocalDate birthDate) { }

// 나쁨
@Data
public class UserRequest {
    private String email;
    private LocalDate birthDate;
}
```

## JPA 연관관계 설정

모든 연관관계 매핑(`@ManyToOne`, `@OneToMany`, `@OneToOne` 등)은 반드시 **`fetch = FetchType.LAZY`** 명시:

```java
// 좋음
@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
private List<Order> orders;

// 나쁨
@OneToMany(mappedBy = "user")  // 기본값 EAGER = N+1 문제 발생
private List<Order> orders;
```

**이유**: N+1 쿼리 문제 방지

## 계층형 분리 (Layered Architecture)

### Controller 계층
- HTTP 요청/응답 처리
- DTO ↔ Entity 변환
- 비즈니스 로직 금지

### Service 계층
- 모든 비즈니스 로직 집중
- Controller는 얇게 유지
- 트랜잭션 관리

### Repository 계층
- DB 접근만 담당
- Spring Data JPA 활용

## 예외 처리

`try-catch`로 예외를 삼키지 마세요. 대신 커스텀 예외를 발생시키고 `@RestControllerAdvice`를 활용한 전역 예외 처리(Global Exception Handling) 사용:

```java
// 나쁨
try {
    // ...
} catch (Exception e) {
    log.error("Error", e);
    return null;
}

// 좋음
throw new InvalidSajuDataException("Invalid date format");
// → GlobalExceptionHandler에서 처리
```

## 네이밍 컨벤션

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스 | PascalCase | `CareerConsultationService` |
| 메서드/변수 | camelCase | `getUserProfile()`, `birthDate` |
| 상수 | UPPER_SNAKE_CASE | `CAREER_TIMING_API_TIMEOUT` |
| REST API URI | 소문자 kebab-case | `/api/users/profile-images` |

## 상수 및 열거형 관리 (Phase 3-Refactor-3 최종)

상수는 **용도별로**, **완벽하게** 다음과 같이 관리합니다:
- ✅ **모든 매직 넘버 제거**: 0, 1, 2, 3, 4, 5, 8, 12, 25, 30, 35, 50, 75, 100 등 모두 상수화
- ✅ **모든 하드코딩 문자열 제거**: "H1", "H2", "정관", "편관", 월 이름 등 모두 상수화
- ✅ **날짜/시간 포맷 상수화**: "YYYY-MM-DD", "HH:mm", "12:00" 등

### Rule 1: 매직 넘버 & String 하드코딩 금지

❌ **금지**:
```java
// 타임아웃 하드코딩
int timeout = 3;  // 3초인지, 3분인지 불명확
if (confidence > 75) { ... }  // 75가 뭘 의미하는지 불명확
String period = "H1";  // 하드코딩된 문자열
```

✅ **필수**:
```java
// 상수 사용
int timeout = ApiTimeoutConstants.FASTAPI_TIMEOUT_SECONDS;
if (confidence > ValidationConstants.CONFIDENCE_THRESHOLD_HIGH) { ... }
String period = CareerFortuneConstants.FIRST_HALF;  // "H1"
```

### Rule 2: Enum: 비즈니스 도메인 상수 (여러 곳에서 사용)

여러 파일/모듈에서 사용하는 상수 → **Enum으로 정의** (career/enums/)

```java
// FeedbackType.java - 피드백 분류: 여러 모듈에서 참조
public enum FeedbackType {
    CAREER_TIMING("관운 분석"),
    CONSULTATION("AI 컨설팅"),
    COMPATIBILITY("기업 궁합");

    private final String description;
    FeedbackType(String description) { this.description = description; }
    
    public String getDescription() {
        return description;
    }
}

// TenGodConstants.java - 십신(十神) 상수: 여러 분석 컴포넌트에서 사용
public enum TenGodConstants {
    CHIEF_OFFICER("정관", "官", 20, true),     // 정관: 가점 +20
    SIDE_OFFICER("편관", "殺", 20, true),      // 편관: 가점 +20
    FOOD_GOD("식신", "食", -15, false),        // 식신: 감점 -15
    INJURING_OFFICER("상관", "傷", -15, false), // 상관: 감점 -15
    COMPARING_FRIEND("비견", "比", -5, false),  // 비견: 감점 -5
    ROBBING_WEALTH("겁재", "劫", -5, false),   // 겁재: 감점 -5
    CHIEF_WEALTH("정재", "財", 0, false),
    SIDE_WEALTH("편재", "利", 0, false),
    CHIEF_SEAL("정인", "印", 0, false),
    SIDE_SEAL("편인", "紬", 0, false);

    private final String name;
    private final String symbol;
    private final int scoreModifier;    // 관운 분석 시 점수 가중치
    private final boolean isOfficer;    // 관성(官性) 여부

    TenGodConstants(String name, String symbol, int scoreModifier, boolean isOfficer) {
        this.name = name;
        this.symbol = symbol;
        this.scoreModifier = scoreModifier;
        this.isOfficer = isOfficer;
    }

    public static TenGodConstants fromName(String name) {
        for (TenGodConstants tg : values()) {
            if (tg.name.equals(name)) return tg;
        }
        return null;
    }
}

// SatisfactionStatus.java - 만족도 상태: 여러 모듈에서 참조
public enum SatisfactionStatus {
    SATISFIED("만족함"),
    DISSATISFIED("만족하지 않음");

    private final String description;
    SatisfactionStatus(String description) { this.description = description; }
}
```

### Rule 3: Static Constants: 기술 및 설정 상수

API 타임아웃, 검증 임계값 등 → **static class 또는 interface** (career/constants/)

```java
// ApiTimeoutConstants.java
public class ApiTimeoutConstants {
    public static final int FASTAPI_TIMEOUT_SECONDS = 3;
    public static final int FASTAPI_MAX_RETRIES = 2;
    
    public static final int OPENAI_TIMEOUT_SECONDS = 8;
    public static final int OPENAI_MAX_RETRIES = 1;
    
    public static final int PUBLIC_DATA_TIMEOUT_SECONDS = 5;
    public static final int PUBLIC_DATA_MAX_RETRIES = 1;
    
    private ApiTimeoutConstants() {}  // 인스턴스 생성 방지
}

// ValidationConstants.java
public class ValidationConstants {
    public static final int REQUIRED_HEAVENLY_STEMS = 4;
    public static final int REQUIRED_EARTHLY_BRANCHES = 4;
    
    public static final int MIN_CONFIDENCE_SCORE = 0;
    public static final int MAX_CONFIDENCE_SCORE = 100;
    public static final int CONFIDENCE_THRESHOLD_HIGH = 75;
    public static final int CONFIDENCE_THRESHOLD_MEDIUM = 50;
    
    private ValidationConstants() {}
}

// CareerFortuneConstants.java
public class CareerFortuneConstants {
    public static final String FIRST_HALF = "H1";
    public static final String SECOND_HALF = "H2";
    
    public static final int TENGO_ZHENG_GUAN_WEIGHT = 20;  // 정관 가중치
    public static final int TENGO_PIAN_GUAN_WEIGHT = 20;   // 편관 가중치
    public static final int TENGO_FOOD_GOD_WEIGHT = 15;    // 식신 가중치
    
    private CareerFortuneConstants() {}
}

// CompatibilityConstants.java
public class CompatibilityConstants {
    public static final String DEFAULT_FOUNDING_TIME = "12:00";
    public static final int MIN_COMPATIBILITY_SCORE = 0;
    public static final int MAX_COMPATIBILITY_SCORE = 100;
    
    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";
    public static final String CONFIDENCE_LOW = "LOW";
    
    private CompatibilityConstants() {}
}
```

### 사용 예시

**Service에서의 사용**:
```java
@Service
public class SajuDataService {
    public FastAPIResponse fetchSajuFromFastAPI(LocalDate birthDate, LocalTime birthTime) {
        // 타임아웃 상수 사용
        Duration timeout = Duration.ofSeconds(ApiTimeoutConstants.FASTAPI_TIMEOUT_SECONDS);
        
        // 재시도 횟수 상수 사용
        int maxRetries = ApiTimeoutConstants.FASTAPI_MAX_RETRIES;
        
        // RestClient 사용 (WebClient 대신 경량 동기 클라이언트)
        return restClient
            .post()
            .uri("http://fastapi:8000/api/saju/calculate")
            .body(new SajuRequest(birthDate, birthTime))
            .retrieve()
            .toEntity(FastAPIResponse.class)
            .getBody();  // @Retryable이 maxRetries 기준으로 재시도 처리
    }
}

@Service
public class CareerFortuneService {
    private final CareerFortuneAnalyzer analyzer;

    public CareerTimingResponse analyzeCareerTiming(SajuData data) {
        int confidenceScore = calculateConfidence(data);

        // Enum 또는 String 상수 사용
        String favoredPeriod = confidenceScore > ValidationConstants.CONFIDENCE_THRESHOLD_HIGH
            ? CareerFortuneConstants.FIRST_HALF
            : CareerFortuneConstants.SECOND_HALF;

        return new CareerTimingResponse(favoredPeriod, confidenceScore, reasoning);
    }
}

// TenGodConstants 사용 예시 (CareerFortuneAnalyzer)
@Component
public class CareerFortuneAnalyzer {
    // ❌ 나쁨: 하드코딩된 십신 문자열
    // private static final List<String> OFFICER_GODS = List.of("정관", "편관");

    // ✅ 좋음: TenGodConstants 사용
    private static final List<String> OFFICER_GODS = List.of(
        TenGodConstants.CHIEF_OFFICER.getName(),
        TenGodConstants.SIDE_OFFICER.getName()
    );

    public int calculateOfficerScore(Map<String, Integer> tenGodDistribution) {
        int score = 0;
        for (Map.Entry<String, Integer> entry : tenGodDistribution.entrySet()) {
            // TenGodConstants로 십신 조회 후 점수 수정자 적용
            TenGodConstants tenGod = TenGodConstants.fromName(entry.getKey());
            if (tenGod != null) {
                score += entry.getValue() * tenGod.getScoreModifier();
            }
        }
        return score;
    }
}
```

### 상수 배치 원칙

| 상수 타입 | 위치 | 예시 |
|----------|------|------|
| **Enum** (도메인 개념) | `career/enums/` | `FeedbackType.java`, `CareerTimingType.java` |
| **Static Constants** (기술 설정) | `career/constants/` | `ApiTimeoutConstants.java`, `ValidationConstants.java` |
| **필드 레벨** (단일 파일 사용) | 클래스 내부 | `private static final int BUFFER_SIZE = 1024;` |

### final static: 파일 내부용 상수 (특정 파일에서만 사용)

해당 클래스 내에서만 사용하는 상수 → **해당 클래스 내에서 선언**

```java
public class CareerFortuneService {
    private static final String LOG_PREFIX = "[CareerFortune]";
    private static final int MAX_RETRY_ATTEMPTS = 2;
    
    // ...
}
```

### 외부 설정: 환경별 값 (application.yaml에서 관리)

**타임아웃, URL, API Key 등** 환경마다 변하는 값 → **application.yaml 및 환경 변수 사용**

```yaml
# application.yaml
saju:
  fastapi:
    url: ${FASTAPI_URL}          # 환경 변수
    timeout-seconds: 3           # 또는 ${FASTAPI_TIMEOUT:3}
    max-retries: 2
  openai:
    api-key: ${OPENAI_API_KEY}   # 환경 변수 (필수)
    timeout-seconds: 8
    max-retries: 1
  public-data:
    url: ${PUBLIC_DATA_API_URL}
    api-key: ${PUBLIC_DATA_API_KEY}
    timeout-seconds: 5
    max-retries: 1
```

**Java에서 사용** (ConfigurationProperties 또는 @Value):
```java
@Configuration
@ConfigurationProperties(prefix = "saju.fastapi")
public class SajuProperties {
    private String url;
    private long timeoutSeconds;
    private int maxRetries;
    // getters, setters
}
```

**규칙**:
- ✅ **Enum**: 비즈니스 도메인 상수 (FeedbackType, SatisfactionStatus, CareerTimingType)
- ✅ **final static**: 파일 내부용 로그 프리픽스, 계산 상수 등
- ✅ **application.yaml**: 타임아웃, URL, API Key, 재시도 횟수 등
- ❌ **Magic number 금지**: 항상 상수로 추상화
- ❌ **하드코딩된 타임아웃/URL/Key 금지**: 환경 설정으로 관리

## Null 처리

서비스 계층에서 엔티티를 조회할 때는 반드시 `Optional`을 반환받아 처리:

```java
// 좋음
Optional<User> user = userRepository.findById(id);
user.orElseThrow(() -> new UserNotFoundException("User not found"));

// 나쁨
User user = userRepository.findById(id).get();  // NPE 위험
```

## 트랜잭션

### 기본 규칙
모든 Service 메서드에는 `@Transactional` 적용, 단순 조회는 `readOnly = true` 명시:

```java
@Service
public class CareerFortuneService {
    @Transactional(readOnly = true)
    public CareerTimingResponse getCareerTiming(LocalDate birthDate) { ... }

    @Transactional
    public void saveSajuResult(SajuResult result) { ... }
}
```

### ⚠️ 예외: 외부 API 호출이 있는 Service
FastAPI, OpenAI, 공공데이터API 등 **외부 I/O가 있는 메서드는 @Transactional 제거**:

```java
// ❌ 나쁜 예: 외부 API 호출 시 @Transactional 유지
@Service
public class ConsultationService {
    @Transactional  // ❌ Connection Pool 고갈 위험!
    public ConsultationResponse getConsultation(ConsultationRequest req) {
        // FastAPI, OpenAI 호출 중 DB 커넥션 점유
        // 네트워크 지연 동안 커넥션 낭비 → 5000명 동시 사용자 불가능
        FastAPIResponse saju = sajuDataService.fetchSajuFromFastAPI(...);
        CareerAdviceResponse advice = openaiService.callOpenAI(...);
        // DB 저장
    }
}

// ✅ 좋은 예: 외부 API 호출 시 @Transactional 제거
@Service
public class ConsultationService {
    // @Transactional 없음 ✅
    public ConsultationResponse getConsultation(ConsultationRequest req) {
        // 1. 외부 API 호출 (트랜잭션 밖)
        FastAPIResponse saju = sajuDataService.fetchSajuFromFastAPI(...);
        CareerAdviceResponse advice = openaiService.callOpenAI(...);

        // 2. 각 save/find는 Repository의 @Transactional에서 처리
        UserProfile profile = userProfileRepository.findOrCreate(...);  // Repository @Transactional
        SajuResult result = sajuResultRepository.findOrCreate(...);     // Repository @Transactional
        CareerConsultation consultation = consultationRepository.save(...); // Repository @Transactional
    }
}
```

**이유**:
- ✅ Connection Pool 고갈 방지 (외부 API 지연이 커넥션을 점유하지 않음)
- ✅ 5000명 동시 사용자 처리 가능
- ✅ Repository 계층에서 개별 트랜잭션으로 관리 (일관성 유지)
- ✅ 네트워크 지연이 DB 성능에 영향을 주지 않음

**⚠️ 트레이드오프 - 원자성 포기**:
@Transactional 제거 시 각 Repository 호출이 별도 트랜잭션으로 실행됨:
- `userProfileRepository.findOrCreate()` → 커밋
- `sajuResultRepository.findOrCreate()` → 커밋
- `consultationRepository.save()` → 실패 시 롤백 안 됨 (이전 2개는 이미 커밋)

→ **데이터 불완전 저장 가능성** 존재. 이 패턴은 Connection Pool 고갈(5000명 동시)이 원자성보다 더 심각한 문제이기 때문에 의도적으로 선택한 트레이드오프.
→ 실패 시 재시도 가능한 구조이거나 미완성 데이터가 허용되는 경우에만 사용할 것.

## 로깅

`slf4j` 인터페이스 활용. 적절한 로그 레벨 사용:

```java
private static final Logger log = LoggerFactory.getLogger(SomeService.class);

log.info("User consultation request: {}", userId);
log.error("OpenAI API timeout", exception);
log.debug("Saju calculation details: {}", result);
```

### 🔒 로깅 보안 정책 (민감 정보 보호)

#### ❌ 로그에 절대 포함 금지

| 분류 | 금지 항목 | 이유 |
|------|---------|------|
| **개인정보** | `birthDate`, `birthTime`, `email`, `phone` | GDPR/법적 규제 |
| **인증 정보** | API Key, Bearer 토큰, Authorization 헤더값 | 보안 누출 위험 |
| **외부 API 원문** | FastAPI 요청 body 전문, OpenAI 프롬프트 | 민감한 사주 데이터 노출 |

#### ✅ 레벨별 올바른 로깅

| 레벨 | 로그 내용 | 예시 |
|------|---------|------|
| **INFO** | 사용자 ID, API 상태코드, 지연시간(ms), 성공/실패만 | `log.info("Career timing analysis completed: userId={}, duration={}ms", userId, duration);` |
| **DEBUG** | birthDate, API 요청/응답 전문, 상세 계산 정보 (프로덕션에서 비활성화) | `log.debug("Saju data: birthDate={}, response={}", birthDate, apiResponse);` |
| **ERROR** | 스택 트레이스, 민감 정보 제거 후 | `log.error("API call failed after 2 retries", exception);` |

**예시**:
```java
// ❌ 금지: birthDate 로깅
log.info("사용자 분석 요청 (birthDate={})", birthDate);

// ✅ 올바름: ID만 로깅
log.info("사용자 분석 요청 (userId={})", userId);

// ❌ 금지: OpenAI 프롬프트 전문
log.info("OpenAI 요청: {}", fullPrompt);

// ✅ 올바름: DEBUG 레벨로 분리
log.debug("OpenAI 요청: {}", fullPrompt);  // 프로덕션에서 비활성화됨
log.info("OpenAI API 호출 완료: 토큰={}개", tokenCount);  // 운영 로그
```

## RestClient + @Retryable 예외 처리

Spring RestClient를 사용한 외부 API 호출 시 정확한 예외 처리 필수:

### 재시도 대상 (자동 재시도, 지수 백오프)
- **ResourceAccessException**: 네트워크 오류, 타임아웃, 연결 실패 → 그대로 던지기 (@Retryable이 처리)
- **RestClientResponseException (5xx)**: 서버 오류 → 그대로 던지기 (@Retryable이 처리)

### 비재시도 대상 (즉시 실패)
- **RestClientResponseException (4xx)**: 클라이언트 오류 → InvalidSajuDataException 변환 (재시도 금지)

### 올바른 구현 패턴

```java
@Service
public class SajuDataService {
    private final RestClient restClient;

    @Retryable(
        retryFor = {ResourceAccessException.class, RestClientResponseException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)  // 1초, 2초, 4초
    )
    public FastAPIResponse fetchSajuFromFastAPI(LocalDate birthDate, LocalTime birthTime) {
        try {
            return restClient
                .post()
                .uri("http://fastapi:8000/api/saju/calculate")
                .body(new SajuRequest(birthDate, birthTime))
                .retrieve()
                .toEntity(FastAPIResponse.class)
                .getBody();
        } catch (ResourceAccessException e) {
            // 네트워크/타임아웃 → 재시도 대상 (@Retryable이 처리)
            throw e;
        } catch (RestClientResponseException e) {
            // HTTP 4xx/5xx 응답 처리
            if (e.getStatusCode().is4xxClientError()) {
                // 4xx: 클라이언트 오류 → 비재시도
                throw new InvalidSajuDataException("Invalid input", e);
            } else {
                // 5xx: 서버 오류 → 재시도 대상 (원본 예외 유지)
                throw e;
            }
        }
    }
}
```

### ⚠️ 흔한 실수

```java
// ❌ 나쁜 예: 5xx를 별도 예외로 변환 (재시도 손상)
catch (RestClientResponseException e) {
    if (e.getStatusCode().is5xxServerError()) {
        throw new FastAPITimeoutException("Server error", e);  // ❌ @Retryable 대상 손상!
    }
}

// ✅ 올바른 예: 원본 예외 유지
catch (RestClientResponseException e) {
    if (e.getStatusCode().is5xxServerError()) {
        throw e;  // ✅ @Retryable이 정상 작동
    }
}
```

### @EnableRetry 설정
```java
@Configuration
@EnableRetry  // 필수: @Retryable 활성화
public class RetryConfig {
    // 추가 설정 없음: @Retryable 속성(maxAttempts, backoff)으로 제어
}
```

**주의**: `spring.task.retry.*` 프로퍼티는 ThreadPoolTask* 설정용이므로 @Retryable과는 무관합니다.

---

## 테스트 스타일

JUnit 5와 AssertJ를 사용한 **Given-When-Then 패턴** 준수:

```java
@Test
void shouldReturnCareerTimingWhenValidBirthDateProvided() {
    // Given
    LocalDate birthDate = LocalDate.of(1990, 1, 15);

    // When
    CareerTimingResponse response = service.getCareerTiming(birthDate);

    // Then
    assertThat(response.favoredPeriod()).isIn("H1", "H2");
    assertThat(response.confidenceScore()).isBetween(0, 100);
}
```

## 아키텍처 패턴 및 설계 원칙

Domain Model 캡슐화, Mapper 패턴, Service Layer 경량화 등의 아키텍처 패턴은 **[architecture-guide.md](./architecture-guide.md)**에서 상세히 다룹니다.

**참고해야 할 섹션**:
- [Service Layer 경량화](./architecture-guide.md#service-layer-경량화-및-책임-분리): Analyzer 분리, Prompt 외부화
- [Domain Model 캡슐화](./architecture-guide.md#domain-model-캡슐화): 비즈니스 메서드, validate*/is*/build* 패턴
- [Mapper 패턴](./architecture-guide.md#mapper-분리): DTO ↔ Entity 변환 분리

본 가이드는 **코드 스타일**에 집중합니다 (Lombok, record DTO, JPA, 로깅, 상수 관리).
@Service
public class ConsultationService {
    private final CareerConsultationMapper mapper;

    public CareerConsultation createConsultation(CareerAdviceResponse advice,
                                                  SajuResult sajuResult) {
        return mapper.toEntity(advice, sajuResult);  // 한 줄로 끝
    }

    public CareerConsultationResponse getConsultation(Long id) {
        CareerConsultation entity = repository.findById(id);
        return mapper.toResponse(entity);  // 한 줄로 끝
    }
}
```

**Mapper 원칙**:
- ✅ 변환 로직은 전용 Mapper에 집중
- ✅ Service는 흐름 제어(orchestration)만 담당
- ✅ 복잡한 변환은 여러 메서드로 분리
- ❌ Service 중간에 new Entity(...) builder 체인 금지
- ✅ Mapper는 독립적으로 단위 테스트 가능

## 상수 추출 (Constant Extraction)

코드에 박힌 "magic number"나 문자열을 상수로 교체하세요.

```java
// ❌ 나쁜 예: magic number
@Service
public class CareerFortuneService {
    public void analyzeCareerTiming(SajuResult result) {
        if (result.getConfidenceScore() > 80) {  // magic number: 80
            // ...
        }
        if (result.getTenGodData().getCount() < 2) {  // magic number: 2
            // ...
        }
    }
}

// ✅ 좋은 예: 상수화
@Service
public class CareerFortuneService {
    private static final int HIGH_CONFIDENCE_THRESHOLD = 80;
    private static final int MIN_TEN_GOD_COUNT = 2;

    public void analyzeCareerTiming(SajuResult result) {
        if (result.getConfidenceScore() > HIGH_CONFIDENCE_THRESHOLD) {
            // ...
        }
        if (result.getTenGodData().getCount() < MIN_TEN_GOD_COUNT) {
            // ...
        }
    }
}

// 또는 Enum으로 (권장):
public enum ConfidenceThreshold {
    HIGH(80),
    MEDIUM(50),
    LOW(0);

    private final int score;

    ConfidenceThreshold(int score) {
        this.score = score;
    }

    public boolean isMet(int confidenceScore) {
        return confidenceScore >= this.score;
    }
}

// 사용
if (ConfidenceThreshold.HIGH.isMet(result.getConfidenceScore())) {
    // ...
}
```

**상수 관리 원칙**:
- ✅ 비즈니스 도메인 상수 → Enum으로 관리 (여러 곳에서 사용)
- ✅ 파일 내부용 상수 → `private static final`로 관리
- ✅ 환경별 설정값 → application.yaml + 환경변수로 관리
- ❌ magic number/string을 코드에 박지 말 것
- ✅ IntelliJ 단축키 `Opt + Cmd + C`로 자동 추출 가능

---

## 보안

API Key, DB 비밀번호 등 민감 정보는 소스에 직접 노출 금지. 환경 변수 활용:

```yaml
# application.yaml
openai:
  api-key: ${OPENAI_API_KEY}

spring:
  datasource:
    password: ${DB_PASSWORD}
```
