# SSAju 아키텍처 가이드 (공통 원칙)

## 계층형 아키텍처 패턴

Spring Boot 프로젝트는 다음 4개 계층으로 분리합니다:

```
┌─────────────────────────┐
│   Controller Layer      │
│  (HTTP 처리)            │
└────────────┬────────────┘
             │
┌────────────▼────────────┐
│    Service Layer        │
│  (비즈니스 로직)        │
└────────────┬────────────┘
             │
┌────────────▼────────────┐
│   Repository Layer      │
│   (DB 접근)             │
└────────────┬────────────┘
             │
┌────────────▼────────────┐
│    Database Layer       │
│    (MySQL/DB)           │
└─────────────────────────┘
```

### 각 계층의 책임

| 계층 | 책임 | 규칙 |
|------|------|------|
| **Controller** | HTTP 요청/응답 처리, DTO↔Entity 변환 | 비즈니스 로직 금지, 얇게 유지 |
| **Service** | 모든 비즈니스 로직, 외부 API 조율, 계산 | @Transactional 필수, 복잡한 로직 집중 |
| **Repository** | DB 접근만 담당 (Spring Data JPA) | 순수 데이터 접근, 쿼리만 담당 |
| **Global Exception Handler** | @RestControllerAdvice로 모든 예외 처리 | try-catch 금지 |

## Service 계층 설계 패턴 (Phase 3-Refactor-3 최종)

### 1. PromptProvider 분리 (프롬프트 외부화)

**패턴**: Service는 비즈니스 로직(orchestration)만, PromptProvider는 프롬프트 생성 담당

```java
// PromptProvider.java (Phase 3-Refactor-3)
@Component
public class PromptProvider {
    public String getCareerConsultationPrompt(SajuData sajuData, int currentYear, 
                                               LocalDate birthDate, LocalTime birthTime) {
        // 16개 필드 그룹, 12개월 타임라인, 십신 + 지장간 포함
        // 완전히 정의된 JSON 스키마 포함
        return String.format(
            "사주 분석 시스템 프롬프트\n" +
            "일간: %s\n" +
            "오행: %s\n" +
            "십신: %s\n" +
            "지장간: %s\n" +
            "현재 연도: %d\n" +
            "응답 형식: [16개 필드 그룹 완전 정의]",
            sajuData.dayMaster(), ...
        );
    }
}

// ConsultationService.java (Phase 3-Refactor-3 수정)
@Service
public class ConsultationService {
    private final PromptProvider promptProvider;
    
    public ConsultationResponse getConsultation(ConsultationRequest req) {
        // 1. 프롬프트 생성 (PromptProvider 위임)
        String prompt = promptProvider.getCareerConsultationPrompt(
            sajuData, LocalDate.now().getYear(), req.birthDate(), req.birthTime());
        
        // 2. OpenAI 호출
        CareerAdviceResponse response = chatClient.prompt().user(prompt).call().entity(...);
        
        // 3. 응답 반환
        return toResponse(response);
    }
}
```

**이점**:
- ✅ 프롬프트 변경이 Service 로직에 영향 없음
- ✅ 프롬프트 버전 관리 용이 (PromptProvider에서만)
- ✅ 테스트 시 PromptProvider mock 가능

## Service 계층의 DTO 변환 및 계산 로직

### 데이터베이스 저장 vs API 응답 분리

일부 API 응답은 **계산/파생 필드**를 포함합니다. 이들은 DB에 저장하지 않고 Service 계층에서 생성합니다:

**예: CompatibilityResponse (US3)**

| 필드 | 저장? | 계산 위치 | 설명 |
|------|------|---------|------|
| compatibilityScore | ✅ | Repository | CompanyCompatibility 엔티티에 저장 |
| confidenceLevel | ✅ | Repository | CompanyCompatibility 엔티티에 저장 |
| **scoreBreakdown** | ❌ | Service | tenGodCompatibility, fiveElementsMatch, hiddenStemAlignment, leadershipFit 계산 |
| **roleCompatibility[]** | 부분 | Service | DB: RecommendedRole(roleName만) → API: roleCompatibility(score, reason, recommendation 추가 계산) |
| **synergies[]** | ❌ | Service | 사주 분석 결과 기반 텍스트 생성 |
| **cautions[]** | ❌ | Service | 위험 요소 분석 기반 생성 |
| **monthlyForecast[]** | ❌ | Service | 5개월 예측 점수/조언 계산 |
| **careerMilestones** | ❌ | Service | 경력 발전 단계 기반 생성 |

**코드 패턴**:
```java
// Service 메서드
public CompatibilityResponse analyzeCompatibility(LocalDate userBirthDate, ...) {
    // 1. DB에서 엔티티 로드
    Saju userSaju = getSajuData(userBirthDate);
    Saju companySaju = getCompanySaju(companyFoundingDate);
    
    // 2. 복합 계산 (CompatibilityScoreCalculator 등 활용)
    int score = calculator.calculateScore(userSaju, companySaju);
    Map<String, Integer> breakdown = calculator.getScoreBreakdown();
    List<RoleMatch> roleMatches = analyzer.analyzeRoles(userSaju, companySaju);
    
    // 3. 계산 결과를 DB 저장 (필요시)
    CompanyCompatibility entity = compRepo.save(
        new CompanyCompatibility(userBirthDate, companyName, score)
    );
    roleMatches.forEach(role -> 
        recRoleRepo.save(new RecommendedRole(entity, role.name))
    );
    
    // 4. DTO로 변환하여 반환 (API는 엔티티보다 많은 필드 포함 가능)
    return CompatibilityResponse.of(
        score, 
        breakdown,           // 계산값
        roleMatches,         // 계산값: score, reason 추가
        synergiesText,       // 계산값
        cautionsText,        // 계산값
        monthlyForecasts,    // 계산값
        careerPlan           // 계산값
    );
}
```

## 의존성 관리 원칙

### 핵심 Spring 의존성

- **Spring Web MVC**: REST 컨트롤러 / HTTP 처리
- **Spring Data JPA**: ORM, 데이터베이스 접근
- **Spring Validation**: @Valid, 제약 어노테이션
- **Spring Security**: 인증/인가 (필요시)
- **Lombok**: 보일러플레이트 감소

### 외부 라이브러리 선택

| 용도 | 권장 | 피해야 할 것 |
|------|------|------------|
| **HTTP 클라이언트** | WebClient, RestTemplate | 라이브러리 추가 금지 |
| **LLM 통합** | Spring AI, OpenAI SDK | 직접 HTTP 호출 |
| **로깅** | slf4j (SLF4J) | System.out.println |
| **검증** | Spring Validation | 수동 if 체크 |
| **트랜잭션** | @Transactional | 수동 트랜잭션 관리 |

## 상수 관리 및 도메인 모델

