package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.domain.TenGodHiddenStemAnalysis;
import ssafy.SSAju.career.entity.Manse;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.util.HiddenStemCalculator;
import ssafy.SSAju.career.util.TenGodCalculator;
import ssafy.SSAju.career.validator.SajuValidator;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.repository.ManseRepository;
import ssafy.SSAju.service.ManseWriteService;
import ssafy.SSAju.service.SajuDataService;

import java.time.Clock;

/**
 * 출생정보 프로필의 만세력을 조회하거나 최초 1회 생성한다.
 *
 * <p>분산락을 사용하지 않는다 — 프로필당 하나라는 보장은 DB 유일 제약이 이미 해준다. 동시에 들어온
 * 최초 요청들이 FastAPI를 중복 호출하는 것은 허용하고(계산은 같은 출생정보에 대해 결정론적이다),
 * 저장 경합에서 진 요청은 승자가 남긴 정본을 다시 읽어 수렴한다.
 *
 * <p>외부 호출은 트랜잭션 밖에서 수행하고 저장만 {@link ManseWriteService}의 짧은 트랜잭션으로
 * 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManseProvider {

    private final ManseRepository manseRepository;
    private final ManseWriteService manseWriteService;
    private final SajuDataService sajuDataService;
    private final SajuValidator sajuValidator;
    private final TenGodCalculator tenGodCalculator;
    private final HiddenStemCalculator hiddenStemCalculator;
    private final Clock clock;

    public Manse findOrCreate(UserProfile userProfile) {
        if (userProfile == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.SAJU_PROFILE_NULL.getMessage());
        }
        return manseRepository.findByUserProfile(userProfile)
                .orElseGet(() -> create(userProfile));
    }

    private Manse create(UserProfile userProfile) {
        FastAPIResponse sajuData = sajuDataService.fetchSajuFromFastAPI(
                userProfile.getBirthDate(), userProfile.getBirthTime());
        sajuValidator.validateWithFiveElements(sajuData);

        ManseData data = ManseData.of(sajuData, TenGodHiddenStemAnalysis.of(
                tenGodCalculator.calculate(sajuData.heavenlyStems()),
                hiddenStemCalculator.calculate(sajuData.earthlyBranches())));

        try {
            return manseWriteService.save(userProfile, data, clock.instant());
        } catch (DataIntegrityViolationException ex) {
            // 같은 프로필의 만세력을 다른 요청이 먼저 저장했다 — 알려진 경합이므로 정본을 다시 읽는다.
            log.info("만세력 생성 경합 — 저장된 정본 재조회: userProfileId={}", userProfile.getId());
            return manseRepository.findByUserProfile(userProfile)
                    .orElseThrow(() -> new DataAccessException(
                            ErrorMessageConstants.MANSE_ACCESS_FAILED.getMessage(), ex));
        }
    }
}
