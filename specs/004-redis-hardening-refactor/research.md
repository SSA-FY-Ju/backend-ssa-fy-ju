# Phase 0 Research: Redis 도입 및 백엔드 전면 하드닝/리팩토링

스펙에는 [NEEDS CLARIFICATION] 마커가 없으나(이해관계자가 사전에 모든 핵심 결정을 확정), 계획 단계에서 구체화가 필요한 기술 선택지가 다수 있다. 각 항목을 Decision / Rationale / Alternatives considered 형식으로 정리한다.

## 1. Redis 클라이언트 선택

- **Decision**: `spring-boot-starter-data-redis`(기본 Lettuce 드라이버) 사용
- **Rationale**: Spring Boot 기본값이라 별도 설정 최소화, Netty 기반 비동기 I/O로 커넥션당 스레드 점유가 없어 커넥션 풀 고갈 위험이 낮음(기존 프로젝트가 "Connection Pool 고갈 방지"를 최우선 원칙으로 삼는 것과 부합 — `skills/code-style-guide.md` 트랜잭션 절). thread-safe한 단일 커넥션 공유 가능.
- **Alternatives considered**: Jedis — 커넥션 풀을 직접 관리해야 하고 블로킹 I/O 기반이라 기존 "Reactive/블로킹 오버헤드 회피" 방침(RestClient 선택 이유와 동일 논리)과 맞지 않아 기각.

## 2. 분산락 라이브러리

- **Decision**: `Redisson`(`RLock`, `tryLock(waitTime, leaseTime, TimeUnit)`)
- **Rationale**: Pub/Sub 기반 대기(스핀 없음), lease time 자동 만료로 데드락 방지, Watchdog으로 장시간 작업 시 자동 락 연장 지원. Spring Data Redis만으로 분산락을 직접 구현(`SET NX PX` + Lua 스크립트 수기 작성)하는 것보다 안전하고 검증됨.
- **Alternatives considered**: Spring Integration의 `RedisLockRegistry` — 기능은 유사하나 커뮤니티/문서가 Redisson보다 적고, 사용자가 이미 "Redisson 기반 분산락"으로 명시 결정.

## 3. Refresh Token 저장 스키마

- **Decision**: 키 `refresh-token:{tokenId}` (tokenId = JWT의 `jti` 클레임), 값 = `{userId, tokenHash}` JSON, TTL = `jwt.refresh-token-expiration`(기존과 동일한 만료 정책 유지, 기본 7일). 사용자 단위 조회가 필요할 때는 `refresh-owner:{userId}` → `tokenId` Set(선택적, 다중 기기 로그아웃 확장 여지를 위해 구조만 예약, 이번 스펙 범위는 단일 세션 무효화까지)로 보조.
- **Rationale**: 기존 JPA `RefreshToken` 엔티티(`tokenHash`, `expiresAt`, `revokedAt`)의 책임을 그대로 흡수하되, TTL을 Redis가 자동 관리하므로 `revokedAt`/만료 배치 삭제가 불필요해짐(FR-001).
- **Alternatives considered**: 토큰 원문을 키로 사용 — 키 길이가 길고 로그 등에 노출 시 토큰 자체가 유출되므로 `jti` 사용이 더 안전.

## 4. Access Token 블랙리스트 전략

- **Decision**: 로그아웃/탈퇴 시 `access-blacklist:{jti}` 키를 등록하고, TTL을 **해당 Access Token의 남은 만료 시간**으로 설정(토큰 자체 만료 이후에는 블랙리스트 엔트리도 자동 삭제되어 영구 누적되지 않음). `JwtExceptionFilter`/인증 필터가 매 요청마다 블랙리스트 존재 여부만 확인(단순 `EXISTS`, O(1)).
- **Rationale**: "즉시 무효화"(SC-001)와 "저장소 자동 정리"(SC-002)를 동시에 만족하는 가장 단순한 방식.
- **Alternatives considered**: Access Token 자체에 버전 필드를 두고 사용자별 유효 버전을 Redis/DB에 저장 — 구현은 더 복잡하고, 탈퇴/로그아웃마다 버전 증가 로직이 필요해 이번 범위(개별 토큰 무효화)에는 과함.

## 5. 분산락 적용 대상 키 설계