**⚠️ 상수 관리의 상세 규칙 및 예제는 [`code-style-guide.md#상수-및-열거형-관리`](./code-style-guide.md)를 참고하세요.**

### 핵심 원칙 (요약)

- **도메인 상수**: Enum으로 정의 (TenGodConstants, FeedbackType 등)
- **기술 상수**: Static Class로 정의 (ApiTimeoutConstants, ValidationConstants 등)
- **일관성**: 모든 매직 넘버와 하드코딩된 String 제거
- **중앙화**: 여러 곳에서 사용하는 상수는 한 곳에서만 정의

## 예외 처리 원칙

### Rule 1: try-catch로 예외를 삼키지 말 것

❌ **나쁜 예**:
```java
try {
    // ...
} catch (Exception e) {
    log.error("Error", e);
    return null;  // 예외를 삼킴
}
```

✅ **좋은 예**:
```java
if (invalid) {
    throw new InvalidSajuDataException("reason");
}
// → GlobalExceptionHandler에서 처리
```

### Rule 2: 커스텀 예외 계층 구조

```
ApplicationException (root)
├── DomainException (비즈니스 로직)
├── ExternalApiException (외부 API)
└── DataAccessException (DB)
```

### Rule 3: GlobalExceptionHandler 구현

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(InvalidSajuDataException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidData(...) {
        // ApiResponse로 통일된 에러 응답
    }
}
```

**이점**:
- 모든 예외가 일관된 형식으로 응답
- 컨트롤러 코드 간결
- 에러 로깅 중앙화

## RestClient + Spring Retry 패턴 (Phase 3-Enhancement)

### RestClient의 이점 (WebClient 대비)

**As-Is (WebClient)**:
- Reactive 의존성 무거움
- block() 호출로 동기 처리 → Reactive의 이점 상실
- 복잡한 설정, 높은 학습곡선

**To-Be (RestClient)**:
- 경량 동기 HTTP 클라이언트
- Spring Retry와 자연스럽게 결합
- 직관적인 API

### 구현 패턴

```java
// RestClient bean 설정
@Configuration
public class FastApiRestClientConfig {
    @Bean
    public RestClient fastApiRestClient() {
        return RestClient.create();
    }
}

// Service에서 RestClient + @Retryable 사용
@Service
public class SajuDataService {
    private final RestClient restClient;
    private final SajuResultJdbcRepository sajuResultJdbc;
    
    @Retryable(
        retryFor = {ResourceAccessException.class, RestClientResponseException.class},
        maxAttempts = 3,
        backoff = @Backoff(
            delay = 1000,  // 1초
            multiplier = 2.0  // 2배씩 증가 (1초, 2초, 4초)
        )
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
            // 네트워크/타임아웃 오류 → 재시도 대상 (@Retryable이 처리)
            throw e;
        } catch (RestClientResponseException e) {
            // HTTP 4xx/5xx 응답 → 재시도 대상
            if (e.getStatusCode().is4xxClientError()) {
                throw new InvalidSajuDataException("Invalid input", e);
            } else {
                throw new FastAPITimeoutException("FastAPI error", e);
            }
        }
    }
}

// 주의: @Retryable은 annotation 속성으로만 제어됨
// spring.task.retry.* 프로퍼티는 ThreadPoolTask* 설정용이므로
// 여기서는 @Retryable의 maxAttempts, backoff로 직접 제어
```

**이점**:
- ✅ 경량 의존성 (Reactive 불필요)
- ✅ Spring Retry로 지수 백오프 자동화
- ✅ 명확한 예외 처리

## 외부 API 호출 패턴

### 재시도 로직 (Retry Pattern)

모든 외부 API 호출은 재시도 메커니즘 포함:

```
요청 → 실패 → 지수 백오프 대기 → 재시도 (최대 N회)
```

**설정 요소**:
- **타임아웃**: 각 API별로 명시 (e.g., 3초, 8초)
- **재시도 횟수**: 외부 API 중요도에 따라 (1~2회)
- **백오프 전략**: 지수 백오프 (1초, 2초, 4초...)

### 예외 변환

외부 API 예외 → 애플리케이션 커스텀 예외로 변환:

```java
try {
    return fastApiClient.calculateSaju(birthDate);
} catch (TimeoutException e) {
    throw new ExternalApiTimeoutException("FastAPI timeout", e);
}
```

**이유**: 외부 API의 세부 구현에 의존하지 않음

## 로깅 전략

### 로그 레벨 사용 원칙

| 레벨 | 사용 시점 | 예시 |
|------|---------|------|
| **DEBUG** | 개발/디버깅용 상세 정보 | `log.debug("입력값: {}", request);` |
| **INFO** | 주요 비즈니스 이벤트 | `log.info("사주 분석 시작: userId={}", userId);` |
| **WARN** | 예상 범위 내 문제 | `log.warn("API 응답 지연: {}ms", duration);` |
| **ERROR** | 예외 발생 | `log.error("FastAPI 호출 실패", exception);` |

### 로깅 대상 (필수)

- 모든 외부 API 호출 (요청, 응답, 지연시간, 에러)
- 주요 비즈니스 로직 시작/완료
- 예외 발생 (스택 트레이스 포함)
- 성능 지표 (느린 쿼리, 응답 시간)

### 민감 정보 보호

❌ **금지**: API Key, 비밀번호, 개인정보 로깅
```java
log.info("OpenAI API Key: {}", apiKey);  // 절대 금지
```

✅ **허용**: 마스킹 또는 ID만 로깅
```java
log.info("User consultation request: userId={}", userId);
```

## 테스트 원칙

### Rule 1: Given-When-Then 패턴 필수

```java
@Test
void shouldReturnCareerTimingWhenValidInput() {
    // Given: 사전 조건 설정
    LocalDate birthDate = LocalDate.of(1990, 1, 15);

    // When: 기능 실행
    CareerTimingResponse response = service.getCareerTiming(birthDate);

    // Then: 결과 검증
    assertThat(response.favoredPeriod()).isIn("H1", "H2");
}
```

### Rule 2: 테스트 대상 명확히

- **단위 테스트**: 개별 메서드/클래스 (모킹 활용)
- **통합 테스트**: 계층 간 상호작용 (실제 DB/API)
- **E2E 테스트**: 전체 API 흐름

### Rule 3: 테스트 격리

각 테스트는 독립적으로 실행 가능해야 함:
- 테스트 순서 무관
- 이전 테스트의 상태 영향 없음
- 공유 리소스 최소화

## 데이터 접근 패턴

### JPA 관계 설정 (필수)

모든 연관관계는 **FetchType.LAZY** 명시:

```java
// 좋음
@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
private List<Order> orders;

