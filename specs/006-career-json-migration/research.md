# Phase 0 Research: 커리어 분석 결과 JSON 저장 마이그레이션

## 1. "관운분석" JSON화 대상의 실제 범위

**Decision**: "관운분석" 마이그레이션 대상은 `CareerFortune` 엔티티 자체가 아니라, `SajuResult`에 딸린 `tenGodDataList`(`TenGodData` 자식)와 `hiddenStemDataList`(`HiddenStemData` 자식)이다. `CareerFortune`은 이미 `favoredPeriod`/`confidenceScore`/`reasoning`(text) 스칼라 컬럼만 가지고 있고 정규화된 자식 엔티티가 없다.

**Rationale**: `SSAju/src/main/java/ssafy/SSAju/career/entity/CareerFortune.java`를 읽어보면 `@OneToMany` 연관관계가 전혀 없다. 반면 `SajuResult.java`는 `tenGodDataList`, `hiddenStemDataList`를 정규화 자식으로 갖고 있고, `SSAju/CLAUDE.md` "Data normalization pattern" 섹션도 "ten-god distribution, hidden stems... 정규화된 자식 엔티티로 저장"이라고 명시한다. 사용자가 "관운분석"이라 지칭한 결과 화면은 이 두 리스트를 근거로 계산된다.

**Alternatives considered**: `CareerFortune`의 스칼라 3개 컬럼만 JSON으로 감싸는 안은 기각 — 필드가 3개뿐이라 JSON화로 얻는 이점이 없고, 사용자가 원한 "정규화 제거"의 실질 대상(자식 엔티티가 있는 곳)이 아니다.

**Scope 반영**: `CareerFortune`은 이번 마이그레이션에서 변경하지 않는다(스칼라 컬럼 유지). `SajuResult.tenGodDataList`/`hiddenStemDataList` → JSON 컬럼 1개(`tenGodHiddenStemAnalysis` 등)로 대체하는 것을 관운분석 대상 작업으로 확정한다.

## 2. JSON 컬럼 저장 방식

**Decision**: 기존 `career/converter/ObjectMapConverter.java` 패턴을 그대로 따르는 `AttributeConverter<T, String>` 구현체를 도메인별로 추가한다 (예: `ConsultationResultConverter`, `CompatibilityResultConverter`, `TenGodHiddenStemConverter`). 컬럼은 MySQL `json` 타입(`columnDefinition = "json"`)으로 선언하되 Java 쪽 매핑은 문자열 직렬화(`String` ↔ 도메인 record)로 유지한다.

**Rationale**: 프로젝트에 `hibernate-types`/`hypersistence-utils` 등 JSON 전용 라이브러리 의존성이 없고, 기존 `ObjectMapConverter`가 이미 이 프로젝트의 검증된 패턴이다. 새 라이브러리를 추가하지 않는 것이 일관성과 리스크 최소화 측면에서 유리하다. `columnDefinition = "json"`을 쓰면 MySQL이 저장 시 JSON 유효성을 한 번 더 검사해주는 부수 이점이 있다(애플리케이션 검증을 대체하지는 않음).

**Alternatives considered**:
- `hypersistence-utils`의 `@Type(JsonType.class)` — 신규 의존성 추가 필요, 기존 컨버터 패턴과 불일치.
- `columnDefinition = "text"` (MySQL JSON 타입 대신) — `ObjectMapConverter`가 이미 이 방식을 쓰고 있어 완전한 일관성은 있으나, DB 레벨 JSON 유효성 검사 이점을 포기하게 됨. 최종적으로는 `json` 타입 채택.

## 3. Jackson 3.x(`tools.jackson`) 직렬화 주의사항

**Decision**: 신규 컨버터는 `career/converter/ObjectMapConverter.java`와 동일하게 `tools.jackson.databind.ObjectMapper`(Jackson 3.x)를 사용하고, `Instant` 등 날짜/시간 필드가 포함될 경우 `config/JacksonConfig.java`에 등록된 `SimpleModule`과 동일한 직렬화 포맷(KST 오프셋)을 컨버터 전용 `ObjectMapper`에도 등록한다.

**Rationale**: `com.fasterxml.jackson`(2.x) 임포트를 실수로 섞으면 컴파일은 되어도 런타임에 별개의 매퍼 인스턴스/모듈 체계가 충돌할 수 있다. 프로젝트 전역에 공유 `ObjectMapper` 빈이 없으므로(`ObjectMapConverter`도 자체 private static 매퍼를 씀) 신규 컨버터도 동일하게 자체 매퍼를 구성한다.

## 4. 정합성 검사 이관 지점 (코드 리뷰로 전제 정정됨)

**최초 결정(오류로 판명)**: `ConsultationOpenAICaller.validate()` / `CompanyMatchingOpenAICaller.validate()`에 `MonthlyForecast.month`(1~12), `MonthlyForecast.score`/`TargetRoleAnalysis.matchScore`(0~100) range 검사를 명시적으로 추가하는 안을 최초 채택했으나, 실제 코드(`CompanyMatchingOpenAICaller.java:87-139`, `CompatibilityNarrativeResponse.java`)를 대조 확인한 결과 **`score`/`matchScore`는 이 메서드가 받는 `CompatibilityNarrativeResponse` DTO에 아예 존재하지 않는 필드**임이 드러나 전제 자체가 틀렸다. `validate()`는 이 두 값을 받지 않으므로 그 안에 range 검사를 추가하는 것은 불가능하다.

