package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "saju_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SajuResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json", nullable = false)
    private String fullSajuData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String careerFortune;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    @Builder
    public SajuResult(UserProfile userProfile, String fullSajuData, String careerFortune) {
        this.userProfile = userProfile;
        this.fullSajuData = fullSajuData;
        this.careerFortune = careerFortune;
        this.fetchedAt = LocalDateTime.now();
    }

    public void updateCareerFortune(String careerFortune) {
        this.careerFortune = careerFortune;
    }
}
