package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.SSAju.career.converter.IntegerMapConverter;
import ssafy.SSAju.career.converter.ObjectMapConverter;
import ssafy.SSAju.career.converter.StringListMapConverter;

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

    @Convert(converter = ObjectMapConverter.class)
    @Column(name = "full_saju_data", columnDefinition = "json")
    private Map<String, Object> fullSajuData;

    @Convert(converter = StringListMapConverter.class)
    @Column(name = "hidden_stems", columnDefinition = "json")
    private Map<String, List<String>> hiddenStems;

    @Convert(converter = IntegerMapConverter.class)
    @Column(name = "ten_god_distribution", columnDefinition = "json")
    private Map<String, Integer> tenGodDistribution;

    @Convert(converter = ObjectMapConverter.class)
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
