-- V6: consultation_pivot_point.month 확장 + company_compatibility UNIQUE 제약 수정

-- 1. consultation_pivot_point.month 컬럼 확장
-- OpenAI가 반환하는 month 값이 7자 초과 가능 (예: "January 2025", "Sept 2025")
ALTER TABLE consultation_pivot_point
    MODIFY COLUMN month VARCHAR(20) NOT NULL;

-- 2. company_compatibility UNIQUE 제약 수정
-- 사용자 격리 강화: user_profile_id를 명시적으로 추가하여
-- 동일 생년월일 다른 사용자가 분석 결과를 공유하지 않도록 보장
ALTER TABLE company_compatibility
    DROP INDEX uk_user_company_role_version;

ALTER TABLE company_compatibility
    ADD CONSTRAINT uk_user_company_role_version
    UNIQUE (user_id, user_profile_id, company_name, target_role_category, version);
