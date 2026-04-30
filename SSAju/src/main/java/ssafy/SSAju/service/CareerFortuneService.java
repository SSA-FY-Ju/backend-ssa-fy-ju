package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.response.CareerTimingResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.repository.UserProfileRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareerFortuneService {

    private final SajuDataService sajuDataService;
    private final UserProfileRepository userProfileRepository;
    private final SajuResultWriteService sajuResultWriteService;
    private final TenGodCalculator tenGodCalculator;
    private final HiddenStemCalculator hiddenStemCalculator;
    private final CareerFortuneAnalyzer careerFortuneAnalyzer;

    /**
     * @Transactional 없음: FastAPI I/O 동안 DB 커넥션을 점유하지 않도록 트랜잭션을 분리.
     * 각 DB 작업은 Repository의 @Transactional에 의해 개별 트랜잭션으로 실행됨.
     */
    public CareerTimingResponse analyzeCareerTiming(LocalDate birthDate, LocalTime birthTime) {
        log.info("관운 분석 시작");

        // 트랜잭션 1 (수 ms): UserProfile 조회/생성 후 커넥션 즉시 반납
        UserProfile userProfile = findOrCreateUserProfile(birthDate, birthTime);

        // 커넥션 없음: FastAPI 네트워크 I/O (수백 ms ~ 수 초)
        FastAPIResponse sajuData = sajuDataService.fetchSajuFromFastAPI(birthDate, birthTime);

        // 천간·지지 검증
        List<String> heavenlyStems = sajuData.heavenlyStems();
        List<String> earthlyBranches = sajuData.earthlyBranches();
        if (heavenlyStems == null || heavenlyStems.size() != 4) {
            throw new InvalidSajuDataException("천간은 정확히 4개여야 합니다");
        }
        if (earthlyBranches == null || earthlyBranches.size() != 4) {
            throw new InvalidSajuDataException("지지는 정확히 4개여야 합니다");
        }

        // 십신·지장간 계산
        Map<String, Integer> tenGodDistribution = tenGodCalculator.calculate(heavenlyStems);
        log.debug("십신 분포: {}", tenGodDistribution);
        Map<String, List<String>> hiddenStems = hiddenStemCalculator.calculate(earthlyBranches);
        log.debug("지장간 분포: {}", hiddenStems);

        // 관운 분석 → H1/H2 판정
        String dayMaster = heavenlyStems.get(2);
        String favoredPeriod = careerFortuneAnalyzer.analyzeFavoredPeriod(
                tenGodDistribution, hiddenStems, dayMaster, earthlyBranches);
        int confidenceScore = buildConfidenceScore(tenGodDistribution);
        String reasoning = buildReasoning(favoredPeriod, tenGodDistribution);

        // 트랜잭션 2 (수 ms): SajuResult upsert 후 커넥션 즉시 반납
        // DIVE = 동시 요청이 먼저 commit → rollback(delete 포함)되어 기존 결과 유지
        try {
            sajuResultWriteService.replaceForUserProfile(userProfile, sajuData,
                    tenGodDistribution, hiddenStems, favoredPeriod, confidenceScore, reasoning);
        } catch (DataIntegrityViolationException ex) {
            log.warn("SajuResult 동시 insert 경합, 기존 결과 유지 (userProfileId={})", userProfile.getId());
        }

        log.info("관운 분석 완료: userProfileId={}, result={}", userProfile.getId(), favoredPeriod);
        return new CareerTimingResponse(favoredPeriod, confidenceScore, reasoning);
    }

    /**
     * 낙관적 생성 패턴: 동시 요청이 동일 (birthDate, birthTime)으로 들어올 때
     * UNIQUE 위반을 catch 후 재조회하여 race condition 해결.
     */
    private UserProfile findOrCreateUserProfile(LocalDate birthDate, LocalTime birthTime) {
        return userProfileRepository
                .findByBirthDateAndBirthTime(birthDate, birthTime)
                .orElseGet(() -> {
                    try {
                        return userProfileRepository.save(
                                UserProfile.builder()
                                        .birthDate(birthDate)
                                        .birthTime(birthTime)
                                        .build());
                    } catch (DataIntegrityViolationException ex) {
                        // 동시 요청이 먼저 commit → 재조회
                        return userProfileRepository
                                .findByBirthDateAndBirthTime(birthDate, birthTime)
                                .orElseThrow(() -> new IllegalStateException(
                                        "UserProfile 조회/생성 실패", ex));
                    }
                });
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
}
