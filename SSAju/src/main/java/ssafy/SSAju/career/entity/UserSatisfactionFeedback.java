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

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
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

    // TODO: Phase 2 인증 추가 시 User 필드 추가 필요
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "user_id", nullable = false)
    // private User user;
    //
    // 또한 @Table의 uniqueConstraints 추가:
    // uniqueConstraints = {
    //     @UniqueConstraint(name = "uk_feedback_saju_user", columnNames = {"saju_result_id", "user_id"})
    // }
    // → 사용자당 SajuResult당 피드백 1개만 가능

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
    public UserSatisfactionFeedback(SajuResult sajuResult, FeedbackType feedbackType,
                                    SatisfactionStatus satisfactionStatus, String feedbackContent) {
        this.sajuResult = sajuResult;
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
