# Phase 1 Data Model: 커리어 분석 결과 JSON 저장 마이그레이션

## 개요

세 루트 엔티티가 대상이다. 각 루트는 (1) 식별/조회용 스칼라 컬럼은 그대로 유지하고, (2) 현재 정규화 자식 엔티티로 나뉘어 있던 결과 데이터를 JSON 컬럼 1개로 대체한다.

## 1. `SajuResult` (관운분석 관련 부분만 변경)

**변경 없는 필드(스키마)**: `id`, `userProfile`, `fetchedAt` (`user` 소유권은 이후 `004` US6에서 `UserSajuAccess`로 분리됨). `sajuFullData`/`careerFortune`(`@OneToOne`) 연관관계와 대상 엔티티의 컬럼 구조 자체는 이번 마이그레이션에서 바뀌지 않는다.

**제거되는 연관관계**:
- `tenGodDataList` (`List<TenGodData>`, `@OneToMany`)
- `hiddenStemDataList` (`List<HiddenStemData>`, `@OneToMany`)

**추가되는 컬럼**:
- `tenGodHiddenStemAnalysis` (JSON) — 기존 `TenGodData`/`HiddenStemData` 각 행이 담던 값(십신 분포, 지장간 상세)을 하나의 JSON 구조로 직렬화. 내부 키 구조는 `TenGodHiddenStemAnalysis`가 두 계산 결과의 asMap() 값을 묶어 정의한다.

**JSON 예시** (`TenGodHiddenStemAnalysis` 기준 — `TenGodDistribution.asMap()`은 `Map<String tenGodName, Integer count>`, `HiddenStems.asMap()`은 `Map<String pillar, List<String> hiddenStems>`):

```json
{
  "tenGods": { "비견": 2, "겁재": 1, "식신": 0 },
  "hiddenStems": { "년주": ["갑", "을"], "월주": [], "일주": ["병"], "시주": [] }
}
```

- `TenGodHiddenStemAnalysis.of(...)`는 null 계산 결과를 빈 맵으로 바꿔 `{"tenGods": {}, "hiddenStems": {}}` 구조를 유지한다. 컨버터 자체에 null 속성을 전달하면 SQL NULL로 변환한다(빈 객체 변환과 구분).
- 키 순서는 보장하지 않는다(맵 기반 직렬화) — 조회 시 순서에 의존하는 로직이 없어야 한다.

**검증 규칙**: 이 데이터는 외부 AI 응답이 아니라 내부 계산(`TenGodCalculator`, `HiddenStemCalculator`) 결과이므로, 기존과 동일하게 계산 로직 자체의 정확성에 의존하며 별도의 저장-전 range 검증 대상은 아니다(삭제 전 `TenGodData`/`HiddenStemData`에 `@Min`/`@Max` 제약 없음 — 검증 이관 대상 아님).

