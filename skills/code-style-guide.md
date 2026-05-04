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

## 상수 및 열거형 관리

상수는 **용도별로** 다음과 같이 관리합니다:

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
        
        // Enum 사용
        return webClient
            .post()
            .timeout(timeout)
            .retrieve()
            .bodyToMono(FastAPIResponse.class)
            .retryWhen(...)  // maxRetries 사용
            .block();
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

// SatisfactionStatus.java
public enum SatisfactionStatus {
    SATISFIED("만족함"),
    DISSATISFIED("만족하지 않음");
    
    private final String description;
    SatisfactionStatus(String description) { this.description = description; }
}
```

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

## 로깅

`slf4j` 인터페이스 활용. 적절한 로그 레벨 사용:

```java
private static final Logger log = LoggerFactory.getLogger(SomeService.class);

log.info("User consultation request: {}", userId);
log.error("OpenAI API timeout", exception);
log.debug("Saju calculation details: {}", result);
```

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
