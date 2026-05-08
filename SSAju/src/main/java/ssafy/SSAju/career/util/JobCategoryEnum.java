package ssafy.SSAju.career.util;

public enum JobCategoryEnum {
    TECH_BACKEND("백엔드 개발", "金", "水"),
    TECH_FRONTEND("프론트엔드 개발", "火", "木"),
    TECH_MOBILE("모바일 개발", "火", "金"),
    TECH_DATA("데이터/AI", "水", "金"),
    TECH_INFRA("인프라/DevOps", "土", "金"),
    FINANCE("금융/회계", "金", "土"),
    MARKETING("마케팅", "火", "木"),
    HR("인사/조직", "土", "水"),
    OPERATIONS("운영/기획", "土", "金"),
    SALES("영업", "火", "木"),
    STRATEGY("전략/경영", "水", "木"),
    RESEARCH("연구개발", "水", "木");

    private final String displayName;
    private final String primaryElement;
    private final String secondaryElement;

    JobCategoryEnum(String displayName, String primaryElement, String secondaryElement) {
        this.displayName = displayName;
        this.primaryElement = primaryElement;
        this.secondaryElement = secondaryElement;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPrimaryElement() {
        return primaryElement;
    }

    public String getSecondaryElement() {
        return secondaryElement;
    }
}
