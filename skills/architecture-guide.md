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
```java
public ConsultationResponse getCareerConsultation(ConsultationRequest request) {
    // 1단계: FastAPI로부터 기본 사주 데이터 조회
    FastAPIResponse sajuData = sajuDataService.fetchSajuFromFastAPI(
        request.birthDate(), request.birthTime());
    
    // 2단계: Spring에서 십신 및 지장간 계산
    Map<String, Integer> tenGodDistribution = tenGodCalculator.calculate(
        sajuData.heavenlyStems());
    Map<String, List<String>> hiddenStems = hiddenStemCalculator.calculate(
        sajuData.earthlyBranches());
    String dayMaster = calculateDayMaster(sajuData.heavenlyStems());
    
    // 3단계: 관운 분석 (H1/H2 판정)
    String favoredPeriod = careerFortuneAnalyzer.analyzeFavoredPeriod(
        tenGodDistribution, hiddenStems, dayMaster, sajuData.earthlyBranches());
    int confidenceScore = careerFortuneAnalyzer.calculateConfidenceScore(
        tenGodDistribution, hiddenStems, dayMaster);
    String reasoning = buildReasoning(favoredPeriod, tenGodDistribution, dayMaster);
    
    // 4단계: 사용자 프로필 및 사주 결과 저장
    UserProfile userProfile = findOrCreateUserProfile(
        request.birthDate(), request.birthTime());
    SajuResult sajuResult = findOrCreateSajuResult(
        userProfile, sajuData, tenGodDistribution, hiddenStems);
    
    // 5단계: OpenAI 호출 (십신 + 지장간 + 16개 필드 그룹 포함 프롬프트)
    CareerAdviceResponse advice = callOpenAI(
        sajuData, tenGodDistribution, hiddenStems, dayMaster);
    
    // 6단계: 컨설팅 결과 저장
    CareerConsultation consultation = CareerConsultation.builder()
        .sajuResult(sajuResult)
        .industries(advice.industries())
        .interviewTips(advice.interviewTips())
        .strengths(advice.strengths())
        .openaiModelVersion(modelVersion)
        .build();
    careerConsultationRepository.save(consultation);
    
    // 7단계: 응답 반환 (19개 필드: 기본 조언 3 + 관운 분석 3 + 사주 프로필 1 + OpenAI 분석 12 필드)
    return new ConsultationResponse(
        advice.industries(), advice.interviewTips(), advice.strengths(),
        modelVersion, favoredPeriod, confidenceScore, reasoning,
        new ConsultationResponse.SajuProfile(
            dayMaster, advice.dayMasterDescription(), sajuData.fiveElements(),
            advice.fiveElementsAnalysis(), tenGodDistribution, advice.keyTenGods()),
        advice.cautions(),
        advice.wealthStyle(),
        advice.longTermRoadmap(),
        advice.personalBranding(),
        advice.powerKeywords(),
        advice.mentalCare(),
        advice.environmentFit(),
        advice.workStyle(),
        advice.relationshipStrategy(),
        advice.careerTimeline());
}
```

**프롬프트 구성** (현재 연도 + 12개월 타임라인 + 십신 + 지장간 + 16개 필드 그룹):
```java
private String buildPrompt(FastAPIResponse sajuData,
                          Map<String, Integer> tenGodDistribution,
                          Map<String, List<String>> hiddenStems,
                          String dayMaster) {
    int currentYear = LocalDate.now().getYear();
    return """
        당신은 사주 명리학 전문가이자 취업 커리어 컨설턴트입니다. 아래 사주 데이터를 분석하여 취업 준비생에게 맞춤 커리어 조언을 제공해주세요.

        [사주 데이터]
        - 일간(日干): %s
        - 천간(天干): %s
        - 지지(地支): %s
        - 오행 분포: %s
        - 지장간(地藏干): %s
        - 십신 분포(十神): %s

        [분석 요청]
        - 현재 연도: %d
        - 12개월 타임라인별 기운 및 취업 최적 시기 분석
        - 일간(%s) 기반 성향 분석

        [JSON 응답 형식 - 16개 필드 그룹 포함]
        {
          // 기본 조언 (3)
          "industries": [{"name": "산업명", "reason": "사주 분석 근거", "recommendedRoles": ["직무1", "직무2"]}],
          "interviewTips": ["면접팁1", "면접팁2", "면접팁3"],
          "strengths": ["강점1", "강점2", "강점3"],
          
          // 사주 데이터 (6)
          "dayMasterDescription": "일간 설명",
          "fiveElementsAnalysis": "오행 분석",
          "keyTenGods": ["십신1", "십신2"],
          
          // OpenAI 분석 (7)
          "cautions": ["주의사항"],
          "wealthStyle": {"incomeSource": "...", "financialAdvice": "...", "investmentTendency": "...", "additionalIncome": "..."},
          "longTermRoadmap": {"phase0to2years": {...}, "phase3to5years": {...}, "ultimateGoal": "...", "goalDescription": "..."},
          "personalBranding": {"suitColor": "...", "impression": "...", "hairAndMakeup": "...", "brandingKeyword": "...", "taglineForResume": "..."},
          "powerKeywords": {"keywords": [{"keyword": "...", "element": "...", "description": "...", "usageExample": "...", "context": "..."}], "selectionGuide": "...", "usageTips": ["팁1"], "avoidanceTip": "..."},
          "mentalCare": {"stressVulnerability": ["..."], "rechargeMethod": ["..."], "mindsetMantra": "...", "emergencyTactic": "..."},
          "environmentFit": {"workVibe": "...", "companySize": "...", "colleagueType": "...", "conflictApproach": "...", "physicalEnv": "...", "culturalFit": "..."},
          "workStyle": {"preferredCompanyType": "...", "leadershipType": "...", "decisionMaking": "...", "conflictResolution": "..."},
          "relationshipStrategy": {"socialStyle": "...", "networkingApproach": "...", "teamPosition": "...", "conflictResolution": "...", "careerNetworking": "..."},
          "careerTimeline": {
            "year": %d,
            "months": {
              "January": {"type": "...", "description": "..."},
              ...
              "December": {"type": "...", "description": "..."}
            },
            "pivotPoints": [{"month": "March", "type": "적극기", "score": 9, "description": "..."}],
            "warningMonths": ["May", "July"],
            "warningDescription": "..."
          }
        }
        """.formatted(
            dayMaster,
            sajuData.heavenlyStems(),
            sajuData.earthlyBranches(),
            sajuData.fiveElements(),
            hiddenStems,
            tenGodDistribution,
            currentYear,
            dayMaster,
            currentYear
        );
}
```

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

## Phase 1 제약사항

- **캐싱 금지**: Redis, In-Memory 전역 캐시 사용 금지
  - 도메인 로직 정확성 우선
  - Phase 2 이후 성능 최적화 고려

- **단순 구조**: 초기 단계이므로 복잡한 패턴 피함
  - CQRS, Event Sourcing 등은 나중에 검토
  - 기본 CRUD 패턴으로 시작

---

**Last Updated**: 2026-04-27 (FastAPI 응답 구조 수정, 십신/지장간 Spring 계산 명시, FastAPI-Spring 역할 분담 추가)