**데이터 정리 시 함께 리셋되는 관련 테이블** (구조 변경 아님, FR-006 데이터 정리 절차의 일부 — research.md #5 참고): `saju_full_data`, `career_fortune`은 `saju_result`를 FK로 참조하므로, `saju_result`를 리셋하려면 이 두 테이블도 함께 비워야 한다(구조적 일관성을 위해 사주 결과 전체를 리셋). 컬럼/엔티티 구조 자체는 이번 마이그레이션과 무관하게 그대로다.

## 2. `CareerConsultation`

**변경 없는 필드**: `id`, `sajuResult`(`@ManyToOne`), `openaiModelVersion`, `consultationMonth`(식별/idempotency 키, `sajuResult` + `consultationMonth` 유니크 제약 유지), `generatedAt`.

**제거되는 필드/연관관계**: `dayMasterDescription`, `fiveElementsAnalysis`(text) 및 14개 직계 자식 연관관계(`Industry`, `InterviewTip`, `Strength`, `ConsultationCaution`, `ConsultationKeyTenGod`, `ConsultationWealthStyle`, `ConsultationRoadmap`, `ConsultationPersonalBranding`, `ConsultationPowerKeywords`, `ConsultationMentalCare`, `ConsultationEnvironmentFit`, `ConsultationWorkStyle`, `ConsultationRelationshipStrategy`, `ConsultationCareerTimeline`) 및 그 손자 엔티티(`ConsultationMentalRechargeMethod`, `ConsultationMentalStressFactor`, `ConsultationPivotPoint`, `ConsultationWarningMonth`, `ConsultationPowerKeyword`, `ConsultationPowerKeywordUsageTip`, `ConsultationMonthFortune`) 전부.

**추가되는 컬럼**: `resultJson` (JSON) — 검증을 통과한 `CareerAdviceResponse` DTO를 그대로 직렬화(`dayMasterDescription`/`fiveElementsAnalysis`도 DTO 내부 필드).

**검증 규칙** (저장 전, `ConsultationOpenAICaller.validate()`에서 수행 — 엔티티 자체에는 Bean Validation 없음):
- 응답 전체 non-null
- industries/interviewTips/strengths 컬렉션 non-empty; industry 원소 non-null, name/reason non-blank; interviewTips/strengths 각 항목 non-blank
- 나머지 DTO 필드 전체를 validate()가 검사한다고 가정하지 않는다(기존 검증 범위 유지)

## 3. `CompanyCompatibility`

**변경 없는 필드**: `id`, `userProfile`, `user`, `companyName`, `targetRoleCategory`(enum), `targetRoleDetailName`, `compatibilityScore`, `summary`(text), `completed`, `compatibilityMonth`(식별/idempotency 키, `user`+`userProfile`+`companyName`+`targetRoleCategory`+`compatibilityMonth` 유니크 제약 유지), `analyzedAt`, `createdAt`.

**제거되는 연관관계**: 8개 직계 자식 연관관계(`TargetRoleAnalysis`, `FiveElementsAnalysis`, `AnalysisBreakdown`, `ActionableStrategy`, `ExpectedInterviewQuestion`, `RoleCompatibility`, `MonthlyForecast`, `Caution`) 및 그 손자 엔티티(`ActionableKeyword`, `LuckyDay`) 전부.

**추가되는 컬럼**: `resultJson` (JSON) — 검증을 통과한 AI 해설과 내부 계산 점수·오행·전략·월별 결과를 합친 `CompatibilityAnalysisData`를 직렬화. `CompatibilityNarrativeResponse` 자체를 저장하지 않으며 `summary`는 루트 text 컬럼에 유지.

**검증 규칙** (저장 전, `CompanyMatchingOpenAICaller.validate()`에서 수행):
- 7개 텍스트 필드 blank 아님
- `interviewQuestions`/`cautions` non-empty
- **크로스필드 일관성**: `monthlyAdvices.size()` == `AnalysisConstants.FORECAST_MONTH_COUNT`, 그리고 `monthlyAdvices`의 month 집합 == 사전 계산된 `expectedTargetMonths` (기존 로직, 변경 없음 — 이 일치 검사가 범위를 벗어나거나 중복된 month 값도 함께 걸러내므로 별도 range 검사는 불필요)
- **범위 검증 없음 (코드 리뷰로 정정)**: 최초 계획은 기존 `MonthlyForecast.score`/`TargetRoleAnalysis.matchScore` 엔티티의 `@Min`/`@Max`를 `validate()`로 이관하는 것이었으나, 이 두 값은 `CompatibilityNarrativeResponse` DTO에 필드로 존재하지 않는다 — OpenAI 응답이 아니라 `JobRoleAnalyzer`/`AnalysisResponseBuilder`가 오행 데이터로 내부 계산하는 값이며 공식 자체가 0~100으로 자체 유계다. 따라서 `validate()`에 추가할 코드가 없다 (research.md #4 참고). `JobRoleAnalyzer.calculateMatchScore()`는 T019a 구현으로 `Math.max(0, Math.min(score, MAX_SCORE))` 하한·상한을 적용한다(research.md #4, tasks.md T019a).

**변경 없는 것 (out of scope, 단 데이터는 정리됨)**: `UserSatisfactionFeedback` — 사용자가 직접 제출하는 피드백이며 AI 생성 결과가 아니므로 JSON화 대상이 아니다. `CompanyCompatibility`/`CareerConsultation`을 `@ManyToOne` FK(`company_compatibility_id`/`career_consultation_id`, 둘 다 nullable)로 참조하므로, 두 루트 테이블 DELETE 전에 이 FK 컬럼만 `NULL`로 갱신한다 — 피드백 행 자체(내용)는 삭제하지 않고 보존한다 (research.md #5).

## 삭제 대상 엔티티/리포지토리 전체 목록

| 루트 | 삭제되는 자식 엔티티 | 삭제되는 손자 엔티티 |
|------|----------------------|------------------------|
| `SajuResult`(관운) | `TenGodData`, `HiddenStemData` | (없음) |
| `CareerConsultation` | `Industry`, `InterviewTip`, `Strength`, `ConsultationCaution`, `ConsultationKeyTenGod`, `ConsultationWealthStyle`, `ConsultationRoadmap`, `ConsultationPersonalBranding`, `ConsultationPowerKeywords`, `ConsultationMentalCare`, `ConsultationEnvironmentFit`, `ConsultationWorkStyle`, `ConsultationRelationshipStrategy`, `ConsultationCareerTimeline` | `ConsultationMentalRechargeMethod`, `ConsultationMentalStressFactor`, `ConsultationPivotPoint`, `ConsultationWarningMonth`, `ConsultationPowerKeyword`, `ConsultationPowerKeywordUsageTip`, `ConsultationMonthFortune` |
| `CompanyCompatibility` | `TargetRoleAnalysis`, `FiveElementsAnalysis`, `AnalysisBreakdown`, `ActionableStrategy`, `ExpectedInterviewQuestion`, `RoleCompatibility`, `MonthlyForecast`, `Caution` | `ActionableKeyword`, `LuckyDay` |

**Repository 삭제 대상은 위 엔티티 전체가 아니다 (코드 리뷰로 정정)** — 실제로 `SSAju/src/main/java/ssafy/SSAju/repository/`를 전수 확인한 결과:
- **Repository 있음 (15개, 인터페이스도 삭제)**: `TenGodData`, `HiddenStemData`, `Industry`, `InterviewTip`, `Strength`, `TargetRoleAnalysis`, `FiveElementsAnalysis`, `AnalysisBreakdown`, `ActionableStrategy`, `ExpectedInterviewQuestion`, `RoleCompatibility`, `MonthlyForecast`, `Caution`, `ActionableKeyword`, `LuckyDay`
- **Repository 없음 (18개, 엔티티 클래스만 삭제 — 부모 `CareerConsultation`을 통한 cascade로만 관리됨)**: `ConsultationCaution`, `ConsultationKeyTenGod`, `ConsultationWealthStyle`, `ConsultationRoadmap`, `ConsultationPersonalBranding`, `ConsultationPowerKeywords`, `ConsultationMentalCare`, `ConsultationEnvironmentFit`, `ConsultationWorkStyle`, `ConsultationRelationshipStrategy`, `ConsultationCareerTimeline`, `ConsultationMentalRechargeMethod`, `ConsultationMentalStressFactor`, `ConsultationPivotPoint`, `ConsultationWarningMonth`, `ConsultationPowerKeyword`, `ConsultationPowerKeywordUsageTip`, `ConsultationMonthFortune`

## 영향받는 서비스 계층 (재작성 대상)

- `CompanyCompatibilitySaveService.java`, `CompatibilityChildReadService.java` — 현재 JSON 저장/조회 경로. 기존 `CompatibilityChildSaveService`는 제거됨
- `ConsultationSaveService.java` — JSON 결과 저장; 과거 `ConsultationInsertService`는 이후 004에서 제거됨
- `career/mapper/ConsultationMapper.java` — 엔티티 매핑 로직 → DTO→JSON 매핑으로 교체
- `SajuResultWriteService.java`, `career/provider/SajuResultProvider.java`, `career/mapper/SajuResultMapper.java` — `tenGodDataList`/`hiddenStemDataList` 저장·조립 로직 → JSON 직렬화/역직렬화로 교체

## 식별 키 및 후속 구현 반영 (2026-10-12)

- `SajuResult`: UNIQUE(`user_profile_id`); 사용자 접근 권한은 `user_saju_access`로 관리한다.
- `CareerConsultation`: UNIQUE(`saju_result_id`, `consultation_month`), 기존 제약명 `uq_career_consultation_result_month` 유지.
- `CompanyCompatibility`: UNIQUE(`user_id`, `user_profile_id`, `company_name`, `target_role_category`, `compatibility_month`), 기존 제약명 `uk_user_company_role_month` 유지.
- 개발 DB 초기화 시 `user_saju_access`의 결과 참조도 제거해야 한다. 적용 순서는 [quickstart §2](quickstart.md#2-스키마-수동-적용-마이그레이션-스크립트-없음--fr-009)를 따른다.
