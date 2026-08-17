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

## 5. 기존 데이터 제거 방식 (코드 리뷰로 TRUNCATE/DROP 절차 명확화 + FK 그래프 반영)

**최초 결정의 모호함**: "TRUNCATE(또는 DROP 후 재생성)"처럼 두 방식을 병기해, quickstart.md/tasks.md의 검증 절차가 "행 수 0건"을 확인해야 하는지 "테이블 존재 여부"를 확인해야 하는지 문서마다 불일치했다.

**1차 정정 이후 추가로 발견된 문제 (코드 리뷰)**: 1차 정정에서 "루트 테이블은 ALTER+TRUNCATE"로 확정했으나, 실제 FK 그래프를 확인하지 않아 다음 두 문제를 놓쳤다 — MySQL(InnoDB)은 **다른 테이블이 FK로 참조 중인 테이블에 대해 TRUNCATE를 거부**한다.
- `UserSatisfactionFeedback.java:35-40`이 `company_compatibility_id`, `career_consultation_id`(둘 다 nullable) FK를 갖고 있어 `company_compatibility`/`career_consultation` TRUNCATE가 막힌다.
- `SajuResult`의 `sajuFullData`/`careerFortune`은 `mappedBy` 관계라 FK 컬럼이 반대편(`saju_full_data.saju_result_id`, `career_fortune.saju_result_id`)에 있다 — 이 두 테이블은 이번 마이그레이션과 무관하게 컬럼 구조는 그대로지만, `saju_result` TRUNCATE를 막는 참조이므로 데이터 정리 절차에는 포함해야 한다.

**정정된 Decision**: 대상을 세 그룹으로 나눠 서로 다른 방식을 적용한다.
- **자식/손자 테이블** (`industry`, `interview_tip`, `monthly_forecast`, `target_role_analysis`, `ten_god_data`, `hidden_stem_data` 등 24개 직계 + 다수 손자 — data-model.md 목록 전체): 대응하는 JPA 엔티티 클래스 자체가 삭제되어 더 이상 어떤 코드도 매핑하지 않으므로 **DROP TABLE**.
- **`user_satisfaction_feedback`**: 사용자가 남긴 피드백 내용은 삭제 대상이 아니므로 행을 지우지 않는다. 대신 `company_compatibility_id`/`career_consultation_id` 컬럼만 **`UPDATE ... SET ... = NULL`**로 링크를 끊어 FK 참조를 해제한다(두 컬럼 모두 `nullable`이라 스키마 변경 불필요).
- **`career_consultation`, `company_compatibility`**: `user_satisfaction_feedback`의 FK를 위 단계로 해제한 뒤 **ALTER TABLE**(`result_json` 컬럼 추가, `summary`/`day_master_description` 등 구 컬럼 제거) 후 **TRUNCATE**.
- **`saju_full_data`, `career_fortune`, `saju_result`**: 구조적 일관성을 위해 사주 결과 전체를 리셋한다(사용자 결정) — 자식(`saju_full_data`, `career_fortune`)을 먼저 **TRUNCATE**한 뒤 `saju_result`에 `ten_god_hidden_stem_analysis` 컬럼을 **ALTER**로 추가하고 **TRUNCATE**. `saju_full_data`/`career_fortune`은 컬럼 구조 자체는 바뀌지 않지만(엔티티/스키마 변경 없음), 데이터는 `saju_result`와 함께 정리된다.

**수동 적용 절차** (버전관리 커밋 없음, 운영자가 로컬/개발 DB에서 직접 수행 — FR-009):
1. 적용 전 각 대상 테이블의 행 수를 기록 (`SELECT COUNT(*)`) — 사전 확인용, 별도 백업 불필요(운영 데이터 아님, 개발 단계).
2. `UPDATE user_satisfaction_feedback SET company_compatibility_id = NULL, career_consultation_id = NULL WHERE company_compatibility_id IS NOT NULL OR career_consultation_id IS NOT NULL;`
3. FK 의존 순서를 지켜 자식 → 손자 순으로, 각 루트를 참조하는 자식부터 먼저 DROP (24개 자식/손자 테이블).
4. `career_consultation`, `company_compatibility` ALTER TABLE 후 TRUNCATE.
5. `saju_full_data`, `career_fortune` TRUNCATE → `saju_result` ALTER TABLE 후 TRUNCATE (자식이 부모보다 먼저 비워져야 함).
6. 실패 시(예: FK 제약으로 DROP/TRUNCATE 거부) 대상 테이블의 FK를 먼저 확인해 순서를 재조정 — 강제로 FK를 비활성화(`SET FOREIGN_KEY_CHECKS=0`)하지 않는다(의도치 않은 다른 FK 무결성 손상 방지).
7. **검증**: `user_satisfaction_feedback`은 1단계 이전 행 수와 동일해야 하고(삭제 없음), 나머지 대상 테이블은 DROP된 것은 `SHOW TABLES`에서, TRUNCATE된 것은 행 수 0건으로 확인.

**Rationale**: 자식 테이블은 애초에 이번 변경으로 "존재 자체가 사라지는" 대상이라 TRUNCATE로 비워두고 남겨둘 이유가 없다. 루트 테이블은 컬럼 구조가 바뀌어야 하므로 ALTER가 어차피 필요하고, 그 김에 구버전 데이터를 TRUNCATE로 정리한다. 피드백은 FK만 끊어 원본 데이터를 보존한다(사용자 결정 — 피드백 자체는 특정 분석 결과와 무관하게 별도로 의미 있는 데이터이므로). `saju_result`는 그 자식인 `saju_full_data`/`career_fortune`이 데이터 정리를 막고 있어, 부분적으로만 리셋하면 구조가 일관되지 않으므로(예: 새 사주 결과는 새 스키마인데 원시 데이터는 예전 것) 함께 리셋한다(사용자 결정).

**Alternatives considered**: 루트 테이블도 DROP 후 Hibernate `ddl-auto`로 재생성 — 더 단순하지만 auto-increment 시퀀스 리셋 등 부수효과가 있고 운영자가 매번 전체 스키마를 재생성해야 해 번거로움 → 기각. `user_satisfaction_feedback` 행 자체를 DELETE — 검토했으나 피드백 내용 보존을 원해 기각, FK만 NULL 처리하는 방식 채택.

## 6. 테스트 전략

**Decision**: (a) 신규 컨버터의 직렬화/역직렬화 라운드트립을 H2 기반 단위 테스트로 검증, (b) MySQL `json` 컬럼 타입 특이 동작(예: 저장 시 JSON 유효성 오류)은 Testcontainers MySQL 통합 테스트로 검증, (c) 기존 `CompanyMatchingOpenAICallerTest`에 "범위를 벗어난/중복된 month를 담은 응답이 기존 대상월 일치 검사로 이미 거부됨"을 확인하는 회귀 테스트 케이스를 추가(신규 검증 코드 없음 — 결정 #4 참고).

**Rationale**: 기존 테스트 인프라(H2 단위 + Testcontainers MySQL 통합)를 그대로 재사용하며 신규 의존성을 추가하지 않는다.
