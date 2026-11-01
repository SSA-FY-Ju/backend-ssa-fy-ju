# 구현 계획: 분석 결과 통합 및 기존 분석 전체 초기화

**브랜치**: `007-unify-saju-analysis` | **수정일**: 2026-10-31 | **명세**: [spec.md](spec.md)

**상태**: 전체 초기화 결정에 맞춰 설계 문서 수정 완료. 코드·DB·원본 추출·배포·커밋은 수행하지 않았다. 보고서 양식과 실제 SQL은 Git에서 제외된 로컬 문서(`specs/007-unify-saju-analysis/local/reset-runbook.md`)로 제공하며 저장소에는 포함하지 않는다. tasks.md는 작성 완료됐으며 구현과 운영자 실행은 미수행이다.

## 1. 요약

세 유형의 결과를 saju에 통합해 성공 요청마다 인증 사용자 소유의 새 결과를 저장한다. 기존 분석·접근·피드백·프로필·일일 사용량은 전부 초기화하고 users와 인증 데이터는 유지한다. 새 user_profile/manse/saju/피드백은 빈 상태에서 출발한다.

`users.user_profile_id`는 선택적인 기본 출생정보이며 실제 분석 프로필은 saju → manse → user_profile로 조회하고 결과 소유자는 saju.user_id로 별도 저장한다. 분석 전에 기본값 또는 다른 출생정보를 고를 수 있도록 기본값 조회·저장 API를 제공한다. 분석 요청의 구체적인 출생정보를 사용하고 기본값은 자동 변경하지 않는다.

삭제 전 피드백 원본 전체와 분석 건수를 로컬로 제공한다. 원본은 모든 행·모든 컬럼 그대로이며 조인·가공·마스킹·필터 없이 탈퇴 사용자 행도 포함한다. 분석 건수는 유형별 원본 수, 별도의 SAJU 접근 수, 사용자별 유형 수와 산정 기준이다. 추출 자료를 같은 정지 시점의 DB와 대조하고 운영자 확인을 받은 뒤에만 초기화한다.

초기화 대상은 saju_result, saju_full_data, career_fortune, user_saju_access, career_consultation, company_compatibility, user_satisfaction_feedback, user_profile, daily_api_usage의 모든 행이다. 사용량 삭제로 당일 이미 소비한 쿼터도 재설정된다. 이후 공유 3회·실패 보상 정책은 유지한다.

분석 ID는 기존 숫자 형식(Java Long·JSON 숫자)을 유지하고 새 saju의 BIGINT AUTO_INCREMENT로 처음부터 발급한다. 과거 최댓값 조회·시작 번호 보정·예전 URL의 영구 404 보장은 두지 않으며 과거 이력은 마이페이지·관리자에 표시되지 않는다. 과거 데이터 변환·복사·재연결·호환 매핑은 구현하지 않는다.

## 2. 기술 맥락

