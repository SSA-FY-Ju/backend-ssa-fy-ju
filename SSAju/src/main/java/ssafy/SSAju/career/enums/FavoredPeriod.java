package ssafy.SSAju.career.enums;

public enum FavoredPeriod {

    H1("상반기"),
    H2("하반기");

    private final String displayName;

    FavoredPeriod(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
