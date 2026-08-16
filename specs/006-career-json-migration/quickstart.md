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

기존 정규화 테이블을 TRUNCATE/DROP하고 신규 JSON 컬럼을 포함한 스키마로 재생성한다 (운영자가 수동 적용 — 이 문서/코드에 스크립트를 포함하지 않는다).

## 3. 시나리오별 검증

### 3.1 기업 궁합 분석 — 정상 케이스 (User Story 1)

1. `POST /api/career/compatibility` 요청 (유효한 회사명/직군)
2. 응답이 정상적으로 반환되는지 확인
3. DB에서 `company_compatibility.result_json` 컬럼을 직접 조회 — 검증을 통과한 AI 응답 전체가 JSON으로 저장되어 있는지 확인
4. 같은 조건으로 재조회 시 재계산 없이 저장된 결과가 그대로 반환되는지 확인 (idempotency 스칼라 컬럼 `compatibility_month` 등으로 조회됨)

### 3.2 정합성 검사 — 실패 케이스 (User Story 2)

1. 테스트 더블/모킹으로 `monthlyAdvices`의 month 집합이 기대값과 다른 OpenAI 응답을 주입
2. 저장이 거부되고 오류가 반환되는지 확인
3. DB에 어떤 부분 데이터도 저장되지 않았는지 확인 (`result_json` NULL 또는 행 자체 없음)
4. `month`가 13 이상이거나 `score`가 100 초과인 응답을 주입 — 마찬가지로 거부되는지 확인 (기존 엔티티 `@Min`/`@Max`가 담당하던 검증이 `validate()`로 이관되었는지 확인하는 핵심 시나리오)

### 3.3 동시 요청 — 중복 방지 (Edge Case / FR-007)

1. 동일 식별 키(같은 회사/직군/월)로 동시에 2개 요청 발송
2. 최종적으로 정확히 1건의 `company_compatibility` 행만 생성되었는지 확인
3. 두 요청 모두 동일한 결과를 응답받는지 확인

### 3.4 기존 데이터 제거 확인 (User Story 3)

1. 스키마 적용 전 존재하던 `career_consultation`, `company_compatibility`, `saju_result`(십신/지장간 자식 테이블 포함) 관련 기존 정규화 테이블 데이터가 적용 후 0건인지 확인
2. 신규 요청이 정상적으로 새 구조로 저장되는지 확인

## 4. 참고 문서

- 저장-전 검증 계약: [contracts/json-storage-and-validation-contract.md](contracts/json-storage-and-validation-contract.md)
- 엔티티/컬럼 변경 상세: [data-model.md](data-model.md)
- 설계 근거: [research.md](research.md)