- **언어/빌드**: Java 21, 기존 Gradle wrapper 9.4.1.
- **의존성**: Spring Boot 4.0.5, JPA/Hibernate, MVC/Security/Validation, Spring AI 2.0.0-M4, Spring Retry, Jackson 3 tools.jackson, Redis (인증 유지, 사용처 없는 Redisson 분산락·의존성 제거), Thymeleaf. 신규 의존성 없음.
- **저장소**: MySQL 8. 기존 프로필/사용량 구조는 행을 비운 뒤 재사용한다. 새 manse·saju, 만세력 계산 데이터는 ManseData record 기반 data_json 하나, 피드백 대상 FK saju_id 하나. 모든 saju의 manse_id는 NOT NULL이며 시각은 created_at 하나다. 계정·인증 데이터는 유지하며 초기화 완료 후 users에 NULL 허용 user_profile_id FK를 추가한다. 기본값은 NULL로 시작하고 공유 프로필의 출생정보는 변경하지 않는다.
- **쿼터 연결 설정**: 공통 `application.yaml`의 `spring.datasource.hikari.data-source-properties.useAffectedRows`를 `true`로 고정한다. 로컬·운영 DB_URL에 옵션을 수동으로 붙이는 방식에 의존하지 않고 충돌하는 URL 옵션은 제거한다. MySQL에서 한도 도달 후 변경 없는 upsert가 0을 반환하고 네 번째 요청이 429인지 검증한다.
- **시험**: JUnit 5/Mockito/Spring Boot Test/Security Test, 기존 H2 기본 테스트, MySQL/Redis Testcontainers. JSON·FK·쿼터 SQL·경합·숫자 ID 계약은 실제 MySQL 기반 검증을 포함한다. 운영 초기화 SQL은 복제 DB와 수동 검증으로 점검한다.
- **플랫폼**: 기존 단일 Spring Boot 모듈 SSAju/, 동기 REST API·관리자 SSR·Linux 컨테이너.
- **성능**: 기존 외부 timeout/retry 유지. 목록은 페이지/count 고정 조회 수, N+1 없음. 상세의 외부 호출·차감·새 결과는 0. 외부 I/O는 DB 트랜잭션 밖에서 수행한다. 프로필·만세력에는 분산락을 사용하지 않고 DB 유일 제약으로 중복을 막는다.
- **범위**: 세 유형 실행·JSON·직접 소유권·개인/관리자 이력·피드백·통계·쿼터와 문서화된 수동 전환 절차. 결제·백엔드 재전송 중복 방지·프론트 구현은 추가하지 않는다. ID 타입 변경과 피드백 요청 필드 제거는 백엔드 API 문서에 정리해 추후 클라이언트에 전달하며 클라이언트 수정·동시 배포 조율은 이번 범위에 포함하지 않는다.

## 3. 설계 검토

constitution은 미채택 템플릿 상태다. 저장소 가이드와 확정 명세를 적용한다.

| 검토 항목 | 설계 결과 |
|---|---|
| 초기화와 사전 제공 | 삭제 대상/계정 유지, 원본·건수·대조·운영자 확인을 명세와 절차에 고정 |
| 계층 | 컨트롤러는 기존 DTO, 공통 서비스는 정책, 저장소는 조회·쓰기 |
| 트랜잭션 | 외부 분석은 비트랜잭션, 기초·결과 저장만 짧은 별도 트랜잭션 |
| 검증/JSON | 세 전략과 기존 검증 재사용, ID 없는 완성 스냅샷 |
| 보안 | 직접 소유권과 기존 관리자/사용자 인증 유지 |
| 쿼터 | 초기화 시 0건, 이후 기존 3회·원래 날짜 보상·커밋 후 미복원 |
| 단순화 | 과거 데이터용 참조·예외·번호 경계·부팅 전환 검증·전용 실행 패키지 없음 |
| 산출물 | SQL/보고서 양식은 제외된 로컬 실행 문서, 개인정보 결과물은 로컬만 보관. 커밋 대상 문서에는 SQL 없음 |

문서 설계가 실제 추출·대조·운영자 확인을 완료했다는 뜻은 아니다. 운영자 확인 전 삭제 금지는 구현·배포 단계에서도 유효하다.

## 4. 문서와 코드 구조

```text
specs/007-unify-saju-analysis/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/
│   ├── api.md
│   └── storage.md
├── quickstart.md
└── tasks.md                 # 작성 완료, 구현·운영 작업 미완료

SSAju/src/main/java/ssafy/SSAju/
├── controller/              # 기존 생성·마이페이지, 숫자 ID 계약, 기본 출생정보 조회·저장
├── career/
│   ├── entity/              # Saju, Manse, UserProfile, 새 피드백 관계
│   ├── domain/              # 입력·완성 결과·AnalysisTimeContext record
│   ├── strategy/            # 기존 AnalysisType 세 전략
│   ├── converter/, mapper/  # typed 스냅샷 codec·응답 변환
│   ├── provider/            # 프로필·만세력 재사용
│   └── util/,caller/,validator/ # 기존 계산·외부 연동·검증
├── service/                 # AnalysisService·SajuWriteService·ManseWriteService, AuthService·AnalysisResultMaskingService 탈퇴 처리
├── repository/              # 새 결과·기초 저장·단일 이력/통계 조회·기존 쿼터
├── admin/                   # 분석·회원 관리·사용량 조정·대시보드·피드백 목록/상세/통계
│   ├── repository/          # AdminUserQueryRepository의 구형 native SQL 제거, 피드백 saju 조인
│   ├── service/,dto/        # AdminDashboardService·AdminUsageAdjustmentService·피드백 DTO 매핑
│   └── controller/          # 기존 관리자 경로/응답/type 필터 유지
├── exception/               # 관리자 type=SAJU의 InvalidFeedbackTypeException 유지
└── config/,handler/         # 기존 인증·오류, 없는/타인 분석 ID의 404·관리자 type 필터 400 유지
SSAju/src/test/java/ssafy/SSAju/
├── service/,controller/,career/
├── concurrency/
├── integration/             # 런타임 JSON·FK·쿼터·숫자 ID 계약
└── admin/
```

