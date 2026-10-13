-- MySQL: Redis로 대체된 기존 JPA Refresh Token 테이블 정리.
-- 수동 배포용 DDL이며 애플리케이션 시작 시 자동 실행하지 않는다.
-- Redis 인증 전환 버전이 모든 인스턴스에 배포되고 이전 버전이 종료된 뒤 적용한다.
-- 적용 전 대상 DB와 테이블 백업을 확인한다. DROP은 자동 롤백되지 않는다.
-- 이전 DB 인증 버전으로 되돌릴 경우 스키마 복구와 사용자 재로그인이 필요하다.
DROP TABLE IF EXISTS refresh_token;
