package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.SSAju.career.enums.FeedbackType;
import ssafy.SSAju.career.enums.SatisfactionStatus;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "user_satisfaction_feedback",
    indexes = @Index(name = "idx_feedback_saju_result_created", columnList = "saju_result_id, created_at")
)
public class UserSatisfactionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saju_result_id", nullable = false)
    private SajuResult sajuResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false)
    private FeedbackType feedbackType;

    @Enumerated(EnumType.STRING)
    @Column(name = "satisfaction_status", nullable = false)
    private SatisfactionStatus satisfactionStatus;

    @Column(name = "feedback_content", length = 500)
    private String feedbackContent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserSatisfactionFeedback(SajuResult sajuResult, FeedbackType feedbackType,
                                    SatisfactionStatus satisfactionStatus, String feedbackContent) {
        this.sajuResult = sajuResult;
        this.feedbackType = feedbackType;
        this.satisfactionStatus = satisfactionStatus;
        this.feedbackContent = feedbackContent;
        this.createdAt = LocalDateTime.now();
    }
}
