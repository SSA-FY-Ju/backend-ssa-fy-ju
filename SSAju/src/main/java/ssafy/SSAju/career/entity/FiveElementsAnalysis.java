package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ssafy.SSAju.career.converter.IntegerMapConverter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "five_elements_analysis")
public class FiveElementsAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compatibility_id", nullable = false, unique = true)
    private CompanyCompatibility companyCompatibility;

    @Convert(converter = IntegerMapConverter.class)
    @Column(name = "user_distribution", columnDefinition = "text")
    private Map<String, Integer> userDistribution;

    @Convert(converter = IntegerMapConverter.class)
    @Column(name = "company_distribution", columnDefinition = "text")
    private Map<String, Integer> companyDistribution;

    @Column(name = "synergy_description", columnDefinition = "text")
    private String synergyDescription;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public FiveElementsAnalysis(CompanyCompatibility companyCompatibility,
                                 Map<String, Integer> userDistribution,
                                 Map<String, Integer> companyDistribution,
                                 String synergyDescription) {
        this.companyCompatibility = companyCompatibility;
        this.userDistribution = userDistribution;
        this.companyDistribution = companyDistribution;
        this.synergyDescription = synergyDescription;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FiveElementsAnalysis that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
