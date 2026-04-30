package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.util.CareerFortuneAnalyzer;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.request.ConsultationRequest;
import ssafy.SSAju.dto.response.ConsultationResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.exception.OpenAIApiException;
import ssafy.SSAju.repository.CareerConsultationRepository;
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
public class ConsultationService {

    private final ChatClient chatClient;
    private final SajuDataService sajuDataService;
    private final TenGodCalculator tenGodCalculator;
    private final HiddenStemCalculator hiddenStemCalculator;
    private final CareerFortuneAnalyzer careerFortuneAnalyzer;
    private final UserProfileRepository userProfileRepository;
    private final SajuResultRepository sajuResultRepository;
    private final CareerConsultationRepository careerConsultationRepository;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelVersion;

    /**
     * @Transactional 없음: FastAPI/OpenAI I/O 동안 DB 커넥션을 점유하지 않도록 트랜잭션 분리.
     * 각 DB 작업은 Repository의 @Transactional에 의해 개별 트랜잭션으로 실행됨.
     */
    public ConsultationResponse getCareerConsultation(ConsultationRequest request) {
        log.info("커리어 컨설팅 시작: birthDate={}, birthTime={}", request.birthDate(), request.birthTime());

        // FastAPI로부터 사주 데이터 조회 (외부 I/O — 트랜잭션 밖)
        FastAPIResponse sajuData = sajuDataService.fetchSajuFromFastAPI(request.birthDate(), request.birthTime());

        List<String> heavenlyStems = sajuData.heavenlyStems();
        List<String> earthlyBranches = sajuData.earthlyBranches();
        if (heavenlyStems == null || heavenlyStems.size() != 4) {
            throw new InvalidSajuDataException("천간은 정확히 4개여야 합니다");
        }
        if (earthlyBranches == null || earthlyBranches.size() != 4) {
            throw new InvalidSajuDataException("지지는 정확히 4개여야 합니다");
        }

        // 십신 분포 및 지장간 계산 (CPU only)
        Map<String, Integer> tenGodDistribution = tenGodCalculator.calculate(heavenlyStems);
        Map<String, List<String>> hiddenStems = hiddenStemCalculator.calculate(earthlyBranches);

        // 관운 분석 (H1/H2, 신뢰도, 근거)
        String dayMaster = heavenlyStems.get(2);
        String favoredPeriod = careerFortuneAnalyzer.analyzeFavoredPeriod(
                tenGodDistribution, hiddenStems, dayMaster, earthlyBranches);
        int confidenceScore = careerFortuneAnalyzer.calculateConfidenceScore(
                tenGodDistribution, hiddenStems, dayMaster);
        String reasoning = buildReasoning(favoredPeriod, tenGodDistribution);

        // DB 1: UserProfile 조회/생성 후 커넥션 즉시 반납
        UserProfile userProfile = findOrCreateUserProfile(request.birthDate(), request.birthTime());

        // DB 2: SajuResult 조회/생성 (기존 결과 재사용 또는 신규 생성) 후 커넥션 즉시 반납
        SajuResult sajuResult = findOrCreateSajuResult(userProfile, sajuData, tenGodDistribution, hiddenStems);

        // OpenAI 호출 (외부 I/O — 트랜잭션 밖)
        CareerAdviceResponse advice = callOpenAI(sajuData, tenGodDistribution, hiddenStems);

        // DB 3: CareerConsultation 저장
        CareerConsultation consultation = CareerConsultation.builder()
                .sajuResult(sajuResult)
                .industries(advice.industries())
                .interviewTips(advice.interviewTips())
                .strengths(advice.strengths())
                .openaiModelVersion(modelVersion)
                .build();
        careerConsultationRepository.save(consultation);

        log.info("커리어 컨설팅 완료: sajuResultId={}, favoredPeriod={}", sajuResult.getId(), favoredPeriod);
        return new ConsultationResponse(
                advice.industries(), advice.interviewTips(), advice.strengths(),
                modelVersion, favoredPeriod, confidenceScore, reasoning);
    }

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
                        return userProfileRepository
                                .findByBirthDateAndBirthTime(birthDate, birthTime)
                                .orElseThrow(() -> new IllegalStateException("UserProfile 조회/생성 실패", ex));
                    }
                });
    }

    private SajuResult findOrCreateSajuResult(UserProfile userProfile, FastAPIResponse sajuData,
                                               Map<String, Integer> tenGodDistribution,
                                               Map<String, List<String>> hiddenStems) {
        return sajuResultRepository.findByUserProfile(userProfile)
                .orElseGet(() -> {
                    try {
                        return sajuResultRepository.save(
                                SajuResult.builder()
                                        .userProfile(userProfile)
                                        .fullSajuData(toObjectMap(sajuData))
                                        .hiddenStems(hiddenStems)
                                        .tenGodDistribution(tenGodDistribution)
                                        .build());
                    } catch (DataIntegrityViolationException ex) {
                        return sajuResultRepository.findByUserProfile(userProfile)
                                .orElseThrow(() -> new IllegalStateException("SajuResult 조회/생성 실패", ex));
                    }
                });
    }

    private Map<String, Object> toObjectMap(FastAPIResponse r) {
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

    private CareerAdviceResponse callOpenAI(FastAPIResponse sajuData,
                                            Map<String, Integer> tenGodDistribution,
                                            Map<String, List<String>> hiddenStems) {
        String prompt = buildPrompt(sajuData, tenGodDistribution, hiddenStems);
        try {
            CareerAdviceResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(CareerAdviceResponse.class);
            if (response == null) {
                throw new OpenAIApiException("OpenAI 응답이 비어있습니다");
            }
            return response;
        } catch (OpenAIApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI API 호출 실패: {}", e.getMessage());
            throw new OpenAIApiException("OpenAI API 호출 실패: " + e.getMessage(), e);
        }
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

    private String buildPrompt(FastAPIResponse sajuData,
                                Map<String, Integer> tenGodDistribution,
                                Map<String, List<String>> hiddenStems) {
        return """
                당신은 사주 명리학 전문가입니다. 아래 사주 데이터를 분석하여 취업 준비생에게 맞춤 커리어 조언을 제공해주세요.

                [사주 데이터]
                - 천간(天干): %s
                - 지지(地支): %s
                - 오행 분포: %s
                - 지장간(地藏干): %s
                - 십신 분포(十神): %s

                아래 JSON 형식으로 정확히 응답해주세요:
                {
                  "industries": [{"name": "산업명", "reason": "사주에 기반한 이유"}],
                  "interviewTips": ["팁1", "팁2", "팁3"],
                  "strengths": ["강점1", "강점2", "강점3"]
                }

                규칙:
                - industries: 추천 산업군 3-5개 (각각 name과 reason 포함)
                - interviewTips: 면접 준비 팁 3개 (사주 특성 기반)
                - strengths: 직무 강점 3개 (십신과 지장간 분석 기반)
                """.formatted(
                sajuData.heavenlyStems(),
                sajuData.earthlyBranches(),
                sajuData.fiveElements(),
                hiddenStems,
                tenGodDistribution
        );
    }
}
