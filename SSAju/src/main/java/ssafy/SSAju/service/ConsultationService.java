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
import ssafy.SSAju.career.enums.SajuPillarIndex;
import ssafy.SSAju.career.mapper.ConsultationMapper;
import ssafy.SSAju.career.mapper.SajuResultMapper;
import ssafy.SSAju.career.provider.SajuResultProvider;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.enums.TenGodConstants;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.career.validator.SajuValidator;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.UnauthorizedException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final UserRepository userRepository;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelVersion;

    /**
     * @Transactional 없음: FastAPI/OpenAI I/O 동안 DB 커넥션을 점유하지 않도록 트랜잭션 분리.
     */
    public ConsultationResponse getCareerConsultation(ConsultationRequest request, Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("인증 정보가 없습니다. 로그인 후 시도해주세요.");
        }
        log.info("커리어 컨설팅 시작");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        FastAPIResponse sajuData = sajuDataService.fetchSajuFromFastAPI(request.birthDate(), request.birthTime());
        sajuValidator.validateWithFiveElements(sajuData);

        List<String> heavenlyStems = sajuData.heavenlyStems();
        List<String> earthlyBranches = sajuData.earthlyBranches();
        TenGodDistribution tenGodDistribution = tenGodCalculator.calculate(heavenlyStems);
        HiddenStems hiddenStems = hiddenStemCalculator.calculate(earthlyBranches);

        String dayMaster = heavenlyStems.get(SajuPillarIndex.DAY_INDEX);
        String favoredPeriod = careerFortuneAnalyzer.analyzeFavoredPeriod(
                tenGodDistribution, hiddenStems, dayMaster, earthlyBranches);
        int confidenceScore = careerFortuneAnalyzer.calculateConfidenceScore(
                tenGodDistribution, hiddenStems, dayMaster);
        String reasoning = careerFortuneAnalyzer.buildReasoning(favoredPeriod, tenGodDistribution);

        UserProfile userProfile = userProfileProvider.findOrCreate(request.birthDate(), request.birthTime());

        SajuResult newResult = sajuResultMapper.buildSajuResult(
                userProfile, user, sajuData, tenGodDistribution, hiddenStems,
                favoredPeriod, confidenceScore, reasoning);
        SajuResult sajuResult = sajuResultProvider.findOrCreate(userProfile, newResult);

        CareerAdviceResponse advice = openAICaller.call(sajuData, tenGodDistribution, hiddenStems, dayMaster);

        CareerConsultation consultation = consultationMapper.buildConsultation(sajuResult, advice, modelVersion);
        careerConsultationRepository.save(consultation);

        Map<String, String> tenGodCharacteristics = tenGodDistribution.asMap().keySet().stream()
                .collect(Collectors.toMap(
                        name -> name,
                        name -> {
                            TenGodConstants tg = TenGodConstants.fromName(name);
                            return tg != null ? tg.getCharacteristics() : "";
                        }
                ));

        ConsultationResponse.SajuProfile sajuProfile = new ConsultationResponse.SajuProfile(
                dayMaster,
                advice.dayMasterDescription(),
                sajuData.fiveElements(),
                advice.fiveElementsAnalysis(),
                tenGodDistribution.asMap(),
                advice.keyTenGods(),
                tenGodCharacteristics
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