- **Decision**:
  - `SajuResultProvider.findOrCreate` → 락 키 `lock:saju-result:{userProfileId}`
  - `ConsultationSaveService`(월별 유니크) → 락 키 `lock:career-consultation:{sajuResultId}:{yearMonth}`
  - `CompanyMatchingService.analyzeCompatibility`(`CompanyCompatibility` 생성) → 락 키 `lock:company-compatibility:{userProfileId}:{companyName}:{targetRoleCategory}`
  - `UserProfileProvider.findOrCreate` → 락 키 `lock:user-profile:{birthDate}:{birthTime}`
- **Rationale**: 각 유니크 제약과 1:1 대응하는 키를 사용해 "같은 유니크 위반을 유발하던 동시 요청"이 정확히 같은 락을 공유하도록 설계. `waitTime`은 각 외부 API 타임아웃 예산 내에서 짧게(예: 2~3초), `leaseTime`은 조회+생성(자식 엔티티 포함) 소요 시간보다 넉넉하게(예: 5~10초) 설정해 Watchdog 없이도 안전.
- **Alternatives considered**: 전역 단일 락 — 서로 다른 사용자/프로필의 요청까지 직렬화되어 처리량이 크게 저하되므로 기각.

## 6. MySQL Testcontainers 통합 테스트 정책

