package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "career_consultation",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_career_consultation_result_month",
                columnNames = {"saju_result_id", "consultation_month"}
        ))
public class CareerConsultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saju_result_id", nullable = false)
    private SajuResult sajuResult;

    @Column(name = "openai_model_version", nullable = false)
    private String openaiModelVersion;

    @Column(name = "consultation_month", nullable = false)
    private Integer consultationMonth;

    // OpenAI 응답 중 단순 String 필드
    @Column(name = "day_master_description", columnDefinition = "text")
    private String dayMasterDescription;

    @Column(name = "five_elements_analysis", columnDefinition = "text")
    private String fiveElementsAnalysis;

    @CreatedDate
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    // ─── 기존 OneToMany ─────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<Industry> industries = new ArrayList<>();

    @OneToMany(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<InterviewTip> interviewTips = new ArrayList<>();

    @OneToMany(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<Strength> strengths = new ArrayList<>();

    // ─── 신규 OneToMany ─────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConsultationCaution> cautions = new ArrayList<>();

    @OneToMany(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConsultationKeyTenGod> keyTenGods = new ArrayList<>();

    // ─── 신규 OneToOne ──────────────────────────────────────────────────────────

    @OneToOne(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ConsultationWealthStyle wealthStyle;

    @OneToOne(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ConsultationRoadmap roadmap;

    @OneToOne(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ConsultationPersonalBranding personalBranding;

    @OneToOne(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ConsultationPowerKeywords powerKeywords;

    @OneToOne(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ConsultationMentalCare mentalCare;

    @OneToOne(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ConsultationEnvironmentFit environmentFit;

    @OneToOne(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ConsultationWorkStyle workStyle;

    @OneToOne(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ConsultationRelationshipStrategy relationshipStrategy;

    @OneToOne(mappedBy = "careerConsultation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ConsultationCareerTimeline careerTimeline;

    @Builder
    public CareerConsultation(SajuResult sajuResult, String openaiModelVersion,
                               Integer consultationMonth, String dayMasterDescription,
                               String fiveElementsAnalysis) {
        this.sajuResult = sajuResult;
        this.openaiModelVersion = openaiModelVersion;
        this.consultationMonth = consultationMonth;
        this.dayMasterDescription = dayMasterDescription;
        this.fiveElementsAnalysis = fiveElementsAnalysis;
    }

    // ─── assign methods ─────────────────────────────────────────────────────────

    public void assignIndustries(List<Industry> list) {
        this.industries.clear();
        if (list != null) this.industries.addAll(list);
    }

    public void assignInterviewTips(List<InterviewTip> list) {
        this.interviewTips.clear();
        if (list != null) this.interviewTips.addAll(list);
    }

    public void assignStrengths(List<Strength> list) {
        this.strengths.clear();
        if (list != null) this.strengths.addAll(list);
    }

    public void assignCautions(List<ConsultationCaution> list) {
        this.cautions.clear();
        if (list != null) this.cautions.addAll(list);
    }

    public void assignKeyTenGods(List<ConsultationKeyTenGod> list) {
        this.keyTenGods.clear();
        if (list != null) this.keyTenGods.addAll(list);
    }

    public void assignWealthStyle(ConsultationWealthStyle ws) { this.wealthStyle = ws; }
    public void assignRoadmap(ConsultationRoadmap rm) { this.roadmap = rm; }
    public void assignPersonalBranding(ConsultationPersonalBranding pb) { this.personalBranding = pb; }
    public void assignPowerKeywords(ConsultationPowerKeywords pk) { this.powerKeywords = pk; }
    public void assignMentalCare(ConsultationMentalCare mc) { this.mentalCare = mc; }
    public void assignEnvironmentFit(ConsultationEnvironmentFit ef) { this.environmentFit = ef; }
    public void assignWorkStyle(ConsultationWorkStyle ws) { this.workStyle = ws; }
    public void assignRelationshipStrategy(ConsultationRelationshipStrategy rs) { this.relationshipStrategy = rs; }
    public void assignCareerTimeline(ConsultationCareerTimeline ct) { this.careerTimeline = ct; }

    /**
     * 모델 버전 변경 시 기본 텍스트 필드를 갱신합니다.
     * 자식 컬렉션/엔티티는 ConsultationMapper.updateConsultation()이 담당합니다.
     */
    public void updateData(String newModelVersion, String newDayMasterDescription,
                           String newFiveElementsAnalysis) {
        this.openaiModelVersion = newModelVersion;
        this.dayMasterDescription = newDayMasterDescription;
        this.fiveElementsAnalysis = newFiveElementsAnalysis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CareerConsultation that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
