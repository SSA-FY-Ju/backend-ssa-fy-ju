package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ssafy.SSAju.career.converter.StringListConverter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "actionable_strategy")
public class ActionableStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compatibility_id", nullable = false, unique = true)
    private CompanyCompatibility companyCompatibility;

    @Convert(converter = StringListConverter.class)
    @Column(name = "interview_keywords", columnDefinition = "text")
    private List<String> interviewKeywords;

    @Column(name = "weakness_defense", columnDefinition = "text")
    private String weaknessDefense;

    @Convert(converter = StringListConverter.class)
    @Column(name = "lucky_days", columnDefinition = "text")
    private List<String> luckyDays;

    @Column(name = "preferred_time")
    private String preferredTime;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ActionableStrategy(CompanyCompatibility companyCompatibility, List<String> interviewKeywords,
                               String weaknessDefense, List<String> luckyDays, String preferredTime) {
        this.companyCompatibility = companyCompatibility;
        this.interviewKeywords = interviewKeywords;
        this.weaknessDefense = weaknessDefense;
        this.luckyDays = luckyDays;
        this.preferredTime = preferredTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionableStrategy that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
