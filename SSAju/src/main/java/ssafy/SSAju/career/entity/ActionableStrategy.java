package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 기업 궁합 지원 전략.
 *
 * <p>정규화 이력:
 * - interview_keywords (TEXT/JSON) → {@link ActionableKeyword} 테이블
 * - lucky_days (TEXT/JSON) → {@link LuckyDay} 테이블
 */
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

    @Column(name = "weakness_defense", columnDefinition = "text")
    private String weaknessDefense;

    @Column(name = "preferred_time")
    private String preferredTime;

    @OneToMany(mappedBy = "actionableStrategy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<ActionableKeyword> keywords = new ArrayList<>();

    @OneToMany(mappedBy = "actionableStrategy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<LuckyDay> luckyDays = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ActionableStrategy(CompanyCompatibility companyCompatibility,
                               String weaknessDefense, String preferredTime) {
        this.companyCompatibility = companyCompatibility;
        this.weaknessDefense = weaknessDefense;
        this.preferredTime = preferredTime;
    }

    public void assignKeywords(List<ActionableKeyword> keywordList) {
        this.keywords = keywordList;
    }

    public void assignLuckyDays(List<LuckyDay> luckyDayList) {
        this.luckyDays = luckyDayList;
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
