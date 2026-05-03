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
@Table(name = "industry")
public class Industry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_consultation_id", nullable = false)
    private CareerConsultation careerConsultation;

    @Column(name = "industry_name", nullable = false)
    private String industryName;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Industry(CareerConsultation careerConsultation, String industryName, String reason) {
        this.careerConsultation = careerConsultation;
        this.industryName = industryName;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }
}
