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
@Table(name = "consultation_power_keywords")
public class ConsultationPowerKeywords {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_consultation_id", nullable = false, unique = true)
    private CareerConsultation careerConsultation;

    @Column(name = "selection_guide", columnDefinition = "text")
    private String selectionGuide;

    @Column(name = "avoidance_tip", columnDefinition = "text")
    private String avoidanceTip;

    @OneToMany(mappedBy = "consultationPowerKeywords", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConsultationPowerKeyword> keywords = new ArrayList<>();

    @OneToMany(mappedBy = "consultationPowerKeywords", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConsultationPowerKeywordUsageTip> usageTips = new ArrayList<>();

    @Builder
    public ConsultationPowerKeywords(CareerConsultation careerConsultation,
                                     String selectionGuide, String avoidanceTip) {
        this.careerConsultation = careerConsultation;
        this.selectionGuide = selectionGuide;
        this.avoidanceTip = avoidanceTip;
    }

    public void assignKeywords(List<ConsultationPowerKeyword> list) {
        this.keywords = list;
    }

    public void assignUsageTips(List<ConsultationPowerKeywordUsageTip> list) {
        this.usageTips = list;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationPowerKeywords that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