// 나쁨
@OneToMany(mappedBy = "user")  // EAGER = N+1
private List<Order> orders;
```

### N+1 쿼리 문제 방지

❌ **N+1 발생**:
```java
List<User> users = userRepository.findAll();  // 1개 쿼리
for (User user : users) {
    user.getOrders();  // 각 user마다 추가 쿼리 (N개)
}
```

✅ **개선**:
```java
List<User> users = userRepository.findAllWithOrders();  // JOIN 사용
// 또는 LAZY로 설정 후 필요시에만 접근
```

## 보안 원칙

### Rule 1: 민감 정보는 환경 변수로

❌ **금지**:
```java
String apiKey = "sk-1234567890...";  // 소스에 노출
```

✅ **허용**:
```yaml
openai:
  api-key: ${OPENAI_API_KEY}
```

### Rule 2: 입력 검증은 경계에서

- **API 입력**: @Valid 어노테이션으로 검증
- **외부 API 응답**: null 체크, 범위 검증
- **DB 데이터**: 비즈니스 로직 검증

### Rule 3: 에러 메시지 주의

❌ **금지**: 시스템 정보 노출
```
"User not found" → 해킹에 취약 (사용자 존재 여부 노출)
```

✅ **허용**: 일반적 메시지
```
"Resource not found"
```

## 로컬 개발 환경 설정

### 1. 환경변수 (production)

배포 환경에서는 다음 환경변수를 설정:

```bash
# 데이터베이스
export DB_URL="jdbc:mysql://localhost:3306/ssaju"
export DB_USERNAME="root"
export DB_PASSWORD="your_password"

# JPA
export JPA_DDL_AUTO="validate"  # 스키마 검증만 수행
export SHOW_SQL="false"

# 외부 API
export OPENAI_API_KEY="sk-..."
```

### 2. 로컬 개발 환경

`application-local.yaml`이 자동 로드되므로 로컬 환경변수 설정 불필요:

```bash
# 방법 1: 로컬 프로파일로 실행 (환경변수 불필요)
cd SSAju/
./gradlew bootRun --args='--spring.profiles.active=local'

# 방법 2: 또는 기본 실행 (이미 local 프로파일 사용)
./gradlew bootRun
```

**로컬 기본 설정** (`application-local.yaml`에 정의):
- DB: `jdbc:mysql://localhost:3306/ssaju`
- JPA DDL: `update` (테이블 자동 생성/수정)
- SQL 로깅: `true` (디버깅용)
- 로컬 환경 자격증명은 `application-local.yaml` 참고

---

## 외부 API 통합 패턴

SSAju는 다음 3개 외부 API와 연동:

### 1. FastAPI (만세력 계산)

```
요청: POST /api/saju/calculate
입력 형식: {
  "birthDate": "1990-10-10",    // YYYY-MM-DD
  "birthTime": "14:30"          // HH:mm (24시간 형식)
}

응답 형식 (camelCase):
{
  "heavenlyStems": ["庚", "丙", "己", "辛"],        // 4개 (年月日時)
  "earthlyBranches": ["午", "戌", "未", "寅"],     // 4개 (年月日時)
  "fiveElements": {"木": 1, "火": 2, "土": 1, "金": 2, "水": 2},
  "yearPillar": "庚午",
  "monthPillar": "丙戌",
  "dayPillar": "己未",
  "hourPillar": "辛寅",
  "birthTime": "14:30",
  "birthDate": "1990-10-10",
  "solarCorrection": {...}
}

타임아웃: `saju.fastapi.timeout-seconds` (기본값: 3초)
재시도: `saju.fastapi.max-retries` (기본값: 2회, 지수 백오프)

참고: 
- 십神은 FastAPI에서 제공하지 않음 (Spring의 TenGodCalculator에서 계산)
- 地藏干도 FastAPI에서 제공하지 않음 (Spring의 HiddenStemCalculator에서 계산)
```

**예외 처리**:
```java
try {
    return fastApiClient.calculateSaju(birthDate);
} catch (TimeoutException e) {
    throw new FastAPITimeoutException("FastAPI 요청 시간 초과", e);
} catch (Exception e) {
    throw new ExternalApiException("FastAPI 호출 실패", e);
}
```

### 2. OpenAI API (커리어 상담 - 16 Field Groups)

```
모델: gpt-4o-mini
기능: JSON Mode (구조화된 응답, 16개 필드 그룹)
입력: {생년월일, 사주 데이터 (십신 + 지장간), 현재 연도, 12개월 타임라인 요청}
응답: {
  // 기본 조언 (3 필드)
  industries: [{"name": "산업명", "reason": "이유", "recommendedRoles": ["직무1", "직무2"]}],
  interviewTips: [면접 팁 문자열],
  strengths: [강점 분석],
  
  // 관운 분석 (3 필드)
  favoredPeriod: "H1" | "H2",
  confidenceScore: 0-100,
  reasoning: "상세 근거 (정관 기운 등)",
  
  // 사주 데이터 (1 필드)
  sajuProfile: {
    dayMaster: "己",
    dayMasterDescription: "己土(기토) - 수용적이고 꼼꼼한 성향",
    fiveElements: {"木":1, "火":2, "土":2, "金":2, "水":1},
    fiveElementsAnalysis: "오행 분석 설명",
    tenGodDistribution: {"正官":1, "偏官":1, ...},
    keyTenGods: ["正官", "偏官"]
  },
  
  // OpenAI 분석 결과 (12 필드)
  cautions: [주의사항],
  wealthStyle: {incomeSource, financialAdvice, investmentTendency, additionalIncome},
  longTermRoadmap: {phase0to2years, phase3to5years, ultimateGoal, goalDescription},
  personalBranding: {suitColor, impression, hairAndMakeup, brandingKeyword, taglineForResume},
  powerKeywords: {keywords: [{keyword, element, description, usageExample, context}], selectionGuide, usageTips, avoidanceTip},
  mentalCare: {stressVulnerability, rechargeMethod, mindsetMantra, emergencyTactic},
  environmentFit: {workVibe, companySize, colleagueType, conflictApproach, physicalEnv, culturalFit},
  workStyle: {preferredCompanyType, leadershipType, decisionMaking, conflictResolution},
  relationshipStrategy: {socialStyle, networkingApproach, teamPosition, conflictResolution, careerNetworking},
  careerTimeline: {year: 2026, months: {"March": {type, description}}, pivotPoints: [{month, type, score, description}], warningMonths, warningDescription}
}

타임아웃: `saju.openai.timeout-seconds` (기본값: 8초, LLM 응답 시간)
재시도: `saju.openai.max-retries` (기본값: 1회, 자동)
```

