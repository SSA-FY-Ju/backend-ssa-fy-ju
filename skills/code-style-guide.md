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

## 상수 및 열거형(Enum) 관리 규칙

### 매직 리터럴(Magic Literal) 사용 금지

코드 내에 의미를 알 수 없는 문자열이나 숫자를 직접 사용하지 않습니다. 반드시 상수(`static final`) 또는 Enum으로 정의하여 사용합니다.

```java
// 나쁨 - 매직 리터럴
if ("H1".equals(favoredPeriod)) { ... }

// 좋음 - Enum 사용
if (favoredPeriod == FavoredPeriod.H1) { ... }
```

### Enum 사용 우선순위

**도메인 모델의 상태, 유형, 카테고리** 등 고정된 집합을 나타낼 때는 반드시 **Enum을 사용**합니다.

- 예시: `FavoredPeriod` (H1/H2), `FeedbackType` (CAREER_TIMING/CONSULTATION/COMPATIBILITY), `SatisfactionStatus` (SATISFIED/DISSATISFIED)
- 장점: 타입 안정성 보장, 관련 로직을 Enum 내부에 응집 가능

```java
// 좋음
public enum FavoredPeriod {
    H1("상반기"),
    H2("하반기");
    
    private final String displayName;
    FavoredPeriod(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
```

### 클래스 내부 상수 (private static final)

특정 클래스 내부에서만 계산 로직이나 설정값으로 쓰이는 상수는 **클래스 상단에 `private static final`로 정의**합니다.

- 외부 공유 불필요 → `private` 접근 제어자 필수
- 예시: `BASE_CONFIDENCE`, `GWAN_BOOST`, `ELEMENT_ORDER`

```java
@Component
public class CareerFortuneAnalyzer {
    private static final int BASE_CONFIDENCE = 50;
    private static final int GWAN_BOOST = 15;
    // ...
}
```

### 공통 상수 (Global Constants)

여러 클래스에서 공통으로 사용되는 상수는:
- **도메인 개념이면 Enum** (`career/enums/` 디렉토리)
- **기술적 설정값이면 Constants 클래스** (`config/Constants.java` 등)

```java
// 좋음 - 도메인 Enum
public enum SajuElement {
    WOOD("木"), FIRE("火"), EARTH("土"), METAL("金"), WATER("水");
    // ...
}

// 좋음 - 기술 상수
public class ApiConstants {
    public static final int OPENAI_TIMEOUT_SECONDS = 8;
    public static final int FASTAPI_TIMEOUT_SECONDS = 3;
}
```

---

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
