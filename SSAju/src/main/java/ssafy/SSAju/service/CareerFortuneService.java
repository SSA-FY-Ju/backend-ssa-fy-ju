package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.response.CareerTimingResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.repository.SajuResultRepository;
import ssafy.SSAju.repository.UserProfileRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareerFortuneService {

    private final SajuDataService sajuDataService;
    private final UserProfileRepository userProfileRepository;
    private final SajuResultRepository sajuResultRepository;
    private final TenGodCalculator tenGodCalculator;
    private final HiddenStemCalculator hiddenStemCalculator;
    private final CareerFortuneAnalyzer careerFortuneAnalyzer;

    @Transactional
    public CareerTimingResponse analyzeCareerTiming(LocalDate birthDate, LocalTime birthTime) {
        log.info("관운 분석 시작: {} {}", birthDate, birthTime);

        // 1. UserProfile 조회 또는 생성
        UserProfile userProfile = userProfileRepository
                .findByBirthDateAndBirthTime(birthDate, birthTime)
                .orElseGet(() -> {
                    UserProfile newProfile = UserProfile.builder()
                            .birthDate(birthDate)
                            .birthTime(birthTime)
                            .build();
                    return userProfileRepository.save(newProfile);
                });

        // 2. FastAPI에서 사주 기본 데이터 조회
        FastAPIResponse sajuData = sajuDataService.fetchSajuFromFastAPI(birthDate, birthTime);

        // 3. 천간·지지 검증
        List<String> heavenlyStems = sajuData.heavenlyStems();
        List<String> earthlyBranches = sajuData.earthlyBranches();
        if (heavenlyStems == null || heavenlyStems.size() != 4) {
            throw new InvalidSajuDataException("천간은 정확히 4개여야 합니다");
        }
        if (earthlyBranches == null || earthlyBranches.size() != 4) {
            throw new InvalidSajuDataException("지지는 정확히 4개여야 합니다");
        }

        // 4. 십신 계산 (Spring 책임 - TenGodCalculator)
        Map<String, Integer> tenGodDistribution = tenGodCalculator.calculate(heavenlyStems);
        log.debug("십신 분포: {}", tenGodDistribution);

        // 5. 지장간 계산 (Spring 책임 - HiddenStemCalculator)
        Map<String, List<String>> hiddenStems = hiddenStemCalculator.calculate(earthlyBranches);
        log.debug("지장간 분포: {}", hiddenStems);

        // 6. 관운 분석 → H1/H2 판정
        String dayMaster = heavenlyStems.get(2);
        String favoredPeriod = careerFortuneAnalyzer.analyzeFavoredPeriod(
                tenGodDistribution, hiddenStems, dayMaster, earthlyBranches);
        int confidenceScore = buildConfidenceScore(tenGodDistribution);
        String reasoning = buildReasoning(favoredPeriod, tenGodDistribution);

        // 7. SajuResult 저장
        Map<String, Object> careerFortune = new HashMap<>();
        careerFortune.put("favoredPeriod", favoredPeriod);
        careerFortune.put("confidenceScore", confidenceScore);
        careerFortune.put("reasoning", reasoning);

        sajuResultRepository.save(SajuResult.builder()
                .userProfile(userProfile)
                .fullSajuData(convertToObjectMap(sajuData))
                .hiddenStems(hiddenStems)
                .tenGodDistribution(tenGodDistribution)
                .careerFortune(careerFortune)
                .build());

        log.info("관운 분석 완료: userProfileId={}, result={}", userProfile.getId(), favoredPeriod);
        return new CareerTimingResponse(favoredPeriod, confidenceScore, reasoning);
    }

    private int buildConfidenceScore(Map<String, Integer> tenGodDistribution) {
        int score = 60;
        score += (tenGodDistribution.getOrDefault("정관", 0)
                + tenGodDistribution.getOrDefault("편관", 0)) * 10;
        score -= (tenGodDistribution.getOrDefault("식신", 0)
                + tenGodDistribution.getOrDefault("상관", 0)) * 8;
        return Math.min(100, Math.max(0, score));
    }

    private String buildReasoning(String favoredPeriod, Map<String, Integer> tenGodDistribution) {
        int officerCount = tenGodDistribution.getOrDefault("정관", 0)
                + tenGodDistribution.getOrDefault("편관", 0);
        StringBuilder sb = new StringBuilder(
                "H1".equals(favoredPeriod) ? "상반기가 취업에 유리합니다. " : "하반기가 취업에 유리합니다. ");
        if (officerCount > 0) sb.append("관성이 강해 리더십 역할에 적합합니다. ");
        sb.append("십신·지장간 통합 분석 기준입니다.");
        return sb.toString();
    }

    private Map<String, Object> convertToObjectMap(FastAPIResponse r) {
        Map<String, Object> map = new HashMap<>();
        map.put("heavenlyStems", r.heavenlyStems());
        map.put("earthlyBranches", r.earthlyBranches());
        map.put("fiveElements", r.fiveElements());
        map.put("yearPillar", r.yearPillar());
        map.put("monthPillar", r.monthPillar());
        map.put("dayPillar", r.dayPillar());
        map.put("hourPillar", r.hourPillar());
        map.put("birthTime", r.birthTime());
        map.put("birthDate", r.birthDate());
        return map;
    }
}
