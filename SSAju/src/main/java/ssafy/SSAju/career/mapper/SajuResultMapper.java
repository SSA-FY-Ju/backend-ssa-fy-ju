package ssafy.SSAju.career.mapper;

import org.springframework.stereotype.Component;
import ssafy.SSAju.career.entity.CareerFortune;
import ssafy.SSAju.career.entity.HiddenStemData;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.entity.TenGodData;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.dto.external.FastAPIResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SajuResultMapper {

    public SajuResult buildSajuResult(UserProfile userProfile,
                                       FastAPIResponse sajuData,
                                       Map<String, Integer> tenGodDistribution,
                                       Map<String, List<String>> hiddenStems,
                                       String favoredPeriod,
                                       int confidenceScore,
                                       String reasoning) {
        SajuResult result = SajuResult.builder()
                .userProfile(userProfile)
                .fullSajuData(toFullSajuDataMap(sajuData))
                .build();

        List<TenGodData> tenGodEntities = toTenGodDataList(result, tenGodDistribution);
        List<HiddenStemData> hiddenStemEntities = toHiddenStemDataList(result, hiddenStems);
        CareerFortune careerFortune = CareerFortune.builder()
                .sajuResult(result)
                .favoredPeriod(favoredPeriod)
                .confidenceScore(confidenceScore)
                .reasoning(reasoning)
                .build();

        result.assignTenGodData(tenGodEntities);
        result.assignHiddenStemData(hiddenStemEntities);
        result.assignCareerFortune(careerFortune);
        return result;
    }

    public SajuResult buildSajuResultWithoutFortune(UserProfile userProfile,
                                                     FastAPIResponse sajuData,
                                                     Map<String, Integer> tenGodDistribution,
                                                     Map<String, List<String>> hiddenStems) {
        SajuResult result = SajuResult.builder()
                .userProfile(userProfile)
                .fullSajuData(toFullSajuDataMap(sajuData))
                .build();

        result.assignTenGodData(toTenGodDataList(result, tenGodDistribution));
        result.assignHiddenStemData(toHiddenStemDataList(result, hiddenStems));
        return result;
    }

    public Map<String, Object> toFullSajuDataMap(FastAPIResponse r) {
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
        map.put("solarCorrection", r.solarCorrection());
        return map;
    }

    private List<TenGodData> toTenGodDataList(SajuResult result, Map<String, Integer> tenGodDistribution) {
        return tenGodDistribution.entrySet().stream()
                .map(entry -> TenGodData.builder()
                        .sajuResult(result)
                        .tenGodName(entry.getKey())
                        .score(entry.getValue())
                        .build())
                .toList();
    }

    private List<HiddenStemData> toHiddenStemDataList(SajuResult result, Map<String, List<String>> hiddenStems) {
        return hiddenStems.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(stem -> HiddenStemData.builder()
                                .sajuResult(result)
                                .earthlyBranch(entry.getKey())
                                .hiddenStem(stem)
                                .build()))
                .toList();
    }
}
