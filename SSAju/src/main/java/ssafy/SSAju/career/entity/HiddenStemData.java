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
@Table(name = "hidden_stem_data")
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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public HiddenStemData(SajuResult sajuResult, String earthlyBranch, String hiddenStem) {
        this.sajuResult = sajuResult;
        this.earthlyBranch = earthlyBranch;
        this.hiddenStem = hiddenStem;
        this.createdAt = LocalDateTime.now();
    }
}
