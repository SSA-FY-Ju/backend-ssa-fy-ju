package ssafy.SSAju.career.domain;

import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.career.enums.FiveElement;
import ssafy.SSAju.career.enums.SajuPillarIndex;
import ssafy.SSAju.dto.external.FastAPIResponse;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 출생정보 하나에 대한 만세력 계산 데이터 전체. {@code manse.data_json} 한 컬럼에 이 record가
 * 통째로 직렬화된다.
 *
 * <p>개별 계산 필드를 조건 검색·집계하지 않고 항상 프로필로 찾아 전체를 읽으므로 컬럼이나 JSON을
 * 나누지 않는다. 요청별 최종 해석(관운 tuple·AI 문구)은 이 record에 담지 않고 분석 결과 스냅샷에만
 * 저장한다.
 *
 * <p>필수값·배열 길이·허용값 검증은 생성 시점과 JSON 역직렬화 시점 양쪽에서 수행된다 — Jackson도
 * 이 canonical 생성자를 통해 값을 만들기 때문에, 손상된 JSON은 조용히 빈 값으로 통과하지 않고
 * {@link InvalidSajuDataException}으로 실패한다.
 */
public record ManseData(
        int schemaVersion,
        String yearPillar,
        String monthPillar,
        String dayPillar,
        String hourPillar,
        List<String> heavenlyStems,
        List<String> earthlyBranches,
        String dayMaster,
        String dayMasterElement,
        Map<String, Integer> fiveElements,
        Map<String, Object> solarCorrection,
        TenGodHiddenStemAnalysis tenGodHiddenStemAnalysis
) {

    /** 현재 지원하는 만세력 JSON 스키마 버전. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** 천간·지지는 각각 年月日時 4개다. */
    private static final int PILLAR_COUNT = 4;

    public ManseData {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new InvalidSajuDataException(ErrorMessageConstants.MANSE_SCHEMA_VERSION_UNSUPPORTED.getMessage());
        }
        requirePillar(yearPillar);
        requirePillar(monthPillar);
        requirePillar(dayPillar);
        requirePillar(hourPillar);
        requireFourValues(heavenlyStems, ErrorMessageConstants.HEAVENLY_STEMS_COUNT_INVALID);
        requireFourValues(earthlyBranches, ErrorMessageConstants.EARTHLY_BRANCHES_COUNT_INVALID);
        if (dayMaster == null || dayMaster.isBlank()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.INVALID_DAY_MASTER.getMessage());
        }
        if (dayMasterElement == null || dayMasterElement.isBlank()
                || !FiveElement.allSymbols().contains(dayMasterElement)) {
            throw new InvalidSajuDataException(ErrorMessageConstants.INVALID_HEAVENLY_STEM.getMessage());
        }
        if (fiveElements == null || fiveElements.isEmpty()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.FIVE_ELEMENTS_REQUIRED.getMessage());
        }
        if (tenGodHiddenStemAnalysis == null
                || tenGodHiddenStemAnalysis.tenGods() == null
                || tenGodHiddenStemAnalysis.hiddenStems() == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.MANSE_TEN_GOD_ANALYSIS_REQUIRED.getMessage());
        }

        heavenlyStems = List.copyOf(heavenlyStems);
        earthlyBranches = List.copyOf(earthlyBranches);
        fiveElements = Collections.unmodifiableMap(new LinkedHashMap<>(fiveElements));
        // solarCorrection은 FastAPI가 주지 않을 수 있는 선택 정보 — null 의미를 그대로 유지한다.
        solarCorrection = solarCorrection == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(solarCorrection));
    }

    /**
     * FastAPI 응답과 Spring이 계산한 십성·지장간을 하나의 만세력 데이터로 결합한다.
     * 일간의 오행은 {@link FiveElement#fromStem(String)}으로 derive하며 알 수 없는 천간은 거부된다.
     */
    public static ManseData of(FastAPIResponse sajuData, TenGodHiddenStemAnalysis analysis) {
        if (sajuData == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.SAJU_DATA_NULL.getMessage());
        }
        requireFourValues(sajuData.heavenlyStems(), ErrorMessageConstants.HEAVENLY_STEMS_COUNT_INVALID);
        String dayMaster = sajuData.heavenlyStems().get(SajuPillarIndex.DAY_INDEX);
        if (dayMaster == null || dayMaster.isBlank()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.INVALID_DAY_MASTER.getMessage());
        }
        return new ManseData(
                CURRENT_SCHEMA_VERSION,
                sajuData.yearPillar(),
                sajuData.monthPillar(),
                sajuData.dayPillar(),
                sajuData.hourPillar(),
                sajuData.heavenlyStems(),
                sajuData.earthlyBranches(),
                dayMaster,
                FiveElement.fromStem(dayMaster).getSymbol(),
                sajuData.fiveElements(),
                sajuData.solarCorrection(),
                analysis);
    }

    /**
     * 저장된 만세력을 기존 계산기·caller가 받는 {@link FastAPIResponse} 형태로 되돌린다.
     * 출생정보는 만세력이 보관하지 않으므로 호출자가 연결된 프로필 값을 전달한다.
     */
    public FastAPIResponse toFastApiResponse(String birthDate, String birthTime) {
        return new FastAPIResponse(
                heavenlyStems, earthlyBranches, fiveElements,
                yearPillar, monthPillar, dayPillar, hourPillar,
                birthTime, birthDate, solarCorrection);
    }

    public TenGodDistribution tenGodDistribution() {
        return new TenGodDistribution(tenGodHiddenStemAnalysis.tenGods());
    }

    public HiddenStems hiddenStems() {
        return new HiddenStems(tenGodHiddenStemAnalysis.hiddenStems());
    }

    private static void requirePillar(String pillar) {
        if (pillar == null || pillar.isBlank()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.MANSE_PILLAR_REQUIRED.getMessage());
        }
    }

    private static void requireFourValues(List<String> values, ErrorMessageConstants error) {
        if (values == null || values.size() != PILLAR_COUNT) {
            throw new InvalidSajuDataException(error.getMessage());
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new InvalidSajuDataException(error.getMessage());
            }
        }
    }
}
