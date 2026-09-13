package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.SSAju.career.converter.TenGodHiddenStemConverter;
import ssafy.SSAju.career.domain.TenGodHiddenStemAnalysis;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "saju_result",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_saju_result_user_profile",
                columnNames = {"user_profile_id"}
        ))
public class SajuResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @OneToOne(mappedBy = "sajuResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private SajuFullData sajuFullData;

    @Convert(converter = TenGodHiddenStemConverter.class)
    @Column(name = "ten_god_hidden_stem_analysis", columnDefinition = "json")
    private TenGodHiddenStemAnalysis tenGodHiddenStemAnalysis;

    @OneToOne(mappedBy = "sajuResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CareerFortune careerFortune;

    @Builder
    public SajuResult(UserProfile userProfile) {
        this.userProfile = userProfile;
        this.fetchedAt = Instant.now();
    }

    public void assignSajuFullData(SajuFullData data) {
        if (this.sajuFullData != null && this.sajuFullData != data) {
            this.sajuFullData.attachTo(null);
        }
        this.sajuFullData = data;
        if (data != null && data.getSajuResult() != this) {
            data.attachTo(this);
        }
    }

    public void assignTenGodHiddenStemAnalysis(TenGodHiddenStemAnalysis analysis) {
        this.tenGodHiddenStemAnalysis = analysis;
    }

    public void assignCareerFortune(CareerFortune fortune) {
        this.careerFortune = fortune;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SajuResult that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