기존 분석 결과 엔티티/provider/save 서비스·구형 FK 조회는 최종 런타임에서 제거한다. 관리자 회원/피드백 Thymeleaf 템플릿·JS·DTO의 필드와 링크도 함께 확인한다. feedbackType·유형별 통계 응답 필드명은 유지하되 saju에서 값을 얻으며 작성 요청의 유형 필드 제거와 구분한다. 전환 코드·커밋되는 SQL 파일·운영 초기화 전용 자동 테스트는 추가하지 않는다. 실제 SQL과 보고서 양식은 제외된 로컬 실행 문서에 둔다. `AGENTS.md`는 이 plan을 가리키는 기존 참조를 유지한다.

## 5. 요청 실행 설계

1. 인증 사용자·유형·기존 요청 DTO를 검증하고 구체적인 출생정보와 불변 분석 시간 문맥을 확정한다. 기본값 선택 시에도 클라이언트가 DTO에 출생정보를 전달하며 누락값을 기본 프로필로 보충하지 않는다.
2. 컨설팅·궁합만 원자적 쿼터를 차감하고 usageDate를 보관한다.
3. 프로필·만세력을 조회하고 없으면 FastAPI·공통 계산을 ManseData로 검증해 data_json 하나에 짧은 쓰기로 처음 저장한다. 프로필·만세력 저장은 분산락 없이 DB 유일 제약 경합 후 재조회로 정본에 수렴한다.
4. 선택 전략이 새 최종 해석·점수를 계산하고 기존 응답 검증을 수행한다. 별도 SAJU 행을 자동 생성하지 않는다.
5. ID 없는 최종 결과와 요청/실제 입력을 확정한다.
6. 별도 SajuWriteService가 사용자·유형·실제 사용한 만세력·payload를 검증하고 숫자 ID 결과 한 건을 커밋한다.
7. 차감 이후부터 저장 프록시 반환까지의 실패만 원래 날짜로 보상한다. 커밋 후 응답 생성/전송은 보상 범위 밖이다.

전략 인터페이스는 type/inputType/validateInput/analyze/validateResult 정도로 한정하며 analyze는 최초 계약부터 AnalysisTimeContext를 인자로 받는다. 유형 선택 경계에서만 입력 클래스를 확인하고 cast한다. 공통 서비스가 인증·쿼터·저장을 책임진다. 기존 결과 캐시·결과 단위 분산락·업데이트·승자 반환 경로는 제거한다. 프로필 생성에도 분산락을 사용하지 않으므로 모든 호출 제거 후 사용처 없는 DistributedLock 어노테이션·Aspect·락 전용 설정·Redisson 의존성을 정리한다. 인증용 Redis와 사용자 행 DB 잠금은 유지한다. RedissonClient로 Redis를 초기화하던 인증 통합 테스트는 같은 변경 단위에서 Spring Data Redis 방식으로 교체하고 Redisson 제거 후 기본 Lettuce 연결에서도 인증·탈퇴 회귀를 검증한다.

기본 출생정보 조회·저장은 분석 실행과 분리한다. 인증 사용자 FK만 저장하며 프로필이 없으면 출생정보 유일 제약으로 생성한다. 기본값 변경은 공유 행 수정 없이 참조를 교체한다. 외부 호출·만세력/결과 생성·쿼터 차감은 없고 분석 수행은 기본값을 자동 변경하지 않는다. 과거 결과와 진행 중 요청의 확정 입력도 유지한다.

