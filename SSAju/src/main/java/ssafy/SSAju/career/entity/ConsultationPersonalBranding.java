package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_personal_branding")
public class ConsultationPersonalBranding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_consultation_id", nullable = false, unique = true)
    private CareerConsultation careerConsultation;

    @Column(name = "suit_color")
    private String suitColor;

    @Column(name = "impression", columnDefinition = "text")
    private String impression;

    @Column(name = "hair_and_makeup", columnDefinition = "text")
    private String hairAndMakeup;

    @Column(name = "branding_keyword")
    private String brandingKeyword;

    @Column(name = "tagline_for_resume", columnDefinition = "text")
    private String taglineForResume;

    @Builder
    public ConsultationPersonalBranding(CareerConsultation careerConsultation, String suitColor,
                                        String impression, String hairAndMakeup,
                                        String brandingKeyword, String taglineForResume) {
        this.careerConsultation = careerConsultation;
        this.suitColor = suitColor;
        this.impression = impression;
        this.hairAndMakeup = hairAndMakeup;
        this.brandingKeyword = brandingKeyword;
        this.taglineForResume = taglineForResume;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationPersonalBranding that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
