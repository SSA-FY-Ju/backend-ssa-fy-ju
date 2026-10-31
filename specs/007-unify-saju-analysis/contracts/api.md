# API 계약: 초기화 이후 신규 분석만 제공

**수정일**: 2026-10-31 | **명세**: [spec.md](../spec.md)

## 1. 식별자·인증·초기화 영향

분석 ID는 신규 `saju.id`의 BIGINT 자동 증가 값이며 Java Long·JSON 숫자로 유지한다. 새 테이블에서 처음부터 발급하고 과거 최댓값 조회·시작 번호 보정·예전 URL의 영구 404 보장은 제공하지 않는다. 과거 결과 매핑·숫자 범위 해석·전환 검증을 위한 런타임 컴포넌트는 두지 않는다.

생성 응답의 기존 필드명 sajuResultId/consultationId/compatibilityId와 목록·상세·피드백의 analysisId는 모두 같은 숫자 ID를 전달한다. 백엔드 API 문서에 전체 분석 ID 필드의 기존 숫자 타입 유지와 피드백 요청 feedbackType 제거를 명시한다. 클라이언트 수정·동시 배포 조율은 이번 범위에 포함하지 않고 확정된 변경 사항을 추후 전달한다. 경로 ID는 기존 Long 바인딩을 유지하고 과거 ID로 되돌리는 조회는 하지 않는다.

상세·피드백은 전달된 ID를 새 분석 식별자로만 취급하고 직접 소유자를 확인한다. 상세는 요청 유형도 대조한다. 없는 숫자 ID, 타인 ID, 상세 요청의 유형 불일치와 관운 대상 피드백은 결과 미존재 404다. 피드백의 허용 유형은 요청값 대신 연결된 saju.analysis_type으로 판정한다. 일반 요청 형식·필수값 오류는 기존 400 정책을 유지한다. 숫자가 아닌 분석 ID의 바인딩 오류는 기존 요청 형식 오류인 400으로 처리한다.

일반 API의 Bearer JWT와 관리자 쿠키 JWT·권한 체인은 유지한다. 소유자는 인증 사용자로 결정하며 관리자 상세는 대상 userId까지 확인한다.

초기화 후 기존 사용자는 로그인할 수 있지만 분석 이력·피드백은 비어 있다. 과거 분석은 조회하지 않으며 예전 URL의 영구 404는 보장하지 않는다. 과거 관리자 분석·기둥 문자열 보존은 제공하지 않는다.

## 2. 생성

| 경로 | 요청 DTO | 응답 ID | 쿼터 |
|---|---|---|---|
| POST /api/career/timing | CareerTimingRequest | sajuResultId: 숫자 | 없음 |
| POST /api/career/consultation | ConsultationRequest | consultationId: 숫자 | 공유 1회 |
| POST /api/company/compatibility | CompatibilityRequest | compatibilityId: 숫자 | 공유 1회 |

기존 경로, HTTP 200, ApiResponse 형식, 유형별 입력·검증·결과 내용은 유지한다. ID 값의 자료형 변경은 위 계약을 따른다. 같은 입력도 요청마다 실제 분석과 별도 ID를 생성한다. 프로필·만세력만 공유하며 실제 분석 프로필은 saju → manse → user_profile로 조회한다. 컨설팅·궁합 계산 때문에 SAJU 행을 별도 생성하지 않는다.

외부 호출·검증·저장 실패는 성공 결과를 만들지 않는다. 저장 후 응답 유실의 재요청은 한도 내에서 추가 분석·차감이다.

### 기본 출생정보 조회·저장

분석 전 클라이언트가 기본 프로필 또는 다른 출생정보를 선택하도록 다음 인증 API를 제공한다. 일반 API의 ApiResponse 형식을 사용한다.

| 경로 | 요청 | 응답 |
|---|---|---|
| GET /api/users/me/default-profile | 없음 | HTTP 200, data는 미설정이면 null, 설정됐으면 birthDate/birthTime |
| PUT /api/users/me/default-profile | birthDate, birthTime | HTTP 200, 저장된 birthDate/birthTime |

저장 입력은 기존 출생정보 허용 범위와 필수 시각 검증을 따른다. 사용자 ID나 프로필 ID를 요청받지 않고 인증 사용자에게 해당 출생정보의 공유 프로필 참조를 저장한다. 출생정보를 바꿀 때 공유 행을 수정하지 않고 기존 프로필을 조회하거나 새로 생성한 뒤 사용자 FK만 교체한다. 기존 계정의 초기 기본값도 null이다. 조회·저장은 FastAPI/OpenAI 호출·만세력 생성·분석 저장·차감이 모두 0이다.

