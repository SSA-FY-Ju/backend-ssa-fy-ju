package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_roadmap")
public class ConsultationRoadmap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_consultation_id", nullable = false, unique = true)
    private CareerConsultation careerConsultation;

    // phase0to2years
    @Column(name = "phase0_goal", columnDefinition = "text")
    private String phase0Goal;

    @Column(name = "phase0_focus", columnDefinition = "text")
    private String phase0Focus;

    @Column(name = "phase0_action", columnDefinition = "text")
    private String phase0Action;

    // phase3to5years
    @Column(name = "phase3_goal", columnDefinition = "text")
    private String phase3Goal;

    @Column(name = "phase3_focus", columnDefinition = "text")
    private String phase3Focus;

    @Column(name = "phase3_action", columnDefinition = "text")
    private String phase3Action;

    @Column(name = "ultimate_goal", columnDefinition = "text")
    private String ultimateGoal;

    @Column(name = "goal_description", columnDefinition = "text")
    private String goalDescription;

    @Builder
    public ConsultationRoadmap(CareerConsultation careerConsultation,
                               String phase0Goal, String phase0Focus, String phase0Action,
                               String phase3Goal, String phase3Focus, String phase3Action,
                               String ultimateGoal, String goalDescription) {
        this.careerConsultation = careerConsultation;
        this.phase0Goal = phase0Goal;
        this.phase0Focus = phase0Focus;
        this.phase0Action = phase0Action;
        this.phase3Goal = phase3Goal;
        this.phase3Focus = phase3Focus;
        this.phase3Action = phase3Action;
        this.ultimateGoal = ultimateGoal;
        this.goalDescription = goalDescription;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationRoadmap that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
