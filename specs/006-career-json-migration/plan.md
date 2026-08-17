# Implementation Plan: 커리어 분석 결과 JSON 저장 마이그레이션

**Branch**: `006-career-json-migration` | **Date**: 2026-08-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-career-json-migration/spec.md`

## Summary

관운분석(`SajuResult`에 딸린 `TenGodData`/`HiddenStemData`), 커리어 컨설팅(`CareerConsultation`), 기업 궁합 분석(`CompanyCompatibility`) — 세 루트 엔티티에 딸린 정규화 자식 엔티티(각각 2/14/8개 직계, 다수의 손자 엔티티 포함)를 제거하고, AI/내부 분석 결과를 루트 엔티티당 JSON 컬럼 하나로 직렬화해 저장한다. (`CareerFortune` 자체는 이미 스칼라 컬럼뿐이라 변경 대상이 아니다.) 식별/조회에 쓰이는 스칼라 컬럼(예: `compatibilityMonth`, `consultationMonth`, 유니크 제약 필드)은 그대로 유지한다. 정합성 검사는 기존과 동일하게 AI 응답 DTO가 JSON으로 직렬화되기 *전* 단계(`ConsultationOpenAICaller.validate()` / `CompanyMatchingOpenAICaller.validate()`)에서 수행한다. 월(month) 범위는 기존 대상월 일치 검사가 이미 포괄하고, 점수(`MonthlyForecast.score`/`TargetRoleAnalysis.matchScore`)는 AI 응답 필드가 아니라 내부 결정론적 계산값이라 별도 caller 검증 코드 추가는 필요하지 않다 — 다만 그 계산(`JobRoleAnalyzer.calculateMatchScore()`)의 하한 클램프가 코드에 강제되어 있지 않음이 리뷰로 드러나 방어적 클램프를 추가한다(자세한 근거는 research.md #4 참고). 기존 저장 데이터는 개발 단계이므로 마이그레이션 스크립트 없이, 자식 테이블은 DROP, 컬럼 구조가 바뀌는 루트 테이블(`career_consultation`/`company_compatibility`/`saju_result`)은 스키마 변경 후 TRUNCATE로 제거한다. `saju_result`는 이를 FK로 참조하는 `saju_full_data`/`career_fortune`(구조 변경 없음)도 함께 TRUNCATE해 사주 결과 전체를 리셋하고, `user_satisfaction_feedback`은 행을 지우지 않고 `company_compatibility_id`/`career_consultation_id` FK만 NULL로 해제해 피드백 내용을 보존한다 — FK 그래프에 따른 절차는 research.md #5 참고.

## Technical Context

**Language/Version**: Java 21 (Gradle toolchain)

**Primary Dependencies**: Spring Boot 4.0.5, Spring Data JPA (Hibernate), Spring AI `2.0.0-M4` (`ChatClient`, OpenAI), Jackson 3.x (`tools.jackson`, 프로젝트 전역 `com.fasterxml.jackson` 아님)

**Storage**: MySQL 8 (운영/로컬), H2(단위 테스트), Testcontainers MySQL(통합 테스트) — 신규 JSON 컬럼은 기존 `ObjectMapConverter`(`career/converter/ObjectMapConverter.java`) 패턴을 따르는 `AttributeConverter<T, String>`으로 구현, `columnDefinition = "json"` 여부는 Phase 0 research에서 결정

**Testing**: JUnit 5 + `spring-boot-starter-test`, Testcontainers(MySQL 특화 통합 테스트, 예: JSON 컬럼 round-trip), H2(순수 단위 테스트)

**Target Platform**: 백엔드 REST API 서버 (Linux 컨테이너 배포, 로컬 `bootRun`)

**Project Type**: web-service (단일 Spring Boot 모듈, `SSAju/`)

**Performance Goals**: 기존 API 응답시간/처리량 목표 변경 없음 (저장 구조 변경이지 API 계약 변경이 아님)

**Constraints**: 응답 스키마(컨트롤러가 반환하는 JSON) 변경 없음; AI 응답 검증 시점/항목은 저장 전 DTO 단계로 통일되어야 함; DB 마이그레이션 스크립트는 버전관리에 커밋하지 않음(FR-009)

**Scale/Scope**: 3개 루트 엔티티(`SajuResult`/`CareerConsultation`/`CompanyCompatibility`, `CareerFortune`은 변경 없음), 직계 자식 엔티티 24개(SajuResult 2 + CareerConsultation 14 + CompanyCompatibility 8) + 손자 엔티티 다수(총 목록은 data-model.md 참조) 제거 대상

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md`는 템플릿 상태([PRINCIPLE_1_NAME] 등 placeholder만 존재)로, 이 저장소에는 formal한 constitution이 채택되어 있지 않다. 대신 `SSAju/CLAUDE.md` "Data normalization pattern" 섹션이 사실상의 아키텍처 규칙 역할을 한다:

> "여러 career-domain 결과는 정규화된 자식 엔티티로 저장되며... 새 파생-분석 결과를 추가할 때는 JSON blob보다 정규화된 자식 엔티티 + repository를 선호한다."