## 6. 조회·피드백·통계

숫자 분석 ID와 소유자·유형으로 새 saju만 조회한다. 없는 ID·타인 ID·유형 불일치는 404이며 과거 ID를 따로 해석하지 않는다. 마이페이지는 빈 이력에서 시작하고 기간 제한 없이 소유자의 전체 새 요청을 created_at DESC,id DESC 순서로 페이지 조회한다. 목록과 count는 같은 소유자·유형 조건을 사용한다. 상세는 저장 스냅샷만 읽는다.

관리자 조회는 기존 AdminAnalyticsQueryRepository·AdminUserQueryRepository의 JdbcTemplate SQL과 DTO 직접 매핑을 유지한다. 분석 목록·count·상세·통계는 saju의 user_id를 직접 읽고 User 연관을 로딩하지 않는다. 계정 표시가 필요하면 SQL로 users를 조인해 저장된 대체 이름/이메일을 읽는다. 회원별 분석 수는 saju.user_id 집계를 LEFT JOIN한다. 관리자에서 User의 @SQLRestriction을 적용하는 JPQL 조인·지연 로딩을 사용하지 않으며 일반 사용자용 필터는 유지한다. 중복 user_id 엔티티 매핑이나 별도 조회 프레임워크는 추가하지 않는다.

피드백은 UNIQUE인 saju_id FK 하나로 새 결과만 참조하며 분석당 0..1개다. feedback_type 컬럼과 feedbackType 요청 필드는 없애고 허용 여부·관리자 유형 표시·필터는 saju.analysis_type을 사용한다. 중복 제출은 409로 거부한다. 모든 유형의 만세력 참조는 필수이며 과거 입력 누락·과거 요약 재구성·NULL 만세력 예외를 두지 않는다. 관리자 통계는 saju 한 행=성공 분석 한 건이며 서울 날짜·유형 필터와 기존 권한을 유지한다. 초기화 전 로컬 집계 자료는 앱 통계에 재적재하지 않는다.

AdminUserQueryRepository의 회원 목록·상세·분석 건수와 이를 호출하는 AdminUsageAdjustmentService를 변경한다. 회원 건수는 saju.user_id별 행 수로 바꾸고 0건 회원도 유지한다. AdminDashboardService와 /admin/dashboard·/admin/api/dashboard는 saju.created_at·analysis_type의 당일 집계와 saju 조인 피드백 통계를 쓴다. AdminFeedbackQueryRepository의 목록·count·상세·만족도별 통계, AdminFeedbackService·FeedbackStatDTO 매핑과 화면을 함께 맞춘다. 관리자 경로·응답 필드·type 필터·권한과 type=SAJU 400 예외 처리는 유지한다.

회원 탈퇴는 계정 soft delete·기존 이름/이메일 대체·피드백 hard delete·세션 무효화를 유지하고 기본 프로필 참조만 NULL로 해제한다. saju와 공유 프로필·만세력은 유지하며 관리자 분석 집계·상세에서 탈퇴 사용자 자료를 누락하지 않는다. 출생정보·스냅샷·내부 소유자 연결이 남는 정책임을 명시한다. AuthService·AnalysisResultMaskingService에서 피드백 삭제·기본 참조 해제·soft delete를 원자적으로 처리한다. 관련 최종 쓰기와 탈퇴는 사용자 행 잠금·활성 확인으로 순서를 정하고 외부 호출 중에는 잠금을 보유하지 않는다. 탈퇴 후 최종 분석 저장이 거부되면 커밋 전 쿼터 보상 규칙을 따른다.

## 7. 전환 설계

**순서**: 분석 쓰기 중단 → 피드백 원본·건수 추출 → 추출 건수와 DB 건수 대조 → 운영자 확인 → 초기화 → 새 스키마 적용 → 배포.

