# Phase 1 Data Model: 커리어 분석 결과 JSON 저장 마이그레이션

## 개요

세 루트 엔티티가 대상이다. 각 루트는 (1) 식별/조회용 스칼라 컬럼은 그대로 유지하고, (2) 현재 정규화 자식 엔티티로 나뉘어 있던 결과 데이터를 JSON 컬럼 1개로 대체한다.

## 1. `SajuResult` (관운분석 관련 부분만 변경)

**변경 없는 필드**: `id`, `userProfile`, `user`, `fetchedAt`, `sajuFullData`(`@OneToOne`, 유지 — 원시 사주 데이터라 이번 범위 아님), `careerFortune`(`@OneToOne`, 유지 — `CareerFortune` 자체는 변경 없음).

**제거되는 연관관계**:
- `tenGodDataList` (`List<TenGodData>`, `@OneToMany`)
- `hiddenStemDataList` (`List<HiddenStemData>`, `@OneToMany`)

**추가되는 컬럼**:
- `tenGodHiddenStemAnalysis` (JSON) — 기존 `TenGodData`/`HiddenStemData` 각 행이 담던 값(십신 분포, 지장간 상세)을 하나의 JSON 구조로 직렬화. 정확한 내부 키 구조는 구현 시 `TenGodCalculator`/`HiddenStemCalculator`의 현재 반환 타입을 그대로 직렬화 대상으로 삼는다(별도 신규 필드 설계 아님).

**검증 규칙**: 이 데이터는 외부 AI 응답이 아니라 내부 계산(`TenGodCalculator`, `HiddenStemCalculator`) 결과이므로, 기존과 동일하게 계산 로직 자체의 정확성에 의존하며 별도의 저장-전 range 검증 대상은 아니다(현재도 `TenGodData`/`HiddenStemData`에 `@Min`/`@Max` 제약 없음 — 검증 이관 대상 아님).

## 2. `CareerConsultation`

**변경 없는 필드**: `id`, `sajuResult`(`@ManyToOne`), `openaiModelVersion`, `consultationMonth`(식별/idempotency 키, `sajuResult` + `consultationMonth` 유니크 제약 유지), `generatedAt`.

**제거되는 필드/연관관계**: `dayMasterDescription`, `fiveElementsAnalysis`(text) 및 14개 직계 자식 연관관계(`Industry`, `InterviewTip`, `Strength`, `ConsultationCaution`, `ConsultationKeyTenGod`, `ConsultationWealthStyle`, `ConsultationRoadmap`, `ConsultationPersonalBranding`, `ConsultationPowerKeywords`, `ConsultationMentalCare`, `ConsultationEnvironmentFit`, `ConsultationWorkStyle`, `ConsultationRelationshipStrategy`, `ConsultationCareerTimeline`) 및 그 손자 엔티티(`ConsultationMentalRechargeMethod`, `ConsultationMentalStressFactor`, `ConsultationPivotPoint`, `ConsultationWarningMonth`, `ConsultationPowerKeyword`, `ConsultationPowerKeywordUsageTip`, `ConsultationMonthFortune`) 전부.

**추가되는 컬럼**: `resultJson` (JSON) — 검증을 통과한 `CareerAdviceResponse` DTO(및 `dayMasterDescription`/`fiveElementsAnalysis`)를 그대로 직렬화.

**검증 규칙** (저장 전, `ConsultationOpenAICaller.validate()`에서 수행 — 엔티티 자체에는 Bean Validation 없음):
- 필수 텍스트 필드 non-null/non-blank
- industries/interviewTips/strengths 등 컬렉션 non-empty, 항목별 blank 아님

## 3. `CompanyCompatibility`

**변경 없는 필드**: `id`, `userProfile`, `user`, `companyName`, `targetRoleCategory`(enum), `targetRoleDetailName`, `compatibilityScore`, `completed`, `compatibilityMonth`(식별/idempotency 키, `user`+`userProfile`+`companyName`+`targetRoleCategory`+`compatibilityMonth` 유니크 제약 유지), `analyzedAt`, `createdAt`.

**제거되는 필드/연관관계**: `summary`(text) 및 8개 직계 자식 연관관계(`TargetRoleAnalysis`, `FiveElementsAnalysis`, `AnalysisBreakdown`, `ActionableStrategy`, `ExpectedInterviewQuestion`, `RoleCompatibility`, `MonthlyForecast`, `Caution`) 및 그 손자 엔티티(`ActionableKeyword`, `LuckyDay`) 전부.

