# Contract: JSON 저장 및 저장-전 검증 계약

이 기능은 외부 REST API 응답 스키마를 바꾸지 않는다(컨트롤러가 클라이언트에게 반환하는 JSON은 기존과 동일). 이 문서는 대신 **서비스 계층 내부**에서 지켜야 할 계약, 즉 "무엇을 검증한 후에만 JSON 컬럼에 쓸 수 있는가"를 정의한다.

## 공통 규칙 (세 루트 모두)

1. 외부 응답(OpenAI) 또는 내부 계산 결과(SajuResult의 십신/지장간)는 **파싱 직후, JSON 직렬화 이전**에 검증을 통과해야만 저장 메서드로 전달될 수 있다.
2. 검증 실패 시 저장 메서드는 호출되지 않으며, 기존과 동일한 예외(`OpenAIApiException` 등)가 던져진다 — 부분 저장(JSON 일부만 기록)은 허용되지 않는다.
3. 식별/조회용 스칼라 컬럼(`consultationMonth`, `compatibilityMonth`, `companyName`, `targetRoleCategory` 등)은 JSON 안에 중복 보관하지 않는다 — 단일 출처(scalar 컬럼)만 신뢰한다.

## `CareerConsultation.resultJson`

**입력 계약**: `ConsultationOpenAICaller.validate(CareerAdviceResponse)`가 예외 없이 정상 반환(`void`)했을 때만 `resultJson`에 직렬화 가능. 검증 실패 시 이 메서드는 `boolean false`를 반환하는 것이 아니라 `OpenAIApiException`을 던진다 — 호출부는 이 예외를 그대로 전파하고 저장을 시도하지 않는다.

**검증 항목**:

| 항목 | 규칙 |
|------|------|
| 응답 전체 | non-null |
| `industries`, `interviewTips`, `strengths` | non-empty, 각 원소 non-blank |

## `CompanyCompatibility.resultJson`

**입력 계약**: `CompanyMatchingOpenAICaller.validate(CompatibilityNarrativeResponse, expectedTargetMonths)`가 예외 없이 정상 반환(`void`)했을 때만 `resultJson`에 직렬화 가능. 검증 실패 시 `OpenAIApiException`을 던진다.

**검증 항목** (`CompanyMatchingOpenAICaller.java:87-139` 기준, DTO 필드 경로 전수):

| 필드 경로 | 규칙 |
|------|------|
| `response` (전체) | non-null |
| `summary`, `roleSynergy`, `roleWarning`, `fiveElementsSynergyDescription`, `weaknessDefense`, `primaryRoleReason`, `secondaryRoleReason` | 각각 non-blank |
| `interviewQuestions` | non-null, non-empty |
| `interviewQuestions[i].question`, `interviewQuestions[i].intent` | 각각 non-null, non-blank |
| `monthlyAdvices` 개수 | non-null, `== AnalysisConstants.FORECAST_MONTH_COUNT` |
| `monthlyAdvices[i]` (원소 자체) | non-null |
| `monthlyAdvices[i].advice` | non-blank |
| `monthlyAdvices[i].month`의 집합 전체 | `== expectedTargetMonths`의 집합 (순서 무관, 원소 완전 일치 — 범위를 벗어나거나 중복된 month는 이 일치 검사에서 자동으로 거부됨, 별도 range 검사 불필요) |
| `cautions` | non-null, non-empty, 각 원소 non-blank |

**DTO에 존재하지 않아 이 계약의 범위 밖인 값**: `TargetRoleAnalysis.matchScore`, `MonthlyForecast.score`는 `CompatibilityNarrativeResponse`(위 DTO)에 애초에 필드로 존재하지 않는다 — OpenAI 응답이 아니라 `JobRoleAnalyzer.analyze()`/`AnalysisResponseBuilder.buildMonthlyForecasts()`가 오행 데이터로 내부 계산하는 값이다. 이 두 값은 계산 공식 자체가 0~100을 벗어날 수 없도록 설계되어 있어(`Math.min(score, MAX_SCORE)` + 입력이 항상 0 이상) 이 문서의 "저장 전 검증" 대상이 아니며, `SajuResult.tenGodHiddenStemAnalysis`와 동일하게 취급한다(아래 참고).

## `SajuResult.tenGodHiddenStemAnalysis`

**입력 계약**: `TenGodCalculator`/`HiddenStemCalculator`의 계산 결과가 그대로 직렬화 대상이다. 이 값은 외부 응답이 아닌 내부 결정론적 계산이므로 별도의 저장-전 검증 절차는 두지 않는다(계산 로직 자체의 정확성이 곧 데이터 정합성 — 기존과 동일). 다만 다음 최소 계약은 지킨다 (코드 리뷰로 시점 정정):
- **null 체크**: 계산 결과가 `null`이면 저장 메서드(`repository.save(...)`)를 호출하기 전에 동기적으로 확인 가능하며, 이 경우 저장을 시도하지 않고 예외를 전파한다.
- **직렬화 자체의 실패**(순환 참조, 지원하지 않는 타입 등): `AttributeConverter`는 JPA가 flush/commit 시점에 호출하므로, `save()` 호출 시점에는 아직 실패 여부를 알 수 없다 — "저장 메서드를 호출하지 않는다"는 보장은 여기에는 적용되지 않는다. 대신 flush/commit 중 컨버터가 예외를 던지면 `@Transactional` 기본 동작(런타임 예외 시 롤백)에 의해 트랜잭션 전체가 롤백되어 **부분 저장은 발생하지 않으며**, 예외는 호출부까지 그대로 전파된다 — "호출 이전에 막는다"가 아니라 "커밋되지 않는다"로 보장 지점이 다를 뿐, 최종적으로 지키는 계약(부분 저장 없음, 예외 전파)은 동일하다.

## 위반 시 동작

검증 실패 → 저장 미수행 → 사용자에게 기존과 동일한 오류 응답 (`SC-002`: 정합성 검사를 통과하지 못하는 AI 응답은 100% 저장되지 않고 거부된다).