**Nested Record Types**:
```java
record CareerAdviceResponse(
    List<IndustryRecommendation> industries,
    List<String> interviewTips,
    List<String> strengths,
    List<String> cautions,
    WealthStyle wealthStyle,
    LongTermRoadmap longTermRoadmap,
    PersonalBranding personalBranding,
    PowerKeywords powerKeywords,
    MentalCare mentalCare,
    EnvironmentFit environmentFit,
    WorkStyle workStyle,
    RelationshipStrategy relationshipStrategy,
    CareerTimeline careerTimeline,
    List<String> keyTenGods,
    String dayMasterDescription,
    String fiveElementsAnalysis
)

// 14+ nested record types
record IndustryRecommendation(String name, String reason, List<String> recommendedRoles)
record WealthStyle(String incomeSource, String financialAdvice, String investmentTendency, String additionalIncome)
record PhaseAdvice(String goal, String focus, String action)
record LongTermRoadmap(PhaseAdvice phase0to2years, PhaseAdvice phase3to5years, String ultimateGoal, String goalDescription)
record PersonalBranding(String suitColor, String impression, String hairAndMakeup, String brandingKeyword, String taglineForResume)
record PowerKeyword(String keyword, String element, String description, String usageExample, String context)
record PowerKeywords(List<PowerKeyword> keywords, String selectionGuide, List<String> usageTips, String avoidanceTip)
record MentalCare(List<String> stressVulnerability, List<String> rechargeMethod, String mindsetMantra, String emergencyTactic)
record EnvironmentFit(String workVibe, String companySize, String colleagueType, String conflictApproach, String physicalEnv, String culturalFit)
record WorkStyle(String preferredCompanyType, String leadershipType, String decisionMaking, String conflictResolution)
record RelationshipStrategy(String socialStyle, String networkingApproach, String teamPosition, String conflictResolution, String careerNetworking)
record MonthFortune(String type, String description)
record PivotPoint(String month, String type, int score, String description)
record CareerTimeline(int year, Map<String, MonthFortune> months, List<PivotPoint> pivotPoints, List<String> warningMonths, String warningDescription)

// ConsultationResponse inner record
record SajuProfile(String dayMaster, String dayMasterDescription, Map<String, Integer> fiveElements, String fiveElementsAnalysis, Map<String, Integer> tenGodDistribution, List<String> keyTenGods)
```

**Spring AI 사용 (권장)** - JSON Mode 자동 처리 (16 Field Groups):
```java
@Configuration
public class ChatClientConfig {
    @Bean
    public ChatClient chatClient(ChatClientBuilder builder) {
        return builder.build();
    }
}

// Service에서 사용
CareerAdviceResponse response = chatClient.prompt()
    .user(prompt)  // 십신 + 지장간 + 현재 연도 + 12개월 타임라인 + 모든 16개 필드 그룹 요청
    .call()
    .entity(CareerAdviceResponse.class);  // 자동 JSON 매핑 (14+ nested record types)
```

**1-Call Design Pattern (Expanded to 16 Field Groups)** (Session 2026-04-30):

▶️ **실제 구현 참고**: `SSAju/src/main/java/ssafy/SSAju/service/ConsultationService.java`

- **메서드**: `getCareerConsultation()` (line 53-136)
- **흐름**: FastAPI 조회 → 십신/지장간 계산 → 관운 분석 → DB 저장 → OpenAI 호출 → 응답 반환
- **트랜잭션**: @Transactional 제거 (Network I/O 동안 DB 커넥션 점유 방지)
- **응답**: 19개 필드 (기본 조언 3 + 관운 분석 3 + 사주 프로필 1 + OpenAI 분석 12 필드)

**프롬프트 구성** (실제 구현):

▶️ **실제 프롬프트 참고**: `ConsultationService.buildPrompt()` (line 245-284)

실제 프롬프트는 다음을 포함합니다:
- **사주 데이터**: 일간, 천간, 지지, 오행, 지장간, 십신 분포
- **분석 요청** (상세):
  - 취업 적합 산업군 3~5개 (name, reason, recommendedRoles 포함)
  - 면접 전략 및 직무 강점·약점 분석
  - 재물운, 장기 커리어 로드맵(0~2년, 3~5년 단계)
  - 퍼스널 브랜딩, 자소서 파워키워드(3개, 오행 기반)
  - 멘탈 케어, 최적 근무 환경, 업무 스타일, 인간관계 전략
  - 12개월 월별 운세 및 전환점(점수 8 이상인 달만)
  - 일간 기반 성향 분석 및 핵심 십신 2~3개 선별
- **JSON 형식 지정**: careerTimeline.months는 객체 형식 필수 (올바른 예/잘못된 예 명시)

**Transaction Separation** (Session 2026-04-30):
- ConsultationService에서 @Transactional 제거 (Network I/O 시간 동안 DB 커넥션 점유 방지)
- FastAPI 호출: 트랜잭션 밖
- OpenAI 호출 (16개 필드 그룹 포함): 트랜잭션 밖
- 각 DB 작업: Repository의 @Transactional에 의해 개별 트랜잭션으로 실행
- Result: Connection Pool 고갈 방지, 응답 시간 15초 이내 달성 (OpenAI 8초 타임아웃 포함)

### FastAPI-Spring 역할 분담 (Phase 1 중요 결정)

**FastAPI 책임** (기본 사주 데이터만):
- 생년월일시(4 기둥: 年月日時) 입력 받음
- 천간(Heavenly Stems) 계산 → 4개
- 지지(Earthly Branches) 계산 → 4개  
- 오행(Five Elements) 계산 → {목화토금수} 개수 매핑
- JSON 응답

**Spring 백엔드 책임** (도메인 로직):
- FastAPI 응답 수신
- **TenGodCalculator**: 십신(十神) 계산 (일간 기준으로 월간/시간 분석)
- **HiddenStemCalculator**: 지장간(地藏干) 계산 (지지별 숨겨진 천간 → Map<String, List<String>>)
- **CareerFortuneAnalyzer**: 관운 분석 (십신 + 지장간 결과 활용하여 H1/H2 판정)
- **CompatibilityScoreCalculator**: 궁합 분석 (두 사주의 십신 + 지장간 비교)

**이점**:
✅ FastAPI 변경에 영향받지 않음 (Spring이 계산 로직 통제)
✅ 십신/지장간 계산 로직을 Spring에서 관리 가능
✅ 도메인 정확성 향상 (전체 사주 분석을 Spring 단에서 수행)
✅ 기업 설립일도 동일 수준의 정확성 유지 (시간 미상 시 12:00 기본값)

---

### 3. 공공데이터 API (기업 정보)

```
기능: 기업명으로 설립연도 조회
입력: 회사명
응답: {foundingYear, companyId}
타임아웃: ApiTimeoutConstants.PUBLIC_DATA_TIMEOUT_SECONDS (5초)
재시도: ApiTimeoutConstants.PUBLIC_DATA_MAX_RETRIES (1회)
Fallback: 찾지 못하면 사용자 수동입력 요청 (graceful degradation)
기본 설립시간: CompatibilityConstants.DEFAULT_FOUNDING_TIME ("12:00")
```

---

---

## Service Layer 경량화 패턴

### Rule 1: Prompt 외부 분리

