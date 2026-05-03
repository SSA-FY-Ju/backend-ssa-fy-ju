package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public CareerFortune(SajuResult sajuResult, String favoredPeriod, Integer confidenceScore, String reasoning) {
        this.sajuResult = sajuResult;
        this.favoredPeriod = favoredPeriod;
        this.confidenceScore = confidenceScore;
        this.reasoning = reasoning;
        this.createdAt = LocalDateTime.now();
    }
}
