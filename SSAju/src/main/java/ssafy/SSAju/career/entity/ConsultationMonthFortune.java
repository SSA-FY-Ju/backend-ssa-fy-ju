package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_month_fortune")
public class ConsultationMonthFortune {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_career_timeline_id", nullable = false)
    private ConsultationCareerTimeline consultationCareerTimeline;

    @Column(name = "month_key", nullable = false)
    private Integer monthKey;

    @Column(name = "type")
    private String type;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Builder
    public ConsultationMonthFortune(ConsultationCareerTimeline consultationCareerTimeline,
                                    Integer monthKey, String type, String description) {
        this.consultationCareerTimeline = consultationCareerTimeline;
        this.monthKey = monthKey;
        this.type = type;
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationMonthFortune that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