피드백 원본은 user_satisfaction_feedback 전체 행·전체 컬럼 그대로이며 탈퇴 사용자 행도 포함한다. 건수는 [spec.md의 지표 정의](spec.md)를 그대로 따른다. 보고서 양식과 실제 SELECT·내보내기·대조·초기화 SQL은 Git에서 제외된 로컬 문서(`specs/007-unify-saju-analysis/local/reset-runbook.md`)로 제공하며 저장소에는 포함하지 않는다. 커밋 대상 문서에는 항목 정의만 둔다. 원본·집계·DB 복구 자료는 개인정보를 포함하므로 로컬에만 보관한다.

추출 시작부터 새 스키마 DDL 적용·validate 통과·호환 애플리케이션 배포 완료까지 구형 애플리케이션 전체 중지 또는 사용자·관리자의 읽기·쓰기 트래픽 전체 차단을 유지하고 진행 중 조회·쓰기를 모두 종료한다. 쓰기 경로만 차단하는 방식은 허용하지 않는다. DDL 적용 직전부터 배포·스키마 검증 완료까지 AnalysisHistoryRepository와 관리자 조회를 포함한 구형 테이블 접근이 실행되지 않음을 확인하며, 조회·쓰기 차단을 보장할 수 없으면 초기화를 중단한다. 차단 방법은 운영자가 선택하며 별도 차단 기능 개발은 범위에 포함하지 않는다. 차단 해제나 쓰기 발생 시 즉시 전환을 중단한다. 파괴적 초기화·DDL 시작 전이면 차단을 복구한 뒤 원본·건수를 재추출·대조하고 운영자 재확인을 받는다. 파괴적 초기화·DDL을 시작한 이후에는 쓰기를 중지하고 구형 앱의 조회 차단도 유지한 채 실제 DB 상태·적용된 문장과 검증된 백업·복구 자료를 확인한다. 신규 기록 손실을 검토하고 운영자 확인 후 복구·재검증하며, 초기화 전 절차처럼 자료를 단순 재추출하거나 자동 재삭제하지 않는다. 검증 전 백업 덮어쓰기를 금지한다. 운영자 확인 전에 대상 DB 전체 백업을 로컬에 확보하고 복구 가능성을 검증하며, 복제 DB에서 해당 백업의 복구 리허설을 완료하고 결과를 로컬에 기록해야 한다. 백업·복구 검증·리허설 결과 중 하나라도 없거나 실패하면 초기화를 중단한다. 상세 실행·대조·운영자 확인·실패 시 중단 기준은 [quickstart.md](quickstart.md)를 따른다. 계정과 인증의 손실이 없어야 하며 계정은 새로 만들지 않는다. 초기화 직후 삭제 대상 잔여 데이터와 새 분석 구조·일일 사용량은 비어 있어야 한다.

### 새 스키마 DDL의 책임과 적용 범위

구현 담당자는 확정 모델에 맞춰 제외된 local/reset-runbook.md의 f절에 CREATE·ALTER·DROP DDL과 사전/사후 검증을 작성·동기화한다. 운영자는 실제 스키마·FK 이름·자료형을 대조하고 복제 DB 리허설 후 실행한다. 행 삭제는 e절, DDL은 f절, 전체 순서는 g절로 구분한다. DDL은 암묵적 커밋을 수반하므로 행 삭제 트랜잭션에 넣거나 전체 ROLLBACK을 보장하지 않는다. 오류 시 즉시 중지하고 완료된 문장과 실제 스키마로 복구 범위를 확인한다.

구형 전용 6개 테이블(user_saju_access, career_consultation, career_fortune, saju_full_data, company_compatibility, saju_result)은 피드백의 구형 FK를 먼저 제거한 뒤 자식부터 DROP한다. 빈 user_profile·daily_api_usage는 재사용한다. 피드백에서 구형 FK·두 대상 컬럼·feedback_type을 제거하고 saju_id UNIQUE·새 FK·작성자 NOT NULL을 적용한다. manse·saju와 FK·조회 인덱스·users의 NULL 허용 기본 참조를 명시적으로 생성한다. 계정·인증 자료는 유지한다.

