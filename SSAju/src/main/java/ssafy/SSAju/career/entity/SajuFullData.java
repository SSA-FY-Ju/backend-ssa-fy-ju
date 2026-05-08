package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ssafy.SSAju.career.converter.IntegerMapConverter;
import ssafy.SSAju.career.converter.ObjectMapConverter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "saju_full_data")
public class SajuFullData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saju_result_id", nullable = false, unique = true)
    private SajuResult sajuResult;

    @Column(name = "year_pillar", nullable = false, length = 4)
    private String yearPillar;

    @Column(name = "month_pillar", nullable = false, length = 4)
    private String monthPillar;

    @Column(name = "day_pillar", nullable = false, length = 4)
    private String dayPillar;

    @Column(name = "hour_pillar", nullable = false, length = 4)
    private String hourPillar;

    @Column(name = "day_master", nullable = false, length = 2)
    private String dayMaster;

    @Column(name = "day_master_element", nullable = false, length = 2)
    private String dayMasterElement;

    @Convert(converter = IntegerMapConverter.class)
    @Column(name = "five_elements", columnDefinition = "json")
    private Map<String, Integer> fiveElements;

    @Convert(converter = ObjectMapConverter.class)
    @Column(name = "solar_correction", columnDefinition = "json")
    private Map<String, Object> solarCorrection;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public SajuFullData(SajuResult sajuResult,
                        String yearPillar, String monthPillar, String dayPillar, String hourPillar,
                        String dayMaster, String dayMasterElement,
                        Map<String, Integer> fiveElements, Map<String, Object> solarCorrection) {
        this.sajuResult = sajuResult;
        this.yearPillar = yearPillar;
        this.monthPillar = monthPillar;
        this.dayPillar = dayPillar;
        this.hourPillar = hourPillar;
        this.dayMaster = dayMaster;
        this.dayMasterElement = dayMasterElement;
        this.fiveElements = fiveElements;
        this.solarCorrection = solarCorrection;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SajuFullData that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
