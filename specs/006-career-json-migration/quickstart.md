# Quickstart: 커리어 분석 결과 JSON 저장 마이그레이션 검증

이 문서는 구현 완료 후 기능이 end-to-end로 동작하는지 확인하는 절차다. 구현 코드 자체는 포함하지 않는다.

## 사전 준비

- 로컬 MySQL 8 기동 + `application-local.yaml` 설정 (`SSAju/CLAUDE.md` 참고)
- (통합 테스트용) Docker 실행 중 — Testcontainers MySQL 필요
- `SSAju/` 디렉터리에서 작업

## 1. 빌드 및 단위/통합 테스트

```bash
./gradlew clean build
```

기대 결과: `BUILD SUCCESSFUL`. 신규/변경된 컨버터 직접 직렬화/역직렬화 단위 테스트, MySQL JSON 컬럼 통합 테스트(Testcontainers), 기존 `validate()` 대상월 검사 및 matchScore 하한 클램프 회귀 테스트가 모두 통과해야 한다.

## 2. 스키마 수동 적용 (마이그레이션 스크립트 없음 — FR-009)

현재 엔티티와 실제 DB의 FK를 확인한 뒤 운영자가 로컬/개발 DB에 수동 적용한다. **이 문서는 적용 완료 기록이 아니다.** 이미 적용한 DB에서는 초기화를 반복하지 말고 스키마·데이터 상태만 확인한다.

