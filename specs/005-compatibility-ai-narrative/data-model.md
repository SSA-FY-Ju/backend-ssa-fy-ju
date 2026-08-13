# Data Model: 기업 궁합 분석 AI 해설 전환 및 점수 산정 일원화

## 요약

**신규 테이블/컬럼 없음.** 이번 기능은 기존 `CompanyCompatibility` 및 하위 자식 엔티티(`specs/001-career-fortune-api/spec.md` 기준)의 텍스트 컬럼에 들어가는 **값의 생성 방식**만 바꾼다. 스키마 마이그레이션이 필요 없다.

## 영향받는 기존 엔티티 (스키마 불변, 값 생성원만 변경)

| 엔티티 | 필드 | 기존 생성원 | 변경 후 생성원 |
|---|---|---|---|
| `CompanyCompatibility` | `summary` | `AnalysisResponseBuilder.buildSummary()`(템플릿) | `CompanyMatchingOpenAICaller` 응답 |
| `TargetRoleAnalysis` | `synergy`, `warning` | `JobRoleAnalyzer.buildSynergyText/buildWarningText()`(템플릿) | AI 응답 |
| `TargetRoleAnalysis` | `matchScore` | `JobRoleAnalyzer.calculateMatchScore()` | 변경 없음(규칙 기반 유지) |
| `FiveElementsAnalysis` | `synergyDescription` | `AnalysisResponseBuilder.buildElementSynergyText()`(템플릿) | AI 응답 |
| `ActionableStrategy` | `weaknessDefense` | `AnalysisResponseBuilder.buildActionableStrategy()`(템플릿) | AI 응답 |
| `ActionableStrategy` | `luckyDays`, `preferredTime` | 고정 오프셋/상수 | 변경 없음(규칙 기반 유지) |
| `ExpectedInterviewQuestion` | `question`, `intent` | 고정 2문항 템플릿 | AI 응답(개수는 가변 가능, 최소 1개 이상 검증 — 검증 규칙 및 [tasks.md](./tasks.md) T006과 동일) |
| `RoleCompatibility` | `reason` | 고정 템플릿 | AI 응답 |
| `RoleCompatibility` | `score`, `tag` | `RoleCompatibilityCalculator`(자체 산식) | `RoleCompatibilityCalculator`(`JobRoleAnalyzer.matchScore` 재사용, 산식 통합 — Decision 2 참고) |
| `MonthlyForecast` | `advice` | `AnalysisResponseBuilder.buildForecastMessage()`(템플릿) | AI 응답 |
| `MonthlyForecast` | `month`, `score`, `status` | `ForecastScoreCalculator` | 변경 없음(규칙 기반 유지) |
| `Caution` | `content` | 고정 템플릿(2개) | AI 응답 |

## 신규 내부 DTO (영속화되지 않음, AI 응답 매핑 전용)

`dto/external/CompatibilityNarrativeResponse.java`(최상위 `ssafy.SSAju.dto.external` 패키지) — 기존 `FastAPIResponse`/`CareerAdviceResponse`와 동일한 위치·역할(외부 AI 응답 역직렬화 전용 record). `career/` 서브패키지가 아닌 최상위 `dto/external`에 두는 이유는 이 두 기존 클래스와 동일한 컨벤션을 따르기 위함([plan.md](./plan.md) Project Structure 참고). DB에는 저장되지 않고, `CompanyMatchingService`가 이 record의 값을 기존 자식 엔티티 필드에 매핑해 저장한다.

```java
public record CompatibilityNarrativeResponse(
    String summary,                                   // CompanyCompatibility.summary
    String roleSynergy,                                // TargetRoleAnalysis.synergy
    String roleWarning,                                // TargetRoleAnalysis.warning
    String fiveElementsSynergyDescription,              // FiveElementsAnalysis.synergyDescription
    String weaknessDefense,                            // ActionableStrategy.weaknessDefense
    List<InterviewQuestion> interviewQuestions,         // ExpectedInterviewQuestion[]
    String primaryRoleReason,                           // RoleCompatibility[전문가].reason
    String secondaryRoleReason,                         // RoleCompatibility[리드].reason
    List<String> monthlyAdvices,                        // MonthlyForecast[].advice (5개월 순서 고정)
    List<String> cautions                               // Caution[]
) {
    public record InterviewQuestion(String question, String intent) {}
}
```

**검증 규칙**(FR-005, `ConsultationOpenAICaller.validate()` 패턴 재사용):
- 모든 텍스트 필드는 null/blank 불가
- `interviewQuestions`는 최소 1개 이상
- `monthlyAdvices`는 정확히 5개(월별 운세 개월 수, `AnalysisConstants.FORECAST_MONTH_COUNT`와 일치)
- `cautions`는 최소 1개 이상
- 검증 실패 시 `OpenAIApiException`(기존 예외 계층, DB 저장 이전 단계에서 실패)

## 산식 통합에 따른 계산 흐름 변화

**Before**:
```
JobRoleAnalyzer.matchScore = primaryCount×40 + secondaryCount×20 (cap 100)
RoleCompatibilityCalculator.primaryScore = primaryCount×30 + 40 (cap 100)   ← 별도 산식, 불일치
RoleCompatibilityCalculator.secondaryScore = primaryScore − 15
```

**After**:
```
JobRoleAnalyzer.matchScore = primaryCount×40 + secondaryCount×20 (cap 100)   ← 변경 없음
RoleCompatibilityCalculator.primaryScore = matchScore                        ← JobRoleAnalyzer 결과 재사용
RoleCompatibilityCalculator.secondaryScore = max(primaryScore − 15, 0)       ← 변경 없음(기존 코드가 이미 하한 0 클램프 적용 중)
```

`RoleCompatibilityCalculator.calculatePrimary(FiveElements, JobCategoryEnum)` 시그니처는 `calculatePrimary(int matchScore)`로 단순화되며, 오행 분포로부터 직접 재계산하지 않는다.
