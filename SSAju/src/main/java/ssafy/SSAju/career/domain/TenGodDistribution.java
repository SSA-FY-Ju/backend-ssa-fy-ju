package ssafy.SSAju.career.domain;

import java.util.Collections;
import java.util.Map;

/**
 * 십신(十神) 분포 데이터.
 *
 * <p><strong>tenGodDistribution JSON 저장 형식</strong>:
 * <pre>{@code
 * {
 *   "正官": 1,
 *   "偏官": 1,
 *   "正財": 2,
 *   "偏財": 1
 * }
 * }</pre>
 * 각 십신(정관·편관·정재·편재 등)의 빈도 수를 나타냅니다.
 * 4개 천간(年月時) 기준으로 계산되므로 합계는 3 이하입니다.
 *
 * <p><strong>keyTenGods 형식</strong>:
 * <pre>{@code
 * ["正官", "正財"]
 * }</pre>
 * 사용자 사주에서 영향력이 큰 상위 십신을 순서대로 나열합니다.
 * AI 커리어 상담 프롬프트의 핵심 입력값으로 사용됩니다.
 */
public class TenGodDistribution {

    private final Map<String, Integer> distribution;

    public TenGodDistribution(Map<String, Integer> data) {
        this.distribution = Collections.unmodifiableMap(data);
    }

    public Integer getScore(String tenGodName) {
        return distribution.getOrDefault(tenGodName, 0);
    }

    public boolean hasHighConfidence(int threshold) {
        return distribution.values().stream().mapToInt(Integer::intValue).sum() >= threshold;
    }

    public Map<String, Integer> asMap() {
        return distribution;
    }

    @Override
    public String toString() {
        return distribution.toString();
    }
}
