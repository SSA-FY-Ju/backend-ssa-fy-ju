-- V4: CompanyCompatibility version 기반 이력 관리 도입
-- 멱등성 보장: Hibernate ddl-auto로 생성된 기존 DB에서도 안전하게 실행됨

-- 1. 기존 UNIQUE 제약 제거 (없으면 무시)
ALTER TABLE company_compatibility
    DROP INDEX IF EXISTS unique_user_company_role;

-- 2. version 컬럼 추가 (없는 경우에만)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'company_compatibility' AND COLUMN_NAME = 'version');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE company_compatibility ADD COLUMN version INT NOT NULL DEFAULT 1',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. analyzed_at 컬럼 추가 (없는 경우에만)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'company_compatibility' AND COLUMN_NAME = 'analyzed_at');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE company_compatibility ADD COLUMN analyzed_at DATETIME(6) DEFAULT NULL',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3-1. 기존 행: created_at 값으로 백필
UPDATE company_compatibility
    SET analyzed_at = created_at
WHERE analyzed_at IS NULL;

-- 3-2. 백필 완료 후 NOT NULL 제약 추가
ALTER TABLE company_compatibility
    MODIFY COLUMN analyzed_at DATETIME(6) NOT NULL;

-- 4. 새로운 UNIQUE 제약 추가 (없는 경우에만)
SET @constraint_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'company_compatibility'
    AND CONSTRAINT_NAME = 'uk_user_company_role_version');
SET @sql = IF(@constraint_exists = 0,
    'ALTER TABLE company_compatibility ADD CONSTRAINT uk_user_company_role_version UNIQUE (user_id, company_name, target_role_category, version)',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
