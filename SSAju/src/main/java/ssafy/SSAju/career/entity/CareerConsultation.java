package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "openai_model_version")
    private String openaiModelVersion;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @OneToMany(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Industry> industries = new ArrayList<>();

    @OneToMany(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InterviewTip> interviewTips = new ArrayList<>();

    @OneToMany(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Strength> strengths = new ArrayList<>();

    @Builder
    public CareerConsultation(SajuResult sajuResult, String openaiModelVersion) {
        this.sajuResult = sajuResult;
        this.openaiModelVersion = openaiModelVersion;
        this.generatedAt = LocalDateTime.now();
    }

    public void assignIndustries(List<Industry> list) {
        this.industries = list;
    }

    public void assignInterviewTips(List<InterviewTip> list) {
        this.interviewTips = list;
    }

    public void assignStrengths(List<Strength> list) {
        this.strengths = list;
    }
}
