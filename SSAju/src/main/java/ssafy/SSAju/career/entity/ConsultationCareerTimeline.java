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
@Table(name = "consultation_career_timeline")
public class ConsultationCareerTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_consultation_id", nullable = false, unique = true)
    private CareerConsultation careerConsultation;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "warning_description", columnDefinition = "text")
    private String warningDescription;

    @OneToMany(mappedBy = "consultationCareerTimeline", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConsultationMonthFortune> monthFortunes = new ArrayList<>();

    @OneToMany(mappedBy = "consultationCareerTimeline", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConsultationPivotPoint> pivotPoints = new ArrayList<>();

    @OneToMany(mappedBy = "consultationCareerTimeline", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConsultationWarningMonth> warningMonths = new ArrayList<>();

    @Builder
    public ConsultationCareerTimeline(CareerConsultation careerConsultation,
                                      int year, String warningDescription) {
        this.careerConsultation = careerConsultation;
        this.year = year;
        this.warningDescription = warningDescription;
    }

    public void assignMonthFortunes(List<ConsultationMonthFortune> list) {
        this.monthFortunes.clear();
        if (list != null) {
            this.monthFortunes.addAll(list);
        }
    }

    public void assignPivotPoints(List<ConsultationPivotPoint> list) {
        this.pivotPoints.clear();
        if (list != null) {
            this.pivotPoints.addAll(list);
        }
    }

    public void assignWarningMonths(List<ConsultationWarningMonth> list) {
        this.warningMonths.clear();
        if (list != null) {
            this.warningMonths.addAll(list);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationCareerTimeline that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
