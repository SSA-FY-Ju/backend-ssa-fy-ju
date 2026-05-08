package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
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
@Table(name = "analysis_breakdown")
public class AnalysisBreakdown {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compatibility_id", nullable = false, unique = true)
    private CompanyCompatibility companyCompatibility;

    @Column(name = "character_match", nullable = false)
    private Integer characterMatch;

    @Column(name = "potential_synergy", nullable = false)
    private Integer potentialSynergy;

    @Column(name = "long_term_stability", nullable = false)
    private Integer longTermStability;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AnalysisBreakdown(CompanyCompatibility companyCompatibility, Integer characterMatch,
                              Integer potentialSynergy, Integer longTermStability) {
        this.companyCompatibility = companyCompatibility;
        this.characterMatch = characterMatch;
        this.potentialSynergy = potentialSynergy;
        this.longTermStability = longTermStability;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnalysisBreakdown that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
