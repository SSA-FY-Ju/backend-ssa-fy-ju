---

description: "Task list for 커리어 분석 결과 JSON 저장 마이그레이션"
---

# Tasks: 커리어 분석 결과 JSON 저장 마이그레이션

**Input**: Design documents from `/specs/006-career-json-migration/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: 이 프로젝트는 `SSAju/CLAUDE.md`에 "Test-before-commit is mandatory"가 명시되어 있으므로, 각 스토리에 테스트 태스크를 포함한다.

**Organization**: 태스크는 spec.md의 User Story(P1/P1/P2)별로 그룹화되어 있다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 병렬 실행 가능 (다른 파일, 미완료 태스크에 대한 의존성 없음)
- **[Story]**: 해당 태스크가 속한 User Story (US1, US2, US3)
- 모든 설명에 정확한 파일 경로 포함

## Path Conventions

단일 프로젝트(Gradle 모듈 `SSAju/`). 모든 경로는 `SSAju/src/main/java/ssafy/SSAju/` 또는 `SSAju/src/test/java/ssafy/SSAju/` 기준.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 신규 JSON 컨버터들이 공유할 직렬화 설정 준비

- [ ] T001 `career/converter/CareerJsonObjectMapperSupport.java`에 신규 JSON 컨버터가 공유할 `tools.jackson` 기반 `ObjectMapper` 유틸(Instant 직렬화 포맷을 `config/JacksonConfig.java`와 동일하게 등록)을 생성 — 파일: `SSAju/src/main/java/ssafy/SSAju/career/converter/CareerJsonObjectMapperSupport.java` (research.md #2, #3)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 세 루트 엔티티 리팩터링 전에 반드시 지켜져야 할 식별/조회 컬럼 보존을 확인

**⚠️ CRITICAL**: 이 단계 완료 전에는 어떤 User Story 작업도 엔티티를 수정해서는 안 됨

- [ ] T002 `CareerConsultation.java`(`sajuResult`+`consultationMonth` 유니크 제약)와 `CompanyCompatibility.java`(`user`+`userProfile`+`companyName`+`targetRoleCategory`+`compatibilityMonth` 유니크 제약)의 현재 식별/조회 컬럼과 제약조건을 문서화하고, 이후 엔티티 리팩터링 태스크(T006-T008)에서 그대로 유지되어야 함을 체크리스트로 남김 — 파일: `SSAju/src/main/java/ssafy/SSAju/career/entity/CareerConsultation.java`, `SSAju/src/main/java/ssafy/SSAju/career/entity/CompanyCompatibility.java` (검토만, 코드 변경 없음)

**Checkpoint**: 식별 컬럼 목록이 확정되어야 이후 엔티티 수정 태스크가 실수로 유니크 제약을 깨뜨리지 않음

---

## Phase 3: User Story 1 - 분석 결과를 JSON 한 덩어리로 저장/조회 (Priority: P1) 🎯 MVP

**Goal**: 관운분석(`SajuResult`의 십신/지장간), 커리어 컨설팅, 기업 궁합 분석 결과를 정규화 자식 테이블 대신 JSON 컬럼 1개로 저장/조회

**Independent Test**: 세 도메인 각각 분석을 1회 요청하고 저장된 JSON을 직접 조회했을 때 기존과 동일한 필드·값이 표시되는지 확인 (quickstart.md §3.1)

### Implementation for User Story 1

- [ ] T003 [P] [US1] `TenGodHiddenStemConverter` (`AttributeConverter`)를 `SSAju/src/main/java/ssafy/SSAju/career/converter/TenGodHiddenStemConverter.java`에 추가 — `TenGodCalculator`/`HiddenStemCalculator` 계산 결과를 JSON 문자열로 직렬화 (T001 사용)
- [ ] T004 [P] [US1] `ConsultationResultConverter` (`AttributeConverter`)를 `SSAju/src/main/java/ssafy/SSAju/career/converter/ConsultationResultConverter.java`에 추가 — 검증 통과한 `CareerAdviceResponse`(+`dayMasterDescription`/`fiveElementsAnalysis`)를 JSON 문자열로 직렬화 (T001 사용)
- [ ] T005 [P] [US1] `CompatibilityResultConverter` (`AttributeConverter`)를 `SSAju/src/main/java/ssafy/SSAju/career/converter/CompatibilityResultConverter.java`에 추가 — 검증 통과한 `CompatibilityNarrativeResponse`(+`summary`)를 JSON 문자열로 직렬화 (T001 사용)
- [ ] T006 [US1] `SajuResult.java`에서 `tenGodDataList`/`hiddenStemDataList` `@OneToMany` 연관관계를 제거하고 `tenGodHiddenStemAnalysis`(JSON, `TenGodHiddenStemConverter` 적용) 컬럼 추가 — 파일: `SSAju/src/main/java/ssafy/SSAju/career/entity/SajuResult.java` (depends on T003)
- [ ] T007 [US1] `CareerConsultation.java`에서 `dayMasterDescription`/`fiveElementsAnalysis` 필드와 14개 직계 자식 연관관계를 제거하고 `resultJson`(JSON, `ConsultationResultConverter` 적용) 컬럼 추가 — 파일: `SSAju/src/main/java/ssafy/SSAju/career/entity/CareerConsultation.java` (depends on T002, T004)
- [ ] T008 [US1] `CompanyCompatibility.java`에서 `summary` 필드와 8개 직계 자식 연관관계를 제거하고 `resultJson`(JSON, `CompatibilityResultConverter` 적용) 컬럼 추가 — 파일: `SSAju/src/main/java/ssafy/SSAju/career/entity/CompanyCompatibility.java` (depends on T002, T005)
- [ ] T009 [P] [US1] `CareerConsultation`의 14개 직계 자식 + 7개 손자 엔티티 클래스(`Industry.java`, `InterviewTip.java`, `Strength.java`, `ConsultationCaution.java`, `ConsultationKeyTenGod.java`, `ConsultationWealthStyle.java`, `ConsultationRoadmap.java`, `ConsultationPersonalBranding.java`, `ConsultationPowerKeywords.java`, `ConsultationMentalCare.java`, `ConsultationEnvironmentFit.java`, `ConsultationWorkStyle.java`, `ConsultationRelationshipStrategy.java`, `ConsultationCareerTimeline.java`, `ConsultationMentalRechargeMethod.java`, `ConsultationMentalStressFactor.java`, `ConsultationPivotPoint.java`, `ConsultationWarningMonth.java`, `ConsultationPowerKeyword.java`, `ConsultationPowerKeywordUsageTip.java`, `ConsultationMonthFortune.java`)와 대응 Repository 인터페이스를 `SSAju/src/main/java/ssafy/SSAju/career/entity/`와 `SSAju/src/main/java/ssafy/SSAju/repository/`에서 삭제 (depends on T007)
- [ ] T010 [P] [US1] `CompanyCompatibility`의 8개 직계 자식 + 2개 손자 엔티티 클래스(`TargetRoleAnalysis.java`, `FiveElementsAnalysis.java`, `AnalysisBreakdown.java`, `ActionableStrategy.java`, `ExpectedInterviewQuestion.java`, `RoleCompatibility.java`, `MonthlyForecast.java`, `Caution.java`, `ActionableKeyword.java`, `LuckyDay.java`)와 대응 Repository 인터페이스를 `SSAju/src/main/java/ssafy/SSAju/career/entity/`와 `SSAju/src/main/java/ssafy/SSAju/repository/`에서 삭제 (depends on T008)
- [ ] T011 [P] [US1] `TenGodData.java`/`HiddenStemData.java` 엔티티 클래스와 대응 Repository 인터페이스를 `SSAju/src/main/java/ssafy/SSAju/career/entity/`와 `SSAju/src/main/java/ssafy/SSAju/repository/`에서 삭제 (depends on T006)
- [ ] T012 [US1] `CompatibilityChildSaveService.java`/`CompatibilityChildReadService.java`를 8개 자식 엔티티 저장/재조립 대신 `resultJson` 직렬화/역직렬화 방식으로 재작성 — 파일: `SSAju/src/main/java/ssafy/SSAju/service/CompatibilityChildSaveService.java`, `SSAju/src/main/java/ssafy/SSAju/service/CompatibilityChildReadService.java` (depends on T008)
- [ ] T013 [US1] `ConsultationSaveService.java`/`ConsultationInsertService.java`/`career/mapper/ConsultationMapper.java`를 14개 자식 엔티티 매핑 대신 `resultJson` 직렬화 방식으로 재작성 — 파일: `SSAju/src/main/java/ssafy/SSAju/service/ConsultationSaveService.java`, `SSAju/src/main/java/ssafy/SSAju/service/ConsultationInsertService.java`, `SSAju/src/main/java/ssafy/SSAju/career/mapper/ConsultationMapper.java` (depends on T007)
- [ ] T014 [US1] `SajuResultWriteService.java`/`career/provider/SajuResultProvider.java`/`career/mapper/SajuResultMapper.java`를 `TenGodData`/`HiddenStemData` 저장 대신 `tenGodHiddenStemAnalysis` 직렬화 방식으로 재작성 — 파일: `SSAju/src/main/java/ssafy/SSAju/service/SajuResultWriteService.java`, `SSAju/src/main/java/ssafy/SSAju/career/provider/SajuResultProvider.java`, `SSAju/src/main/java/ssafy/SSAju/career/mapper/SajuResultMapper.java` (depends on T006)
- [ ] T015 [P] [US1] T003-T005의 각 컨버터에 대해 직렬화→저장→재조회→역직렬화 라운드트립을 검증하는 H2 단위 테스트 추가 — 파일: `SSAju/src/test/java/ssafy/SSAju/career/converter/TenGodHiddenStemConverterTest.java`, `ConsultationResultConverterTest.java`, `CompatibilityResultConverterTest.java` (depends on T003, T004, T005)
- [ ] T016 [P] [US1] MySQL `json` 컬럼 타입의 저장/조회 동작을 검증하는 Testcontainers 통합 테스트 추가 — 파일: `SSAju/src/test/java/ssafy/SSAju/integration/CareerResultJsonStorageIntegrationTest.java` (depends on T006, T007, T008)
- [ ] T017 [US1] 엔티티/서비스 재작성으로 깨지는 기존 테스트(`CompanyMatchingServiceTest`, `CompanyInfoServiceTest` 계열, 컨설팅 저장 관련 테스트, `CareerFortuneServiceTest` 등)를 새 JSON 기반 저장/조회 경로에 맞게 갱신 — 파일: `SSAju/src/test/java/ssafy/SSAju/service/CompanyMatchingServiceTest.java` 등 관련 테스트 전체 (depends on T012, T013, T014)

**Checkpoint**: User Story 1 독립 검증 가능 — quickstart.md §3.1, §3.4 실행 가능

---

## Phase 4: User Story 2 - AI 응답 정합성 검사를 저장 방식과 무관하게 유지 (Priority: P1)

**Goal**: 저장 형식이 JSON으로 바뀌어도 OpenAI 응답 검증 시점(파싱 직후, 직렬화 이전)과 항목이 기존과 동일하게 유지됨

**Independent Test**: 필수 필드 누락, 기대 대상월 불일치 응답을 주입했을 때 저장이 거부되는지 확인 (quickstart.md §3.2)

> **범위 정정 (코드 리뷰 반영)**: 최초 계획은 `MonthlyForecast.score`/`TargetRoleAnalysis.matchScore`의 `@Min`/`@Max`를 `validate()`로 이관하는 것이었으나, 실제 코드 확인 결과 이 두 값은 `CompanyMatchingOpenAICaller.validate()`가 받는 `CompatibilityNarrativeResponse` DTO에 필드로 존재하지 않는다(OpenAI 응답이 아니라 `JobRoleAnalyzer`/`AnalysisResponseBuilder`의 내부 계산값이며, 공식 자체가 0~100으로 자체 유계). month 범위도 기존 대상월 집합 일치 검사가 이미 포괄한다. 따라서 이 스토리는 **신규 검증 코드 추가가 아니라 기존 검사가 이미 요구사항을 충족함을 확인하는 회귀 테스트 위주**로 축소되었다 — research.md #4 참고.

### Implementation for User Story 2

- [ ] T018 [P] [US2] `CompanyMatchingOpenAICaller.validate()` 메서드 바로 위에, month 범위는 대상월 집합 일치 검사가 이미 포괄하고 `score`/`matchScore`는 이 DTO의 필드가 아니라 내부 계산값이라 이 메서드의 검증 대상이 아니라는 설계 근거를 코드 주석으로 남김 — 파일: `SSAju/src/main/java/ssafy/SSAju/career/caller/CompanyMatchingOpenAICaller.java` (contracts/json-storage-and-validation-contract.md 참조, 코드 로직 변경 없음)
- [ ] T019 [P] [US2] `ConsultationOpenAICaller.validate()`의 기존 null/blank/필수 컬렉션 검사가 엔티티 제거 후에도 변경 없이 유지되는지 회귀 확인 및 필요 시 보강 — 파일: `SSAju/src/main/java/ssafy/SSAju/career/caller/ConsultationOpenAICaller.java`
- [ ] T020 [P] [US2] 범위를 벗어나거나(13 이상 등) 기대 대상월과 다른 month 값을 담은 `monthlyAdvices` 응답이 기존 "대상월 집합 일치" 검사만으로 이미 거부됨을 확인하는 회귀 테스트 추가(신규 검증 코드 없음) — 파일: `SSAju/src/test/java/ssafy/SSAju/career/caller/CompanyMatchingOpenAICallerTest.java`
- [ ] T021 [P] [US2] 검증 실패 시 어떤 데이터도 `resultJson`에 부분 저장되지 않음을 확인하는 회귀 테스트 추가 — 파일: `SSAju/src/test/java/ssafy/SSAju/career/caller/ConsultationOpenAICallerTest.java`, `CompanyMatchingOpenAICallerTest.java` (depends on T012, T013)

**Checkpoint**: User Story 2 독립 검증 가능 — quickstart.md §3.2 실행 가능

---

## Phase 5: User Story 3 - 기존 사용자 분석 결과 정리 (Priority: P2)

**Goal**: 실사용자 데이터가 없는 개발 단계 특성을 활용해 기존 정규화 데이터를 제거하고 새 JSON 구조로 재시작

**Independent Test**: 새 구조 적용 후 기존 결과 테이블에 데이터가 남아있지 않고, 신규 분석 요청이 정상적으로 새 구조로 저장되는지 확인 (quickstart.md §3.4)

### Implementation for User Story 3

- [ ] T022 [US3] 로컬/개발 DB에 신규 스키마를 수동 적용: (a) 자식/손자 테이블(`industry`, `monthly_forecast`, `ten_god_data`, `hidden_stem_data` 등 data-model.md 전체 목록)은 **DROP TABLE**, (b) 루트 테이블(`career_consultation`, `company_compatibility`, `saju_result`)은 **ALTER TABLE**로 `result_json`/`ten_god_hidden_stem_analysis` 컬럼 추가 후 **TRUNCATE** — 절차: `specs/006-career-json-migration/quickstart.md` §2, 버전관리에 스크립트 커밋하지 않음(FR-009) (depends on T009, T010, T011)
- [ ] T023 [P] [US3] 마이그레이션 후 (a) DROP된 자식/손자 테이블이 `SHOW TABLES`에서 더 이상 조회되지 않는지, (b) TRUNCATE된 루트 테이블(`career_consultation`, `company_compatibility`, `saju_result`)의 행 수가 0건인지, (c) 삭제된 엔티티에 대응하는 Repository 빈이 더 이상 존재하지 않는지 확인하는 통합 테스트 추가 — 파일: `SSAju/src/test/java/ssafy/SSAju/integration/CareerResultLegacyDataCleanupIntegrationTest.java` (depends on T022)
- [ ] T024 [US3] 동일 식별 키(예: 같은 사용자+대상월)로 재요청 시 마이그레이션 이전 데이터를 재사용하지 않고 새로 생성된 JSON 결과가 반환되는지 수동/통합 검증 — quickstart.md §3.4 절차 실행. 정리 대상은 T022에서 DROP된 자식/손자 테이블의 구버전 행(테이블째 사라짐)과 TRUNCATE된 루트 테이블(`career_consultation`/`company_compatibility`/`saju_result`)의 구버전 행이며, 보존되는 것은 ALTER된 루트 테이블 자체(스키마만 바뀐 채 존재)다 — 신규 요청은 이 보존된 루트 테이블에 새 스키마로 다시 INSERT된다 (depends on T022)

**Checkpoint**: User Story 3 독립 검증 가능

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 세 User Story에 걸쳐 영향을 주는 마무리 작업

- [ ] T025 [P] `SSAju/CLAUDE.md`의 "Data normalization pattern" 섹션을 이번 세 도메인 JSON 저장 예외를 반영해 갱신 — 파일: `SSAju/CLAUDE.md` (plan.md Complexity Tracking 참조)
- [ ] T026 quickstart.md의 검증 시나리오(§3.1~§3.4)를 로컬 환경에서 전체 실행하고 결과 기록
- [ ] T027 `./gradlew clean build` 실행 후 `BUILD SUCCESSFUL` 및 신규/변경 테스트 전체 통과 확인 (`SSAju/CLAUDE.md` "Test-before-commit is mandatory" 준수)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 의존성 없음 — 즉시 시작 가능
- **Foundational (Phase 2)**: Setup 완료 후 — User Story 1의 엔티티 수정을 블록
- **User Story 1 (Phase 3)**: Foundational 완료 후 시작
- **User Story 2 (Phase 4)**: Foundational 완료 후 시작 가능 (T018-T020은 US1과 독립적인 caller 파일만 다룸); T021만 US1의 T012/T013 완료 필요
- **User Story 3 (Phase 5)**: User Story 1의 엔티티/리포지토리 삭제(T009-T011) 완료 후 시작 — 새 스키마가 존재해야 TRUNCATE/검증이 의미 있음
- **Polish (Phase 6)**: 원하는 모든 User Story 완료 후

### User Story Dependencies

- **User Story 1 (P1)**: Foundational 이후 독립 시작 가능 — 다른 스토리에 대한 의존성 없음
- **User Story 2 (P1)**: Foundational 이후 독립 시작 가능 (caller 파일은 US1과 파일이 겹치지 않음); 완전한 회귀 검증(T021)만 US1 완료를 기다림
- **User Story 3 (P2)**: User Story 1 완료(신규 스키마 존재) 후 시작

### Within Each User Story

- 컨버터(T003-T005) → 엔티티(T006-T008) → 삭제(T009-T011)/서비스 재작성(T012-T014) → 테스트(T015-T017) 순
- Story 완료 후 다음 우선순위로 이동

### Parallel Opportunities

- T003, T004, T005 (세 컨버터)는 서로 다른 파일이므로 병렬 가능
- T009, T010, T011 (세 도메인의 삭제 작업)은 서로 다른 파일 집합이므로 병렬 가능
- T018, T019 (US2의 두 caller 파일)는 병렬 가능
- User Story 1과 User Story 2는 Foundational 완료 후 서로 다른 개발자가 병렬 진행 가능 (T021 제외)

---

## Parallel Example: User Story 1

```bash
# 세 컨버터를 동시에:
Task: "TenGodHiddenStemConverter 추가 in SSAju/src/main/java/ssafy/SSAju/career/converter/TenGodHiddenStemConverter.java"
Task: "ConsultationResultConverter 추가 in SSAju/src/main/java/ssafy/SSAju/career/converter/ConsultationResultConverter.java"
Task: "CompatibilityResultConverter 추가 in SSAju/src/main/java/ssafy/SSAju/career/converter/CompatibilityResultConverter.java"

