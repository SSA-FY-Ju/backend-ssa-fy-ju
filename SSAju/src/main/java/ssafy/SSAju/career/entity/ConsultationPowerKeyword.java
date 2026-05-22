package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consultation_power_keyword")
public class ConsultationPowerKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_power_keywords_id", nullable = false)
    private ConsultationPowerKeywords consultationPowerKeywords;

    @Column(name = "keyword", nullable = false)
    private String keyword;

    @Column(name = "element")
    private String element;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "usage_example", columnDefinition = "text")
    private String usageExample;

    @Column(name = "context", columnDefinition = "text")
    private String context;

    @Builder
    public ConsultationPowerKeyword(ConsultationPowerKeywords consultationPowerKeywords, String keyword,
                                    String element, String description, String usageExample, String context) {
        this.consultationPowerKeywords = consultationPowerKeywords;
        this.keyword = keyword;
        this.element = element;
        this.description = description;
        this.usageExample = usageExample;
        this.context = context;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConsultationPowerKeyword that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
