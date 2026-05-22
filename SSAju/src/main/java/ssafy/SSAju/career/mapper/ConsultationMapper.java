package ssafy.SSAju.career.mapper;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.entity.*;
import ssafy.SSAju.career.enums.TenGodConstants;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.response.ConsultationResponse;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ConsultationMapper {

    /**
     * CareerAdviceResponse 전체를 CareerConsultation + 자식 엔티티로 변환.
     */
    public CareerConsultation buildConsultation(SajuResult sajuResult,
                                                CareerAdviceResponse advice,
                                                String modelVersion,
                                                String consultationMonth) {
        CareerConsultation consultation = CareerConsultation.builder()
                .sajuResult(sajuResult)
                .openaiModelVersion(modelVersion)
                .consultationMonth(consultationMonth)
                .dayMasterDescription(advice.dayMasterDescription())
                .fiveElementsAnalysis(advice.fiveElementsAnalysis())
                .build();

        consultation.assignIndustries(toIndustryList(consultation, advice.industries()));
        consultation.assignInterviewTips(toInterviewTipList(consultation, advice.interviewTips()));
        consultation.assignStrengths(toStrengthList(consultation, advice.strengths()));
        consultation.assignCautions(toCautionList(consultation, advice.cautions()));
        consultation.assignKeyTenGods(toKeyTenGodList(consultation, advice.keyTenGods()));
        consultation.assignWealthStyle(toWealthStyle(consultation, advice.wealthStyle()));
        consultation.assignRoadmap(toRoadmap(consultation, advice.longTermRoadmap()));
        consultation.assignPersonalBranding(toPersonalBranding(consultation, advice.personalBranding()));
        consultation.assignPowerKeywords(toPowerKeywords(consultation, advice.powerKeywords()));
        consultation.assignMentalCare(toMentalCare(consultation, advice.mentalCare()));
        consultation.assignEnvironmentFit(toEnvironmentFit(consultation, advice.environmentFit()));
        consultation.assignWorkStyle(toWorkStyle(consultation, advice.workStyle()));
        consultation.assignRelationshipStrategy(toRelationshipStrategy(consultation, advice.relationshipStrategy()));
        consultation.assignCareerTimeline(toCareerTimeline(consultation, advice.careerTimeline()));

        return consultation;
    }

    /**
     * 모델 버전 변경 시 기존 CareerConsultation을 새 분석 결과로 갱신.
     * 모든 자식 컬렉션/엔티티를 교체하고 기본 텍스트 필드를 업데이트합니다.
     */
    public void updateConsultation(CareerConsultation existing, CareerAdviceResponse advice, String modelVersion) {
        existing.updateData(modelVersion, advice.dayMasterDescription(), advice.fiveElementsAnalysis());
        existing.assignIndustries(toIndustryList(existing, advice.industries()));
        existing.assignInterviewTips(toInterviewTipList(existing, advice.interviewTips()));
        existing.assignStrengths(toStrengthList(existing, advice.strengths()));
        existing.assignCautions(toCautionList(existing, advice.cautions()));
        existing.assignKeyTenGods(toKeyTenGodList(existing, advice.keyTenGods()));
        existing.assignWealthStyle(toWealthStyle(existing, advice.wealthStyle()));
        existing.assignRoadmap(toRoadmap(existing, advice.longTermRoadmap()));
        existing.assignPersonalBranding(toPersonalBranding(existing, advice.personalBranding()));
        existing.assignPowerKeywords(toPowerKeywords(existing, advice.powerKeywords()));
        existing.assignMentalCare(toMentalCare(existing, advice.mentalCare()));
        existing.assignEnvironmentFit(toEnvironmentFit(existing, advice.environmentFit()));
        existing.assignWorkStyle(toWorkStyle(existing, advice.workStyle()));
        existing.assignRelationshipStrategy(toRelationshipStrategy(existing, advice.relationshipStrategy()));
        existing.assignCareerTimeline(toCareerTimeline(existing, advice.careerTimeline()));
    }

    /**
     * 저장된 CareerConsultation에서 CareerAdviceResponse 복원 (캐시 히트 시 사용).
     */
    public CareerAdviceResponse restoreAdvice(CareerConsultation cc) {
        return new CareerAdviceResponse(
                toIndustryRecommendations(cc.getIndustries()),
                toStringList(cc.getInterviewTips(), InterviewTip::getTipText),
                toStringList(cc.getStrengths(), Strength::getStrengthText),
                toStringList(cc.getCautions(), ConsultationCaution::getContent),
                toWealthStyleDto(cc.getWealthStyle()),
                toRoadmapDto(cc.getRoadmap()),
                toPersonalBrandingDto(cc.getPersonalBranding()),
                toPowerKeywordsDto(cc.getPowerKeywords()),
                toMentalCareDto(cc.getMentalCare()),
                toEnvironmentFitDto(cc.getEnvironmentFit()),
                toWorkStyleDto(cc.getWorkStyle()),
                toRelationshipStrategyDto(cc.getRelationshipStrategy()),
                toCareerTimelineDto(cc.getCareerTimeline()),
                toStringList(cc.getKeyTenGods(), ConsultationKeyTenGod::getTenGodName),
                cc.getDayMasterDescription(),
                cc.getFiveElementsAnalysis()
        );
    }

    public ConsultationResponse toResponse(FastAPIResponse sajuData,
                                            TenGodDistribution tenGodDistribution,
                                            String dayMaster,
                                            String favoredPeriod,
                                            int confidenceScore,
                                            String reasoning,
                                            SajuResult sajuResult,
                                            CareerAdviceResponse advice,
                                            String modelVersion) {
        Map<String, String> tenGodCharacteristics = buildTenGodCharacteristics(tenGodDistribution);
        ConsultationResponse.SajuProfile sajuProfile = new ConsultationResponse.SajuProfile(
                dayMaster,
                advice.dayMasterDescription(),
                sajuData.fiveElements(),
                advice.fiveElementsAnalysis(),
                tenGodDistribution.asMap(),
                advice.keyTenGods(),
                tenGodCharacteristics
        );
        String analysisSummary = buildAnalysisSummary(
                dayMaster, tenGodDistribution, sajuData.fiveElements(), favoredPeriod);

        return new ConsultationResponse(
                sajuResult.getId(),
                advice.industries(),
                advice.interviewTips(),
                advice.strengths(),
                modelVersion,
                favoredPeriod,
                confidenceScore,
                reasoning,
                sajuProfile,
                advice.cautions(),
                advice.wealthStyle(),
                advice.longTermRoadmap(),
                advice.personalBranding(),
                advice.powerKeywords(),
                advice.mentalCare(),
                advice.environmentFit(),
                advice.workStyle(),
                advice.relationshipStrategy(),
                advice.careerTimeline(),
                analysisSummary
        );
    }

    private Map<String, String> buildTenGodCharacteristics(TenGodDistribution tenGodDistribution) {
        return tenGodDistribution.asMap().keySet().stream()
                .collect(Collectors.toMap(
                        name -> name,
                        name -> {
                            TenGodConstants tg = TenGodConstants.fromName(name);
                            return tg != null ? tg.getCharacteristics() : "";
                        }
                ));
    }

    public String buildAnalysisSummary(String dayMaster,
                                        TenGodDistribution tenGodDistribution,
                                        Map<String, Integer> fiveElements,
                                        String favoredPeriod) {
        String dominantElements = (fiveElements == null || fiveElements.isEmpty())
                ? "정보 없음"
                : fiveElements.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(2)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.joining("·"));

        int officerCount = tenGodDistribution.getScore("정관")
                + tenGodDistribution.getScore("편관");
        String tenGodSummary = officerCount > 0 ? "정관·편관 기운" : "십신 종합";

        int currentYear = LocalDate.now().getYear();
        return "%s 일간 · 오행 %s 강세 · %s 기반 | %d년 12개월 타임라인 + 관운 분석 (%s)"
                .formatted(dayMaster, dominantElements, tenGodSummary, currentYear, favoredPeriod);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 엔티티 → DTO 변환 (캐시 복원)
    // ─────────────────────────────────────────────────────────────────────────

    private <T> List<String> toStringList(List<T> list, java.util.function.Function<T, String> mapper) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(mapper).toList();
    }

    private List<CareerAdviceResponse.IndustryRecommendation> toIndustryRecommendations(List<Industry> industries) {
        if (industries == null) return Collections.emptyList();
        return industries.stream()
                .map(i -> new CareerAdviceResponse.IndustryRecommendation(
                        i.getIndustryName(),
                        i.getReason(),
                        toStringList(i.getRecommendedRoles(), IndustryRecommendedRole::getRoleName)
                ))
                .toList();
    }

    private CareerAdviceResponse.WealthStyle toWealthStyleDto(ConsultationWealthStyle ws) {
        if (ws == null) return null;
        return new CareerAdviceResponse.WealthStyle(
                ws.getIncomeSource(), ws.getFinancialAdvice(),
                ws.getInvestmentTendency(), ws.getAdditionalIncome());
    }

    private CareerAdviceResponse.LongTermRoadmap toRoadmapDto(ConsultationRoadmap rm) {
        if (rm == null) return null;
        return new CareerAdviceResponse.LongTermRoadmap(
                new CareerAdviceResponse.PhaseAdvice(rm.getPhase0Goal(), rm.getPhase0Focus(), rm.getPhase0Action()),
                new CareerAdviceResponse.PhaseAdvice(rm.getPhase3Goal(), rm.getPhase3Focus(), rm.getPhase3Action()),
                rm.getUltimateGoal(), rm.getGoalDescription());
    }

    private CareerAdviceResponse.PersonalBranding toPersonalBrandingDto(ConsultationPersonalBranding pb) {
        if (pb == null) return null;
        return new CareerAdviceResponse.PersonalBranding(
                pb.getSuitColor(), pb.getImpression(), pb.getHairAndMakeup(),
                pb.getBrandingKeyword(), pb.getTaglineForResume());
    }

    private CareerAdviceResponse.PowerKeywords toPowerKeywordsDto(ConsultationPowerKeywords pk) {
        if (pk == null) return null;
        List<CareerAdviceResponse.PowerKeyword> keywords = pk.getKeywords() == null
                ? Collections.emptyList()
                : pk.getKeywords().stream()
                        .map(k -> new CareerAdviceResponse.PowerKeyword(
                                k.getKeyword(), k.getElement(), k.getDescription(),
                                k.getUsageExample(), k.getContext()))
                        .toList();
        List<String> usageTips = toStringList(pk.getUsageTips(), ConsultationPowerKeywordUsageTip::getTip);
        return new CareerAdviceResponse.PowerKeywords(keywords, pk.getSelectionGuide(), usageTips, pk.getAvoidanceTip());
    }

    private CareerAdviceResponse.MentalCare toMentalCareDto(ConsultationMentalCare mc) {
        if (mc == null) return null;
        return new CareerAdviceResponse.MentalCare(
                toStringList(mc.getStressFactors(), ConsultationMentalStressFactor::getContent),
                toStringList(mc.getRechargeMethods(), ConsultationMentalRechargeMethod::getContent),
                mc.getMindsetMantra(), mc.getEmergencyTactic());
    }

    private CareerAdviceResponse.EnvironmentFit toEnvironmentFitDto(ConsultationEnvironmentFit ef) {
        if (ef == null) return null;
        return new CareerAdviceResponse.EnvironmentFit(
                ef.getWorkVibe(), ef.getCompanySize(), ef.getColleagueType(),
                ef.getConflictApproach(), ef.getPhysicalEnv(), ef.getCulturalFit());
    }

    private CareerAdviceResponse.WorkStyle toWorkStyleDto(ConsultationWorkStyle ws) {
        if (ws == null) return null;
        return new CareerAdviceResponse.WorkStyle(
                ws.getPreferredCompanyType(), ws.getLeadershipType(),
                ws.getDecisionMaking(), ws.getConflictResolution());
    }

    private CareerAdviceResponse.RelationshipStrategy toRelationshipStrategyDto(ConsultationRelationshipStrategy rs) {
        if (rs == null) return null;
        return new CareerAdviceResponse.RelationshipStrategy(
                rs.getSocialStyle(), rs.getNetworkingApproach(), rs.getTeamPosition(),
                rs.getConflictResolution(), rs.getCareerNetworking());
    }

    private CareerAdviceResponse.CareerTimeline toCareerTimelineDto(ConsultationCareerTimeline ct) {
        if (ct == null) return null;

        Map<String, CareerAdviceResponse.MonthFortune> months = ct.getMonthFortunes() == null
                ? Collections.emptyMap()
                : ct.getMonthFortunes().stream()
                        .collect(Collectors.toMap(
                                ConsultationMonthFortune::getMonthKey,
                                mf -> new CareerAdviceResponse.MonthFortune(mf.getType(), mf.getDescription()),
                                (a, b) -> a
                        ));

        List<CareerAdviceResponse.PivotPoint> pivotPoints = ct.getPivotPoints() == null
                ? Collections.emptyList()
                : ct.getPivotPoints().stream()
                        .map(pp -> new CareerAdviceResponse.PivotPoint(
                                pp.getMonth(), pp.getType(), pp.getScore(), pp.getDescription()))
                        .toList();

        List<String> warningMonths = toStringList(ct.getWarningMonths(), ConsultationWarningMonth::getMonth);

        return new CareerAdviceResponse.CareerTimeline(
                ct.getYear(), months, pivotPoints, warningMonths, ct.getWarningDescription());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DTO → 엔티티 변환 (저장)
    // ─────────────────────────────────────────────────────────────────────────

    private List<Industry> toIndustryList(CareerConsultation consultation,
                                           List<CareerAdviceResponse.IndustryRecommendation> industries) {
        if (industries == null) return Collections.emptyList();
        return industries.stream()
                .map(i -> {
                    Industry industry = Industry.builder()
                            .careerConsultation(consultation)
                            .industryName(i.name())
                            .reason(i.reason())
                            .build();
                    industry.assignRecommendedRoles(toRecommendedRoles(industry, i.recommendedRoles()));
                    return industry;
                })
                .toList();
    }

    private List<IndustryRecommendedRole> toRecommendedRoles(Industry industry, List<String> roles) {
        if (roles == null) return Collections.emptyList();
        return roles.stream()
                .map(r -> IndustryRecommendedRole.builder().industry(industry).roleName(r).build())
                .toList();
    }

    private List<InterviewTip> toInterviewTipList(CareerConsultation consultation, List<String> tips) {
        if (tips == null) return Collections.emptyList();
        return tips.stream()
                .map(tip -> InterviewTip.builder().careerConsultation(consultation).tipText(tip).build())
                .toList();
    }

    private List<Strength> toStrengthList(CareerConsultation consultation, List<String> strengthTexts) {
        if (strengthTexts == null) return Collections.emptyList();
        return strengthTexts.stream()
                .map(text -> Strength.builder().careerConsultation(consultation).strengthText(text).build())
                .toList();
    }

    private List<ConsultationCaution> toCautionList(CareerConsultation consultation, List<String> cautions) {
        if (cautions == null) return Collections.emptyList();
        return cautions.stream()
                .map(c -> ConsultationCaution.builder().careerConsultation(consultation).content(c).build())
                .toList();
    }

    private List<ConsultationKeyTenGod> toKeyTenGodList(CareerConsultation consultation, List<String> keyTenGods) {
        if (keyTenGods == null) return Collections.emptyList();
        return keyTenGods.stream()
                .map(k -> ConsultationKeyTenGod.builder().careerConsultation(consultation).tenGodName(k).build())
                .toList();
    }

    private ConsultationWealthStyle toWealthStyle(CareerConsultation consultation,
                                                   CareerAdviceResponse.WealthStyle ws) {
        if (ws == null) return null;
        return ConsultationWealthStyle.builder()
                .careerConsultation(consultation)
                .incomeSource(ws.incomeSource())
                .financialAdvice(ws.financialAdvice())
                .investmentTendency(ws.investmentTendency())
                .additionalIncome(ws.additionalIncome())
                .build();
    }

    private ConsultationRoadmap toRoadmap(CareerConsultation consultation,
                                           CareerAdviceResponse.LongTermRoadmap rm) {
        if (rm == null) return null;
        CareerAdviceResponse.PhaseAdvice p0 = rm.phase0to2years();
        CareerAdviceResponse.PhaseAdvice p3 = rm.phase3to5years();
        return ConsultationRoadmap.builder()
                .careerConsultation(consultation)
                .phase0Goal(p0 != null ? p0.goal() : null)
                .phase0Focus(p0 != null ? p0.focus() : null)
                .phase0Action(p0 != null ? p0.action() : null)
                .phase3Goal(p3 != null ? p3.goal() : null)
                .phase3Focus(p3 != null ? p3.focus() : null)
                .phase3Action(p3 != null ? p3.action() : null)
                .ultimateGoal(rm.ultimateGoal())
                .goalDescription(rm.goalDescription())
                .build();
    }

    private ConsultationPersonalBranding toPersonalBranding(CareerConsultation consultation,
                                                              CareerAdviceResponse.PersonalBranding pb) {
        if (pb == null) return null;
        return ConsultationPersonalBranding.builder()
                .careerConsultation(consultation)
                .suitColor(pb.suitColor())
                .impression(pb.impression())
                .hairAndMakeup(pb.hairAndMakeup())
                .brandingKeyword(pb.brandingKeyword())
                .taglineForResume(pb.taglineForResume())
                .build();
    }

    private ConsultationPowerKeywords toPowerKeywords(CareerConsultation consultation,
                                                       CareerAdviceResponse.PowerKeywords pk) {
        if (pk == null) return null;
        ConsultationPowerKeywords entity = ConsultationPowerKeywords.builder()
                .careerConsultation(consultation)
                .selectionGuide(pk.selectionGuide())
                .avoidanceTip(pk.avoidanceTip())
                .build();

        entity.assignKeywords(pk.validKeywords().stream()
                .map(k -> ConsultationPowerKeyword.builder()
                        .consultationPowerKeywords(entity)
                        .keyword(k.keyword())
                        .element(k.element())
                        .description(k.description())
                        .usageExample(k.usageExample())
                        .context(k.context())
                        .build())
                .toList());
        entity.assignUsageTips(pk.validUsageTips().stream()
                .map(tip -> ConsultationPowerKeywordUsageTip.builder()
                        .consultationPowerKeywords(entity)
                        .tip(tip)
                        .build())
                .toList());
        return entity;
    }

    private ConsultationMentalCare toMentalCare(CareerConsultation consultation,
                                                  CareerAdviceResponse.MentalCare mc) {
        if (mc == null) return null;
        ConsultationMentalCare entity = ConsultationMentalCare.builder()
                .careerConsultation(consultation)
                .mindsetMantra(mc.mindsetMantra())
                .emergencyTactic(mc.emergencyTactic())
                .build();

        entity.assignStressFactors(mc.validStressVulnerability().stream()
                .map(s -> ConsultationMentalStressFactor.builder()
                        .consultationMentalCare(entity).content(s).build())
                .toList());
        entity.assignRechargeMethods(mc.validRechargeMethod().stream()
                .map(r -> ConsultationMentalRechargeMethod.builder()
                        .consultationMentalCare(entity).content(r).build())
                .toList());
        return entity;
    }

    private ConsultationEnvironmentFit toEnvironmentFit(CareerConsultation consultation,
                                                         CareerAdviceResponse.EnvironmentFit ef) {
        if (ef == null) return null;
        return ConsultationEnvironmentFit.builder()
                .careerConsultation(consultation)
                .workVibe(ef.workVibe())
                .companySize(ef.companySize())
                .colleagueType(ef.colleagueType())
                .conflictApproach(ef.conflictApproach())
                .physicalEnv(ef.physicalEnv())
                .culturalFit(ef.culturalFit())
                .build();
    }

    private ConsultationWorkStyle toWorkStyle(CareerConsultation consultation,
                                               CareerAdviceResponse.WorkStyle ws) {
        if (ws == null) return null;
        return ConsultationWorkStyle.builder()
                .careerConsultation(consultation)
                .preferredCompanyType(ws.preferredCompanyType())
                .leadershipType(ws.leadershipType())
                .decisionMaking(ws.decisionMaking())
                .conflictResolution(ws.conflictResolution())
                .build();
    }

    private ConsultationRelationshipStrategy toRelationshipStrategy(CareerConsultation consultation,
                                                                     CareerAdviceResponse.RelationshipStrategy rs) {
        if (rs == null) return null;
        return ConsultationRelationshipStrategy.builder()
                .careerConsultation(consultation)
                .socialStyle(rs.socialStyle())
                .networkingApproach(rs.networkingApproach())
                .teamPosition(rs.teamPosition())
                .conflictResolution(rs.conflictResolution())
                .careerNetworking(rs.careerNetworking())
                .build();
    }

    private ConsultationCareerTimeline toCareerTimeline(CareerConsultation consultation,
                                                         CareerAdviceResponse.CareerTimeline ct) {
        if (ct == null) return null;
        ConsultationCareerTimeline entity = ConsultationCareerTimeline.builder()
                .careerConsultation(consultation)
                .year(ct.year())
                .warningDescription(ct.warningDescription())
                .build();

        entity.assignMonthFortunes(ct.validMonths().entrySet().stream()
                .map(e -> ConsultationMonthFortune.builder()
                        .consultationCareerTimeline(entity)
                        .monthKey(e.getKey())
                        .type(e.getValue().type())
                        .description(e.getValue().description())
                        .build())
                .toList());
        entity.assignPivotPoints(ct.validPivotPoints().stream()
                .map(pp -> ConsultationPivotPoint.builder()
                        .consultationCareerTimeline(entity)
                        .month(pp.month())
                        .type(pp.type())
                        .score(pp.score())
                        .description(pp.description())
                        .build())
                .toList());
        entity.assignWarningMonths(ct.validWarningMonths().stream()
                .map(m -> ConsultationWarningMonth.builder()
                        .consultationCareerTimeline(entity).month(m).build())
                .toList());
        return entity;
    }
}