MySQL InnoDB는 다른 테이블의 FK 제약이 남아 있으면 참조 행이 0건이거나 NULL이어도 부모 테이블의 TRUNCATE를 거부한다. 따라서 유지할 테이블의 데이터는 FK를 보존한 채 **DELETE**로 정리한다. [MySQL 8.0 TRUNCATE 문서](https://dev.mysql.com/doc/refman/8.0/en/truncate-table.html) 참고.

1. 분석 요청을 중지하고 대상 테이블의 스키마·FK와 행 수를 기록한다. 사용자/프로필과 피드백 내용은 보존 대상이다.
2. `user_satisfaction_feedback`의 `company_compatibility_id`/`career_consultation_id`만 NULL로 갱신한다. 행은 삭제하지 않는다.
3. 폐기할 정규화 손자 → 자식 테이블을 DROP한다([data-model](data-model.md) 목록과 실제 FK 대조). `industry_recommended_role` 같은 연결 테이블도 포함한다.
4. `career_consultation`, `company_compatibility`의 기존 결과 행을 DELETE한다. `saju_full_data`, `career_fortune`, `user_saju_access`의 결과 참조 행을 DELETE한 뒤 `saju_result`를 DELETE한다. 접근 매핑 테이블 자체와 users/user_profile 행은 유지한다.
5. 빈 루트 테이블에 현재 엔티티와 동일한 JSON 컬럼을 적용한다: `career_consultation.result_json`(NOT NULL), `company_compatibility.result_json`, `saju_result.ten_god_hidden_stem_analysis`. 컨설팅의 `day_master_description`/`five_elements_analysis`는 제거하지만 **궁합의 `summary`는 유지**한다. 식별 컬럼·유니크 제약은 보존한다.
6. 실패하면 실제 FK를 확인해 참조 해제/삭제 순서를 바로잡는다. `FOREIGN_KEY_CHECKS=0`으로 우회하지 않는다.
7. 새 분석 요청 전에 폐기 테이블 부재, 결과·접근 매핑 테이블 행 수 0, 피드백 행 수 보존과 두 FK의 NULL 상태를 확인한다. 이후 신규 요청으로 JSON 저장·재조회와 `user_saju_access` 재생성을 검증한다.

## 3. 시나리오별 검증

### 3.1 정상 케이스 — 세 도메인 모두 (User Story 1)

**3.1.1 기업 궁합 분석**

1. `POST /api/company/compatibility` 요청 (유효한 회사명/직군)
2. 응답이 정상적으로 반환되는지 확인
3. DB에서 `company_compatibility.result_json` 컬럼을 직접 조회 — AI 해설과 계산 결과를 합친 `CompatibilityAnalysisData`가 저장되는지 확인. `summary`/`compatibility_score`는 별도 루트 컬럼이며, 이 값과 JSON을 조합한 API 응답이 일치해야 한다
4. 같은 조건으로 재조회 시 재계산 없이 저장된 결과가 그대로 반환되는지 확인 (idempotency 스칼라 컬럼 `compatibility_month` 등으로 조회됨)

**3.1.2 커리어 컨설팅**

1. `POST /api/career/consultation` 요청 (유효한 사주 결과/대상월)
2. 응답이 정상적으로 반환되는지 확인
3. DB에서 `career_consultation.result_json` 컬럼을 직접 조회 — 검증을 통과한 AI 응답 전체가 JSON으로 저장되어 있는지, 응답 필드·값이 API 응답과 일치하는지 확인
4. 같은 사용자·같은 `consultation_month`로 재조회 시 재계산 없이 저장된 결과가 그대로 반환되는지 확인

**3.1.3 관운분석 (사주 결과의 십신/지장간)**

1. `POST /api/career/timing` 등 사주 결과를 최초 생성하는 요청을 보낸다
2. 응답이 정상적으로 반환되는지 확인
3. DB에서 `saju_result.ten_god_hidden_stem_analysis` 컬럼을 직접 조회 — `TenGodCalculator`/`HiddenStemCalculator` 계산 결과 전체가 JSON으로 저장되어 있는지, 응답 필드·값이 API 응답과 일치하는지 확인
4. 같은 사용자 프로필로 재조회 시 재계산 없이 저장된 결과가 그대로 반환되는지 확인

### 3.2 정합성 검사 — 실패 케이스 (User Story 2)

1. 테스트 더블/모킹으로 `monthlyAdvices`의 month 집합이 기대값과 다른 OpenAI 응답을 주입
2. 저장이 거부되고 오류가 반환되는지 확인
3. DB에 어떤 부분 데이터도 저장되지 않았는지 확인 (실패 요청이 신규 분석 결과를 남기지 않고 기존 결과를 변경하지 않아야 함)
4. `monthlyAdvices`에 범위를 벗어나거나(13 이상 등) 기대 대상월과 다른 month 값을 담은 응답을 주입 — 신규 range 검사 코드가 아니라 기존 "대상월 집합 일치" 검사만으로 이미 거부되는지 확인 (research.md #4 — `score`/`matchScore`는 OpenAI 응답 필드가 아니라 내부 계산값이라 이 시나리오의 대상이 아님)

### 3.3 동시 요청 — 중복 방지 (Edge Case / FR-007)

1. 동일 식별 키(같은 회사/직군/월)로 동시에 2개 요청 발송
2. 외부 FastAPI/OpenAI 중복 호출은 허용된다(현재 `004`의 저장 구간 분산락 정책). 최종적으로 정확히 1건의 `company_compatibility` 행만 생성되었는지 확인
3. 두 요청 모두 동일한 결과를 응답받는지 확인

### 3.4 기존 데이터 제거 확인 (User Story 3)

1. §2에서 DROP한 자식/손자 테이블(`industry`, `monthly_forecast`, `ten_god_data` 등)이 더 이상 존재하지 않는지 확인 (`SHOW TABLES`)
2. §2에서 정리한 테이블(`career_consultation`, `company_compatibility`, `saju_full_data`, `career_fortune`, `user_saju_access`, `saju_result`)의 행 수가 0건인지 확인
3. `user_satisfaction_feedback`의 행 수가 §2 1단계 사전 확인 값과 동일하고(삭제되지 않음), `company_compatibility_id`/`career_consultation_id`가 모두 `NULL`인지 확인
4. 신규 요청이 정상적으로 새 구조로 저장되는지 확인

## 4. 참고 문서

- 저장-전 검증 계약: [contracts/json-storage-and-validation-contract.md](contracts/json-storage-and-validation-contract.md)
- 엔티티/컬럼 변경 상세: [data-model.md](data-model.md)
- 설계 근거: [research.md](research.md)

**검증 상태 (2026-10-12)**: Testcontainers 테스트는 신규 스키마의 저장/조회 및 피드백 FK 해제 가능성을 검증한다. 실제 개발 DB 적용·재요청·수동 시나리오·clean build(T022/T024/T026/T027)는 사용자 요청에 따라 완료 처리했다. 이번 문서 수정에서 해당 항목을 새로 실행하거나 재검증하지 않았다.