세 분석 생성 API의 출생정보 필드와 필수값·정규화 검증은 유지한다. 클라이언트는 기본값을 선택하면 조회한 출생정보를, 다른 정보를 선택하면 입력한 출생정보를 기존 DTO 필드로 전달한다. 궁합은 userBirthDate/userBirthTime을 사용한다. 서버는 누락된 분석 입력을 기본 프로필로 암묵 보충하지 않는다. 요청에 확정된 입력을 사용하므로 이후 기본값 변경은 진행 중 분석에 영향을 주지 않는다. 분석 요청은 기본값을 자동 저장·변경하지 않는다. 선택 화면 구현은 이번 범위 밖이다.

## 3. 마이페이지

GET /api/mypage의 기존 type/page/size와 응답 구조를 유지한다. 기본 page=0,size=10, 최근 1년 조건을 유지하며 소유자의 새 분석만 조회한다. 목록 analysisId는 숫자이다. 정렬은 created_at DESC,id DESC이고 count·목록은 같은 소유자·유형·기간 조건이다. 초기화 직후에는 빈 목록과 0건을 반환한다.

GET /api/mypage/analyses/{analysisId}?type=... 상세도 동일 숫자 ID·유형을 검증한다. 개인·관리자 목록과 상세의 createdAt은 모두 `saju.created_at`이다. 상세는 저장한 result_json 스냅샷만 반환하며 FastAPI/OpenAI/회사 조회·차감·새 분석 INSERT가 모두 0이다.

## 4. 피드백

POST /api/feedback/satisfaction의 요청 필드는 analysisId(숫자), satisfactionStatus, feedbackContent다. satisfactionStatus와 feedbackContent의 기존 검증은 유지한다. 작성자가 결과 소유자인지 확인하고 연결된 saju.analysis_type이 컨설팅·궁합인지 검사한다. SAJU 대상은 HTTP 404로 거부한다. 요청 DTO의 feedbackType과 피드백 테이블의 feedback_type은 제거한다. 클라이언트도 분석 유형을 피드백 요청에 전달하지 않는다.

새 피드백의 user_id는 NOT NULL이며 인증 주체에서 주입하고 결과 소유자와 같아야 한다. 대상 FK는 saju_id 하나만 있고 구형 유형별 FK는 없다. 분석당 피드백은 0개 또는 1개다. saju_id에 UNIQUE를 두고 같은 분석의 중복 제출은 409로 거부한다. 피드백 자체의 ID·생성 시각은 기존 방식을 유지한다. 전환 전 원본은 운영자에게 로컬로 제공한 후 모든 행을 초기화하며 앱에서 조회할 수 없다.

## 5. 관리자

| 경로 | 계약 |
|---|---|
| GET /admin/analytics | 기존 화면·필터·권한 유지 |
| GET /admin/analytics/api | 새 saju 목록, id는 숫자 ID |
| GET /admin/analytics/{id}?type=...&userId=... | 새 ID·소유자·유형으로만 상세 조회 |
| GET /admin/users, /admin/users/api, /admin/users/{id} | 회원 목록·상세의 totalAnalysisCount는 saju.user_id별 성공 결과 수, 분석 없는 회원도 0으로 포함 |
| POST /admin/daily-usages/users/{userId}/adjust | 기존 사용량 조정·회원 존재 확인 유지, 회원 조회가 구형 분석 테이블에 의존하지 않도록 변경 |
| GET /admin/dashboard, /admin/api/dashboard | 서울 날짜 기준 saju.created_at·analysis_type별 당일 분석 수와 새 피드백 통계, 기존 일일 한도 소진 집계 유지 |
| GET /admin/feedback, /admin/api/feedback | 기존 type 필터·페이지 유지, 목록과 count 모두 saju_id로 연결한 saju.analysis_type 기준 |
| GET /admin/api/feedback/stats | 연결된 saju.analysis_type·피드백 만족도별 행 수 및 전체 피드백 수 |
| GET /admin/api/feedback/{feedbackId} | 새 피드백과 연결된 saju 숫자 ID·analysis_type·created_at 조회 |

페이지 크기 20·서울 기준 날짜 범위·기존 검색·기간·유형 필터를 유지한다. 목록·상세는 created_at 기준이며 새 스냅샷을 표시한다. 제거된 구형 테이블·컬럼을 조회하지 않는다.

통계는 새 saju 한 행을 성공 분석 한 건으로 세는 단일 집계다. 유형별·서울 날짜별로 created_at을 사용하며 과거 분리 집계·전환 전후 기준 안내·원본별 묶음을 두지 않는다. 초기화 직후 0건이다. 운영자에게 로컬로 제공한 과거 건수를 관리 화면에 적재하지 않는다.

