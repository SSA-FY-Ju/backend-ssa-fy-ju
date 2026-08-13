# Implementation Plan: 기업 궁합 분석 AI 해설 전환 및 점수 산정 일원화

**Branch**: `005-compatibility-ai-narrative` | **Date**: 2026-08-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-compatibility-ai-narrative/spec.md`

## Summary

`CompanyMatchingService.analyzeCompatibility`가 만들어내는 8개 해설 텍스트 필드(시너지/경고/오행 시너지 설명/약점 방어 전략/예상 면접 질문/역할별 사유/월별 조언/주의사항/요약)를 고정 템플릿 문자열 생성에서 `ConsultationOpenAICaller`와 동일한 1-call JSON 모드 OpenAI 호출로 대체한다. 숫자 점수(전체 궁합 점수, 직군 매칭 점수, 역할별 점수, 월별 운세 점수)는 기존 규칙 기반 계산을 그대로 유지하되, `JobRoleAnalyzer.matchScore`와 `RoleCompatibilityCalculator`가 각자 다른 가중치 공식을 쓰던 기존 불일치를 "역할별 점수가 이미 계산된 matchScore를 그대로 재사용"하는 방식으로 통합해 별도 산식을 없앤다. 분산락(`specs/004-redis-hardening-refactor` US5)은 이번 범위 밖이며, `analyzeCompatibility`는 지금과 동일하게 INSERT IGNORE 기반 경합 처리를 유지한다. 응답 JSON 필드 구조(`CompatibilityResponse` 및 하위 record)는 변경하지 않는다.

## Technical Context

**Language/Version**: Java 21 (toolchain), Gradle 9.4.1, Spring Boot 4.0.5 — 변경 없음

**Primary Dependencies**: 기존 Spring AI `ChatClient`(OpenAI, JSON 모드), `spring-retry`(`@Retryable`/`@Recover`), `RestClient` — 신규 외부 라이브러리 의존성 없음. `ConsultationOpenAICaller`/`PromptProvider`/`ChatClientConfig`를 그대로 재사용

**Storage**: MySQL 8(JPA/Hibernate) — 신규 테이블/컬럼 없음. `TargetRoleAnalysis.synergy/warning`, `FiveElementsAnalysis.synergyDescription`, `ActionableStrategy.weaknessDefense`, `ExpectedInterviewQuestion`, `RoleCompatibility.reason`, `MonthlyForecast.advice`, `Caution.content`, `CompanyCompatibility.summary` 등 기존 컬럼의 **값 생성 방식만** 규칙 기반 → AI 생성으로 전환

**Testing**: JUnit 5 + AssertJ(Given-When-Then), H2(단위), MySQL Testcontainers(통합, 기존 인프라 재사용) — 신규: `CompanyMatchingOpenAICallerTest`(`ConsultationOpenAICallerTest` 패턴, 재시도/예외 경로). 점수 산식 통합 검증은 별도 신규 계산기 클래스 없이(research.md Decision 2) 기존 `RoleCompatibilityCalculatorTest`/`JobRoleAnalyzerTest`/`CompanyMatchingServiceTest` 확장으로 커버

**Target Platform**: 기존과 동일 — Linux 서버(Docker), JSON REST API

**Project Type**: 단일 Spring Boot 백엔드 웹 서비스(web-service) — `specs/004-redis-hardening-refactor`와 동일 저장소, 별도 프로젝트 아님

**Performance Goals**: 궁합 분석 응답을 15초 이내(SC-004)에 반환. 기존 OpenAI 호출 타임아웃 예산(`ApiTimeoutConstants.OPENAI_TIMEOUT_SECONDS`, 8초) 및 재시도 정책(`@Retryable` maxAttempts=3, 지수 백오프)을 그대로 재사용

**Constraints**:
- 외부 I/O(OpenAI)를 호출하는 `CompanyMatchingService.analyzeCompatibility`는 `@Transactional` 금지 원칙 유지(`skills/code-style-guide.md` 트랜잭션 절)
- 분산락(`@DistributedLock`) 적용은 `specs/004-redis-hardening-refactor` US5(T035) 범위 — 이번 계획에서는 도입하지 않으며, 나중에 락이 적용될 때는 Redisson lease-time이 AI 호출 지연(최대 3회 재시도 포함)을 수용하는지만 재검토
- 응답 JSON 필드 구조(`CompatibilityResponse` 및 모든 하위 record의 필드명·타입)는 변경 금지(FR-007, SC-005) — 프론트엔드 계약 유지
- 쿼터 차감/복원은 `DailyApiUsageService.restoreDailyUsage` 보상 트랜잭션 패턴을 재사용하며, 기존 try/catch 범위를 AI 호출까지 확장

**Scale/Scope**: `career/util`(`JobRoleAnalyzer`, `RoleCompatibilityCalculator`, `AnalysisResponseBuilder`), `career/caller`(신규 `CompanyMatchingOpenAICaller`), `career/provider/PromptProvider`, `service/CompanyMatchingService`, `dto/external`(신규 `CompatibilityNarrativeResponse`)에 국한. 컨트롤러/DTO 구조, 엔티티 스키마, 인증/세션 등 다른 도메인은 범위 밖

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md`는 아직 프로젝트 고유 원칙이 채워지지 않은 템플릿 상태다(`specs/004-redis-hardening-refactor/plan.md`와 동일 상황). 이 프로젝트는 `CLAUDE.md` + `skills/architecture-guide.md` + `skills/code-style-guide.md`를 사실상의 헌법으로 사용하므로, 이 문서들에서 도출한 게이트로 대체 평가한다.

