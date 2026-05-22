package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_relationship_strategy")
public class ConsultationRelationshipStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_consultation_id", nullable = false, unique = true)
    private CareerConsultation careerConsultation;

    @Column(name = "social_style", columnDefinition = "text")
    private String socialStyle;

    @Column(name = "networking_approach", columnDefinition = "text")
    private String networkingApproach;

    @Column(name = "team_position", columnDefinition = "text")
    private String teamPosition;

    @Column(name = "conflict_resolution", columnDefinition = "text")
    private String conflictResolution;

    @Column(name = "career_networking", columnDefinition = "text")
    private String careerNetworking;

    @Builder
    public ConsultationRelationshipStrategy(CareerConsultation careerConsultation, String socialStyle,
                                            String networkingApproach, String teamPosition,
                                            String conflictResolution, String careerNetworking) {
        this.careerConsultation = careerConsultation;
        this.socialStyle = socialStyle;
        this.networkingApproach = networkingApproach;
        this.teamPosition = teamPosition;
        this.conflictResolution = conflictResolution;
        this.careerNetworking = careerNetworking;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationRelationshipStrategy that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
