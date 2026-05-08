package ssafy.SSAju.career.mapper;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.domain.HiddenStems;
import ssafy.SSAju.career.domain.TenGodDistribution;
import ssafy.SSAju.career.entity.CareerFortune;
import ssafy.SSAju.career.entity.HiddenStemData;
import ssafy.SSAju.career.entity.SajuFullData;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.TenGodData;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.enums.SajuPillarIndex;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SajuResultMapper {

    // 천간(天干) → 오행(五行) 매핑. FastAPIResponse가 천간을 String으로 제공하므로 Map 사용.
    // 유효하지 않은 천간 입력은 toSajuFullData()에서 fail-fast로 처리.
    private static final Map<String, String> STEM_ELEMENT_MAP = Map.of(
            "甲", "木", "乙", "木",
            "丙", "火", "丁", "火",
            "戊", "土", "己", "土",
            "庚", "金", "辛", "金",
            "壬", "水", "癸", "水"
    );

    public SajuResult buildSajuResult(UserProfile userProfile,
                                       FastAPIResponse sajuData,
                                       TenGodDistribution tenGodDistribution,
                                       HiddenStems hiddenStems,
                                       String favoredPeriod,
                                       int confidenceScore,
                                       String reasoning) {
        SajuResult result = SajuResult.builder()
                .userProfile(userProfile)
                .build();

        SajuFullData fullData = toSajuFullData(result, sajuData);
        List<TenGodData> tenGodEntities = toTenGodDataList(result, tenGodDistribution);
        List<HiddenStemData> hiddenStemEntities = toHiddenStemDataList(result, hiddenStems);
        CareerFortune careerFortune = CareerFortune.builder()
                .sajuResult(result)
                .favoredPeriod(favoredPeriod)
                .confidenceScore(confidenceScore)
                .reasoning(reasoning)
                .build();

        result.assignSajuFullData(fullData);
        result.assignTenGodData(tenGodEntities);
        result.assignHiddenStemData(hiddenStemEntities);
        result.assignCareerFortune(careerFortune);
        return result;
    }

    public SajuResult buildSajuResultWithoutFortune(UserProfile userProfile,
                                                     FastAPIResponse sajuData,
                                                     TenGodDistribution tenGodDistribution,
                                                     HiddenStems hiddenStems) {
        SajuResult result = SajuResult.builder()
                .userProfile(userProfile)
                .build();

        result.assignSajuFullData(toSajuFullData(result, sajuData));
        result.assignTenGodData(toTenGodDataList(result, tenGodDistribution));
        result.assignHiddenStemData(toHiddenStemDataList(result, hiddenStems));
        return result;
    }

    public SajuFullData toSajuFullData(SajuResult result, FastAPIResponse r) {
        String dayMaster = extractDayMaster(r.heavenlyStems());
        String dayMasterElement = STEM_ELEMENT_MAP.get(dayMaster);
        if (dayMasterElement == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.INVALID_HEAVENLY_STEM.getMessage());
        }
        return SajuFullData.builder()
                .sajuResult(result)
                .yearPillar(r.yearPillar())
                .monthPillar(r.monthPillar())
                .dayPillar(r.dayPillar())
                .hourPillar(r.hourPillar())
                .dayMaster(dayMaster)
                .dayMasterElement(dayMasterElement)
                .fiveElements(r.fiveElements())
                .solarCorrection(r.solarCorrection())
                .build();
    }

    private String extractDayMaster(List<String> heavenlyStems) {
        if (heavenlyStems == null || heavenlyStems.size() <= SajuPillarIndex.DAY_INDEX) {
            throw new InvalidSajuDataException(ErrorMessageConstants.HEAVENLY_STEMS_COUNT_INVALID.getMessage());
        }
        String dayMaster = heavenlyStems.get(SajuPillarIndex.DAY_INDEX);
        if (dayMaster == null || dayMaster.isBlank()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.INVALID_DAY_MASTER.getMessage());
        }
        return dayMaster;
    }

    private List<TenGodData> toTenGodDataList(SajuResult result, TenGodDistribution tenGodDistribution) {
        if (tenGodDistribution == null || tenGodDistribution.asMap().isEmpty()) {
            return new ArrayList<>();
        }
        return tenGodDistribution.asMap().entrySet().stream()
                .map(entry -> TenGodData.builder()
                        .sajuResult(result)
                        .tenGodName(entry.getKey())
                        .score(entry.getValue())
                        .build())
                .toList();
    }

    private List<HiddenStemData> toHiddenStemDataList(SajuResult result, HiddenStems hiddenStems) {
        if (hiddenStems == null || hiddenStems.asMap().isEmpty()) {
            return new ArrayList<>();
        }
        return hiddenStems.asMap().entrySet().stream()
                .flatMap(entry -> {
                    List<String> stems = entry.getValue();
                    if (stems == null || stems.isEmpty()) {
                        return java.util.stream.Stream.empty();
                    }
                    return stems.stream()
                            .map(stem -> HiddenStemData.builder()
                                    .sajuResult(result)
                                    .earthlyBranch(entry.getKey())
                                    .hiddenStem(stem)
                                    .build());
                })
                .toList();
    }
}
