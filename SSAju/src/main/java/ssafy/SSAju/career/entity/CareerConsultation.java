package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ssafy.SSAju.career.converter.ConsultationResultConverter;
import ssafy.SSAju.dto.external.CareerAdviceResponse;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "career_consultation",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_career_consultation_result_month",
                columnNames = {"saju_result_id", "consultation_month"}
        ))
public class CareerConsultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saju_result_id", nullable = false)
    private SajuResult sajuResult;

    @Column(name = "openai_model_version", nullable = false)
    private String openaiModelVersion;

    @Column(name = "consultation_month", nullable = false)
    private Integer consultationMonth;

    @Convert(converter = ConsultationResultConverter.class)
    @Column(name = "result_json", columnDefinition = "json")
    private CareerAdviceResponse resultJson;

    @CreatedDate
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Builder
    public CareerConsultation(SajuResult sajuResult, String openaiModelVersion,
                               Integer consultationMonth, CareerAdviceResponse resultJson) {
        this.sajuResult = sajuResult;
        this.openaiModelVersion = openaiModelVersion;
        this.consultationMonth = consultationMonth;
        this.resultJson = resultJson;
    }

    /**
     * 모델 버전 변경 시 결과를 갱신합니다.
     */
    public void updateData(String newModelVersion, CareerAdviceResponse newResultJson) {
        this.openaiModelVersion = newModelVersion;
        this.resultJson = newResultJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CareerConsultation that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
