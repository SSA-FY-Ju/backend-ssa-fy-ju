package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_mental_care")
public class ConsultationMentalCare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_consultation_id", nullable = false, unique = true)
    private CareerConsultation careerConsultation;

    @Column(name = "mindset_mantra", columnDefinition = "text")
    private String mindsetMantra;

    @Column(name = "emergency_tactic", columnDefinition = "text")
    private String emergencyTactic;

    @OneToMany(mappedBy = "consultationMentalCare", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConsultationMentalStressFactor> stressFactors = new ArrayList<>();

    @OneToMany(mappedBy = "consultationMentalCare", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConsultationMentalRechargeMethod> rechargeMethods = new ArrayList<>();

    @Builder
    public ConsultationMentalCare(CareerConsultation careerConsultation,
                                  String mindsetMantra, String emergencyTactic) {
        this.careerConsultation = careerConsultation;
        this.mindsetMantra = mindsetMantra;
        this.emergencyTactic = emergencyTactic;
    }

    public void assignStressFactors(List<ConsultationMentalStressFactor> list) {
        this.stressFactors.clear();
        if (list != null) {
            this.stressFactors.addAll(list);
        }
    }

    public void assignRechargeMethods(List<ConsultationMentalRechargeMethod> list) {
        this.rechargeMethods.clear();
        if (list != null) {
            this.rechargeMethods.addAll(list);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationMentalCare that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
