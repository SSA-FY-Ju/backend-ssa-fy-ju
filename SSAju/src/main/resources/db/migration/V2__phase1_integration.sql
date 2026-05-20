-- V2: Phase 1 Integration - career 분석 결과에 user_id FK 추가

ALTER TABLE saju_result
    ADD COLUMN user_id BIGINT,
    ADD CONSTRAINT fk_saju_result_user FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_saju_result_user_id ON saju_result (user_id);

ALTER TABLE company_compatibility
    ADD COLUMN user_id BIGINT,
    ADD CONSTRAINT fk_company_compatibility_user FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_company_compatibility_user_id ON company_compatibility (user_id);
