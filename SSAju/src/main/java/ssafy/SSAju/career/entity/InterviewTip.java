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
@Table(name = "interview_tip")
public class InterviewTip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_consultation_id", nullable = false)
    private CareerConsultation careerConsultation;

    @Column(name = "tip_text", columnDefinition = "text", nullable = false)
    private String tipText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public InterviewTip(CareerConsultation careerConsultation, String tipText) {
        this.careerConsultation = careerConsultation;
        this.tipText = tipText;
        this.createdAt = LocalDateTime.now();
    }
}
