package ssafy.SSAju.career.util;

import java.util.List;

public enum JobCategoryEnum {
    TECH_BACKEND ("백엔드 개발",    "金", "水", List.of("체계적 설계", "논리적 사고", "안정적 실행")),
    TECH_FRONTEND("프론트엔드 개발","火", "木", List.of("창의적 사고", "사용자 중심", "시각적 구현")),
    TECH_MOBILE  ("모바일 개발",    "火", "金", List.of("크로스 플랫폼", "UX 최적화", "성능 개선")),
    TECH_DATA    ("데이터/AI",     "水", "金", List.of("데이터 분석", "패턴 인식", "알고리즘 설계")),
    TECH_INFRA   ("인프라/DevOps", "土", "金", List.of("시스템 안정성", "자동화", "장애 대응")),
    FINANCE      ("금융/회계",     "金", "土", List.of("수치 분석", "리스크 관리", "정확성")),
    MARKETING    ("마케팅",        "火", "木", List.of("트렌드 파악", "창의적 기획", "고객 이해")),
    HR           ("인사/조직",     "土", "水", List.of("공감 능력", "조직 문화", "갈등 해결")),
    OPERATIONS   ("운영/기획",     "土", "金", List.of("프로세스 최적화", "문제 해결", "효율성")),
    SALES        ("영업",          "火", "木", List.of("커뮤니케이션", "목표 달성", "관계 구축")),
    STRATEGY     ("전략/경영",     "水", "木", List.of("거시적 사고", "의사 결정", "시장 분석")),
    RESEARCH     ("연구개발",      "水", "木", List.of("깊이 있는 분석", "창의적 연구", "논리적 사고"));

    private final String displayName;
    private final String primaryElement;
    private final String secondaryElement;
    private final List<String> keywords;

    JobCategoryEnum(String displayName, String primaryElement, String secondaryElement, List<String> keywords) {
        this.displayName = displayName;
        this.primaryElement = primaryElement;
        this.secondaryElement = secondaryElement;
        this.keywords = keywords;
    }

    public String getDisplayName()      { return displayName; }
    public String getPrimaryElement()   { return primaryElement; }
    public String getSecondaryElement() { return secondaryElement; }
    public List<String> getKeywords()   { return keywords; }
}