| Gate | 판정 | 근거 |
|---|---|---|
| 계층 분리(Controller 얇게/Service 로직 집중/Repository는 DB만) | PASS | 텍스트 생성 책임이 `AnalysisResponseBuilder`/`JobRoleAnalyzer`(규칙 기반)에서 `CompanyMatchingOpenAICaller`(AI 기반)로 이동할 뿐, `CompanyMatchingService`는 그대로 orchestration만 담당 |
| 외부 I/O 메서드 `@Transactional` 금지 | PASS | `analyzeCompatibility`는 현재도 미적용 상태 유지. 신규 AI 호출도 FastAPI/공공데이터 호출과 동일하게 트랜잭션 밖에서 실행 |
| PromptProvider로 프롬프트 외부화(하드코딩 금지) | PASS | 신규 프롬프트는 `PromptProvider`에 메서드로 추가, Service에 인라인 문자열 금지 |
| RestClient/ChatClient + `@Retryable` 재시도 패턴(5xx·타임아웃 재시도, 4xx 비재시도) | PASS | `CompanyMatchingOpenAICaller`는 `ConsultationOpenAICaller`의 `retryFor`/`noRetryFor`/`@Recover` 구성을 그대로 재사용 |
| 상수: 도메인=Enum, 기술=Static Class, 매직넘버/문자열 금지 | PASS | 신규 점수 통합 계산기의 가중치·오프셋은 `AnalysisConstants`에 추가, 매직넘버 없음 |
| DTO는 record | PASS | 신규 `CompatibilityNarrativeResponse` 및 하위 타입 모두 record |
| 로깅 보안(개인정보/프롬프트 원문 미노출) | PASS | AI 요청/응답 로깅 시 `userId`/상태코드/지연시간만 기록, 프롬프트 원문·사주 데이터 미기록(기존 `ConsultationOpenAICaller` 로깅 정책과 동일) |
| 예외는 커스텀 예외 계층 + 전역 핸들러, try-catch로 삼키지 않음 | PASS | AI 실패 시 `OpenAIApiException`(기존 계층) 재사용, 쿼터 보상 후 재throw |
| 중복 로직 제거(단일 소스 원칙) | PASS | `RoleCompatibilityCalculator`가 `JobRoleAnalyzer.matchScore`를 재사용하도록 변경해 중복 산식 제거(신규 3번째 공식 도입 안 함) |

Constitution Check 위반 없음 — Complexity Tracking 불필요.

## Project Structure

### Documentation (this feature)

```text
specs/005-compatibility-ai-narrative/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── compatibility-narrative-contract.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

기존 `SSAju/src/main/java/ssafy/SSAju/` 단일 Gradle 프로젝트(web-service) 구조를 그대로 유지한다. `career/` 서브패키지 컨벤션(`entity/enums/domain/converter/mapper/provider/util/validator/caller`만 두고 `controller/service/repository`는 최상위 유지)에 맞춰 신규 파일을 배치한다.

```text
SSAju/src/main/java/ssafy/SSAju/
├── career/
│   ├── caller/
│   │   ├── ConsultationOpenAICaller.java          # [기존, 참고 패턴]
│   │   └── CompanyMatchingOpenAICaller.java       # [신규] 1-call JSON 모드, @Retryable/@Recover
│   ├── provider/
│   │   └── PromptProvider.java                    # [수정] 궁합 해설 프롬프트 메서드 추가
│   └── util/
│       ├── JobRoleAnalyzer.java                   # [수정] buildSynergyText/buildWarningText 제거(AI 응답 사용), matchScore 계산 로직은 유지
│       ├── RoleCompatibilityCalculator.java       # [수정] 자체 산식 제거, JobRoleAnalyzer.matchScore를 primary 점수로 재사용
│       └── AnalysisResponseBuilder.java           # [수정] 텍스트 생성 메서드 제거, AI 응답(CompatibilityNarrativeResponse) 필드를 그대로 조립
├── dto/
│   └── external/
│       └── CompatibilityNarrativeResponse.java    # [신규] AI 해설 응답 record(8개 텍스트 필드 대응)
└── service/
    └── CompanyMatchingService.java                # [수정] AI 캐러 호출 추가, restoreDailyUsage try/catch 범위를 AI 호출까지 확장

SSAju/src/test/java/ssafy/SSAju/
├── career/
│   ├── caller/CompanyMatchingOpenAICallerTest.java     # [신규]
│   └── util/
│       ├── JobRoleAnalyzerTest.java                    # [수정] 텍스트 생성 케이스 제거, matchScore 계산만 검증
│       └── RoleCompatibilityCalculatorTest.java        # [수정] matchScore 재사용 검증으로 갱신
└── service/CompanyMatchingServiceTest.java             # [수정] ChatClient mock 추가, AI 실패 시 쿼터 보상 케이스 추가
```

**Structure Decision**: 신규 파일은 모두 `career/` 하위(`caller`, `util`, `provider`) 또는 최상위 `service/`/`dto/external`에 배치해 `SSAju/CLAUDE.md`에 문서화된 기존 패키지 컨벤션(career 도메인 로직은 `career/`, career 서비스/컨트롤러는 최상위)을 그대로 따른다. 새 하위 디렉터리나 별도 모듈은 만들지 않는다.

## Complexity Tracking

*Constitution Check 위반 없음 — 해당 없음*
