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
import java.util.stream.Collectors;

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

        // DB 2: SajuResult 조회/생성 후 커넥션 즉시 반납
        SajuResult sajuResult = findOrCreateSajuResult(userProfile, sajuData, tenGodDistribution, hiddenStems);

        // OpenAI 호출 (외부 I/O — 트랜잭션 밖)
        CareerAdviceResponse advice = callOpenAI(sajuData, tenGodDistribution, hiddenStems, dayMaster);

        // DB 3: CareerConsultation 저장
        CareerConsultation consultation = CareerConsultation.builder()
                .sajuResult(sajuResult)
                .industries(toIndustriesMap(advice.industries()))
                .interviewTips(advice.interviewTips())
                .strengths(advice.strengths())
                .openaiModelVersion(modelVersion)
                .build();
        careerConsultationRepository.save(consultation);

        // sajuProfile 구성 (Spring 데이터 + OpenAI 분석 혼합)
        ConsultationResponse.SajuProfile sajuProfile = new ConsultationResponse.SajuProfile(
                dayMaster,
                advice.dayMasterDescription(),
                sajuData.fiveElements(),
                advice.fiveElementsAnalysis(),
                tenGodDistribution,
                advice.keyTenGods()
        );

        String analysisSummary = buildAnalysisSummary(dayMaster, tenGodDistribution, sajuData.fiveElements(), favoredPeriod);

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

    private List<Map<String, String>> toIndustriesMap(List<CareerAdviceResponse.IndustryRecommendation> industries) {
        return industries.stream()
                .map(i -> Map.of("name", i.name(), "reason", i.reason()))
                .toList();
    }

    private CareerAdviceResponse callOpenAI(FastAPIResponse sajuData,
                                            Map<String, Integer> tenGodDistribution,
                                            Map<String, List<String>> hiddenStems,
                                            String dayMaster) {
        String prompt = buildPrompt(sajuData, tenGodDistribution, hiddenStems, dayMaster);
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
        if (officerCount > 0) {
            sb.append("정관(正官)의 운이 ").append("H1".equals(favoredPeriod) ? "상반기" : "하반기")
              .append("에 집중되어 있어 조직의 부름이 많아지고, 면접에서 호의적인 평가를 받기 쉬운 시기입니다. ");
        }
        sb.append("십신·지장간 통합 분석 기준입니다.");
        return sb.toString();
    }

    private String buildPrompt(FastAPIResponse sajuData,
                                Map<String, Integer> tenGodDistribution,
                                Map<String, List<String>> hiddenStems,
                                String dayMaster) {
        int currentYear = LocalDate.now().getYear();
        return """
                당신은 사주 명리학 전문가이자 취업 커리어 컨설턴트입니다.
                아래 사주 데이터를 분석하여 취업 준비생에게 맞춤 커리어 조언을 한글로 제공해주세요.

                [사주 데이터]
                - 일간(日干): %s
                - 천간(天干): %s
                - 지지(地支): %s
                - 오행 분포: %s
                - 지장간(地藏干): %s
                - 십신 분포(十神): %s

                [분석 요청]
                - 취업 적합 산업군 3~5개 (name, reason, recommendedRoles 포함)
                - 면접 전략 및 직무 강점·약점 분석
                - 재물운, 장기 커리어 로드맵(0~2년, 3~5년 단계)
                - 퍼스널 브랜딩, 자소서 파워키워드(3개, 오행 기반, 해시태그 형식)
                - 멘탈 케어, 최적 근무 환경, 업무 스타일, 인간관계 전략
                - %d년 기준 12개월 월별 운세 및 전환점(pivotPoints: 점수 8 이상인 달만)
                - 일간(%s) 기반 성향 분석 및 핵심 십신 2~3개 선별

                [중요] careerTimeline.months의 각 달은 반드시 객체 형식으로 응답:
                올바른 예: "January": {"type": "적극기", "description": "면접 기회가 많은 시기"}
                잘못된 예: "January": "좋음" 또는 "January": 3
                """.formatted(
                dayMaster,
                sajuData.heavenlyStems(),
                sajuData.earthlyBranches(),
                sajuData.fiveElements(),
                hiddenStems,
                tenGodDistribution,
                currentYear,
                dayMaster
        );
    }

    private String buildAnalysisSummary(String dayMaster,
                                         Map<String, Integer> tenGodDistribution,
                                         Map<String, Integer> fiveElements,
                                         String favoredPeriod) {
        String dominantElements = fiveElements.entrySet().stream()
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
}
