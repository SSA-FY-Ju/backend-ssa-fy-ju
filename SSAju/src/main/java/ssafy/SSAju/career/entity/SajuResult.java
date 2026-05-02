package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.SSAju.career.converter.ObjectMapConverter;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @OneToMany(mappedBy = "sajuResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TenGodData> tenGodDataList = new ArrayList<>();

    @OneToMany(mappedBy = "sajuResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<HiddenStemData> hiddenStemDataList = new ArrayList<>();

    @OneToOne(mappedBy = "sajuResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CareerFortune careerFortune;

    @Builder
    public SajuResult(UserProfile userProfile, Map<String, Object> fullSajuData) {
        this.userProfile = userProfile;
        this.fullSajuData = fullSajuData;
        this.fetchedAt = LocalDateTime.now();
    }

    public void assignTenGodData(List<TenGodData> list) {
        this.tenGodDataList = list;
    }

    public void assignHiddenStemData(List<HiddenStemData> list) {
        this.hiddenStemDataList = list;
    }

    public void assignCareerFortune(CareerFortune fortune) {
        this.careerFortune = fortune;
    }
}
