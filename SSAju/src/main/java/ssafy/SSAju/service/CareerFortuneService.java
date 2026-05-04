package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.mapper.SajuResultMapper;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.response.CareerTimingResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareerFortuneService {

    private final SajuDataService sajuDataService;
    private final UserProfileProvider userProfileProvider;
    private final SajuResultWriteService sajuResultWriteService;
    private final TenGodCalculator tenGodCalculator;
    private final HiddenStemCalculator hiddenStemCalculator;
    private final CareerFortuneAnalyzer careerFortuneAnalyzer;
    private final SajuResultMapper sajuResultMapper;

    /**
     * @Transactional 없음: FastAPI I/O 동안 DB 커넥션을 점유하지 않도록 트랜잭션을 분리.
     * 각 DB 작업은 하위 컴포넌트의 @Transactional에 의해 개별 트랜잭션으로 실행됨.
     */
    public CareerTimingResponse analyzeCareerTiming(LocalDate birthDate, LocalTime birthTime) {
        log.info("관운 분석 시작");

        UserProfile userProfile = userProfileProvider.findOrCreate(birthDate, birthTime);

        FastAPIResponse sajuData = sajuDataService.fetchSajuFromFastAPI(birthDate, birthTime);
        validateSajuData(sajuData);

        List<String> heavenlyStems = sajuData.heavenlyStems();
        List<String> earthlyBranches = sajuData.earthlyBranches();
        Map<String, Integer> tenGodDistribution = tenGodCalculator.calculate(heavenlyStems);
        Map<String, List<String>> hiddenStems = hiddenStemCalculator.calculate(earthlyBranches);
        log.debug("십신 분포: {}, 지장간: {}", tenGodDistribution, hiddenStems);

        String dayMaster = heavenlyStems.get(2);
        String favoredPeriod = careerFortuneAnalyzer.analyzeFavoredPeriod(
                tenGodDistribution, hiddenStems, dayMaster, earthlyBranches);
        int confidenceScore = careerFortuneAnalyzer.calculateConfidenceScore(
                tenGodDistribution, hiddenStems, dayMaster);
        String reasoning = careerFortuneAnalyzer.buildReasoning(favoredPeriod, tenGodDistribution);

        SajuResult newResult = sajuResultMapper.buildSajuResult(
                userProfile, sajuData, tenGodDistribution, hiddenStems,
                favoredPeriod, confidenceScore, reasoning);

        try {
            sajuResultWriteService.replaceForUserProfile(userProfile, newResult);
        } catch (DataIntegrityViolationException ex) {
            log.warn("SajuResult 동시 insert 경합, 기존 결과 유지 (userId={})", userProfile.getId());
        }

        log.info("관운 분석 완료: favoredPeriod={}", favoredPeriod);
        return new CareerTimingResponse(favoredPeriod, confidenceScore, reasoning);
    }

    private void validateSajuData(FastAPIResponse sajuData) {
        List<String> heavenlyStems = sajuData.heavenlyStems();
        List<String> earthlyBranches = sajuData.earthlyBranches();
        if (heavenlyStems == null || heavenlyStems.size() != 4) {
            throw new InvalidSajuDataException(ErrorMessageConstants.HEAVENLY_STEMS_COUNT_INVALID.getMessage());
        }
        if (earthlyBranches == null || earthlyBranches.size() != 4) {
            throw new InvalidSajuDataException(ErrorMessageConstants.EARTHLY_BRANCHES_COUNT_INVALID.getMessage());
        }
    }
}