회원 관리의 native SQL과 AdminUsageAdjustmentService가 사용하는 회원 조회도 변경 범위다. 회원 건수는 공유 접근 관계가 아닌 사용자 소유 saju 행 수다. 피드백 조회·count·통계는 같은 saju 조인 기준을 쓰고 회원 탈퇴 정책에 따른 자료 범위도 일치시킨다. 당일 분석 통계는 saju.created_at, 피드백 시각은 피드백 created_at으로 구분한다.

관리자 응답의 feedbackType, satisfiedCountByFeedbackType, unsatisfiedCountByFeedbackType은 기존 화면 호환을 위해 유지하되 값은 연결된 saju.analysis_type에서 구한다. 제거되는 것은 작성 요청의 feedbackType과 DB feedback_type 컬럼이다. AdminFeedbackService·DTO 매핑·대시보드 합산·회원/피드백 템플릿 및 링크를 함께 확인한다. 관리자 목록의 type=SAJU 거부용 InvalidFeedbackTypeException과 SajuGlobalExceptionHandler의 기존 400 처리는 유지한다. 피드백 작성 시 SAJU 대상은 연결된 분석을 기준으로 HTTP 404로 거부하며 관리자 필터 오류 400과 구분한다.

### 회원 탈퇴와 관리자 표시

DELETE /api/users/me는 기존 비밀번호 확인·응답·세션 무효화 경로를 유지한다. 계정 soft delete와 피드백 hard delete, 기본 프로필 참조 NULL 해제를 한 DB 트랜잭션으로 처리한다. saju·공유 프로필·만세력은 삭제하지 않고 원래 이름·이메일 대신 기존 탈퇴 계정의 대체 값을 사용한다. 관리자 회원 조회·분석 집계·권한 있는 상세는 탈퇴 사용자 분석을 포함하며 soft delete 사용자 조회 제한 때문에 saju가 사라지지 않게 한다. 일반 사용자에게 탈퇴 계정 결과를 공개하지 않는다. 출생정보를 포함한 스냅샷은 남으며 전체 익명화를 제공하는 계약이 아니다.

탈퇴와 최종 분석 저장·피드백·기본값 저장은 사용자 행 잠금과 활성 상태 확인을 짧은 쓰기 구간에서 수행해 순서를 정한다. 탈퇴 전에 커밋한 분석은 남고 탈퇴 완료 후 최종 저장은 기존 비활성 사용자 정책으로 거부한다. 분석 저장 전 실패라면 기존 쿼터 보상 규칙을 적용한다. 외부 API 동안 사용자 잠금·트랜잭션을 유지하지 않는다.

## 6. 오류 및 테스트

미인증·권한 없음·유형별 검증·429 한도 초과·외부 timeout/retry·저장 오류의 기존 정책을 유지한다. 분석 미존재는 위 404 규칙을 따른다. 소유권 오류에서 결과 내용을 노출하지 않는다.

세 생성→목록→상세→피드백→관리자가 같은 숫자 ID를 사용하는지, 입력이 같은 동시 성공 요청이 다른 결과를 받는지 확인한다. 초기화 이후 빈 이력·통계, 기존 계정 로그인, 숫자 ID 형식 유지와 없는/타인/유형 불일치 ID·관운 대상 피드백의 404, 모든 유형 manse 필수, 상세 부작용 0을 검증한다.

관리자 회귀에는 회원 목록·상세의 0건/유형별 반복 건수, 사용량 조정의 회원 확인·기존 차감/초기화 동작, 대시보드의 서울 자정 경계, 피드백 type 필터의 목록/count 일치·만족도별 합계·상세 숫자 ID·기존 응답 필드·type=SAJU 400·권한 거부를 포함한다. 구형 테이블과 feedback_type이 실제로 없는 스키마에서 조회가 성공해야 한다.

## 7. 운영자 사전 제공

전환 전 피드백 원본과 건수는 이 API의 새 기능으로 구현하지 않는다. 보고서 양식과 SQL은 Git에서 제외된 로컬 문서(`specs/007-unify-saju-analysis/local/reset-runbook.md`)로 제공하며 저장소에는 포함하지 않는다. 사용자는 이 로컬 문서의 SQL을 직접 실행한다. 원본 전체 범위·집계 기준·대조·운영자 확인 순서는 [spec.md](../spec.md)와 [quickstart.md](../quickstart.md)를 따른다. 이번 설계 문서에는 실제 SQL을 넣지 않는다.
