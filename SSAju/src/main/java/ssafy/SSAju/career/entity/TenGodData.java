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
@Table(name = "ten_god_data",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"saju_result_id", "ten_god_name"},
                        name = "uk_ten_god_data_result_name")
        })
public class TenGodData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saju_result_id", nullable = false)
    private SajuResult sajuResult;

    @Column(name = "ten_god_name", nullable = false, length = 10)
    private String tenGodName;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public TenGodData(SajuResult sajuResult, String tenGodName, Integer score) {
        this.sajuResult = sajuResult;
        this.tenGodName = tenGodName;
        this.score = score;
        this.createdAt = LocalDateTime.now();
    }
}
