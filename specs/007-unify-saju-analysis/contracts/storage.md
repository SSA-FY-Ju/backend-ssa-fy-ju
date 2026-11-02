# 저장 계약: 신규 요청의 입력·결과 스냅샷

**수정일**: 2026-10-31 | **관련 문서**: [data-model.md](../data-model.md)

## 공통 규칙

분석·프로필·만세력·피드백은 빈 상태에서 시작하며 과거 데이터용 예외 필드를 두지 않는다. `saju.manse_id`는 모든 유형에서 NOT NULL이다. 실제 분석 프로필은 saju → manse → user_profile로 조회하며 saju에는 중복 프로필 FK를 두지 않는다. manse.user_profile_id는 생성 후 변경하지 않는다.

입력·결과 JSON은 schemaVersion=1인 객체다. analysis_type에 따라 타입별 Java record를 선택하고 기존 Jackson 3 tools.jackson 및 JSON 지원 패턴으로 직렬화한다. 알 수 없는 유형·버전·필수값 누락은 저장을 거부한다. JSON 컬럼의 유효성은 업무 검증을 대체하지 않는다.

공통 ID·소유자·유형·만세력·created_at은 컬럼이 기준이며 실제 분석 프로필은 만세력을 경유한다. 분석 ID는 BIGINT 자동 증가·Java Long·JSON 숫자이며 결과 JSON에 중복 저장하지 않는다. `result_json.data`에는 생성 응답에서 ID를 제외한 완성 내용을 저장하고 조회 시 컬럼 ID를 붙인다. `saju`에는 created_at 하나만 두며 목록·상세·통계에 동일하게 쓴다.

## 물리 형식과 DDL 일치

