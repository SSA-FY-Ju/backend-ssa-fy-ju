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

타임아웃: 3초
재시도: 2회 (지수 백오프)

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

타임아웃: 8초 (LLM 응답 시간)
재시도: 1회 (자동)
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
타임아웃: 5초
재시도: 1회
Fallback: 찾지 못하면 사용자 수동입력 요청 (graceful degradation)
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

---

**Last Updated**: 2026-05-02 (Service Layer 경량화 + Domain Model 캡슐화 추가)