프롬프트는 비즈니스 로직이 아니라 '설정'입니다. 서비스 코드에 하드코딩하지 마세요.

❌ **나쁜 예** (프롬프트 하드코딩):
```java
@Service
public class ConsultationService {
    public String getConsultation() {
        String prompt = "다음 사주를 분석하여...";  // 서비스에 박힘
        return openaiClient.call(prompt);
    }
}
```

✅ **좋은 예** (PromptProvider로 분리):
```java
// PromptProvider.java (설정 전용)
@Component
public class PromptProvider {
    public String getCareerConsultationPrompt(SajuData data, int currentYear) {
        // application.yaml 또는 프로퍼티에서 로드
        return String.format(
            "당신은 사주 명리학 전문가입니다. 다음 데이터를 분석하세요:\n" +
            "- 일간: %s\n" +
            "- 오행: %s\n" +
            "- 십신: %s\n" +
            "- 지장간: %s\n" +
            "현재 연도: %d\n" +
            "12개월 타임라인을 포함한 JSON 응답을 제공하세요.",
            data.dayMaster(), data.fiveElements(),
            data.tenGods(), data.hiddenStems(), currentYear
        );
    }
}

// Service (간결)
@Service
public class ConsultationService {
    private final PromptProvider promptProvider;

    public ConsultationResponse getConsultation(ConsultationRequest req) {
        String prompt = promptProvider.getCareerConsultationPrompt(
            sajuData, LocalDate.now().getYear());
        return openaiClient.call(prompt);
    }
}
```

### Rule 2: Analyzer 분리

덩치가 큰 분석 로직은 별도의 컴포넌트로 추출하세요.

❌ **나쁜 예** (거대 서비스):
```java
@Service
public class CareerFortuneService {
    public H1H2Result analyzeCareerTiming(SajuData data) {
        // 십신 계산, 지장간 계산, 관운 판정, 신뢰도 계산...
        // 모두 여기에 있음 (200+ 라인)
        int tenGodScore = calculateTenGod(...);
        Map<String, List<String>> hiddenStems = calculateHiddenStems(...);
        String favoredPeriod = determineFavoredPeriod(...);
        // ...
    }
}
```

✅ **좋은 예** (Analyzer 분리 - Composition):
```java
// 1. Analyzer 컴포넌트 (단일 책임)
@Component
public class CareerFortuneAnalyzer {
    public H1H2Result analyze(SajuData data,
                              Map<String, Integer> tenGods,
                              Map<String, List<String>> hiddenStems) {
        // 관운 분석 로직만 집중
        int confidenceScore = calculateConfidence(tenGods, hiddenStems);
        String period = determineFavoredPeriod(tenGods, hiddenStems);
        String reasoning = buildReasoning(period, tenGods);
        return new H1H2Result(period, confidenceScore, reasoning);
    }
}

// 2. Service는 orchestration만 (흐름 제어)
@Service
public class CareerFortuneService {
    private final TenGodCalculator tenGodCalc;
    private final HiddenStemCalculator hiddenStemCalc;
    private final CareerFortuneAnalyzer analyzer;

    public H1H2Result analyzeCareerTiming(SajuData data) {
        // 1단계: 계산
        var tenGods = tenGodCalc.calculate(data.heavenlyStems());
        var hiddenStems = hiddenStemCalc.calculate(data.earthlyBranches());

        // 2단계: Analyzer에 위임
        return analyzer.analyze(data, tenGods, hiddenStems);  // Composition
    }
}
```

**이점**:
- ✅ 각 컴포넌트는 단일 책임만 수행
- ✅ Service는 흐름만 담당 (orchestration)
- ✅ Analyzer, Calculator는 독립적으로 테스트 가능
- ✅ 변경의 파급 범위 최소화

### Rule 3: Mapper 분리

DTO ↔ Entity 변환 로직은 서비스에서 분리하세요.

❌ **나쁜 예** (서비스에서 변환):
```java
@Service
public class ConsultationService {
    public CareerConsultation createConsultation(CareerAdviceResponse advice) {
        // 변환 로직이 서비스에 박힘
        var industries = advice.industries().stream()
            .map(ind -> Industry.builder()
                .name(ind.name())
                .reason(ind.reason())
                .build())
            .collect(toList());

        var tips = advice.interviewTips().stream()
            .map(tip -> InterviewTip.builder()
                .content(tip)
                .build())
            .collect(toList());

        // ... 더 많은 변환

        return CareerConsultation.builder()
            .industries(industries)
            .interviewTips(tips)
            .build();
    }
}
```

✅ **좋은 예** (Mapper로 분리):
```java
// Mapper 컴포넌트
@Component
public class CareerConsultationMapper {
    public CareerConsultation toEntity(CareerAdviceResponse advice,
                                       SajuResult sajuResult) {
        return CareerConsultation.builder()
            .sajuResult(sajuResult)
            .industries(mapIndustries(advice.industries()))
            .interviewTips(mapInterviewTips(advice.interviewTips()))
            .strengths(mapStrengths(advice.strengths()))
            .openaiModelVersion(advice.openaiModelVersion())
            .build();
    }

    private List<Industry> mapIndustries(List<CareerAdviceResponse.IndustryRecommendation> dtos) {
        return dtos.stream()
            .map(dto -> Industry.builder()
                .name(dto.name())
                .reason(dto.reason())
                .build())
            .collect(toList());
    }

    private List<InterviewTip> mapInterviewTips(List<String> tips) {
        return tips.stream()
            .map(tip -> InterviewTip.builder().content(tip).build())
            .collect(toList());
    }

    private List<Strength> mapStrengths(List<String> strengths) {
        return strengths.stream()
            .map(str -> Strength.builder().description(str).build())
            .collect(toList());
    }
}

// Service는 간결하게
@Service
public class ConsultationService {
    private final CareerConsultationMapper mapper;

    public CareerConsultation createConsultation(CareerAdviceResponse advice,
                                                  SajuResult sajuResult) {
        return mapper.toEntity(advice, sajuResult);  // 한 줄로 끝
    }
}
```

**Mapper 원칙**:
- ✅ 변환 로직은 전용 Mapper에 집중
- ✅ Service는 흐름 제어(orchestration)만 담당
- ✅ 복잡한 변환은 여러 메서드로 분리
- ✅ Mapper는 독립적으로 단위 테스트 가능

---

## Domain Model 캡슐화

비즈니스 로직을 엔티티 내부에 담으세요. getter로 필드를 꺼내 로직을 짜지 마세요.

❌ **나쁜 예** (getter 남용):
```java
// Service에서 필드 꺼냄
var tenGodType = sajuResult.getTenGodData().getType();
var hiddenStems = sajuResult.getHiddenStemData();
var confidence = sajuResult.getConfidenceScore();

if (confidence > 80 && hiddenStems != null) {
    // 로직이 Service에 흩어짐
    result = "HIGH";
} else if (confidence > 50) {
    result = "MEDIUM";
}
```

