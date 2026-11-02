package ssafy.SSAju.career.enums;

/**
 * 궁합 분석에 사용한 기업 설립일의 취득 경로.
 *
 * <p>요청값과 실제 계산에 사용한 값을 구분해 저장하기 위해 입력 스냅샷에 함께 보관한다 —
 * 같은 설립일이라도 사용자가 직접 입력한 것인지 공공데이터 API로 조회한 것인지에 따라
 * 결과 해석의 근거가 달라지며, 조회 시 회사를 다시 조회하지 않기 때문이다.
 */
public enum CompanyFoundingDateSource {

    /** 요청 DTO의 companyFoundingDate를 그대로 사용했다. */
    REQUEST("요청 입력"),

    /** 요청에 설립일이 없어 공공데이터 API로 조회했다. */
    PUBLIC_DATA_API("공공데이터 API 조회");

    private final String description;

    CompanyFoundingDateSource(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
