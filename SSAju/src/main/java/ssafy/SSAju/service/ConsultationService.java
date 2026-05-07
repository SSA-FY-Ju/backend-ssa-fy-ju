package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ssafy.SSAju.career.caller.ConsultationOpenAICaller;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.mapper.ConsultationMapper;
import ssafy.SSAju.career.mapper.SajuResultMapper;
import ssafy.SSAju.career.provider.SajuResultProvider;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.career.validator.SajuValidator;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.repository.CareerConsultationRepository;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationService {

    private final ConsultationOpenAICaller openAICaller;
    private final SajuDataService sajuDataService;
    private final TenGodCalculator tenGodCalculator;
    private final HiddenStemCalculator hiddenStemCalculator;
    private final CareerFortuneAnalyzer careerFortuneAnalyzer;
    private final UserProfileProvider userProfileProvider;
    private final SajuResultProvider sajuResultProvider;
    private final SajuResultMapper sajuResultMapper;
    private final ConsultationMapper consultationMapper;
    private final CareerConsultationRepository careerConsultationRepository;
    private final SajuValidator sajuValidator;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelVersion;

    /**
     * @Transactional 없음: FastAPI/OpenAI I/O 동안 DB 커넥션을 점유하지 않도록 트랜잭션 분리.
     */
    public ConsultationResponse getCareerConsultation(ConsultationRequest request) {
        log.info("커리어 컨설팅 시작");

        FastAPIResponse sajuData = sajuDataService.fetchSajuFromFastAPI(request.birthDate(), request.birthTime());
        sajuValidator.validateWithFiveElements(sajuData);

        List<String> heavenlyStems = sajuData.heavenlyStems();
        List<String> earthlyBranches = sajuData.earthlyBranches();
        TenGodDistribution tenGodDistribution = tenGodCalculator.calculate(heavenlyStems);
        HiddenStems hiddenStems = hiddenStemCalculator.calculate(earthlyBranches);

        String dayMaster = heavenlyStems.get(2);
        String favoredPeriod = careerFortuneAnalyzer.analyzeFavoredPeriod(
                tenGodDistribution, hiddenStems, dayMaster, earthlyBranches);
        int confidenceScore = careerFortuneAnalyzer.calculateConfidenceScore(
                tenGodDistribution, hiddenStems, dayMaster);
        String reasoning = careerFortuneAnalyzer.buildReasoning(favoredPeriod, tenGodDistribution);

        UserProfile userProfile = userProfileProvider.findOrCreate(request.birthDate(), request.birthTime());

        SajuResult newResult = sajuResultMapper.buildSajuResult(
                userProfile, sajuData, tenGodDistribution, hiddenStems,
                favoredPeriod, confidenceScore, reasoning);
        SajuResult sajuResult = sajuResultProvider.findOrCreate(userProfile, newResult);

        CareerAdviceResponse advice = openAICaller.call(sajuData, tenGodDistribution, hiddenStems, dayMaster);

        CareerConsultation consultation = consultationMapper.buildConsultation(sajuResult, advice, modelVersion);
        careerConsultationRepository.save(consultation);

        ConsultationResponse.SajuProfile sajuProfile = new ConsultationResponse.SajuProfile(
                dayMaster,
                advice.dayMasterDescription(),
                sajuData.fiveElements(),
                advice.fiveElementsAnalysis(),
                tenGodDistribution.asMap(),
                advice.keyTenGods()
        );

        String analysisSummary = consultationMapper.buildAnalysisSummary(
                dayMaster, tenGodDistribution, sajuData.fiveElements(), favoredPeriod);

        log.info("커리어 컨설팅 완료: sajuResultId={}, favoredPeriod={}", sajuResult.getId(), favoredPeriod);
        return new ConsultationResponse(
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

}
