package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_key_ten_god")
public class ConsultationKeyTenGod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_consultation_id", nullable = false)
    private CareerConsultation careerConsultation;

    @Column(name = "ten_god_name", nullable = false)
    private String tenGodName;

    @Builder
    public ConsultationKeyTenGod(CareerConsultation careerConsultation, String tenGodName) {
        this.careerConsultation = careerConsultation;
        this.tenGodName = tenGodName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationKeyTenGod that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
