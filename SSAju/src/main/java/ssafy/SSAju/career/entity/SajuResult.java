package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "saju_result")
public class SajuResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "full_saju_data", columnDefinition = "json")
    private Map<String, Object> fullSajuData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hidden_stems", columnDefinition = "json")
    private Map<String, List<String>> hiddenStems;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ten_god_distribution", columnDefinition = "json")
    private Map<String, Integer> tenGodDistribution;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "career_fortune", columnDefinition = "json")
    private Map<String, Object> careerFortune;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Builder
    public SajuResult(UserProfile userProfile,
                      Map<String, Object> fullSajuData,
                      Map<String, List<String>> hiddenStems,
                      Map<String, Integer> tenGodDistribution,
                      Map<String, Object> careerFortune) {
        this.userProfile = userProfile;
        this.fullSajuData = fullSajuData;
        this.hiddenStems = hiddenStems;
        this.tenGodDistribution = tenGodDistribution;
        this.careerFortune = careerFortune;
        this.fetchedAt = LocalDateTime.now();
    }
}
