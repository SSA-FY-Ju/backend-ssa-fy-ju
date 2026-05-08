package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ssafy.SSAju.career.util.JobCategoryEnum;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(
        name = "company_compatibility",
        uniqueConstraints = @UniqueConstraint(
                name = "unique_user_company_role",
                columnNames = {"user_profile_id", "company_name", "target_role_category"}
        )
)
public class CompanyCompatibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role_category", nullable = false, length = 30)
    private JobCategoryEnum targetRoleCategory;

    @Column(name = "target_role_detail_name")
    private String targetRoleDetailName;

    @Column(name = "compatibility_score", nullable = false)
    private Integer compatibilityScore;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public CompanyCompatibility(UserProfile userProfile, String companyName,
                                 JobCategoryEnum targetRoleCategory, String targetRoleDetailName,
                                 Integer compatibilityScore, String summary) {
        this.userProfile = userProfile;
        this.companyName = companyName;
        this.targetRoleCategory = targetRoleCategory;
        this.targetRoleDetailName = targetRoleDetailName;
        this.compatibilityScore = compatibilityScore;
        this.summary = summary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompanyCompatibility that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