운영의 ddl-auto 기본값 validate와 로컬 update를 스키마 전환 수단으로 쓰지 않는다. 로컬도 기존 앱을 중지하고 같은 추출/확인/초기화·DDL 절차를 적용한 뒤 JPA_DDL_AUTO=validate와 spring.jpa.hibernate.ddl-auto=validate를 명시한 설정으로 기동해 검증한다. local 프로필의 update보다 우선하는 명령행 설정을 사용한다. 코드의 설정 파일은 이번 문서 작업에서 수정하지 않는다.

새 스키마 DDL 적용·validate 통과·호환 애플리케이션 배포 완료를 모두 확인한 뒤 읽기·쓰기 차단을 해제한다. smoke test는 차단 해제 조건에 포함하지 않고 해제 후 수행한다.

## 8. 작업 생성 순서

1. 새 구조의 유형별 요청·JSON·숫자 ID·소유권 계약 fixture.
2. 빈 프로필/만세력/분석/피드백 모델·codec·유일 제약·모든 유형의 필수 참조·NULL 허용 사용자 기본 프로필 FK·기본값 조회/명시적 저장. 만세력은 ManseData 전체를 data_json 한 컬럼으로 직렬화하고 개별 계산 컬럼은 만들지 않는다. BIGINT ID·enum VARCHAR·신규 Instant DATETIME(6)/UTC 매핑을 로컬 DDL과 일치시킨다.
3. 공통 실행·세 전략·쿼터 경계·캐시/결과 합치기 제거.
4. 마이페이지·숫자 ID 상세·단일 피드백 FK·관리자 회원 목록/상세/사용량 조정·대시보드·피드백 목록/count/상세/통계·없는 분석 ID 404 및 관리자 DTO/템플릿/예외 회귀.
5. 탈퇴 정책·원자성·분석/피드백/기본값 동시 쓰기와 관리자 탈퇴 사용자 표시를 포함한 런타임 회귀·MySQL/Redis 동시성·새 구조의 초기 빈 상태 검증.
6. 실제 대상 스키마 확인, 제외된 로컬 실행 문서 e절의 초기화와 f절의 새 스키마 CREATE/ALTER/DROP·검증 SQL 제공, 구형 테이블/컬럼 제거 확인, 운영/로컬 validate 기동 확인, 복제 DB에서 추출·대조·초기화·스키마 적용 절차 리허설. 실제 운영자는 자료 대조 후 별도 확인한다.

실제 운영 실행은 이번 문서 수정의 일부가 아니다. 작업 목록은 생성 완료됐으며 과거 데이터 변환·참조 매핑·전용 실행 작업을 생성하지 않는다.

## 9. 요구사항 추적

| 요구사항 | 설계·검증 |
|---|---|
| FR-001~008, SC-001~002/005~006 | 새 모델·세 전략·기초 재사용·요청별 결과·동시성 |
| FR-009~013, SC-003~004 | 숫자 ID 계약·직접 소유권·스냅샷 읽기·단일 피드백 FK·saju 통계 |
| FR-026, SC-014 | 탈퇴 계정 마스킹·기본 참조 해제·피드백 삭제·분석/공유 자료 유지·동시 쓰기·관리자 표시 회귀 |
| FR-024~025, SC-012~013 | 로컬 DDL 산출·구형 구조 제거·운영/로컬 validate·전체 관리자 경로/응답/집계 회귀 |
| FR-022~023, SC-011 | 기본값 미설정·저장·조회·선택·다른 입력·참조 교체, 공유 프로필/과거 결과/진행 중 입력 불변 |
| FR-014~016 | 성공 결과 원자 저장·회귀 테스트·범위 제외 |
| FR-017, SC-007 | 피드백 전체 원본·탈퇴 행 포함·무가공·로컬 제공·DB 대조 |
| FR-018, SC-008 | 사용량 초기화와 이후 공유 한도·실패 보상 |
| FR-019~021, SC-009~010 | 원본/접근/사용자별 건수·기준·운영자 확인·전환 순서·계정 유지 |

## 10. 검토 순서

이 plan → [research.md](research.md)의 현재 결정과 폐기 기록 → [data-model.md](data-model.md) → [API 계약](contracts/api.md) → [저장 계약](contracts/storage.md) → [quickstart.md](quickstart.md). 초기화 전에 무엇을 제공하고 언제 운영자가 확인하는지부터 검토한다.
