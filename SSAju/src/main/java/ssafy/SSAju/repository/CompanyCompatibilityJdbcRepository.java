package ssafy.SSAju.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.career.entity.CompanyCompatibility;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;


@Repository
@RequiredArgsConstructor
public class CompanyCompatibilityJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * UNIQUE(user_id, company_name, target_role_category, version) 제약으로 동일 버전 중복 삽입 방지.
     *
     * @return 1 (신규 삽입), 0 (이미 존재)
     */
    public int insertOrIgnore(CompanyCompatibility entity) {
        return jdbcTemplate.update(
                "INSERT IGNORE INTO company_compatibility " +
                        "(user_profile_id, user_id, company_name, target_role_category, target_role_detail_name, " +
                        "compatibility_score, summary, version, analyzed_at, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                entity.getUserProfile().getId(),
                entity.getUser().getId(),
                entity.getCompanyName(),
                entity.getTargetRoleCategory().name(),
                entity.getTargetRoleDetailName(),
                entity.getCompatibilityScore(),
                entity.getSummary(),
                entity.getVersion(),
                entity.getAnalyzedAt()
        );
    }

    /**
     * 재분석 시 새로운 버전으로 저장합니다.
     * user_id + company_name + target_role_category 기준으로 기존 최대 버전을 조회하고 1 증가시켜 저장합니다.
     *
     * @return 생성된 레코드의 id
     */
    public Long insertNewVersion(CompanyCompatibility entity) {
        Integer maxVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM company_compatibility " +
                "WHERE user_id = ? AND company_name = ? AND target_role_category = ?",
                Integer.class,
                entity.getUser().getId(),
                entity.getCompanyName(),
                entity.getTargetRoleCategory().name()
        );
        int nextVersion = (maxVersion != null ? maxVersion : 0) + 1;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO company_compatibility " +
                    "(user_profile_id, user_id, company_name, target_role_category, target_role_detail_name, " +
                    "compatibility_score, summary, version, analyzed_at, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, entity.getUserProfile().getId());
            ps.setLong(2, entity.getUser().getId());
            ps.setString(3, entity.getCompanyName());
            ps.setString(4, entity.getTargetRoleCategory().name());
            ps.setString(5, entity.getTargetRoleDetailName());
            ps.setInt(6, entity.getCompatibilityScore());
            ps.setString(7, entity.getSummary());
            ps.setInt(8, nextVersion);
            ps.setObject(9, LocalDateTime.now());
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }
}
