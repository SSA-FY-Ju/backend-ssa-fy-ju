package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.SSAju.career.converter.StringListConverter;
import ssafy.SSAju.career.converter.StringMapListConverter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Entity
@Table(name = "career_consultation")
public class CareerConsultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saju_result_id", nullable = false)
    private SajuResult sajuResult;

    @Convert(converter = StringMapListConverter.class)
    @Column(name = "industries", columnDefinition = "json")
    private List<Map<String, String>> industries;

    @Convert(converter = StringListConverter.class)
    @Column(name = "interview_tips", columnDefinition = "json")
    private List<String> interviewTips;

    @Convert(converter = StringListConverter.class)
    @Column(name = "strengths", columnDefinition = "json")
    private List<String> strengths;

    @Column(name = "openai_model_version")
    private String openaiModelVersion;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Builder
    public CareerConsultation(SajuResult sajuResult,
                              List<Map<String, String>> industries,
                              List<String> interviewTips,
                              List<String> strengths,
                              String openaiModelVersion) {
        this.sajuResult = sajuResult;
        this.industries = industries;
        this.interviewTips = interviewTips;
        this.strengths = strengths;
        this.openaiModelVersion = openaiModelVersion;
        this.generatedAt = LocalDateTime.now();
    }
}
