-- 로그인 없이 분석 불가 정책 적용: user_id NOT NULL 제약 추가
-- 기존 user_id가 NULL인 레코드는 삭제 후 마이그레이션 실행

DELETE FROM saju_result WHERE user_id IS NULL;
DELETE FROM company_compatibility WHERE user_id IS NULL;

ALTER TABLE saju_result
    MODIFY COLUMN user_id BIGINT NOT NULL;

ALTER TABLE company_compatibility
    MODIFY COLUMN user_id BIGINT NOT NULL;
