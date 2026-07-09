# Contract: 커리어 도메인 쿼터/재시도/정본 (User Story 2, 3, 5, 6, 7)

## `POST /api/career/compatibility` (`CompanyMatchingService.analyzeCompatibility`)

**Before**: `dailyApiUsageService.checkAndIncrementDailyUsage(userId)` → FastAPI/OpenAI 등 외부 호출 순서. 외부 호출 실패 시 이미 차감된 쿼터 복구 없음.

**After**:
- 쿼터 차감은 분석이 **성공적으로 완료된 시점**(모든 외부 호출 + DB 저장 성공 후)으로 이동하거나, 기존 순서를 유지하되 외부 호출 실패 시 보상 트랜잭션(`dailyApiUsageService.restoreDailyUsage(userId)` 등)으로 복구.
- 응답 스키마 변경 없음. 실패 시 사용자에게 보이는 오류 메시지도 기존과 동일(내부 쿼터 정합성만 개선).
- 동시 요청에 대해 정본(`CompanyCompatibility`) 생성은 `lock:company-compatibility:{userProfileId}:{companyName}:{targetRoleCategory}` 분산락으로 보호 — 락 획득 실패(경합) 시에도 최종적으로는 하나의 결과가 만들어지고 모든 요청자가 그 결과를 받음(대기 후 재조회).

## `GET /api/companies/{name}/info` 계열 (`CompanyInfoService`)

**Before**: 5xx도 `PublicDataApiException`으로 변환되어 `@Retryable` 미작동.

**After**:
- 5xx: 원본 예외(`ResourceAccessException`/`HttpServerErrorException`) 그대로 전파 → `@Retryable`이 `SajuDataService`와 동일한 방식으로 재시도.
- 4xx: 기존과 동일하게 커스텀 예외로 변환(비재시도).
- 클라이언트 관점 응답 스키마 변경 없음 — 재시도가 성공하면 정상 응답, 재시도 소진 시 기존과 동일한 오류 응답.

## `POST /api/career/timing`, `/api/career/consultation` 등 (`SajuResultProvider`, `ConsultationSaveService`)

**Before**: `insertOrIgnore` + 재조회(정본), 제약조건 이름 기반 재조회+복구(상담).

**After**:
- `lock:saju-result:{userProfileId}`, `lock:career-consultation:{sajuResultId}:{yearMonth}` 분산락으로 "조회→생성(자식 엔티티 포함)" 전체 구간을 보호.
- 응답 스키마/필드 변경 없음. 동일 생년월일시 조합에 대한 반복 요청은 재계산 없이 기존 정본을 재사용(FR-014, 기존 동작 유지 — 구현 메커니즘만 변경).
- 정본 최초 생성 시 `UserSajuAccess` 매핑 행이 함께 생성되며, 이 생성 자체는 일일 쿼터에 영향을 주지 않음(FR-016 — `CareerFortuneService`에서 쿼터 차감 로직 완전 제거).

## 생년월일시(선택) 관련 응답 차이

- 가입 시 `birthTime`을 입력하지 않은 사용자가 분석을 요청하면: FastAPI 호출에는 내부적으로 더미 시간(`12:00`, `CompatibilityConstants`류 상수 재사용)을 채워 보내지만, 응답 스키마에서 다음 필드는 **응답에서 제외**(구체 목록은 구현 시 FastAPI/십신/시주 기반 파생 필드 전수 조사로 확정, 최소한 `hourPillar`/시주 기반 십신·지장간 세부값 포함):
  - 시주(時柱, hour pillar) 관련 필드
  - 시(時) 기반으로만 산출되는 십신/지장간 세부 항목
- `birthTime`을 입력한 사용자는 기존과 동일하게 전체 필드를 받는다.
