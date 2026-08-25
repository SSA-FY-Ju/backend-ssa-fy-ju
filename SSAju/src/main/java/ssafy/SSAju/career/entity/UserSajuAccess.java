package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ssafy.SSAju.entity.User;

import java.time.Instant;

/**
 * User와 정본 SajuResult 사이의 접근 매핑.
 *
 * <p>SajuResult는 동일 생년월일시를 가진 사용자들 사이에 공유되는 정본이므로,
 * 어떤 사용자가 어떤 정본에 접근 가능한지는 이 매핑 테이블로 별도 관리한다(B1).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(
        name = "user_saju_access",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_saju_access_user_saju_result",
                columnNames = {"user_id", "saju_result_id"}
        ),
        // 유니크 제약은 (user_id, saju_result_id) 순서라 saju_result_id 단독 조인(예:
        // AnalysisHistoryRepository의 마이페이지 이력 조회)에는 인덱스를 못 탄다.
        indexes = @Index(name = "idx_user_saju_access_saju_result", columnList = "saju_result_id")
)
public class UserSajuAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saju_result_id", nullable = false)
    private SajuResult sajuResult;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public UserSajuAccess(User user, SajuResult sajuResult) {
        this.user = user;
        this.sajuResult = sajuResult;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserSajuAccess that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
