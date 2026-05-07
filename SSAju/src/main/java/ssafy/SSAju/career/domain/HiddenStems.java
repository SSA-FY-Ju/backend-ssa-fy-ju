package ssafy.SSAju.career.domain;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HiddenStems {

    private final Map<String, List<String>> stems;

    public HiddenStems(Map<String, List<String>> data) {
        this.stems = Collections.unmodifiableMap(data);
    }

    public List<String> getByBranch(String branch) {
        return stems.getOrDefault(branch, List.of());
    }

    public boolean containsStem(String stem) {
        return stems.values().stream().anyMatch(list -> list.contains(stem));
    }

    public Map<String, List<String>> asMap() {
        return stems;
    }

    @Override
    public String toString() {
        return stems.toString();
    }
}
