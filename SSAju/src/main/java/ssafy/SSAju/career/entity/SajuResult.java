package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "saju_result",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_saju_result_user_profile",
                columnNames = {"user_id", "user_profile_id"}
        ))
public class SajuResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @OneToOne(mappedBy = "sajuResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private SajuFullData sajuFullData;

    @OneToMany(mappedBy = "sajuResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TenGodData> tenGodDataList = new ArrayList<>();

    @OneToMany(mappedBy = "sajuResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<HiddenStemData> hiddenStemDataList = new ArrayList<>();

    @OneToOne(mappedBy = "sajuResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private CareerFortune careerFortune;

    @Builder
    public SajuResult(UserProfile userProfile, User user) {
        this.userProfile = userProfile;
        this.user = user;
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

    public void assignTenGodData(List<TenGodData> list) {
        this.tenGodDataList.clear();
        this.tenGodDataList.addAll(list);
    }

    public void assignHiddenStemData(List<HiddenStemData> list) {
        this.hiddenStemDataList.clear();
        this.hiddenStemDataList.addAll(list);
    }

    public void assignCareerFortune(CareerFortune fortune) {
        this.careerFortune = fortune;
    }

    public void assignUser(User user) {
        this.user = user;
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
