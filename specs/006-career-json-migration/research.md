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

## 4. 정합성 검사 이관 지점

**Decision**: `ConsultationOpenAICaller.validate()` / `CompanyMatchingOpenAICaller.validate()`에 다음을 명시적으로 추가한다:
- `MonthlyForecast.month` 대응 필드: 1~12 범위 검사
- `MonthlyForecast.score`, `TargetRoleAnalysis.matchScore` 대응 필드: 0~100 범위 검사

**Rationale**: 대화에서 이미 합의된 내용(스펙 FR-004)이며, 기존 `validate()`가 이미 null/blank/월-집합 일치 검사를 수행하는 지점이라 동일한 위치에 range 검사를 추가하는 것이 가장 낮은 리스크의 선택이다. 이 두 range는 현재 JPA `@Min`/`@Max`(Hibernate Validator, persist 시점)로만 강제되고 있어 자식 엔티티가 사라지면 강제 지점이 없어진다.

**Alternatives considered**: 저장 직전 별도 "JSON 저장용 Validator" 컴포넌트 신설 — 검사 로직이 이미 존재하는 `validate()`와 분리되어 이중 관리 지점이 생기므로 기각.

## 5. 기존 데이터 제거 방식

**Decision**: `SajuResult.tenGodDataList`/`hiddenStemDataList`, `CareerConsultation`(+14 직계/손자 자식), `CompanyCompatibility`(+8 직계/손자 자식) 관련 테이블을 TRUNCATE(또는 DROP 후 재생성)한다. 데이터 변환 스크립트는 작성하지 않는다.

**Rationale**: 기존 project memory(`project_career_json_migration`) 및 이번 스펙의 Assumptions에서 이미 결정됨 — 운영 실사용자 데이터 없음, 마이그레이션 스크립트는 커밋하지 않고 운영자가 수동 적용(FR-009).

**Alternatives considered**: 없음 — 이미 확정된 결정.

## 6. 테스트 전략

**Decision**: (a) 신규 컨버터의 직렬화/역직렬화 라운드트립을 H2 기반 단위 테스트로 검증, (b) MySQL `json` 컬럼 타입 특이 동작(예: 저장 시 JSON 유효성 오류)은 Testcontainers MySQL 통합 테스트로 검증, (c) `validate()`에 추가된 range 검사는 기존 `ConsultationOpenAICallerTest`/`CompanyMatchingOpenAICallerTest`에 케이스를 추가.

**Rationale**: 기존 테스트 인프라(H2 단위 + Testcontainers MySQL 통합)를 그대로 재사용하며 신규 의존성을 추가하지 않는다.
