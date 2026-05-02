package ssafy.SSAju.career.mapper;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.Industry;
import ssafy.SSAju.career.entity.InterviewTip;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.Strength;
import ssafy.SSAju.dto.external.CareerAdviceResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ConsultationMapper {

    public CareerConsultation buildConsultation(SajuResult sajuResult,
                                                CareerAdviceResponse advice,
                                                String modelVersion) {
        CareerConsultation consultation = CareerConsultation.builder()
                .sajuResult(sajuResult)
                .openaiModelVersion(modelVersion)
                .build();

        consultation.assignIndustries(toIndustryList(consultation, advice.industries()));
        consultation.assignInterviewTips(toInterviewTipList(consultation, advice.interviewTips()));
        consultation.assignStrengths(toStrengthList(consultation, advice.strengths()));
        return consultation;
    }

    public String buildAnalysisSummary(String dayMaster,
                                        Map<String, Integer> tenGodDistribution,
                                        Map<String, Integer> fiveElements,
                                        String favoredPeriod) {
        String dominantElements = (fiveElements == null || fiveElements.isEmpty())
                ? "정보 없음"
                : fiveElements.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(2)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.joining("·"));

        int officerCount = tenGodDistribution.getOrDefault("정관", 0)
                + tenGodDistribution.getOrDefault("편관", 0);
        String tenGodSummary = officerCount > 0 ? "정관·편관 기운" : "십신 종합";

        int currentYear = LocalDate.now().getYear();
        return "%s 일간 · 오행 %s 강세 · %s 기반 | %d년 12개월 타임라인 + 관운 분석 (%s)"
                .formatted(dayMaster, dominantElements, tenGodSummary, currentYear, favoredPeriod);
    }

    private List<Industry> toIndustryList(CareerConsultation consultation,
                                           List<CareerAdviceResponse.IndustryRecommendation> industries) {
        return industries.stream()
                .map(i -> Industry.builder()
                        .careerConsultation(consultation)
                        .industryName(i.name())
                        .reason(i.reason())
                        .build())
                .toList();
    }

    private List<InterviewTip> toInterviewTipList(CareerConsultation consultation, List<String> tips) {
        return tips.stream()
                .map(tip -> InterviewTip.builder()
                        .careerConsultation(consultation)
                        .tipText(tip)
                        .build())
                .toList();
    }

    private List<Strength> toStrengthList(CareerConsultation consultation, List<String> strengthTexts) {
        return strengthTexts.stream()
                .map(text -> Strength.builder()
                        .careerConsultation(consultation)
                        .strengthText(text)
                        .build())
                .toList();
    }
}