✅ **좋은 예** (엔티티가 로직 수행):
```java
// SajuResult 엔티티 (비즈니스 메서드 제공)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class SajuResult {
    private Long id;
    private TenGodData tenGodData;
    private List<HiddenStemData> hiddenStemData;
    private Integer confidenceScore;

    // 비즈니스 메서드 (도메인 로직을 엔티티가 담당)
    public ConfidenceLevel getConfidenceLevel() {
        if (confidenceScore == null) {
            throw new InvalidSajuDataException("Confidence score not set");
        }
        if (confidenceScore >= 80) return ConfidenceLevel.HIGH;
        if (confidenceScore >= 50) return ConfidenceLevel.MEDIUM;
        return ConfidenceLevel.LOW;
    }

    public boolean isHighConfidence() {
        return getConfidenceLevel() == ConfidenceLevel.HIGH;
    }

    public void validateForAnalysis() {
        if (tenGodData == null) {
            throw new InvalidSajuDataException("Ten God data required");
        }
        if (hiddenStemData == null || hiddenStemData.isEmpty()) {
            throw new InvalidSajuDataException("Hidden stem data required");
        }
        if (confidenceScore == null) {
            throw new InvalidSajuDataException("Confidence score required");
        }
    }

    public String buildAnalysisReasoning() {
        // 관운 분석 근거 생성
        StringBuilder sb = new StringBuilder();
        sb.append("일간(").append(tenGodData.getDayMaster()).append(") ");
        sb.append("기반 십신 분포가 ").append(tenGodData.getDistribution()).append(", ");
        sb.append("신뢰도는 ").append(confidenceScore).append("점입니다.");
        return sb.toString();
    }
}

// Service에서는 엔티티의 메서드만 호출 (간결)
@Service
public class CareerFortuneService {
    public void useCareerResult(SajuResult result) {
        // 엔티티가 검증
        result.validateForAnalysis();

        // 엔티티의 메서드로 판단
        if (result.isHighConfidence()) {
            // HIGH confidence 처리
        }

        // 엔티티가 제공하는 정보
        String reasoning = result.buildAnalysisReasoning();
        log.info("Analysis reasoning: {}", reasoning);
    }
}
```

**캡슐화 원칙**:
- ✅ 엔티티는 `@Getter` 제공 (읽기 용도)
- ❌ 엔티티는 setter 최소화 (불변성 선호, Builder 사용)
- ✅ 비즈니스 로직은 엔티티의 public 메서드로
- ❌ Service에서 getter로 필드 꺼내 계산하지 말 것
- ✅ 검증, 판단, 계산 로직은 엔티티가 담당

---

## Phase 3-Refactor: Entity Normalization 패턴

### SajuFullData 완전 정규화 (Phase 3-Refactor-3)

**목표**: SajuResult.fullSajuData (Map<String, Object>, JSON) → SajuFullData (1:1 엔티티)로 완전 정규화

**변환 전**:
```java
// SajuResult에 JSON 저장
Map<String, Object> fullSajuData = Map.of(
    "yearPillar", "庚午",
    "monthPillar", "丙戌",
    "dayPillar", "己未",
    "hourPillar", "辛寅",
    "dayMaster", "己",
    "dayMasterElement", "土",
    "fiveElements", Map.of("木", 1, "火", 2, ...),
    "solarCorrection", Map.of(...)
);
sajuResult.setFullSajuData(fullSajuData);  // JSON 컬럼
```

**변환 후**:
```java
// SajuFullData 엔티티 저장
SajuFullData data = SajuFullData.builder()
    .sajuResult(sajuResult)
    .yearPillar("庚午")
    .monthPillar("丙戌")
    .dayPillar("己未")
    .hourPillar("辛寅")
    .dayMaster("己")
    .dayMasterElement("土")
    .fiveElements(Map.of("木", 1, "火", 2, ...))  // JSON 또는 1:N 엔티티
    .solarCorrection(Map.of(...))
    .build();
sajuFullDataRepository.save(data);  // 정규화된 엔티티
```

**설계 트레이드오프**:
- **완전 정규화**: fiveElements도 1:N 엔티티로 → 가장 많은 쿼리 유연성
- **선택적 JSON 유지**: dayMaster/dayMasterElement는 엔티티, fiveElements는 JSON → 균형잡힌 정규화 (권장)

**Mapper 업데이트**:
```java
// SajuResultMapper.java
public SajuFullData toSajuFullData(FastAPIResponse apiResponse, SajuResult sajuResult) {
    return SajuFullData.builder()
        .sajuResult(sajuResult)
        .yearPillar(apiResponse.yearPillar())
        .monthPillar(apiResponse.monthPillar())
        .dayPillar(apiResponse.dayPillar())
        .hourPillar(apiResponse.hourPillar())
        .dayMaster(extractDayMaster(apiResponse))  // 일간 추출 로직
        .dayMasterElement(extractDayMasterElement(apiResponse))
        .fiveElements(apiResponse.fiveElements())
        .solarCorrection(apiResponse.solarCorrection())
        .build();
}
```

### JSON → 행 단위 정규화 (TenGodData, HiddenStemData)

**목표**: JSON 컬럼을 완전히 정규화된 엔티티로 변환하여 쿼리 최적화 + 타입 안전성 향상

#### TenGodData (1:N with SajuResult)

**변환 전** (JSON):
```java
// SajuResult에 JSON 저장
Map<String, Integer> tenGodDistribution = Map.of(
    "正官", 1,
    "偏官", 1,
    "正财", 2,
    "偏财", 1
);
sajuResult.setTenGodDistribution(tenGodDistribution);
```

**변환 후** (정규화 엔티티):
```java
// 각 십신별 1행씩 저장
TenGodData row1 = TenGodData.builder()
    .sajuResult(sajuResult)
    .tenGodName("正官")
    .score(1)
    .build();

TenGodData row2 = TenGodData.builder()
    .sajuResult(sajuResult)
    .tenGodName("偏官")
    .score(1)
    .build();
// ... 4개 행 저장
```

**Repository 쿼리**:
```java
@Repository
public interface TenGodDataRepository extends JpaRepository<TenGodData, Long> {
    // 특정 SajuResult의 모든 십신 데이터 조회
    List<TenGodData> findBySajuResult(SajuResult sajuResult);
    
    // 특정 十神으로 검색
    TenGodData findBySajuResultAndTenGodName(SajuResult sajuResult, String tenGodName);
}
```

#### HiddenStemData (1:N with SajuResult)

**변환 전** (JSON):
```java
// SajuResult에 JSON 저장
Map<String, List<String>> hiddenStems = Map.of(
    "子", List.of("癸"),
    "丑", List.of("癸", "辛", "己"),
    "寅", List.of("甲", "丙", "戊")
);
sajuResult.setHiddenStems(hiddenStems);
```

