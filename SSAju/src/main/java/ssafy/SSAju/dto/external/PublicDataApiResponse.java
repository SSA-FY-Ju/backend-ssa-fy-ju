package ssafy.SSAju.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 금융위원회 공공데이터 Open API 응답 매핑 DTO.
 * <p>
 * API: getCorpOutline_V2 (기업개요조회)
 * <p>
 * 응답 구조:
 * {@code response → header / body → items → item[]}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicDataApiResponse(
        @JsonProperty("response") Response response
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
            @JsonProperty("header") Header header,
            @JsonProperty("body") Body body
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(
            @JsonProperty("resultCode") String resultCode,
            @JsonProperty("resultMsg") String resultMsg
    ) {
        /** API 정상 응답 코드 */
        public static final String SUCCESS_CODE = "00";

        public boolean isSuccess() {
            return SUCCESS_CODE.equals(resultCode);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            @JsonProperty("numOfRows") int numOfRows,
            @JsonProperty("pageNo") int pageNo,
            @JsonProperty("totalCount") int totalCount,
            @JsonProperty("items") Items items
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(
            @JsonProperty("item") List<CompanyItem> item
    ) {}

    /**
     * 기업개요 단건 응답 아이템.
     * 필요한 필드만 선언하고 나머지는 {@code @JsonIgnoreProperties}로 무시.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompanyItem(
            @JsonProperty("crno") String crno,               // 법인등록번호
            @JsonProperty("corpNm") String corpNm,           // 법인명
            @JsonProperty("enpEstbDt") String enpEstbDt,     // 기업설립일자 (YYYYMMDD)
            @JsonProperty("enpBsadr") String enpBsadr,       // 기업기본주소
            @JsonProperty("enpTlno") String enpTlno          // 기업전화번호
    ) {}
}
