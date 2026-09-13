package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.SatisfactionStatus;
import ssafy.SSAju.entity.User;

import java.time.Instant;
import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(
    name = "user_satisfaction_feedback",
    indexes = @Index(name = "idx_feedback_created", columnList = "created_at")
)
public class UserSatisfactionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_compatibility_id")
    private CompanyCompatibility companyCompatibility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_consultation_id")
    private CareerConsultation careerConsultation;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false)
    private AnalysisType feedbackType;

    @Enumerated(EnumType.STRING)
    @Column(name = "satisfaction_status", nullable = false)
    private SatisfactionStatus satisfactionStatus;

    @Column(name = "feedback_content", length = 500)
    private String feedbackContent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public UserSatisfactionFeedback(User user,
                                    CompanyCompatibility companyCompatibility,
                                    CareerConsultation careerConsultation,
                                    AnalysisType feedbackType,
                                    SatisfactionStatus satisfactionStatus,
                                    String feedbackContent) {
        if ((companyCompatibility == null) == (careerConsultation == null)) {
            throw new IllegalArgumentException(
                    "피드백 대상은 회사 궁합 또는 커리어 컨설팅 중 정확히 하나만 지정해야 합니다.");
        }
        this.user = user;
        this.companyCompatibility = companyCompatibility;
        this.careerConsultation = careerConsultation;
        this.feedbackType = feedbackType;
        this.satisfactionStatus = satisfactionStatus;
        this.feedbackContent = feedbackContent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserSatisfactionFeedback that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
