package ssafy.SSAju.career.domain;

import java.util.Collections;
import java.util.Map;

public class FiveElements {

    private final Map<String, Integer> elements;

    public FiveElements(Map<String, Integer> data) {
        this.elements = Collections.unmodifiableMap(data);
    }

    public Integer getCount(String element) {
        return elements.getOrDefault(element, 0);
    }

    public Map<String, Integer> asMap() {
        return elements;
    }
}