**정정된 Decision**:
- **month**: 별도 range 검사를 추가하지 않는다. `CompanyMatchingOpenAICaller.validate()`가 이미 `actualMonths.equals(Set.copyOf(expectedTargetMonths))`로 대상월 집합과 정확히 일치하는지 검사하고 있고(`CompanyMatchingOpenAICaller.java:125`), `expectedTargetMonths`는 항상 1~12 범위의 달력 월이므로 범위를 벗어난 값은 이 동등성 검사에서 이미 자동으로 거부된다. 신규 코드 불필요, 회귀 테스트로 이 사실만 확인한다.
- **score / matchScore**: `TargetRoleAnalysis.matchScore`는 `JobRoleAnalyzer.analyze()`가, `MonthlyForecast.score`는 `AnalysisResponseBuilder.buildMonthlyForecasts()`가 각각 오행 카운트 기반으로 **내부적으로 결정론적으로 계산**하는 값이지 OpenAI 응답 필드가 아니다. `JobRoleAnalyzer.calculateMatchScore()`는 `Math.min(score, MAX_SCORE)`로 상한을 클램프한다. 하한은 `FiveElements.getCount()`(`elements.getOrDefault(element, 0)`)가 항상 0 이상인 카운트를 반환한다는 **가정**에 의존하는데, 이 가정은 코드로 강제되지 않는다 — `FiveElements`/`JobRoleAnalyzer` 어디에도 음수 방지 로직이 없다(코드 리뷰로 확인, 최초 "자동으로 보장된다"는 표현은 과장이었음). 이는 `SajuResult`의 십신/지장간 계산과 동일한 범주(신뢰할 수 없는 외부 응답이 아니라 내부 결정론적 계산)이므로 `validate()`에 별도의 저장-전 검증을 추가하지는 않되, `JobRoleAnalyzer.calculateMatchScore()`에 `Math.max(0, ...)` 하한 클램프와 음수 입력 회귀 테스트를 추가해 가정을 코드로 강제한다(tasks.md T019a — 이번 마이그레이션과 별개의 기존 코드 방어 강화이며 저장 형식 변경과는 무관).

**Rationale**: 검증은 "신뢰할 수 없는 외부 입력(OpenAI 응답)"에 대해서만 의미가 있다. score/matchScore는 애초에 그 범주에 속하지 않으므로, 없어지는 것은 엔티티 컬럼의 Hibernate Validator라는 *강제 지점*일 뿐 실질적인 위험이 아니다. month는 이미 더 엄격한 검사(집합 전체 일치)로 커버되고 있어 범위 검사를 별도로 추가하면 중복 코드가 된다.

**Alternatives considered**:
- 저장 직전 별도 "JSON 저장용 Validator" 컴포넌트 신설 — 애초에 검증 대상 필드가 `validate()`에 존재하지 않는 문제를 해결하지 못하므로 기각.
- `JobRoleAnalyzer`/`AnalysisResponseBuilder` 계산 직후에 방어적 assert 추가 — 공식이 이미 자체 유계라 실익이 낮고, "저장 전 응답 검증"이 아닌 "내부 계산 유닛의 방어적 프로그래밍"으로 스펙 성격이 달라져 채택하지 않음(YAGNI).

## 5. 기존 데이터 제거 방식 (2026-10-12 정합성 정정)

최초 문서의 "피드백 FK 값을 NULL로 변경한 뒤 부모 TRUNCATE"는 성립하지 않는다. MySQL은 참조 값이 아니라 FK 제약 존재 여부로 TRUNCATE를 제한한다. 현재 `004` US6에서 추가된 `user_saju_access`도 `saju_result`를 참조한다.

**Decision**: 폐기되는 정규화 테이블은 손자 → 자식 순서로 DROP하고, 유지되는 루트·관련 테이블은 FK를 보존한 DELETE로 비운다. 피드백 내용과 사용자/프로필은 유지하며 피드백의 두 분석 FK만 NULL로 해제한다. JSON 컬럼은 실제 엔티티 타입에 맞춰 적용하고 `CompanyCompatibility.summary`는 유지한다.

수동 적용의 단일 기준은 [quickstart §2](quickstart.md#2-스키마-수동-적용-마이그레이션-스크립트-없음--fr-009)다. 마이그레이션 스크립트는 커밋하지 않으며 실제 DB 적용 완료 여부는 T022/T024에서 별도로 기록한다.

## 6. 최종 구현 및 후속 리팩터링 반영

- 컨버터는 `AbstractJsonConverter<T>`와 `CareerJsonObjectMapperSupport`를 공유한다.
- 궁합 JSON 타입은 `CompatibilityAnalysisData`이며 summary는 별도 컬럼이다. AI DTO만 저장하면 내부 계산 결과가 빠지므로 AI 해설과 계산 결과를 합친 VO를 사용한다.
- matchScore 하한 클램프 및 회귀 테스트(T019a)는 구현 완료됐다.
- 이후 `004`의 분산락 도입으로 `SajuResultWriteService.saveNewResult`는 루트·JSON·관련 엔티티를 단일 트랜잭션으로 저장한다. INSERT IGNORE 자동 커밋 경로는 더 이상 현재 계약이 아니다.
