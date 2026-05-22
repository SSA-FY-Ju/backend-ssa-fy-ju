package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_warning_month")
public class ConsultationWarningMonth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_career_timeline_id", nullable = false)
    private ConsultationCareerTimeline consultationCareerTimeline;

    @Column(name = "month", nullable = false, length = 7)
    private String month;

    @Builder
    public ConsultationWarningMonth(ConsultationCareerTimeline consultationCareerTimeline, String month) {
        this.consultationCareerTimeline = consultationCareerTimeline;
        this.month = month;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationWarningMonth that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
