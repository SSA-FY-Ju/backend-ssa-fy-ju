package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 오행 분포 분석 엔티티.
 *
 * <p>정규화 이력:
 * - user_distribution (TEXT/JSON) → 5개 명시적 INT 컬럼 (user_wood ~ user_water)
 * - company_distribution (TEXT/JSON) → 5개 명시적 INT 컬럼 (company_wood ~ company_water)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "five_elements_analysis")
public class FiveElementsAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compatibility_id", nullable = false, unique = true)
    private CompanyCompatibility companyCompatibility;

    @Column(name = "user_wood", nullable = false)
    private int userWood;

    @Column(name = "user_fire", nullable = false)
    private int userFire;

    @Column(name = "user_earth", nullable = false)
    private int userEarth;

    @Column(name = "user_metal", nullable = false)
    private int userMetal;

    @Column(name = "user_water", nullable = false)
    private int userWater;

    @Column(name = "company_wood", nullable = false)
    private int companyWood;

    @Column(name = "company_fire", nullable = false)
    private int companyFire;

    @Column(name = "company_earth", nullable = false)
    private int companyEarth;

    @Column(name = "company_metal", nullable = false)
    private int companyMetal;

    @Column(name = "company_water", nullable = false)
    private int companyWater;

    @Column(name = "synergy_description", columnDefinition = "text")
    private String synergyDescription;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public FiveElementsAnalysis(CompanyCompatibility companyCompatibility,
                                 int userWood, int userFire, int userEarth, int userMetal, int userWater,
                                 int companyWood, int companyFire, int companyEarth, int companyMetal, int companyWater,
                                 String synergyDescription) {
        this.companyCompatibility = companyCompatibility;
        this.userWood = userWood;
        this.userFire = userFire;
        this.userEarth = userEarth;
        this.userMetal = userMetal;
        this.userWater = userWater;
        this.companyWood = companyWood;
        this.companyFire = companyFire;
        this.companyEarth = companyEarth;
        this.companyMetal = companyMetal;
        this.companyWater = companyWater;
        this.synergyDescription = synergyDescription;
    }

    public Map<String, Integer> getUserDistribution() {
        return Map.of("木", userWood, "火", userFire, "土", userEarth, "金", userMetal, "水", userWater);
    }

    public Map<String, Integer> getCompanyDistribution() {
        return Map.of("木", companyWood, "火", companyFire, "土", companyEarth, "金", companyMetal, "水", companyWater);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FiveElementsAnalysis that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
