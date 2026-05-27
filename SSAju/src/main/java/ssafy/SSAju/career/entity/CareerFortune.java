package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "career_fortune")
public class CareerFortune {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saju_result_id", nullable = false, unique = true)
    private SajuResult sajuResult;

    @Column(name = "favored_period", nullable = false, length = 2)
    private String favoredPeriod;

    @Column(name = "confidence_score", nullable = false)
    private Integer confidenceScore;

    @Column(name = "reasoning", columnDefinition = "text")
    private String reasoning;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public CareerFortune(SajuResult sajuResult, String favoredPeriod, Integer confidenceScore, String reasoning) {
        this.sajuResult = sajuResult;
        this.favoredPeriod = favoredPeriod;
        this.confidenceScore = confidenceScore;
        this.reasoning = reasoning;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CareerFortune that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
