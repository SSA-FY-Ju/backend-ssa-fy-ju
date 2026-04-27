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
요청: POST /saju/calculate
입력: 생년월일시 (YYYY-MM-DD HH:mm)
응답: {
  heavenlyStems: ["庚", "丙", "己", "辛"],        // 4개 (年月日時)
  earthlyBranches: ["午", "戌", "未", "未"],     // 4개 (年月日時)
  fiveElements: {"木": 1, "火": 2, "土": 1, "金": 2, "水": 2}
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

### 2. OpenAI API (커리어 상담)

```
모델: gpt-4o-mini
기능: JSON Mode (구조화된 응답)
입력: {생년월일, 사주 데이터}
응답: {
  industries: [추천 산업],
  interviewTips: [면접 팁],
  strengths: [강점 분석]
}
타임아웃: 8초 (LLM 응답 시간)
재시도: 1회
```

**Spring AI 사용 (권장)**:
```java
@Configuration
public class ChatClientConfig {
    @Bean
    public ChatClient chatClient(ChatClientBuilder builder) {
        return builder.build();
    }
}
```

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
