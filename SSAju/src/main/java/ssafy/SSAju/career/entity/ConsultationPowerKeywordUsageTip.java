package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_power_keyword_usage_tip")
public class ConsultationPowerKeywordUsageTip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_power_keywords_id", nullable = false)
    private ConsultationPowerKeywords consultationPowerKeywords;

    @Column(name = "tip", columnDefinition = "text", nullable = false)
    private String tip;

    @Builder
    public ConsultationPowerKeywordUsageTip(ConsultationPowerKeywords consultationPowerKeywords, String tip) {
        this.consultationPowerKeywords = consultationPowerKeywords;
        this.tip = tip;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationPowerKeywordUsageTip that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
