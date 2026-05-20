package ssafy.SSAju.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.career.entity.CompanyCompatibility;


@Repository
@RequiredArgsConstructor
public class CompanyCompatibilityJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * UNIQUE(user_profile_id, company_name, target_role_category) 제약으로 중복 삽입 방지.
     *
     * @return 1 (신규 삽입), 0 (이미 존재)
     */
    public int insertOrIgnore(CompanyCompatibility entity) {
        return jdbcTemplate.update(
                "INSERT IGNORE INTO company_compatibility " +
                        "(user_profile_id, user_id, company_name, target_role_category, target_role_detail_name, " +
                        "compatibility_score, summary, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
                entity.getUserProfile().getId(),
                entity.getUser().getId(),
                entity.getCompanyName(),
                entity.getTargetRoleCategory().name(),
                entity.getTargetRoleDetailName(),
                entity.getCompatibilityScore(),
                entity.getSummary()
        );
    }
}
