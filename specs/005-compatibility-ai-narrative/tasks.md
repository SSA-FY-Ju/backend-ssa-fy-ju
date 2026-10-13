---

description: "Task list for 기업 궁합 분석 AI 해설 전환 및 점수 산정 일원화"

---

# Tasks: 기업 궁합 분석 AI 해설 전환 및 점수 산정 일원화

**상태 정리 (2026-10-12)**: 구현은 `main`에 병합됨(7331796 / PR #45). 완료 체크는 현재 코드·테스트·커밋 이력을 대조한 결과이며, 이번 문서 정리에서 테스트나 DB 작업을 새로 실행한 기록이 아니다. 기존 2026-10-11 전체 테스트 기록은 387개 통과(실패/오류/스킵 0)다. 이전에 미확인으로 남겨둔 환경·수동 검증 및 clean 명령 항목은 2026-10-12 사용자 요청에 따라 완료 처리했다. Codex가 해당 항목을 새로 실행하거나 재검증한 것은 아니다.

**남은 항목**: 없음(사용자 요청에 따른 완료 처리 포함).

**Input**: Design documents from `specs/005-compatibility-ai-narrative/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: 포함됨 — `SSAju/CLAUDE.md`("Test-before-commit is mandatory")와 이 저장소의 기존 규칙(`specs/004-redis-hardening-refactor/tasks.md`와 동일 컨벤션)에 따라 각 User Story마다 검증 테스트 태스크를 포함한다.

**Organization**: 태스크는 spec.md의 우선순위(US1 P1 → US2 P2 → US3 P3) 순서로 그룹화했다. US1이 궁합 분석 흐름에 AI 호출 자체를 도입하므로, US3(AI 실패 시 쿼터 보상)은 US1이 추가한 호출 지점을 대상으로 하는 **코드 의존**을 가진다(spec.md 기준 "독립적으로 테스트 가능"은 유지되지만 구현 순서상 US1 이후 진행 권장). US2(점수 산식 통합)는 AI와 무관하게 완전히 독립적이다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 병렬 실행 가능(다른 파일, 선행 의존성 없음)
- **[Story]**: 이 태스크가 속한 User Story(US1~US3)
- 파일 경로는 모두 `SSAju/src/main/java/ssafy/SSAju/` 또는 `SSAju/src/test/java/ssafy/SSAju/` 기준 상대경로로 표기

## Path Conventions

단일 Gradle 프로젝트(`SSAju/`), 소스는 `SSAju/src/main/java/ssafy/SSAju/`, 테스트는 `SSAju/src/test/java/ssafy/SSAju/`. `plan.md`의 Project Structure 섹션과 동일.

---

## Phase 1: Setup

**Purpose**: 신규 외부 의존성 없음(기존 Spring AI `ChatClient`/`spring-retry` 재사용) — 로컬 환경 확인만 필요

- [x] T001 로컬 `SSAju/application-local.yaml`에 `OPENAI_API_KEY`가 설정되어 있는지 확인(기존 `ConsultationOpenAICaller`와 동일 키 재사용, `build.gradle` 변경 불필요)

**Checkpoint**: `cd SSAju && ./gradlew build` 성공(기존과 동일, 신규 의존성 없으므로 변화 없어야 함)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 이번 기능은 인증/세션처럼 모든 스토리가 공유하는 신규 인프라가 없다 — `ChatClientConfig`, `RetryConfig`, `DailyApiUsageService.restoreDailyUsage`(모두 기존 스펙에서 이미 구현 완료)를 그대로 재사용하므로 별도 Foundational 태스크가 필요 없다.

**Checkpoint**: 없음 — 바로 User Story 단계로 진행

---

## Phase 3: User Story 1 - 개인화된 궁합 해설 받기 (Priority: P1) 🎯 MVP

**Goal**: 궁합 분석의 8개 해설 텍스트 필드(시너지/경고/오행 시너지 설명/약점 방어 전략/면접 질문/역할별 사유/월별 조언/주의사항/요약)를 고정 템플릿에서 OpenAI 1-call JSON 응답으로 대체

**Independent Test**: 서로 다른 입력 조합으로 5회 이상 요청 시 8개 텍스트 필드가 매번 다르게 나오는지, 같은 달 재요청 시 캐시된 텍스트가 재사용되는지 확인 (quickstart.md "US1" 시나리오)

### Tests for User Story 1

- [x] T002 [P] [US1] `CompanyMatchingOpenAICallerTest.java`의 응답 검증·예외 분류 테스트와 `CompanyMatchingOpenAICallerRetryTest.java`의 Spring 프록시 기반 재시도 테스트 구현
- [x] T003 [P] [US1] `SSAju/src/test/java/ssafy/SSAju/service/CompanyMatchingServiceTest.java`에 케이스 추가 — `ChatClient`/`CompanyMatchingOpenAICaller`를 mock으로 대체했을 때 응답의 8개 텍스트 필드가 mock 응답값을 그대로 반영하는지, 캐시 히트 시 AI가 재호출되지 않는지 확인(구현 전 실패 확인 필수)

### Implementation for User Story 1

- [x] T004 [P] [US1] `dto/external/CompatibilityNarrativeResponse.java` 생성(최상위 `ssafy.SSAju.dto.external` 패키지 — 기존 `FastAPIResponse`/`CareerAdviceResponse`와 동일 위치, `career/` 하위 아님) — [data-model.md](./data-model.md)의 필드 정의(`summary`, `roleSynergy`, `roleWarning`, `fiveElementsSynergyDescription`, `weaknessDefense`, `interviewQuestions[]`, `primaryRoleReason`, `secondaryRoleReason`, `monthlyAdvices[]`, `cautions[]`) 그대로 record로 작성
- [x] T005 [US1] `career/provider/PromptProvider.java`에 `getCompatibilityNarrativePrompt(...)` 메서드 추가 — [contracts/compatibility-narrative-contract.md](./contracts/compatibility-narrative-contract.md)의 입력(사용자/기업 오행·지장간·일간, 이미 계산된 점수 3종, 직군)을 받아 프롬프트 문자열 조립, "점수는 재계산하지 말고 해설만 작성" 지시 포함, `monthlyAdvices` 5개 고정 명시 (T004 의존)
- [x] T006 [US1] `career/caller/CompanyMatchingOpenAICaller.java` 생성 — `ConsultationOpenAICaller`와 동일하게 `@Retryable(retryFor={ResourceAccessException.class, TransientAiException.class}, noRetryFor={OpenAIApiException.class, HttpMessageConversionException.class}, maxAttempts=3)` + `@Recover` 3종 + `validate()`(null/blank 금지, `interviewQuestions` 최소 1개, `monthlyAdvices` 정확히 5개, `cautions` 최소 1개) 구현 (T004, T005 의존)
- [x] T007 [US1] `career/util/JobRoleAnalyzer.java` 수정 — `buildSynergyText`/`buildWarningText`(및 관련 문구 상수 `SYNERGY_*_FMT`/`WARNING_*_FMT`) 삭제, `analyze()`는 `matchScore` 계산만 반환하도록 축소(문구는 AI 응답으로 대체되므로 `RoleAnalysis` VO의 synergy/warning은 호출부에서 AI 값으로 채움)
- [x] T008 [US1] `career/util/AnalysisResponseBuilder.java` 수정 — `buildElementSynergyText`, `buildForecastMessage`(문구 생성부만, `status`/`score` 산정 로직은 유지), `buildInterviewQuestions`, `buildCautions`, `buildSummary`의 템플릿 문자열 생성 로직 제거하고 `CompatibilityNarrativeResponse` 값을 그대로 받아 조립하도록 시그니처 변경(`buildActionableStrategy`는 `weaknessDefense` 파라미터만 AI 값으로 교체, `luckyDays`/`preferredTime` 로직은 유지)
- [x] T009 [US1] `service/CompanyMatchingService.java` 수정 — 궁합 점수·`matchScore` 계산 완료 후 `CompanyMatchingOpenAICaller.call(...)` 호출 추가, 반환된 `CompatibilityNarrativeResponse`를 T007/T008에서 축소된 메서드들에 전달해 최종 응답 조립 (T006, T007, T008 의존)

**Checkpoint**: `cd SSAju && ./gradlew test`로 US1 관련 테스트 통과 확인 — 이 시점에 궁합 분석 해설 텍스트가 AI 기반으로 완전히 전환됨

---

## Phase 4: User Story 2 - 일관된 점수 체계로 신뢰도 확보 (Priority: P2)

**Goal**: `JobRoleAnalyzer.matchScore`와 `RoleCompatibilityCalculator`의 점수 산식 불일치를 "역할별 점수가 `matchScore`를 재사용"하는 방식으로 통합

**Independent Test**: 동일한 사용자 오행 입력에 대해 `targetRoleAnalysis.matchScore == roleCompatibility[0].score`(전문가), `roleCompatibility[1].score == matchScore - 15`(리드)를 단위 테스트로 검증 (quickstart.md "US2" 시나리오, AI 텍스트 전환과 무관하게 독립 검증 가능)

### Tests for User Story 2

- [x] T010 [P] [US2] `RoleCompatibilityCalculatorTest.java`에서 보조 역할의 -15 페널티와 하한 0을 검증. 주 역할이 matchScore를 재사용하는 흐름은 `CompanyMatchingServiceTest.java`에서 확인
- [x] T011 [P] [US2] `SSAju/src/test/java/ssafy/SSAju/career/util/JobRoleAnalyzerTest.java` 갱신 — 텍스트(synergy/warning) 관련 단언 제거, `matchScore` 계산 케이스만 유지·검증

### Implementation for User Story 2

- [x] T012 [US2] `RoleCompatibilityCalculator`의 주 역할 산식 및 `calculatePrimary` 메서드 제거. `CompanyMatchingService`가 이미 계산한 matchScore를 주 역할 점수로 직접 재사용하며 계산기는 `calculateSecondary(int)`만 담당
- [x] T013 [US2] `CompanyMatchingService`가 matchScore와 secondaryScore를 `AnalysisResponseBuilder.buildRoleCompatibilities(...)`에 전달하도록 변경. 별도 주 역할 계산 호출 없음 (T008, T012 의존)
- [x] T014 [P] [US2] `career/util/AnalysisConstants.java`에서 더 이상 쓰이지 않는 `PRIMARY_ROLE_SCORE_MULTIPLIER`, `PRIMARY_ROLE_SCORE_BASE` 상수 제거(F: 죽은 코드 정리, T012 완료 후 사용처 없음을 확인한 뒤 진행)

**Checkpoint**: `cd SSAju && ./gradlew test`로 US1+US2 테스트 모두 통과 확인 — 직군 매칭 점수와 역할별 점수가 하나의 산식에서 파생됨

---

## Phase 5: User Story 3 - AI 서비스 장애 시에도 안전하게 실패 처리 (Priority: P3)

**Goal**: AI 해설 생성이 실패해도 사용자의 일일 분석 가능 횟수가 소진된 채 남지 않도록 기존 쿼터 보상 범위를 AI 호출까지 확장

**Independent Test**: AI 해설 생성 호출이 재시도 소진 후 최종 실패하거나, AI 해설은 성공했지만 최종 저장이 실패하는 상황 모두에서 사용자의 일일 사용 횟수가 요청 전과 동일하게 유지되고, 응답이 명확한 오류로 반환되는지 확인 (quickstart.md "US3" 시나리오, spec.md US3 Acceptance Scenario 1·2)

### Tests for User Story 3

- [x] T015 [US3] `CompanyMatchingServiceTest.java`에서 AI 호출 실패와 `CompanyCompatibilitySaveService.saveWithLock(...)` 실패 시 `restoreQuietly(userId, usageDate, originalException)` 호출 및 원본 예외 전파 확인. 경합으로 기존 행 재사용 시 복원, 저장 성공 후 응답 조립 실패 시 미복원도 검증

### Implementation for User Story 3

- [x] T016 [US3] `CompanyMatchingService.analyzeCompatibility`에서 `analyzeAndPersist(...)`의 사주 계산·AI 호출·최종 저장을 보상 범위로 포함. 실패 시 `restoreQuietly(...)`로 복원하고 원본 예외 전파. 저장 성공 후 응답 조립은 보상 범위 밖 (T009, T015 의존)

**Checkpoint**: `cd SSAju && ./gradlew test`로 US1+US2+US3 전체 테스트 통과 확인 — AI 실패가 쿼터에 영향을 주지 않음이 검증됨

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 전체 통합 검증 및 마무리

- [x] T017 `cd SSAju && ./gradlew clean test` 실행 — 전체 스위트 `BUILD SUCCESSFUL` 확인
- [x] T018 [P] `quickstart.md`의 US1~US3 시나리오를 로컬(`./gradlew bootRun`)에서 수동 실행하여 최종 확인
- [x] T019 [P] `SSAju/CLAUDE.md` 갱신 여부 검토 — `career/caller/CompanyMatchingOpenAICaller` 신설 등 아키텍처 변경 사항이 문서화 기준에 해당하면 반영

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 의존성 없음 — 즉시 시작 가능(단, 실질적으로 확인 작업뿐)
- **Foundational (Phase 2)**: 해당 없음(신규 공유 인프라 없음)
- **User Story 1 (Phase 3)**: Setup 이후 즉시 시작 가능 — 이번 기능의 핵심(AI 호출 도입)이며 다른 두 스토리의 전제가 되는 코드 변경을 포함
- **User Story 2 (Phase 4)**: Setup 이후 시작 가능하나, `AnalysisResponseBuilder.java`(T008/T013)가 같은 파일을 다루므로 **US1 완료 후 진행 권장**(동일 파일 동시 수정 충돌 방지)
- **User Story 3 (Phase 5)**: `CompanyMatchingService.analyzeCompatibility`에 US1(T009)이 추가한 AI 호출이 있어야 보상 범위를 확장할 대상이 생기므로 **US1 완료 후 진행 필수**
- **Polish (Phase 6)**: US1~US3 모두 완료 후 진행

### User Story Dependencies

- **US1(P1)**: 독립적으로 시작 가능, 이 기능의 MVP
- **US2(P2)**: AI 텍스트 전환과 무관하게 점수 계산 로직만 다루므로 개념적으로는 독립적이나, `AnalysisResponseBuilder.java` 파일 충돌을 피하기 위해 US1 이후 순차 진행 권장
- **US3(P3)**: US1이 추가한 AI 호출 지점을 대상으로 하므로 US1 완료가 사실상 선행 조건

### Within Each User Story

- 테스트 우선 작성 후 실패 확인 → 구현
- DTO/계산 로직 변경 → Service 통합 순
- 각 스토리 완료 시 `./gradlew test`로 회귀 확인 후 다음 우선순위로 이동

### Parallel Opportunities

- T002, T003(US1 테스트)은 병렬 가능
- T004(US1 DTO)는 다른 US1 태스크와 독립적으로 먼저 병렬 진행 가능
- T010, T011(US2 테스트)은 병렬 가능
- T014(US2 상수 정리)는 T012 완료 후 다른 US2 태스크와 병렬 가능
- T018, T019(Polish)는 병렬 가능

---

## Parallel Example: User Story 1

```bash
# US1 테스트 2종 병렬 작성
Task: "CompanyMatchingOpenAICallerTest.java 작성"
Task: "CompanyMatchingServiceTest.java에 AI mock 케이스 추가"

# US1 신규 DTO는 다른 구현 태스크 착수 전 먼저 병렬로 완료 가능
Task: "CompatibilityNarrativeResponse.java 생성"
```

---

## Implementation Strategy

### MVP First (User Story 1만)

1. Phase 1(Setup) 완료
2. Phase 3(US1: AI 해설 전환) 완료 → 독립 검증(quickstart.md "US1")
3. 여기까지가 사용자가 체감하는 핵심 가치 — 배포/데모 가능

### Incremental Delivery

1. Setup → US1(AI 해설 전환, MVP) → 완료 시 `./gradlew test` + quickstart "US1" 데모
2. US2(점수 산식 통합) → 완료 시 quickstart "US2" 데모
3. US3(AI 실패 시 쿼터 보상) → 완료 시 quickstart "US3" 데모
4. Polish(Phase 6) → 전체 통합 확인

---

## Notes

- `[P]` 태스크 = 서로 다른 파일, 의존성 없음
- `[Story]` 라벨은 각 태스크의 추적성을 위해 부여됨
- 각 태스크 또는 논리적 그룹 단위로 커밋(`skills/git-workflow.md` 규칙 준수, 커밋 메시지에 `[Test Passed]` 푸터 필수)
- 체크포인트마다 스토리 단독 동작을 검증 후 정지 가능
- 분산락(`@DistributedLock`) 적용은 이 스펙의 최초 구현 범위 밖이었으며, 현재는 `004` US5에서 구현된 `CompanyCompatibilitySaveService`가 DB 저장 구간을 보호한다. 외부 AI 호출 중복은 허용하고 결과 행 중복을 막는다
