package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_environment_fit")
public class ConsultationEnvironmentFit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_consultation_id", nullable = false, unique = true)
    private CareerConsultation careerConsultation;

    @Column(name = "work_vibe", columnDefinition = "text")
    private String workVibe;

    @Column(name = "company_size", columnDefinition = "text")
    private String companySize;

    @Column(name = "colleague_type", columnDefinition = "text")
    private String colleagueType;

    @Column(name = "conflict_approach", columnDefinition = "text")
    private String conflictApproach;

    @Column(name = "physical_env", columnDefinition = "text")
    private String physicalEnv;

    @Column(name = "cultural_fit", columnDefinition = "text")
    private String culturalFit;

    @Builder
    public ConsultationEnvironmentFit(CareerConsultation careerConsultation, String workVibe,
                                      String companySize, String colleagueType,
                                      String conflictApproach, String physicalEnv, String culturalFit) {
        this.careerConsultation = careerConsultation;
        this.workVibe = workVibe;
        this.companySize = companySize;
        this.colleagueType = colleagueType;
        this.conflictApproach = conflictApproach;
        this.physicalEnv = physicalEnv;
        this.culturalFit = culturalFit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationEnvironmentFit that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
