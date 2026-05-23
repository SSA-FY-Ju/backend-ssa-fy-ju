package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ssafy.SSAju.career.enums.FeedbackType;
import ssafy.SSAju.career.enums.SatisfactionStatus;
import ssafy.SSAju.entity.User;

import java.time.LocalDateTime;

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
    private FeedbackType feedbackType;

    @Enumerated(EnumType.STRING)
    @Column(name = "satisfaction_status", nullable = false)
    private SatisfactionStatus satisfactionStatus;

    @Column(name = "feedback_content", length = 500)
    private String feedbackContent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserSatisfactionFeedback(User user,
                                    CompanyCompatibility companyCompatibility,
                                    CareerConsultation careerConsultation,
                                    FeedbackType feedbackType,
                                    SatisfactionStatus satisfactionStatus,
                                    String feedbackContent) {
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
