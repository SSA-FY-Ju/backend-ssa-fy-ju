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
import ssafy.SSAju.career.provider.SajuAnalysisFacade;
import ssafy.SSAju.career.provider.SajuResultProvider;
import ssafy.SSAju.career.provider.UserProfileProvider;
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

import java.util.Optional;
import java.time.YearMonth;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationService {

    private final ConsultationOpenAICaller openAICaller;
    private final SajuDataService sajuDataService;
    private final SajuAnalysisFacade sajuAnalysisFacade;
    private final UserProfileProvider userProfileProvider;
    private final SajuResultProvider sajuResultProvider;
    private final SajuResultMapper sajuResultMapper;
    private final ConsultationMapper consultationMapper;
    private final CareerConsultationRepository careerConsultationRepository;
    private final ConsultationSaveService consultationSaveService;
    private final SajuValidator sajuValidator;
    private final UserRepository userRepository;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelVersion;

    /**
     * 커리어 컨설팅 조회.
     *
     * <p>같은 달 캐시가 있으면 OpenAI 호출을 생략하고 DB에 저장된 분석 결과를 반환 (M-9).
     * 외부 I/O(FastAPI, OpenAI) 동안 DB 커넥션을 점유하지 않도록 트랜잭션 미적용.
     * 저장 단계는 {@link ConsultationSaveService#save}에서 @Transactional 보장 (C-7).
     */
    public ConsultationResponse getCareerConsultation(ConsultationRequest request, Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("인증 정보가 없습니다. 로그인 후 시도해주세요.");
        }
        log.info("커리어 컨설팅 시작");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        // ─── 1. FastAPI 호출 (외부 I/O) ─────────────────────────────────────────
        FastAPIResponse sajuData = sajuDataService.fetchSajuFromFastAPI(
                request.birthDate(), request.birthTime());
        sajuValidator.validateWithFiveElements(sajuData);

        SajuAnalysisFacade.SajuAnalysisContext ctx = sajuAnalysisFacade.analyze(sajuData);
        TenGodDistribution tenGodDistribution = ctx.tenGodDistribution();
        HiddenStems hiddenStems = ctx.hiddenStems();
        String dayMaster = ctx.dayMaster();
        String favoredPeriod = ctx.favoredPeriod();
        int confidenceScore = ctx.confidenceScore();
        String reasoning = ctx.reasoning();

        // ─── 2. UserProfile / SajuResult 조회·생성 ───────────────────────────────
        UserProfile userProfile = userProfileProvider.findOrCreate(
                request.birthDate(), request.birthTime());
        SajuResult newResult = sajuResultMapper.buildSajuResult(
                userProfile, user, sajuData, tenGodDistribution, hiddenStems,
                favoredPeriod, confidenceScore, reasoning);
        SajuResult sajuResult = sajuResultProvider.findOrCreate(userProfile, newResult);

        // ─── 3. 캐시 조회 (M-9) ─────────────────────────────────────────────────
        String consultationMonth = YearMonth.now().toString();
        Optional<CareerConsultation> cached = careerConsultationRepository
                .findBySajuResultAndConsultationMonth(sajuResult, consultationMonth);

        if (cached.isPresent()) {
            CareerConsultation cachedData = cached.get();
            if (cachedData.getOpenaiModelVersion().equals(modelVersion)) {
                log.info("이번 달 컨설팅 캐시 히트 — OpenAI 호출 생략: sajuResultId={}, month={}",
                        sajuResult.getId(), consultationMonth);
                CareerAdviceResponse cachedAdvice = consultationMapper.restoreAdvice(cachedData);
                return consultationMapper.toResponse(sajuData, tenGodDistribution, dayMaster,
                        favoredPeriod, confidenceScore, reasoning, sajuResult,
                        cachedData.getId(), cachedAdvice, modelVersion);
            }
            log.info("캐시 모델 버전 불일치(캐시={}, 현재={}) — 재분석: sajuResultId={}",
                    cachedData.getOpenaiModelVersion(), modelVersion, sajuResult.getId());
        }

        // ─── 4. OpenAI 호출 (캐시 미스, 외부 I/O) ───────────────────────────────
        CareerAdviceResponse advice = openAICaller.call(
                sajuData, tenGodDistribution, hiddenStems, dayMaster);

        // ─── 5. 저장 (C-7: @Transactional 보장) ─────────────────────────────────
        Long consultationId = consultationSaveService.saveOrUpdate(sajuResult, advice, modelVersion, consultationMonth);

        log.info("커리어 컨설팅 완료: sajuResultId={}, favoredPeriod={}", sajuResult.getId(), favoredPeriod);
        return consultationMapper.toResponse(sajuData, tenGodDistribution, dayMaster,
                favoredPeriod, confidenceScore, reasoning, sajuResult, consultationId, advice, modelVersion);
    }
}