- **Decision**: 기존 `build.gradle`에 이미 `spring-boot-testcontainers` + `testcontainers:mysql`이 있으므로 이를 그대로 재사용. 신규 Redis 통합 테스트는 `testcontainers:redis`(또는 GenericContainer로 `redis:7-alpine`) 컨테이너를 추가.
- **Rationale**: H2와 MySQL의 Dialect 차이(제약조건 이름 파싱 등)로 인한 오탐을 피하기 위해 이미 채택된 정책을 그대로 따름(이해관계자 결정 #3).
- **Alternatives considered**: Embedded Redis(단일 프로세스 임베디드 라이브러리) — 실제 Redis와 동작 차이(Lua 스크립트, 클러스터 명령 지원 등) 위험이 있어 Testcontainers가 더 신뢰도 높음.

## 7. `DataIntegrityViolationException` vs 동시성 경합 구분

- **Decision**: 분산락 도입 이후, 락으로 보호되는 "조회 후 생성" 경로에서는 유니크 제약 위반이 정상적으로는 발생하지 않아야 한다. 따라서 락 보호 구간 내부에서 `DataIntegrityViolationException`이 발생하면 이는 **진짜 데이터 무결성 위반**(예: FK 참조 무결성 깨짐, NOT NULL 위반)으로 간주하고 그대로 사용자 오류로 전환한다. 락 자체를 획득하지 못한 경우(`tryLock` 실패)는 별도의 "일시적 경합" 오류(재시도 유도 메시지)로 구분한다.
- **Rationale**: 예외 타입 하나로 두 가지 다른 원인(제약 위반 vs 경합)을 구분하려던 기존 방식(제약조건 이름 문자열 파싱)의 모호함을 제거.
- **Alternatives considered**: 기존처럼 제약조건 이름으로 분기 유지 — H2/MySQL 간 파싱 차이 문제가 여전히 남아 이해관계자 결정 #3과 상충.

## 8. `AnalysisType`/`FeedbackType` 통합 매핑

- **Decision**: `FeedbackType.CAREER_TIMING → AnalysisType.SAJU`, `FeedbackType.CONSULTATION → AnalysisType.CAREER_CONSULTATION`, `FeedbackType.COMPATIBILITY → AnalysisType.COMPANY_COMPATIBILITY`로 매핑 후 `FeedbackType` 삭제. `UserSatisfactionFeedback`, `AdminFeedbackController/Service/QueryRepository`, `FeedbackListDTO`, `FeedbackStatDTO`, `SatisfactionFeedbackRequest`의 참조를 전량 `AnalysisType`으로 치환.
- **Rationale**: 두 Enum이 서로 다른 이름으로 동일한 세 가지 분석 유형(사주/컨설팅/궁합)을 나타내고 있어 하나로 합쳐도 의미 손실이 없음.
- **Open item for tasks phase**: 기존 DB에 문자열로 저장된 `feedback_type` 컬럼 값(`CAREER_TIMING`/`CONSULTATION`/`COMPATIBILITY`)이 있다면 `SAJU`/`CAREER_CONSULTATION`/`COMPANY_COMPATIBILITY`로 값 자체를 변환하는 데이터 마이그레이션이 필요할 수 있음 — 구현 단계에서 `UserSatisfactionFeedback` 컬럼 매핑 방식(name() 저장 여부) 확인 후 확정.

## 9. 회원가입 생년월일시(선택) 처리 위치

- **Decision**: `User` 엔티티에 `birthDate`(필수, `LocalDate`, not null)와 `birthTime`(선택, `LocalTime`, nullable) 컬럼을 신설한다. 기존 `career/entity/UserProfile`(`birthDate`+`birthTime` 유니크 조합, 사주 정본 캐시 키 역할)과는 별개로 유지한다 — `UserProfile`은 "동일 생년월일시 조합의 재사용 가능한 계산 캐시 키"라는 책임이고, `User.birthDate/birthTime`은 "가입자 개인정보"라는 책임이라 목적이 다르다. 첫 분석 요청 시점에 기존 `UserProfileProvider.findOrCreate(birthDate, birthTime-or-dummy)` 흐름을 그대로 재사용해 `UserProfile`을 연결한다.
- **Rationale**: 기존 `UserProfileProvider`/`SajuResultProvider`의 "여러 사용자가 같은 생년월일시면 같은 정본을 공유"하는 설계를 깨지 않으면서, "가입 시점에 개인정보로서의 생년월일시를 받는다"는 신규 요구를 분리해서 만족.
- **Alternatives considered**: 가입 시 바로 `UserProfile`을 생성/연결 — 분석을 한 번도 요청하지 않은 사용자까지 `UserProfile`/`SajuResult` 생성 파이프라인을 태우게 되어 B1(정본은 최초 분석 시에만 생성)과 충돌하므로 기각.
- **더미 시간 값**: `birthTime`이 없을 때 FastAPI 호출용 더미 값은 기존 공공데이터 API의 `CompatibilityConstants.DEFAULT_FOUNDING_TIME`("12:00")과 동일한 관례를 따라 `12:00`을 재사용(신규 상수 클래스에 명시적으로 선언, 매직 스트링 금지 원칙 준수).

## 10. 쿠키 기반 Refresh Token 전달 규칙

- **Decision**: `Set-Cookie` 속성 — `HttpOnly=true`, `Secure`(기존 `server.cookie.secure` 설정값 재사용, 로컬은 `application-local.yaml`에서 `false` 오버라이드 기존 관례 유지), `Path=/api/auth`(이해관계자 결정 #2), `SameSite=Strict`(CSRF 완화, CORS 화이트리스트와 병행), `Max-Age`=`jwt.refresh-token-expiration`(ms→sec 환산).
- **Rationale**: `Path=/api/auth`로 좁히면 브라우저가 그 외 API 요청에는 쿠키 자체를 전송하지 않으므로, `TokenValidationFilter`가 "갱신/로그아웃 경로에서만 검사"하도록 좁히는 결정과 자연스럽게 맞물림.
- **Alternatives considered**: `Path=/`(전역) — 모든 요청에 쿠키가 실려 불필요한 페이로드 증가 및 필터 분기 로직이 더 복잡해짐.

## 11. CORS `allowedHeaders` 화이트리스트

- **Decision**: `allowedHeaders = List.of("Authorization", "Content-Type")`로 고정.
- **Rationale**: 현재 클라이언트가 실제로 보내는 헤더는 `Authorization`(Access Token)과 `Content-Type`(JSON) 뿐이며, `exposedHeaders`(Authorization, Refresh-Token — A2 이후 Refresh-Token 헤더는 쿠키로 대체되므로 함께 정리)는 별도로 재검토.
- **Alternatives considered**: 없음 — 스펙에서 명시적으로 지정된 화이트리스트.

## 12. `AbstractJwtValidationFilter` 공통화 범위

- **Decision**: 사용자용(`JwtAuthenticationFilter` 계열)과 관리자용(`AdminCookieJwtFilter`) 필터가 공통으로 수행하는 "토큰 파싱 → 서명/만료 검증 → 블랙리스트 확인 → SecurityContext 설정" 단계를 추상 클래스의 템플릿 메서드로 추출하고, "토큰 추출 위치(Authorization 헤더 vs 쿠키)"와 "권한 매핑(ROLE_USER vs ROLE_ADMIN)"만 하위 클래스가 오버라이드.
- **Rationale**: 두 필터가 이미 별도 Security 설정(`SecurityConfig`/`AdminSecurityConfig`)으로 분리되어 있어 완전 통합은 과도하고, 중복 로직만 추출하는 것이 기존 "두 개의 독립된 Spring Security 설정" 아키텍처와 부합.
