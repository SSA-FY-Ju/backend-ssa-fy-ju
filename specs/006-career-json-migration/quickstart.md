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

기대 결과: `BUILD SUCCESSFUL`. 신규/변경된 컨버터 라운드트립 테스트(H2), MySQL JSON 컬럼 통합 테스트(Testcontainers), `validate()` range 검사 추가 테스트가 모두 통과해야 한다.

## 2. 스키마 수동 적용 (마이그레이션 스크립트 없음 — FR-009)

research.md #5에서 확정한 절차에 따라 적용한다 (운영자가 로컬/개발 DB에서 수동 적용 — 이 문서/코드에 스크립트를 포함하지 않는다):

1. **사전 확인**: 대상 테이블(자식 24개 + 손자 다수, 루트 3개)의 현재 행 수를 `SELECT COUNT(*)`로 기록해둔다. 별도 백업은 하지 않는다(운영 데이터 아님, 개발 단계 — FR-006 전제).
2. **자식/손자 테이블 DROP**: `industry`, `interview_tip`, `monthly_forecast`, `target_role_analysis`, `ten_god_data`, `hidden_stem_data` 등(data-model.md 전체 목록) — 자식→손자 순, FK로 참조하는 테이블부터 먼저 DROP.
3. **루트 테이블 ALTER + TRUNCATE**: `career_consultation`, `company_compatibility`, `saju_result`에 `result_json`/`ten_god_hidden_stem_analysis` JSON 컬럼을 추가하고 구 컬럼(`summary`, `day_master_description` 등)을 제거한 뒤, TRUNCATE로 구버전 형식의 기존 행을 비운다.
4. **실패 시**: FK 제약으로 DROP이 거부되면 해당 테이블을 참조하는 다른 테이블을 먼저 처리하도록 순서를 재조정한다. `SET FOREIGN_KEY_CHECKS=0`으로 강제 우회하지 않는다.
5. **검증**: DROP된 각 테이블에 대해 `SHOW TABLES LIKE '...'` 결과가 비어있는지, TRUNCATE된 각 루트 테이블은 `SELECT COUNT(*)`가 0인지 확인한다.

## 3. 시나리오별 검증

### 3.1 정상 케이스 — 세 도메인 모두 (User Story 1)

**3.1.1 기업 궁합 분석**

1. `POST /api/career/compatibility` 요청 (유효한 회사명/직군)
2. 응답이 정상적으로 반환되는지 확인
3. DB에서 `company_compatibility.result_json` 컬럼을 직접 조회 — 검증을 통과한 AI 응답 전체가 JSON으로 저장되어 있는지, 응답 필드·값이 API 응답과 일치하는지 확인
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
3. DB에 어떤 부분 데이터도 저장되지 않았는지 확인 (`result_json` NULL 또는 행 자체 없음)
4. `monthlyAdvices`에 범위를 벗어나거나(13 이상 등) 기대 대상월과 다른 month 값을 담은 응답을 주입 — 신규 range 검사 코드가 아니라 기존 "대상월 집합 일치" 검사만으로 이미 거부되는지 확인 (research.md #4 — `score`/`matchScore`는 OpenAI 응답 필드가 아니라 내부 계산값이라 이 시나리오의 대상이 아님)

### 3.3 동시 요청 — 중복 방지 (Edge Case / FR-007)

1. 동일 식별 키(같은 회사/직군/월)로 동시에 2개 요청 발송
2. 최종적으로 정확히 1건의 `company_compatibility` 행만 생성되었는지 확인
3. 두 요청 모두 동일한 결과를 응답받는지 확인

### 3.4 기존 데이터 제거 확인 (User Story 3)

1. §2에서 DROP한 자식/손자 테이블(`industry`, `monthly_forecast`, `ten_god_data` 등)이 더 이상 존재하지 않는지 확인 (`SHOW TABLES`)
2. §2에서 ALTER+TRUNCATE한 루트 테이블(`career_consultation`, `company_compatibility`, `saju_result`)의 행 수가 0건인지 확인
3. 신규 요청이 정상적으로 새 구조로 저장되는지 확인

## 4. 참고 문서

- 저장-전 검증 계약: [contracts/json-storage-and-validation-contract.md](contracts/json-storage-and-validation-contract.md)
- 엔티티/컬럼 변경 상세: [data-model.md](data-model.md)
- 설계 근거: [research.md](research.md)
