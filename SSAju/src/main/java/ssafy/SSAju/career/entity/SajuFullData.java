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

import java.time.Instant;
import java.util.Map;

/**
 * FastAPI에서 반환된 사주 원본 데이터를 정규화하여 저장하는 엔티티.
 *
 * <p>SajuResult와 1:1 관계를 가지며, FK(saju_result_id)를 owning side로 관리합니다.
 * 자주 조회·필터링되는 기둥 데이터(yearPillar, dayMaster 등)는 타입 안전 필드로,
 * 변경 빈도가 낮은 fiveElements와 solarCorrection은 JSON 컬럼으로 저장합니다.
 *
 * @see SajuResult
 * @see ssafy.SSAju.career.mapper.SajuResultMapper#toSajuFullData
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "saju_full_data")
public class SajuFullData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 연결된 사주 결과 (owning side — FK: saju_result_id). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saju_result_id", nullable = false, unique = true)
    private SajuResult sajuResult;

    /** 연주(年柱): 天干地支 2자 조합 (예: "庚午"). */
    @Column(name = "year_pillar", nullable = false, length = 4)
    private String yearPillar;

    /** 월주(月柱): 天干地支 2자 조합 (예: "甲戌"). */
    @Column(name = "month_pillar", nullable = false, length = 4)
    private String monthPillar;

    /** 일주(日柱): 天干地支 2자 조합 (예: "己未"). */
    @Column(name = "day_pillar", nullable = false, length = 4)
    private String dayPillar;

    /** 시주(時柱): 天干地支 2자 조합 (예: "丁寅"). */
    @Column(name = "hour_pillar", nullable = false, length = 4)
    private String hourPillar;

    /** 일간(日干): 일주의 천간 1자 (예: "己"). */
    @Column(name = "day_master", nullable = false, length = 2)
    private String dayMaster;

    /** 일간의 오행(五行): 木·火·土·金·水 중 하나 (예: "土"). */
    @Column(name = "day_master_element", nullable = false, length = 2)
    private String dayMasterElement;

    /** 오행 분포: 木·火·土·金·水 별 점수 맵. JSON 컬럼으로 저장. */
    @Convert(converter = IntegerMapConverter.class)
    @Column(name = "five_elements", columnDefinition = "json")
    private Map<String, Integer> fiveElements;

    /** 태양 보정 데이터 (선택). JSON 컬럼으로 저장. */
    @Convert(converter = ObjectMapConverter.class)
    @Column(name = "solar_correction", columnDefinition = "json")
    private Map<String, Object> solarCorrection;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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

    void attachTo(SajuResult result) {
        this.sajuResult = result;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
