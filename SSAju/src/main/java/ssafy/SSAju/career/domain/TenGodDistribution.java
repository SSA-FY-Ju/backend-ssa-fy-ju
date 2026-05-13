package ssafy.SSAju.career.domain;

import java.util.Collections;
import java.util.Map;

/**
 * 십신(十神) 분포 데이터.
 *
 * <p>4개 천간(年月日時) 중 일간(日)을 제외한 나머지 3개(年月時)를 기준으로 계산합니다.
 * 따라서 분포 값의 합계는 항상 3 이하입니다.
 *
 * <p><strong>API 응답 예시</strong> ({@code ConsultationResponse.SajuProfile}):
 * <pre>{@code
 * "tenGodDistribution": {
 *   "편관": 1,
 *   "정관": 1,
 *   "비견": 1
 * },
 * "keyTenGods": [
 *   "편관",
 *   "정관",
 *   "비견"
 * ]
 * }</pre>
 *
 * <ul>
 *   <li><strong>tenGodDistribution</strong>: 십신별 출현 횟수. 같은 십신이 여러 기둥에 나타나면 합산됩니다.</li>
 *   <li><strong>keyTenGods</strong>: 출현 횟수가 많은 순서로 정렬된 상위 십신 목록.
 *       AI 커리어 상담 프롬프트의 핵심 입력값으로 사용됩니다.</li>
 * </ul>
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