신규 saju.id는 BIGINT AUTO_INCREMENT·Java Long이며 피드백 saju_id도 동일한 signed BIGINT로 일치시킨다. 기존 JSON 숫자 형식을 유지하고 과거 번호와 분리하기 위한 시작 번호 보정은 하지 않는다. 새 manse·saju의 Instant 컬럼은 DATETIME(6)·UTC 저장/조회로 맞추고 기존 감사 시각 컬럼의 자료형은 유지한다. JPA는 공통 application.yaml에 spring.jpa.properties.hibernate.jdbc.time_zone=UTC를 명시하고 신규 Instant는 DATETIME(6)에 대응하는 JDBC TIMESTAMP 매핑을 명시한다. JdbcTemplate의 created_at 조회는 ResultSet.getTimestamp("created_at", Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant()로 UTC Calendar를 전달하며 JVM 기본 시간대에 의존하지 않는다. 실제 MySQL에서 JVM 기본 시간대를 Asia/Seoul 등 비UTC로 설정한 뒤 마이크로초 정밀도의 고정 Instant를 JPA로 저장·flush/clear하고 JPA와 직접 JDBC로 다시 읽어 원본과 같은지 검증한다. 테스트가 변경한 JVM 시간대는 finally에서 복원한다. saju.analysis_type과 피드백 satisfaction_status는 VARCHAR(32)로 저장하며 enum의 VARCHAR JDBC 매핑과 기존 허용값 검증을 명시한다. 최종 엔티티·로컬 DDL을 함께 수정하고 MySQL validate 및 숫자 ID·JSON·enum·UTC 시각 왕복으로 확인한다. 구체적인 DDL은 제외된 local/reset-runbook.md f절에만 둔다.

## 기본 출생정보와 실제 분석 입력

`users.user_profile_id`는 NULL 허용 기본값이고 실제 분석 입력의 프로필은 NOT NULL인 `saju.manse_id → manse.user_profile_id`로 참조한다. 기본값 조회·저장은 인증 사용자만 대상으로 하며 다른 출생정보로 분석해도 사용자 기본 참조는 바꾸지 않는다. 공유 프로필의 출생정보는 생성 후 변경하지 않고 기본값 변경 시 다른 프로필을 조회·생성해 사용자 참조만 교체한다. 사용자 참조에 UNIQUE나 삭제 연쇄 전파를 두지 않는다.

기본 프로필 저장은 프로필 생성·사용자 참조 저장만 수행하며 만세력·분석·차감을 만들지 않는다. 분석 요청은 DTO의 구체적인 출생정보를 기존 검증·정규화로 확정하고 그 값을 input_json에 보관한다. 사용자의 나중 기본값으로 저장 입력을 재해석하거나 과거 참조·스냅샷을 수정하지 않는다.

## 만세력 data_json

`manse`의 계산 데이터는 NOT NULL인 `data_json` 하나에 저장하며 Java에서는 `ManseData` record로 다룬다. ID·프로필 FK·fetched_at·created_at은 일반 컬럼으로 유지한다. fetched_at과 created_at은 NOT NULL이다. 만세력의 개별 계산 필드를 별도 컬럼으로 중복 저장하지 않는다.

| JSON 필드 | Java 형식과 검증 |
|---|---|
| schemaVersion | 정수 1, 지원하지 않는 버전 거부 |
| yearPillar / monthPillar / dayPillar / hourPillar | String, 기존 기둥 필수값·형식 검증 유지 |
| heavenlyStems / earthlyBranches | List<String>, 각각 4개 및 기존 허용값 검증 |
| dayMaster / dayMasterElement | String, 기존 추출·계산·허용값 검증 |
| fiveElements | Map<String, Integer>, 기존 오행 검증 유지 |
| solarCorrection | Map<String, Object>, 기존 선택 정보·null 허용 의미 유지 |
| tenGodHiddenStemAnalysis | 기존 TenGodHiddenStemAnalysis record, tenGods·hiddenStems의 타입과 의미 유지 |

Jackson 3와 기존 JSON 지원을 재사용해 record 전체를 하나의 JSON 객체로 변환한다. JSON 컬럼 자체가 업무 유효성을 보장한다고 간주하지 않는다. 생성 시 FastAPI·공통 계산 검증을 수행하고 읽을 때도 타입·필수값·배열 길이·허용값을 확인한다. 손상되거나 버전이 맞지 않으면 기존 오류 체계로 실패시키며 값을 임의로 비워 정상 자료처럼 사용하지 않는다.

JSON 왕복 검증은 기둥·배열·일간·오행·선택 태양 보정·십성·지장간의 전 필드가 동일한지 확인한다. 기초 정보를 재사용하는 계산기/caller에는 record의 저장값을 전달한다. 요청별 최종 관운 해석·컨설팅·궁합 응답은 기존처럼 saju.result_json에 독립 저장한다.

## 입력

| 유형 | 저장할 내용 |
|---|---|
| SAJU | birthDate, birthTime, 분석 기준 year/month |
| CAREER_CONSULTATION | birthDate, birthTime, consultationMonth, 분석 기준 year |
| COMPANY_COMPATIBILITY | 요청 userBirthDate/userBirthTime/companyName/targetRole/companyFoundingDate/companyFoundingTime, 실제 계산에 쓴 출생/회사 설립 시각·날짜, compatibilityMonth, 회사 날짜 취득 경로 및 사용한 회사 기초 참조 |

신규 요청의 선택값 null과 실제 정규화 값은 구분한다. 회사 조회·수동 입력·12:00 기본 시각·날짜 보정의 현재 의미를 유지한다. 요청값을 실제 사용값으로 덮어쓰지 않는다.

불변 AnalysisTimeContext의 analysisDate/year/month/targetMonths를 요청당 한 번 확정해 프롬프트·caller 재시도·검증·조립·요약에 전달한다. 쿼터 usageDate는 실제 차감 날짜로 별도 보관한다.

## 결과

### SAJU

`data`는 favoredPeriod, confidenceScore, reasoning이다. 동일 입력의 값이 같아도 각 요청은 별도 행이다.

### CAREER_CONSULTATION

현 ConsultationResponse의 consultationId를 제외한 모든 필드를 저장한다.

- industries, interviewTips, strengths, openaiModelVersion.
- favoredPeriod, confidenceScore, reasoning.
- sajuProfile 전체: 일간·해석, 오행 분포·해석, 십성 분포·중요 십성·특성 문구.
- cautions, wealthStyle, longTermRoadmap, personalBranding, powerKeywords, mentalCare, environmentFit, workStyle, relationshipStrategy, careerTimeline의 모든 중첩 필드.
- 요청 당시 확정 연도로 한 번 만든 analysisSummary.

기존 CareerAdviceResponse의 keyTenGods/dayMasterDescription/fiveElementsAnalysis 등도 응답의 해당 sajuProfile 필드로 매핑한다. 저장된 summary나 문구를 조회 시 현재 연도·모델로 재생성하지 않는다.

### COMPANY_COMPATIBILITY

현 CompatibilityResponse의 compatibilityId를 제외한 requestContext, compatibilityScore, summary, targetRoleAnalysis, fiveElements, analysisBreakdown, actionableStrategy, expectedInterviewQuestions, roleCompatibility, monthlyForecast, cautions 전체를 저장한다.

기존 CompatibilityAnalysisData와 본체 점수·summary를 완성 결과로 결합한다. requestContext는 저장된 입력에서 구성한다. 조회 시 새 요청 DTO·회사 재조회·현재 계산기에 의존하지 않는다. 점수는 기존 결정론적 계산을, 문구는 기존 AI 응답 검증을 유지한다.

## 쓰기·읽기 실패

완성 스냅샷과 직렬화 가능 여부를 결과 쓰기 전에 검증한다. 별도 저장 트랜잭션이 커밋될 때만 성공 결과로 본다. 커밋 전 실패는 결과를 롤백하고 해당 쿼터를 원래 날짜로 복원한다. 커밋 후 ID 주입·HTTP 직렬화·전송 실패는 성공 결과를 유지하며 쿼터를 복원하지 않는다.

읽기 중 JSON 유형·버전·형식 오류가 나면 기존 오류 체계로 실패한다. 외부 재분석·다른 결과 반환으로 대체하지 않는다. 로그에는 식별자·유형·실패 단계만 남기고 출생정보·프롬프트·결과 전문을 남기지 않는다.

## 피드백 개수

새 피드백의 user_id는 NOT NULL FK이며 인증된 작성자와 결과의 user_id가 같아야 한다. saju_id는 NOT NULL·UNIQUE이며 분석 한 건당 0개 또는 1개다. 분석 유형은 saju.analysis_type에서 읽고 피드백의 별도 유형 컬럼·요청 필드는 두지 않는다. 컨설팅·궁합 허용 여부도 연결된 분석을 기준으로 검증하고 관운·타인·없는 분석 대상은 HTTP 404로 거부한다. 중복 제출은 409로 거부한다. 이 제한은 새 구조에 적용하며 초기화 전 원본의 중복 행을 제거하는 근거로 사용하지 않는다.

## 회원 탈퇴의 저장 정책

계정 soft delete 시 해당 사용자의 피드백은 hard delete하고 기본 프로필 참조만 NULL로 해제한다. saju 행·user_id·manse_id·스냅샷과 공유 프로필·만세력은 유지한다. 계정의 원래 이름·이메일은 기존 대체 처리에 따르며 분석에 별도로 복사하지 않는다. 출생정보·결과·내부 user_id는 남으므로 완전한 익명화가 아니다. 공유 자료에 삭제 연쇄 전파를 두지 않는다.

피드백 삭제·기본 참조 해제·계정 soft delete는 한 트랜잭션이며 관련 사용자 행의 짧은 쓰기 잠금으로 최종 분석/피드백/기본값 저장과 순서를 정한다. 탈퇴 완료 뒤 새 쓰기는 거부하고, 이미 커밋한 분석은 유지한다. 외부 호출은 이 잠금 밖에서 수행한다.

## 초기화 전 원본 제공과의 구분

전환 전 피드백 원본은 위의 새 저장 계약으로 변환하지 않는다. 기존 user_satisfaction_feedback의 모든 행·컬럼·값을 조인·가공·마스킹·필터 없이 로컬에 제공한다. 예전 유형별 FK 컬럼과 탈퇴 사용자 행도 그대로 포함한다. 원본 자료는 새 피드백으로 다시 적재하지 않는다. 기존 탈퇴 처리로 이미 삭제된 행은 복원하지 않으며 탈퇴 사용자 피드백이 실제 DB에 0건이면 정상이다. 새 작성자 NOT NULL 제약을 원본 추출의 필터로 사용하지 않는다.

유형별 원본 총수·SAJU 접근수·사용자별 유형별 수의 정의는 [spec.md](../spec.md)를 따른다. 보고서 양식과 SQL은 Git에서 제외된 로컬 문서(`specs/007-unify-saju-analysis/local/reset-runbook.md`)로 제공하며 저장소에는 포함하지 않는다. 실제 SQL은 커밋 대상 문서에 포함하지 않고 원본 결과 파일도 로컬에만 보관한다.
