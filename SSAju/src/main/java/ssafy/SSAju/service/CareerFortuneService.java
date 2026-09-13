package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.mapper.SajuResultMapper;
import ssafy.SSAju.career.provider.SajuAnalysisFacade;
import ssafy.SSAju.career.provider.SajuResultProvider;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.validator.SajuValidator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.dto.response.CareerTimingResponse;
import ssafy.SSAju.entity.User;
import ssafy.SSAju.exception.UnauthorizedException;
import ssafy.SSAju.exception.UserNotFoundException;
import ssafy.SSAju.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareerFortuneService {

    private final SajuDataService sajuDataService;
    private final UserProfileProvider userProfileProvider;
    private final SajuResultProvider sajuResultProvider;
    private final SajuAnalysisFacade sajuAnalysisFacade;
    private final SajuResultMapper sajuResultMapper;
    private final SajuValidator sajuValidator;
    private final UserRepository userRepository;

    /**
     * @Transactional 없음: FastAPI I/O 동안 DB 커넥션을 점유하지 않도록 트랜잭션을 분리.
     * 각 DB 작업은 하위 컴포넌트의 @Transactional에 의해 개별 트랜잭션으로 실행됨.
     *
     * <p>사주(SajuResult)는 userProfile 기준으로 불변이며 여러 사용자가 공유하는 정본이므로
     * findOrCreate로 재사용한다(B1). 동시성 경쟁은 SajuResultProvider의 userProfile 단위
     * 분산락(@DistributedLock)이 처리.
     *
     * <p>정본 최초 생성/최초 접근은 일일 쿼터를 차감하지 않는다(B1) — 쿼터는 OpenAI/공공데이터 등
     * 실제 과금성 외부 API 호출에만 적용되고, 캐시 가능한 사주 계산 자체는 쿼터 대상이 아니다.
     */
    public CareerTimingResponse analyzeCareerTiming(LocalDate birthDate, LocalTime birthTime, Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("인증 정보가 없습니다. 로그인 후 시도해주세요.");
        }
        log.info("관운 분석 시작");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        UserProfile userProfile = userProfileProvider.findOrCreate(birthDate, birthTime);

        FastAPIResponse sajuData = sajuDataService.fetchSajuFromFastAPI(birthDate, birthTime);
        sajuValidator.validate(sajuData);

        SajuAnalysisFacade.SajuAnalysisContext ctx = sajuAnalysisFacade.analyze(sajuData);
        log.debug("십신 분포: {}, 지장간: {}", ctx.tenGodDistribution(), ctx.hiddenStems());

        SajuResult newResult = sajuResultMapper.buildSajuResult(
                userProfile, sajuData, ctx.tenGodDistribution(), ctx.hiddenStems(),
                ctx.favoredPeriod(), ctx.confidenceScore(), ctx.reasoning());

        SajuResult savedResult = sajuResultProvider.findOrCreate(user, userProfile, newResult);

        log.info("관운 분석 완료: favoredPeriod={}", ctx.favoredPeriod());
        return new CareerTimingResponse(savedResult.getId(), ctx.favoredPeriod(), ctx.confidenceScore(), ctx.reasoning());
    }
}
