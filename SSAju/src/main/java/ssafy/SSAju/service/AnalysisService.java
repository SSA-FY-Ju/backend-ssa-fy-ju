package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ssafy.SSAju.career.domain.AnalysisExecution;
import ssafy.SSAju.career.domain.AnalysisPayload;
import ssafy.SSAju.career.domain.AnalysisTimeContext;
import ssafy.SSAju.career.domain.BirthInfo;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.provider.ManseProvider;
import ssafy.SSAju.career.provider.UserProfileProvider;
import ssafy.SSAju.career.strategy.AnalysisOutcome;
import ssafy.SSAju.career.strategy.AnalysisStrategy;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.exception.UnauthorizedException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 세 분석 유형의 공통 실행 경로.
 *
 * <p>순서는 인증 확인 → 유형별 입력 검증 → 시간 문맥 확정 → (해당 유형만) 쿼터 차감 →
 * 프로필·만세력 확보 → 전략 실행·결과 검증 → 별도 트랜잭션 저장이다.
 *
 * <p>{@code @Transactional}이 없다 — FastAPI·OpenAI·공공데이터 호출이 이 흐름 한가운데에 있어서,
 * 트랜잭션을 걸면 외부 응답을 기다리는 동안 DB 커넥션을 붙잡게 된다. 저장은
 * {@link SajuWriteService}의 짧은 트랜잭션에서만 일어난다.
 *
 * <p>보상 범위는 "차감 이후부터 저장 프록시가 정상 반환할 때까지"다. 저장이 커밋된 뒤의 ID 주입·
 * 응답 조립·전송 실패는 이미 성공한 분석이므로 쿼터를 되돌리지 않는다.
 *
 * <p>기존 결과 캐시·요청 합치기·결과 단위 락·완료 상태 경로는 두지 않는다. 같은 입력이어도 요청마다
 * 새로 분석하고 새 결과를 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final List<AnalysisStrategy<?, ?>> strategies;
    private final UserProfileProvider userProfileProvider;
    private final ManseProvider manseProvider;
    private final SajuWriteService sajuWriteService;
    private final DailyApiUsageService dailyApiUsageService;
    private final Clock clock;

    public AnalysisExecution analyze(AnalysisType type, Object request, Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("인증 정보가 없습니다. 로그인 후 시도해주세요.");
        }
        AnalysisExecution execution = execute(select(type), type, request, userId);
        log.info("분석 완료: userId={}, analysisType={}, analysisId={}",
                userId, type, execution.analysisId());
        return execution;
    }

    private AnalysisStrategy<?, ?> select(AnalysisType type) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(type))
                .findFirst()
                .orElseThrow(() -> new InvalidSajuDataException(
                        ErrorMessageConstants.ANALYSIS_TYPE_UNSUPPORTED.getMessage()));
    }

    /**
     * 전략의 입력·결과 타입을 여기서 한 번 묶는다. 요청 객체의 cast도 이 경계에서만 수행한다.
     */
    private <I, R extends AnalysisPayload.Result> AnalysisExecution execute(
            AnalysisStrategy<I, R> strategy, AnalysisType type, Object request, Long userId) {

        I input = strategy.inputType().cast(request);
        strategy.validateInput(input);

        AnalysisTimeContext time = AnalysisTimeContext.from(clock);
        LocalDate usageDate = type.consumesDailyQuota()
                ? dailyApiUsageService.checkAndIncrementDailyUsage(userId)
                : null;

        try {
            BirthInfo birthInfo = strategy.birthInfo(input);
            UserProfile userProfile = userProfileProvider.findOrCreate(
                    birthInfo.birthDate(), birthInfo.birthTime());
            Manse manse = manseProvider.findOrCreate(userProfile);

            AnalysisOutcome<R> outcome = strategy.analyze(input, manse.getData(), time);
            strategy.validateResult(outcome.result());

            Long analysisId = sajuWriteService.save(
                    userId, type, manse, outcome.input(), outcome.result());
            return new AnalysisExecution(analysisId, outcome.result());
        } catch (RuntimeException e) {
            // 저장 커밋 전의 실패만 여기로 온다 — 차감한 쿼터를 원래 날짜로 복원한 뒤 원본 예외를 전파한다.
            if (usageDate != null) {
                dailyApiUsageService.restoreQuietly(userId, usageDate, e);
            }
            throw e;
        }
    }
}
