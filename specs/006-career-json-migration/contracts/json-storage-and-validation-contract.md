# Contract: JSON 저장 및 저장-전 검증 계약

이 기능은 외부 REST API 응답 스키마를 바꾸지 않는다(컨트롤러가 클라이언트에게 반환하는 JSON은 기존과 동일). 이 문서는 대신 **서비스 계층 내부**에서 지켜야 할 계약, 즉 "무엇을 검증한 후에만 JSON 컬럼에 쓸 수 있는가"를 정의한다.

## 공통 규칙 (세 루트 모두)

1. 외부 응답(OpenAI) 또는 내부 계산 결과(SajuResult의 십신/지장간)는 **파싱 직후, JSON 직렬화 이전**에 검증을 통과해야만 저장 메서드로 전달될 수 있다.
2. 검증 실패 시 저장 메서드는 호출되지 않으며, 기존과 동일한 예외(`OpenAIApiException` 등)가 던져진다 — 부분 저장(JSON 일부만 기록)은 허용되지 않는다.
3. 식별/조회용 스칼라 컬럼(`consultationMonth`, `compatibilityMonth`, `companyName`, `targetRoleCategory` 등)은 JSON 안에 중복 보관하지 않는다 — 단일 출처(scalar 컬럼)만 신뢰한다.

## `CareerConsultation.resultJson`

**입력 계약**: `ConsultationOpenAICaller.validate(CareerAdviceResponse)`가 `true`(또는 예외 없이 반환)일 때만 `resultJson`에 직렬화 가능.

**검증 항목**:
| 항목 | 규칙 |
|------|------|
| 응답 전체 | non-null |
| `industries`, `interviewTips`, `strengths` | non-empty, 각 원소 non-blank |

## `CompanyCompatibility.resultJson`

**입력 계약**: `CompanyMatchingOpenAICaller.validate(CompatibilityNarrativeResponse, expectedTargetMonths)`가 통과했을 때만 `resultJson`에 직렬화 가능.

**검증 항목**:
| 항목 | 규칙 |
|------|------|
| 7개 텍스트 필드 | non-blank |
| `interviewQuestions`, `cautions` | non-empty |
| `monthlyAdvices` 개수 | `== AnalysisConstants.FORECAST_MONTH_COUNT` |
| `monthlyAdvices`의 month 집합 | `== expectedTargetMonths` (순서 무관, 원소 집합 일치) |
| `monthlyAdvices[i].month` (신규) | `1 <= month <= 12` |
| `monthlyAdvices[i].score`, `targetRoleAnalysis.matchScore` (신규) | `0 <= score <= 100` |

이 표의 "신규" 두 항목은 기존에 `MonthlyForecast`/`TargetRoleAnalysis` 엔티티의 `@Min`/`@Max`로 강제되던 것을 이관한 것이다 (엔티티가 사라지면서 Hibernate Validator가 더 이상 개입하지 않으므로).

## `SajuResult.tenGodHiddenStemAnalysis`

**입력 계약**: `TenGodCalculator`/`HiddenStemCalculator`의 계산 결과가 그대로 직렬화 대상이다. 이 값은 외부 응답이 아닌 내부 결정론적 계산이므로 별도의 저장-전 검증 절차는 두지 않는다(계산 로직 자체의 정확성이 곧 데이터 정합성 — 기존과 동일).

## 위반 시 동작

검증 실패 → 저장 미수행 → 사용자에게 기존과 동일한 오류 응답 (`SC-002`: 정합성 검사를 통과하지 못하는 AI 응답은 100% 저장되지 않고 거부된다).