**변환 후** (정규화 엔티티):
```java
// 각 지지-지장간 조합별 1행씩 저장
HiddenStemData row1 = HiddenStemData.builder()
    .sajuResult(sajuResult)
    .earthlyBranch("子")
    .hiddenStem("癸")
    .build();

HiddenStemData row2 = HiddenStemData.builder()
    .sajuResult(sajuResult)
    .earthlyBranch("丑")
    .hiddenStem("癸")
    .build();

HiddenStemData row3 = HiddenStemData.builder()
    .sajuResult(sajuResult)
    .earthlyBranch("丑")
    .hiddenStem("辛")
    .build();
// ... (지지별 × 지장간별 행수) 저장
```

**Repository 쿼리**:
```java
@Repository
public interface HiddenStemDataRepository extends JpaRepository<HiddenStemData, Long> {
    // 특정 SajuResult의 모든 지장간 데이터 조회
    List<HiddenStemData> findBySajuResult(SajuResult sajuResult);
    
    // 특정 지지의 모든 지장간 조회
    List<HiddenStemData> findBySajuResultAndEarthlyBranch(SajuResult sajuResult, String earthlyBranch);
    
    // 특정 지지-지장간 조합 조회
    HiddenStemData findBySajuResultAndEarthlyBranchAndHiddenStem(
        SajuResult sajuResult, String earthlyBranch, String hiddenStem);
}
```

**Service에서의 변환**:
```java
@Service
public class CareerFortuneService {
    
    private final TenGodDataRepository tenGodDataRepository;
    private final HiddenStemDataRepository hiddenStemDataRepository;
    
    // 저장 시: Map → Entity 배치로 변환
    public void saveTenGodData(SajuResult sajuResult, Map<String, Integer> tenGodDistribution) {
        tenGodDistribution.forEach((tenGodName, score) -> {
            TenGodData data = TenGodData.builder()
                .sajuResult(sajuResult)
                .tenGodName(tenGodName)
                .score(score)
                .build();
            tenGodDataRepository.save(data);
        });
    }
    
    // 조회 시: Entity → Map 재조립
    public Map<String, Integer> getTenGodDistribution(SajuResult sajuResult) {
        return tenGodDataRepository.findBySajuResult(sajuResult)
            .stream()
            .collect(Collectors.toMap(
                TenGodData::getTenGodName,
                TenGodData::getScore
            ));
    }
    
    // 같은 방식으로 HiddenStemData도 처리
}
```

**장점**:
- ✅ JSON 쿼리 제거 (MySQL LIKE 검색 불가능한 JSON 대신 SQL WHERE 절 사용)
- ✅ 정규화로 인한 데이터 무결성 향상 (예: tenGodName 중복 불가 등)
- ✅ 인덱싱 가능 (tenGodName, earthlyBranch)
- ✅ 타입 안전성 (String 대신 Enum 사용 가능하도록 향후 확장)

---

## Phase 1 제약사항

- **캐싱 금지**: Redis, In-Memory 전역 캐시 사용 금지
  - 도메인 로직 정확성 우선
  - Phase 2 이후 성능 최적화 고려

- **단순 구조**: 초기 단계이므로 복잡한 패턴 피함
  - CQRS, Event Sourcing 등은 나중에 검토
  - 기본 CRUD 패턴으로 시작

## 엔티티 정규화 패턴 (Phase 3-Refactor-3)

### Map → Entity 변환: tenGodDistribution, hiddenStems, fullSajuData

**문제**: Phase 3.1-3.2에서 Saju 데이터를 JSON (Map)으로 임시 저장하면:
- ❌ 타입 안전성 부족 (Map<String, Object> = 모든 type 가능)
- ❌ 쿼리 성능 저하 (JSON 컬럼은 인덱스 불가, 부분 검색 어려움)
- ❌ 일관성 관리 어려움 (여러 곳에서 Map 구조 다를 수 있음)

**해결책**: 정규화된 엔티티로 변환

#### Pattern 1: tenGodDistribution (Map<String, Integer> → TenGodData 엔티티)

```text
Phase 3.1-3.2 (임시):
SajuResult { 
    tenGodDistribution: { "정관": 20, "식신": 15, ... } ← JSON 저장
}

Phase 3-Refactor (정규화):
SajuResult {
    @OneToMany(mappedBy="sajuResult", fetch=LAZY, cascade=ALL)
    List<TenGodData> tenGodDataList
}

TenGodData {
    id, sajuResultId (FK), tenGodName, score, createdAt
}

// 데이터 예시:
TenGodData(id=1, sajuResultId=100, tenGodName="정관", score=20)
TenGodData(id=2, sajuResultId=100, tenGodName="식신", score=15)
```

**이점**:
- ✅ 각 십신이 별도 행 → 신뢰도 높음, 타입 안전
- ✅ tenGodName에 인덱스 가능 → "정관" 포함 분석 빠름
- ✅ Mapper: `List<TenGodData> toTenGodDataList(Map<String, Integer> map)` 로직 명확

#### Pattern 2: hiddenStems (Map<String, List<String>> → HiddenStemData 엔티티)

```text
Phase 3.1-3.2 (임시):
SajuResult { 
    hiddenStems: { "子": ["癸"], "丑": ["癸", "辛", "己"], ... } ← JSON 저장
}

Phase 3-Refactor (정규화):
SajuResult {
    @OneToMany(mappedBy="sajuResult", fetch=LAZY, cascade=ALL)
    List<HiddenStemData> hiddenStemDataList
}

HiddenStemData {
    id, sajuResultId (FK), earthlyBranch, hiddenStem, createdAt
}

// 데이터 예시:
HiddenStemData(id=1, sajuResultId=100, earthlyBranch="子", hiddenStem="癸")
HiddenStemData(id=2, sajuResultId=100, earthlyBranch="丑", hiddenStem="癸")
HiddenStemData(id=3, sajuResultId=100, earthlyBranch="丑", hiddenStem="辛")
HiddenStemData(id=4, sajuResultId=100, earthlyBranch="丑", hiddenStem="己")
```

**이점**:
- ✅ 지지별 지장간 1행 = 1개 관계 → 명확성 높음
- ✅ earthlyBranch + hiddenStem에 복합 인덱스 가능
- ✅ Mapper: 네스트된 Map을 flat list로 변환 용이

```java
// Mapper 예시
public List<HiddenStemData> toHiddenStemDataList(Map<String, List<String>> hiddenStems) {
    return hiddenStems.entrySet().stream()
        .flatMap(branch -> 
            branch.getValue().stream()
                .map(stem -> new HiddenStemData(branch.getKey(), stem))
        )
        .collect(Collectors.toList());
}
```

#### Pattern 3: fullSajuData (Map<String, Object> → SajuFullData 엔티티)

