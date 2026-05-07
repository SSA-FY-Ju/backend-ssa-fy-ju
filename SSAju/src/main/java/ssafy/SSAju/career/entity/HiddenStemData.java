package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "hidden_stem_data",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"saju_result_id", "earthly_branch", "hidden_stem"},
                        name = "uk_hidden_stem_data_result_branch_stem")
        })
public class HiddenStemData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saju_result_id", nullable = false)
    private SajuResult sajuResult;

    @Column(name = "earthly_branch", nullable = false, length = 5)
    private String earthlyBranch;

    @Column(name = "hidden_stem", nullable = false, length = 5)
    private String hiddenStem;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public HiddenStemData(SajuResult sajuResult, String earthlyBranch, String hiddenStem) {
        this.sajuResult = sajuResult;
        this.earthlyBranch = earthlyBranch;
        this.hiddenStem = hiddenStem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HiddenStemData that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