**추가되는 컬럼**: `resultJson` (JSON) — 검증을 통과한 `CompatibilityNarrativeResponse` DTO(및 `summary`)를 그대로 직렬화.

**검증 규칙** (저장 전, `CompanyMatchingOpenAICaller.validate()`에서 수행):
- 7개 텍스트 필드 blank 아님
- `interviewQuestions`/`cautions` non-empty
- **크로스필드 일관성**: `monthlyAdvices.size()` == `AnalysisConstants.FORECAST_MONTH_COUNT`, 그리고 `monthlyAdvices`의 month 집합 == 사전 계산된 `expectedTargetMonths` (기존 로직, 변경 없음 — 이 일치 검사가 범위를 벗어나거나 중복된 month 값도 함께 걸러내므로 별도 range 검사는 불필요)
- **범위 검증 없음 (코드 리뷰로 정정)**: 최초 계획은 기존 `MonthlyForecast.score`/`TargetRoleAnalysis.matchScore` 엔티티의 `@Min`/`@Max`를 `validate()`로 이관하는 것이었으나, 이 두 값은 `CompatibilityNarrativeResponse` DTO에 필드로 존재하지 않는다 — OpenAI 응답이 아니라 `JobRoleAnalyzer`/`AnalysisResponseBuilder`가 오행 데이터로 내부 계산하는 값이며 공식 자체가 0~100으로 자체 유계다. 따라서 `validate()`에 추가할 코드가 없다 (research.md #4 참고).

**변경 없는 것 (out of scope)**: `UserSatisfactionFeedback` — 사용자가 직접 제출하는 피드백이며 AI 생성 결과가 아니므로 JSON화 대상이 아니다. `CompanyCompatibility`와의 FK 관계만 유지.

## 삭제 대상 엔티티/리포지토리 전체 목록

| 루트 | 삭제되는 자식 엔티티 | 삭제되는 손자 엔티티 |
|------|----------------------|------------------------|
| `SajuResult`(관운) | `TenGodData`, `HiddenStemData` | (없음) |
| `CareerConsultation` | `Industry`, `InterviewTip`, `Strength`, `ConsultationCaution`, `ConsultationKeyTenGod`, `ConsultationWealthStyle`, `ConsultationRoadmap`, `ConsultationPersonalBranding`, `ConsultationPowerKeywords`, `ConsultationMentalCare`, `ConsultationEnvironmentFit`, `ConsultationWorkStyle`, `ConsultationRelationshipStrategy`, `ConsultationCareerTimeline` | `ConsultationMentalRechargeMethod`, `ConsultationMentalStressFactor`, `ConsultationPivotPoint`, `ConsultationWarningMonth`, `ConsultationPowerKeyword`, `ConsultationPowerKeywordUsageTip`, `ConsultationMonthFortune` |
| `CompanyCompatibility` | `TargetRoleAnalysis`, `FiveElementsAnalysis`, `AnalysisBreakdown`, `ActionableStrategy`, `ExpectedInterviewQuestion`, `RoleCompatibility`, `MonthlyForecast`, `Caution` | `ActionableKeyword`, `LuckyDay` |

이에 대응하는 Repository 인터페이스(각 자식/손자 엔티티당 1개, `SSAju/src/main/java/ssafy/SSAju/repository/`)도 함께 삭제 대상이다.

## 영향받는 서비스 계층 (재작성 대상)

- `CompatibilityChildSaveService.java`, `CompatibilityChildReadService.java` — 자식 8개 저장/재조립 로직 → JSON 직렬화/역직렬화로 교체
- `ConsultationSaveService.java`, `ConsultationInsertService.java` — 자식 14개 저장 로직 → JSON 직렬화로 교체
- `career/mapper/ConsultationMapper.java` — 엔티티 매핑 로직 → DTO→JSON 매핑으로 교체
- `SajuResultWriteService.java`, `career/provider/SajuResultProvider.java`, `career/mapper/SajuResultMapper.java` — `tenGodDataList`/`hiddenStemDataList` 저장·조립 로직 → JSON 직렬화/역직렬화로 교체