```text
Phase 3.1-3.2 (임시):
SajuResult { 
    fullSajuData: { 
        "yearPillar": "甲子", "dayMaster": "甲", 
        "fiveElements": { "木": 2, "火": 1, ... },
        "solarCorrection": { ... }
    } ← JSON 저장
}

Phase 3-Refactor-3 (정규화):
SajuResult {
    @OneToOne(fetch=LAZY, cascade=ALL)
    @JoinColumn(name="saju_full_data_id", unique=true)
    SajuFullData sajuFullData
}

SajuFullData {
    id, sajuResultId (FK, unique), 
    yearPillar, monthPillar, dayPillar, hourPillar (String),
    dayMaster, dayMasterElement (String),
    fiveElements (Map, JSON 유지 가능),
    solarCorrection (Map, JSON 유지 가능),
    createdAt (@CreatedDate)
}

// 데이터 예시:
SajuFullData(id=1, sajuResultId=100, yearPillar="甲子", dayMaster="甲", 
    dayMasterElement="木", fiveElements={"木":2, "火":1}, ...)
```

**설계 결정** (완전 정규화 vs. 선택적 JSON 유지):
- yearPillar, dayMaster, dayMasterElement: 엔티티 필드 (자주 조회/필터링)
- fiveElements, solarCorrection: JSON 유지 (변경 빈도 낮음, 복합 구조)

**이점**:
- ✅ yearPillar, dayMaster에 인덱스 가능
- ✅ 1:1 관계로 조인 성능 우수
- ✅ JSON 컬럼 개수 최소화 → DB 성능 향상

---

---

## Race Condition 안전 처리 (JdbcTemplate INSERT IGNORE)

### 문제: SajuResult 동시 Insert

**As-Is**:
```java
// DataIntegrityViolationException 발생 → 로깅만 하거나 재시도
try {
    repository.save(sajuResult);
} catch (DataIntegrityViolationException e) {
    log.warn("Race condition: {}", e);
    // 문제: 예외 발생 후 처리 로직 불명확
}
```

**To-Be**: JdbcTemplate INSERT IGNORE (정규화 후)

```java
// SajuResultJdbcRepository.java
@Repository
public class SajuResultJdbcRepository {
    private final JdbcTemplate jdbcTemplate;
    
    public int insertOrIgnore(SajuResult result) {
        String sql = "INSERT IGNORE INTO saju_result " +
            "(user_profile_id, birth_date, birth_time, created_at) " +
            "VALUES (?, ?, ?, ?)";
        
        return jdbcTemplate.update(sql,
            result.getUserProfile().getId(),
            result.getBirthDate(),
            result.getBirthTime(),
            LocalDateTime.now()
        );
    }
}

// Service에서 사용 (정규화된 엔티티와 함께)
@Service
public class SajuDataService {
    private final SajuResultJdbcRepository sajuResultJdbc;
    private final SajuResultRepository sajuResultRepo;
    private final SajuFullDataRepository sajuFullDataRepo;
    
    public SajuResult fetchOrCreateSajuResult(UserProfile userProfile, FastAPIResponse response) {
        // 1. JdbcTemplate INSERT IGNORE로 SajuResult 생성
        SajuResult sajuResult = new SajuResult(userProfile, response.getBirthDate(), response.getBirthTime());
        int inserted = sajuResultJdbc.insertOrIgnore(sajuResult);
        
        // 2. 조회하거나 새로 생성
        SajuResult result = sajuResultRepo.findByUserProfileAndBirthDateAndBirthTime(...)
            .orElse(sajuResult);
        
        // 3. 정규화된 SajuFullData 저장
        SajuFullData fullData = SajuFullData.builder()
            .sajuResult(result)
            .yearPillar(response.getYearPillar())
            .monthPillar(response.getMonthPillar())
            .dayPillar(response.getDayPillar())
            .hourPillar(response.getHourPillar())
            .dayMaster(response.getDayMaster())
            .dayMasterElement(response.getDayMasterElement())
            .fiveElements(response.getFiveElements())
            .solarCorrection(response.getSolarCorrection())
            .build();
        sajuFullDataRepo.save(fullData);  // cascade=ALL로 자동 저장
        
        return result;
    }
}
```

**UNIQUE 제약**:
```sql
-- schema.sql
ALTER TABLE saju_result 
ADD UNIQUE KEY unique_user_saju (user_profile_id, birth_date, birth_time);
```

**이점**:
- ✅ Race condition 안전: UNIQUE 제약으로 중복 insert 방지
- ✅ 명확한 반환값: inserted=1/0 구분
- ✅ 예외 처리 불필요: INSERT IGNORE는 예외 발생 안 함
- ✅ 성능: 네이티브 SQL로 최적화

### H2 MySQL 모드 테스트

**프로덕션 환경과의 호환성 보장**:

```yaml
# application-test.yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE
    driver-class-name: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
```

**이점**:
- ✅ INSERT IGNORE 테스트 가능
- ✅ UNIQUE constraint 동작 검증
- ✅ 프로덕션과 동일한 문법 (MySQL)

## 로깅 정책 (민감정보 보호)

### ❌ 로그에 절대 포함 금지

| 분류 | 금지 항목 |
|------|-----------|
| **개인정보** | `birthDate`, `birthTime`, `email`, `phone`, `name` |
| **인증 정보** | API Key, Bearer 토큰, Authorization 헤더값 |
| **외부 API 원문** | FastAPI 요청 body 전문, OpenAI 프롬프트 전문 |
| **예외 메시지 직접 출력** | `e.getMessage()` — 내부 정보 노출 가능 |

### ✅ 로그에 허용하는 식별자

- 숫자 ID만: `userId`, `userProfileId`, `sajuResultId`
- 추적 ID: `requestId`, `traceId`
- 상태 정보: 성공/실패 여부, HTTP 상태코드, 지연시간(ms)

### 📌 예시

```java
// ❌ 잘못된 예
log.warn("동시 경합 발생 (birthDate={})", birthDate);
log.error("OpenAI 호출 실패: {}", e.getMessage());

// ✅ 올바른 예
log.warn("동시 경합 발생 (userId={})", userProfile.getId());
log.error("OpenAI 호출 실패", e);  // 스택트레이스만 로깅
```

### 로그 레벨 기준

| 레벨 | 용도 |
|------|------|
| `DEBUG` | 개발 환경 상세 정보 (프로덕션에서 비활성화) |
| `INFO` | 주요 비즈니스 이벤트 (요청 시작/완료) |
| `WARN` | 예상 가능한 예외 (동시성 경합, 재시도) |
| `ERROR` | 예상 불가능한 예외 + 스택트레이스 |

---

**Last Updated**: 2026-05-03 (CodeRabbit 리뷰 반영 — 로깅 정책 + try-catch 규칙 명확화)
