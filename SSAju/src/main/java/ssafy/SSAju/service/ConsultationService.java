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
import ssafy.SSAju.exception.ConsultationRecoveryFailedException;
import ssafy.SSAju.exception.UnauthorizedException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.CareerConsultationRepository;
import ssafy.SSAju.repository.UserRepository;
import ssafy.SSAju.service.DailyApiUsageService;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.time.YearMonth;
import java.time.LocalDate;

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
    private final DailyApiUsageService dailyApiUsageService;
    /** KST 기준 현재 월 계산용 Clock. 테스트에서 고정 시각 주입 가능. */
    private final Clock clock;

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
        SajuResult sajuResult = sajuResultProvider.findOrCreate(user, userProfile, newResult);

        // ─── 3. 캐시 조회 (M-9) ─────────────────────────────────────────────────
        YearMonth now = YearMonth.now(clock);
        Integer consultationMonth = now.getYear() * 100 + now.getMonthValue();
        Optional<CareerConsultation> cached = careerConsultationRepository
                .findBySajuResultAndConsultationMonth(sajuResult, consultationMonth);

        if (cached.isPresent()) {
            CareerConsultation cachedData = cached.get();
            if (Objects.equals(cachedData.getOpenaiModelVersion(), modelVersion)) {
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
        // 캐시 히트 경로는 위에서 이미 반환 → 여기까지 온 경우만 신규 OpenAI 호출, 차감
        // 호출 실패 시 쿼터가 소진된 채 남지 않도록 차감 이후 구간을 보상 트랜잭션으로 감싼다
        LocalDate usageDate = dailyApiUsageService.checkAndIncrementDailyUsage(userId);
        CareerAdviceResponse advice;
        try {
            advice = openAICaller.call(sajuData, tenGodDistribution, hiddenStems, dayMaster);
        } catch (RuntimeException e) {
            dailyApiUsageService.restoreDailyUsage(userId, usageDate);
            throw e;
        }

        // ─── 5. 저장 (C-7: @Transactional 보장) ─────────────────────────────────
        ConsultationSaveService.SaveOutcome outcome;
        try {
            outcome = consultationSaveService.saveOrUpdate(sajuResult, advice, modelVersion, consultationMonth);
        } catch (RuntimeException e) {
            // 저장/경합 복구 자체가 실패한 경우(예: ConsultationRecoveryFailedException) — 이 요청은
            // 어떤 값도 만들지 못했으므로 앞서 차감한 쿼터를 복원한 뒤 원본 예외를 그대로 전파한다.
            try {
                dailyApiUsageService.restoreDailyUsage(userId, usageDate);
            } catch (RuntimeException restoreException) {
                log.error("쿼터 복원 실패 (userId={}, usageDate={})", userId, usageDate, restoreException);
                e.addSuppressed(restoreException);
            }
            throw e;
        }
        CareerAdviceResponse responseAdvice = advice;
        if (!outcome.persisted()) {
            // 따닥(동일 요청 동시 도착)으로 락 안 재확인에서 다른 요청이 이미 저장한 결과로
            // 수렴한 경우 — 이 요청이 방금 낸 OpenAI 호출은 어떤 값도 만들지 못했으므로
            // 앞서 차감한 일일 쿼터를 보상 복원한다.
            try {
                dailyApiUsageService.restoreDailyUsage(userId, usageDate);
            } catch (RuntimeException restoreException) {
                log.error("경합으로 인한 쿼터 복원 실패 (userId={}, usageDate={})", userId, usageDate, restoreException);
            }
            // 이 요청이 만든 advice는 버려졌으므로, 그대로 반환하면 실제 DB에 저장된 내용과
            // 달라질 수 있다(OpenAI 응답은 호출마다 조금씩 다름). outcome.consultationId()가
            // 가리키는 실제 저장분을 다시 읽어와 응답에 실어야 consultationId와 내용이 일치한다.
            CareerConsultation persisted = careerConsultationRepository.findById(outcome.consultationId())
                    .orElseThrow(() -> new ConsultationRecoveryFailedException(
                            "경합 후 저장된 CareerConsultation 재조회 실패: consultationId=" + outcome.consultationId()));
            responseAdvice = consultationMapper.restoreAdvice(persisted);
        }

        log.info("커리어 컨설팅 완료: sajuResultId={}, favoredPeriod={}", sajuResult.getId(), favoredPeriod);
        return consultationMapper.toResponse(sajuData, tenGodDistribution, dayMaster,
                favoredPeriod, confidenceScore, reasoning, sajuResult, outcome.consultationId(), responseAdvice, modelVersion);
    }
}
