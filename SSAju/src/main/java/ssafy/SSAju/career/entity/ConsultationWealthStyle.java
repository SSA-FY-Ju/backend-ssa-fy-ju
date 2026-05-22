package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_wealth_style")
public class ConsultationWealthStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_consultation_id", nullable = false, unique = true)
    private CareerConsultation careerConsultation;

    @Column(name = "income_source", columnDefinition = "text")
    private String incomeSource;

    @Column(name = "financial_advice", columnDefinition = "text")
    private String financialAdvice;

    @Column(name = "investment_tendency", columnDefinition = "text")
    private String investmentTendency;

    @Column(name = "additional_income", columnDefinition = "text")
    private String additionalIncome;

    @Builder
    public ConsultationWealthStyle(CareerConsultation careerConsultation, String incomeSource,
                                   String financialAdvice, String investmentTendency, String additionalIncome) {
        this.careerConsultation = careerConsultation;
        this.incomeSource = incomeSource;
        this.financialAdvice = financialAdvice;
        this.investmentTendency = investmentTendency;
        this.additionalIncome = additionalIncome;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationWealthStyle that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
