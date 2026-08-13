# Contract: 기업 궁합 분석 — AI 해설 통합

## 외부 REST API 계약 (변경 없음)

`POST /api/compatibility`(또는 기존 `CompatibilityController`가 노출하는 엔드포인트)의 요청/응답 스키마는 `specs/001-career-fortune-api/spec.md`에 정의된 `CompatibilityResponse` 구조를 그대로 따른다. 이번 기능은 다음 필드들의 **값**만 바꾸고 **구조**는 바꾸지 않는다(FR-007, SC-005):

- `summary`
- `targetRoleAnalysis.synergy`, `targetRoleAnalysis.warning` (`targetRoleAnalysis.matchScore`는 값도 불변 — 규칙 기반 유지)
- `fiveElements.synergyDescription`
- `actionableStrategy.weaknessDefense`
- `expectedInterviewQuestions[]`
- `roleCompatibility[].reason`, `roleCompatibility[].score`(산식 통합으로 수치 변경 가능), `roleCompatibility[].tag`
- `monthlyForecast[].advice`
- `cautions[]`

## 내부 계약: `CompanyMatchingService` ↔ `CompanyMatchingOpenAICaller`

`ConsultationOpenAICaller.call(...)`과 동일한 형태의 내부 컴포넌트 계약.

**요청 입력** (프롬프트 조립에 사용, `PromptProvider`가 문자열로 변환):
- 사용자 오행 분포(`FiveElements`), 지장간(`HiddenStems`), 일간(`String`)
- 기업 오행 분포, 지장간, 일간
- 이미 계산된 궁합 점수(`compatibilityScore`), 직군 매칭 점수(`matchScore`), 역할별 점수(`primaryScore`/`secondaryScore`)
- 요청 직군(`JobCategoryEnum`), 상세 직무명(`String`, nullable)

**응답**: [data-model.md](../data-model.md)의 `CompatibilityNarrativeResponse`

**예외 계약** (`ConsultationOpenAICaller`와 동일):

| 예외 | 처리 |
|---|---|
| `ResourceAccessException`(네트워크/타임아웃) | `@Retryable` 재시도 대상(최대 3회, 지수 백오프 1s/2s/4s) |
| `TransientAiException`(OpenAI 5xx 상당) | `@Retryable` 재시도 대상 |
| `NonTransientAiException`(OpenAI 4xx 상당) | `OpenAIApiException`으로 변환, 재시도 안 함 |
| 응답 검증 실패(필수 필드 누락 등) | `OpenAIApiException`, 재시도 안 함 |
| 재시도 소진 | `@Recover`가 `OpenAIApiException`으로 변환 후 상위로 전파 |

**호출자(`CompanyMatchingService`) 계약**: 위 예외가 전파되면 `DailyApiUsageService.restoreDailyUsage(userId, usageDate)`를 호출해 쿼터를 복원한 뒤 재throw한다(FR-004, FR-005). 이 컴포넌트 자신은 쿼터 로직을 알지 못한다 — 순수하게 프롬프트 입력을 받아 해설 텍스트를 반환하는 책임만 가진다.

## 프롬프트 계약: `PromptProvider.getCompatibilityNarrativePrompt(...)`

기존 `getCareerConsultationPrompt(...)`와 동일한 패턴 — Service에 프롬프트 문자열을 하드코딩하지 않고 `PromptProvider`에 신규 메서드로 추가한다. 프롬프트에는 다음이 반드시 포함되어야 한다(`quickstart.md`의 검증 시나리오 대응):

- 위 8개 응답 필드에 대한 JSON 스키마 명시(`CompatibilityNarrativeResponse` 구조와 1:1 대응)
- 이미 계산된 점수(궁합/직군매칭/역할별)를 "다시 계산하지 말고 해설만 작성하라"는 지시 — AI가 점수를 임의로 바꾸지 않도록 방지
- `monthlyAdvices`는 정확히 5개, 순서는 이번 달부터 5개월 순서 고정
