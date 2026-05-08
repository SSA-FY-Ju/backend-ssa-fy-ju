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
import ssafy.SSAju.career.enums.SajuPillarIndex;
import ssafy.SSAju.dto.external.FastAPIResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SajuResultMapper {

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
        String dayMasterElement = STEM_ELEMENT_MAP.getOrDefault(dayMaster, "");
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
        if (heavenlyStems == null || heavenlyStems.size() <= SajuPillarIndex.DAY_INDEX) return "";
        return heavenlyStems.get(SajuPillarIndex.DAY_INDEX);
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
