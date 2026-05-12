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
import ssafy.SSAju.career.enums.ForecastStatus;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "monthly_forecast")
public class MonthlyForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compatibility_id", nullable = false)
    private CompanyCompatibility companyCompatibility;

    @Min(1) @Max(12)
    @Column(name = "month", nullable = false)
    private Integer month;

    @Min(0) @Max(100)
    @Column(name = "score", nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ForecastStatus status;

    @Column(name = "advice", columnDefinition = "text")
    private String advice;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public MonthlyForecast(CompanyCompatibility companyCompatibility, Integer month,
                            Integer score, ForecastStatus status, String advice) {
        this.companyCompatibility = companyCompatibility;
        this.month = month;
        this.score = score;
        this.status = status;
        this.advice = advice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MonthlyForecast that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
