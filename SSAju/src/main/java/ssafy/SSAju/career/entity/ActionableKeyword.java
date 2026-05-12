package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 지원 전략의 면접 키워드.
 * ActionableStrategy의 interview_keywords TEXT(JSON) 컬럼을 정규화한 테이블.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "actionable_keyword")
public class ActionableKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actionable_strategy_id", nullable = false)
    private ActionableStrategy actionableStrategy;

    @Column(name = "keyword", nullable = false)
    private String keyword;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ActionableKeyword(ActionableStrategy actionableStrategy, String keyword, Integer displayOrder) {
        this.actionableStrategy = actionableStrategy;
        this.keyword = keyword;
        this.displayOrder = displayOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionableKeyword that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
