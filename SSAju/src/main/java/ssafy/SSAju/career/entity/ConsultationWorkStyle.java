package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_work_style")
public class ConsultationWorkStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_consultation_id", nullable = false, unique = true)
    private CareerConsultation careerConsultation;

    @Column(name = "preferred_company_type", columnDefinition = "text")
    private String preferredCompanyType;

    @Column(name = "leadership_type", columnDefinition = "text")
    private String leadershipType;

    @Column(name = "decision_making", columnDefinition = "text")
    private String decisionMaking;

    @Column(name = "conflict_resolution", columnDefinition = "text")
    private String conflictResolution;

    @Builder
    public ConsultationWorkStyle(CareerConsultation careerConsultation, String preferredCompanyType,
                                 String leadershipType, String decisionMaking, String conflictResolution) {
        this.careerConsultation = careerConsultation;
        this.preferredCompanyType = preferredCompanyType;
        this.leadershipType = leadershipType;
        this.decisionMaking = decisionMaking;
        this.conflictResolution = conflictResolution;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationWorkStyle that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
