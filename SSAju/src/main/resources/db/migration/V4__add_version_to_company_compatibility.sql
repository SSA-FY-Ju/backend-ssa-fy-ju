-- V4: CompanyCompatibility version 기반 이력 관리 도입
-- UNIQUE 제약을 user_profile_id 기준에서 user_id + version 기준으로 교체

-- 1. 기존 UNIQUE 제약 제거 (user_profile_id, company_name, target_role_category 기준)
ALTER TABLE company_compatibility
    DROP INDEX unique_user_company_role;

-- 2. version 컬럼 추가 (기존 데이터는 1로 설정)
ALTER TABLE company_compatibility
    ADD COLUMN version INT NOT NULL DEFAULT 1;

-- 3. analyzed_at 컬럼 추가 (먼저 nullable로 추가 후 기존 데이터 백필)
ALTER TABLE company_compatibility
    ADD COLUMN analyzed_at DATETIME(6) DEFAULT NULL;

-- 3-1. 기존 행: created_at 값으로 백필하여 마이페이지 정렬 시간축 보존
UPDATE company_compatibility
    SET analyzed_at = created_at
WHERE analyzed_at IS NULL;

-- 3-2. 백필 완료 후 NOT NULL 제약 추가
ALTER TABLE company_compatibility
    MODIFY COLUMN analyzed_at DATETIME(6) NOT NULL;

-- 4. 새로운 UNIQUE 제약 추가 (user_id 기준 + version)
ALTER TABLE company_compatibility
    ADD CONSTRAINT uk_user_company_role_version
    UNIQUE (user_id, company_name, target_role_category, version);
