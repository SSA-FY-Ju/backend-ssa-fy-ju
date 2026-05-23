package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_pivot_point")
public class ConsultationPivotPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_career_timeline_id", nullable = false)
    private ConsultationCareerTimeline consultationCareerTimeline;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "type")
    private String type;

    @Column(name = "score")
    private int score;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Builder
    public ConsultationPivotPoint(ConsultationCareerTimeline consultationCareerTimeline,
                                  Integer month, String type, int score, String description) {
        this.consultationCareerTimeline = consultationCareerTimeline;
        this.month = month;
        this.type = type;
        this.score = score;
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationPivotPoint that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
