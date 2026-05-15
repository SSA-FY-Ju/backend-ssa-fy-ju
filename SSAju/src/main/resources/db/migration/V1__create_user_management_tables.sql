-- V1: User Management 테이블 생성 (Phase 2)

CREATE TABLE IF NOT EXISTS users (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    email             VARCHAR(255)    NOT NULL,
    password_hash     VARCHAR(255)    NOT NULL,
    name              VARCHAR(100)    NOT NULL,
    role              VARCHAR(20)     NOT NULL DEFAULT 'USER',
    status            VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    last_login_at     DATETIME,
    terms_agreed_at   DATETIME        NOT NULL,
    privacy_agreed_at DATETIME        NOT NULL,
    deleted_at        DATETIME,
    created_at        DATETIME        NOT NULL,
    updated_at        DATETIME        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_email (email)
);

CREATE TABLE IF NOT EXISTS refresh_token (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    token_hash  VARCHAR(512)    NOT NULL,
    expires_at  DATETIME        NOT NULL,
    revoked_at  DATETIME,
    created_at  DATETIME        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS daily_api_usage (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    request_count INT         NOT NULL DEFAULT 0,
    usage_date    DATE        NOT NULL,
    created_at    DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_date (user_id, usage_date),
    CONSTRAINT fk_daily_api_usage_user FOREIGN KEY (user_id) REFERENCES users (id)
);
-- UNIQUE INDEX: Race Condition 방지 (Atomic UPDATE + DataIntegrityViolationException 처리)
CREATE INDEX idx_daily_api_usage_user_date ON daily_api_usage (user_id, usage_date);

CREATE TABLE IF NOT EXISTS login_attempt (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    email          VARCHAR(255) NOT NULL,
    success        BOOLEAN      NOT NULL,
    ip_address     VARCHAR(45),
    failure_reason VARCHAR(20),
    attempted_at   DATETIME     NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS user_satisfaction_feedback (
    id                              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id                         BIGINT,
    saju_result_id                  BIGINT      NOT NULL,
    feedback_type                   VARCHAR(50) NOT NULL,
    satisfaction_status             VARCHAR(50) NOT NULL,
    feedback_content                VARCHAR(500),
    created_at                      DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feedback_saju_user (saju_result_id, user_id),
    INDEX idx_feedback_saju_result_created (saju_result_id, created_at),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_feedback_saju_result FOREIGN KEY (saju_result_id) REFERENCES saju_result (id)
);
