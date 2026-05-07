package ssafy.SSAju.career.domain;

import java.util.Collections;
import java.util.Map;

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
