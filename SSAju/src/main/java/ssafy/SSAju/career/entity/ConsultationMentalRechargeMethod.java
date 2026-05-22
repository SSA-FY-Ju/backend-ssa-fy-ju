package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_mental_recharge_method")
public class ConsultationMentalRechargeMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_mental_care_id", nullable = false)
    private ConsultationMentalCare consultationMentalCare;

    @Column(name = "content", columnDefinition = "text", nullable = false)
    private String content;

    @Builder
    public ConsultationMentalRechargeMethod(ConsultationMentalCare consultationMentalCare, String content) {
        this.consultationMentalCare = consultationMentalCare;
        this.content = content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationMentalRechargeMethod that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
