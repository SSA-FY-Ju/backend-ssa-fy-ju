package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "target_role_analysis")
public class TargetRoleAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compatibility_id", nullable = false, unique = true)
    private CompanyCompatibility companyCompatibility;

    @Min(0) @Max(100)
    @Column(name = "match_score", nullable = false)
    private Integer matchScore;

    @Column(name = "synergy", columnDefinition = "text")
    private String synergy;

    @Column(name = "warning", columnDefinition = "text")
    private String warning;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public TargetRoleAnalysis(CompanyCompatibility companyCompatibility, Integer matchScore,
                               String synergy, String warning) {
        this.companyCompatibility = companyCompatibility;
        this.matchScore = matchScore;
        this.synergy = synergy;
        this.warning = warning;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TargetRoleAnalysis that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
