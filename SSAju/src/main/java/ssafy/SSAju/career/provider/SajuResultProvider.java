package ssafy.SSAju.career.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ssafy.SSAju.career.entity.CareerFortune;
import ssafy.SSAju.career.entity.HiddenStemData;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.TenGodData;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.DataAccessException;
import ssafy.SSAju.exception.InvalidSajuDataException;
import ssafy.SSAju.repository.SajuResultJdbcRepository;
import ssafy.SSAju.repository.SajuResultRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SajuResultProvider {

    private final SajuResultRepository sajuResultRepository;
    private final SajuResultJdbcRepository sajuResultJdbcRepository;

    public SajuResult findOrCreate(UserProfile userProfile, SajuResult newResult) {
        if (userProfile == null || newResult == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.SAJU_PROFILE_NULL.getMessage());
        }
        if (newResult.getUserProfile() != null
                && !newResult.getUserProfile().equals(userProfile)) {
            throw new InvalidSajuDataException(ErrorMessageConstants.USER_PROFILE_MISMATCH.getMessage());
        }

        var existing = sajuResultRepository.findByUserProfile(userProfile);
        if (existing.isPresent()) {
            return existing.get();
        }

        int inserted = sajuResultJdbcRepository.insertOrIgnore(newResult);

        SajuResult saved = sajuResultRepository.findByUserProfile(userProfile)
                .orElseThrow(() -> new DataAccessException(ErrorMessageConstants.SAJU_RESULT_ACCESS_FAILED.getMessage()));

        if (inserted == 1) {
            attachChildren(saved, newResult);
            return sajuResultRepository.save(saved);
        }
        return saved;
    }

    private void attachChildren(SajuResult saved, SajuResult source) {
        List<TenGodData> tenGods = source.getTenGodDataList().stream()
                .map(e -> TenGodData.builder()
                        .sajuResult(saved)
                        .tenGodName(e.getTenGodName())
                        .score(e.getScore())
                        .build())
                .toList();

        List<HiddenStemData> hiddenStems = source.getHiddenStemDataList().stream()
                .map(e -> HiddenStemData.builder()
                        .sajuResult(saved)
                        .earthlyBranch(e.getEarthlyBranch())
                        .hiddenStem(e.getHiddenStem())
                        .build())
                .toList();

        saved.assignTenGodData(tenGods);
        saved.assignHiddenStemData(hiddenStems);

        CareerFortune cf = source.getCareerFortune();
        if (cf != null) {
            saved.assignCareerFortune(CareerFortune.builder()
                    .sajuResult(saved)
                    .favoredPeriod(cf.getFavoredPeriod())
                    .confidenceScore(cf.getConfidenceScore())
                    .reasoning(cf.getReasoning())
                    .build());
        }
    }
}