# 세 도메인의 자식 엔티티 삭제를 동시에:
Task: "CareerConsultation 자식/손자 엔티티 21개 삭제"
Task: "CompanyCompatibility 자식/손자 엔티티 10개 삭제"
Task: "TenGodData/HiddenStemData 삭제"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup 완료
2. Phase 2 Foundational 완료 (식별 컬럼 확인 — CRITICAL)
3. Phase 3 User Story 1 완료
4. **STOP and VALIDATE**: quickstart.md §3.1/§3.4로 User Story 1 독립 검증
5. 준비되면 배포/데모

### Incremental Delivery

1. Setup + Foundational 완료 → 기반 준비
2. User Story 1 추가 → 독립 검증 → 배포/데모 (MVP)
3. User Story 2 추가 → 독립 검증 → 배포/데모
4. User Story 3 추가 → 독립 검증 → 배포/데모
5. 각 스토리는 이전 스토리를 깨지 않고 가치를 더함

### Parallel Team Strategy

1. Setup + Foundational을 함께 완료
2. Foundational 완료 후:
   - 개발자 A: User Story 1 (엔티티/컨버터/서비스)
   - 개발자 B: User Story 2 (caller validate() 회귀 확인/주석화, T021 제외 전부 US1과 독립)
3. User Story 1 완료 후 개발자 A 또는 B가 User Story 3 착수

---

## Notes

- [P] 태스크 = 서로 다른 파일, 의존성 없음
- [Story] 라벨은 태스크를 특정 User Story에 매핑하기 위함
- 각 User Story는 독립적으로 완료·검증 가능해야 함
- 커밋 전 반드시 `./gradlew build`로 `BUILD SUCCESSFUL` 확인 (`SSAju/CLAUDE.md` 규칙)
- 각 태스크 또는 논리적 묶음 완료 후 커밋
- 체크포인트에서 멈춰 스토리를 독립적으로 검증할 것
- 피할 것: 모호한 태스크, 동일 파일 충돌, 스토리 간 독립성을 깨는 교차 의존성