**이번 기능은 이 문서화된 관례에서 의도적으로 벗어난다.** Complexity Tracking에 근거를 기록한다(아래). 다른 gate 위반은 없음 — 계층 구조(`Controller → Service → Repository → DB`), `@Transactional` 외부 API 호출 금지, 엔티티 컨벤션(Lombok/equals-hashCode/LAZY fetch) 등은 이번 변경에서 그대로 준수된다.

**Post-Phase-1 재확인**: data-model.md/contracts/quickstart 작성 완료 — 정규화 관례 이탈은 Complexity Tracking에 근거가 기록되어 있고, 다른 신규 gate 위반은 발견되지 않음. 통과.

## Project Structure

### Documentation (this feature)

```text
specs/006-career-json-migration/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── contracts/            # Phase 1 output (저장 JSON 스키마 계약)
├── quickstart.md         # Phase 1 output
└── tasks.md              # Phase 2 output (/speckit-tasks, 이 커맨드 범위 아님)
```

### Source Code (repository root)

기존 구조를 그대로 따르는 단일 Spring Boot 모듈(web-service, Option 2/3 해당 없음). 신규/변경 대상만 표기:

```text
SSAju/src/main/java/ssafy/SSAju/
├── career/
│   ├── entity/
│   │   ├── SajuResult.java                 # 변경: tenGodDataList/hiddenStemDataList 연관관계 제거, tenGodHiddenStemAnalysis JSON 컬럼 추가
│   │   ├── CareerFortune.java              # 변경 없음 (이미 스칼라 컬럼뿐, 자식 엔티티 없음)
│   │   ├── CareerConsultation.java         # 변경: 14개 직계 자식 연관관계 제거, JSON 컬럼 추가
│   │   ├── CompanyCompatibility.java       # 변경: 8개 직계 자식 연관관계 제거, JSON 컬럼 추가
│   │   └── (Industry.java, InterviewTip.java, TenGodData.java, HiddenStemData.java, ... 24개 직계 + 손자 자식 엔티티)  # 삭제 대상
│   ├── converter/
│   │   ├── ObjectMapConverter.java         # 기존 패턴 참고
│   │   └── (신규 JSON 컬럼용 AttributeConverter 추가 — Phase 0에서 설계)
│   ├── caller/
│   │   ├── ConsultationOpenAICaller.java   # 변경 없음(회귀 확인만) — range 검사 이관 없음, research.md #4 참고
│   │   └── CompanyMatchingOpenAICaller.java # 변경 없음(설계 근거 주석만 추가) — score/matchScore는 이 DTO에 없는 필드라 이관 불가, research.md #4 참고
│   └── mapper/ConsultationMapper.java      # 변경: 엔티티 매핑 → JSON 직렬화로 교체
├── service/
│   ├── CompatibilityChildSaveService.java  # 삭제 또는 단순 JSON 저장으로 교체
│   ├── CompatibilityChildReadService.java  # 삭제 또는 단순 JSON 역직렬화로 교체
│   ├── ConsultationSaveService.java        # 변경
│   └── ConsultationInsertService.java      # 변경
└── repository/
    ├── CareerFortuneRepository.java        # 영향 적음 (자식 리포지토리 없음)
    ├── CareerConsultationRepository.java    # 영향 적음
    ├── CompanyCompatibilityRepository.java  # 영향 적음
    └── (Repository가 있는 자식/손자 엔티티 15개 전용 인터페이스만 삭제 대상 — 20개는 Repository 자체가 없어 엔티티 클래스만 삭제, data-model.md 참조)

SSAju/src/test/java/ssafy/SSAju/
├── career/caller/  # 기존 validate() 테스트 + JobRoleAnalyzer 하한 클램프 회귀 테스트 추가(T019a)
└── service/        # 저장/조회 서비스 재작성에 따른 테스트 갱신
```

**Structure Decision**: 기존 `career/`(도메인 로직) + top-level `service/repository`(영속성) 분리 구조를 그대로 유지한다. 새 하위 패키지를 만들지 않고, 기존 파일을 수정/삭제하는 것으로 충분하다 (Option 1/단일 프로젝트).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `SSAju/CLAUDE.md` "Data normalization pattern" 관례 위반 (JSON blob 대신 정규화 자식 엔티티를 선호하는 기존 문서화된 규칙과 반대 방향) | 저장소 전수 grep 결과, `compatibility_score`/`completed`/`confidence_score` 등 어떤 스칼라 결과 필드도 idempotency 조회 외 쿼리/필터/분석에 쓰이지 않음 — 정규화가 제공하는 "쿼리/인덱스 가능성"이라는 이점이 이 세 도메인에는 실질적으로 활용되지 않고 있음. 커리어 컨설팅은 정적 계산에서 AI 생성 콘텐츠로 전환 중이라 고정 스키마보다 유연한 구조가 필요 | 정규화 유지 시: (1) AI 응답 필드가 추가/변경될 때마다 자식 엔티티+마이그레이션+매퍼 코드를 계속 늘려야 함, (2) `REQUIRES_NEW` 다중 트랜잭션 저장 구조가 예정된 분산락 임계구역을 불필요하게 키움, (3) 실질적으로 조회조차 안 되는 필드를 위해 22개 테이블+리포지토리를 유지하는 비용이 이점보다 큼 |

구현 완료 후 `SSAju/CLAUDE.md` "Data normalization pattern" 섹션을 이번 예외를 반영해 갱신해야 한다(별도 문서화 작업, tasks.md에 포함 권장).
