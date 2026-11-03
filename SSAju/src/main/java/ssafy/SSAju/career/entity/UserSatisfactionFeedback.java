package ssafy.SSAju.career.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.enums.SatisfactionStatus;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.Instant;

/**
 * 분석 결과 한 건에 대한 만족도 피드백. 분석당 0개 또는 1개다.
 *
 * <p>대상 FK는 {@code saju_id} 하나뿐이다. 유형별로 FK를 나누지 않고 분석 유형은 연결된
 * {@link Saju#getAnalysisType()}에서 읽는다 — 같은 값을 두 곳에 저장하면 서로 어긋날 수 있다.
 *
 * <p>작성자는 결과 소유자와 같아야 하며 NOT NULL이다. 회원 탈퇴 시 피드백은 hard delete되므로
 * 탈퇴 계정을 가리키는 행은 남지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(
        name = "user_satisfaction_feedback",
        indexes = @Index(name = "idx_feedback_created", columnList = "created_at"),
        uniqueConstraints = @UniqueConstraint(name = "uk_feedback_saju", columnNames = {"saju_id"})
)
public class UserSatisfactionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 피드백 대상 분석. 분석당 하나만 작성할 수 있도록 테이블 레벨에서 이름 있는 UNIQUE 제약
     * ({@code uk_feedback_saju})을 건다 — 제약 이름이 정해져 있어야 동시 중복 제출을 다른 무결성
     * 오류와 구분해 409로 바꿀 수 있다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saju_id", nullable = false)
    private Saju saju;

    @Enumerated(EnumType.STRING)
    @Column(name = "satisfaction_status", nullable = false, length = 32)
    private SatisfactionStatus satisfactionStatus;

    @Column(name = "feedback_content", length = 500)
    private String feedbackContent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public UserSatisfactionFeedback(User user,
                                    Saju saju,
                                    SatisfactionStatus satisfactionStatus,
                                    String feedbackContent) {
        if (user == null || saju == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.FEEDBACK_TARGET_REQUIRED.getMessage());
        }
        if (satisfactionStatus == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.FEEDBACK_TARGET_REQUIRED.getMessage());
        }
        this.user = user;
        this.saju = saju;
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
