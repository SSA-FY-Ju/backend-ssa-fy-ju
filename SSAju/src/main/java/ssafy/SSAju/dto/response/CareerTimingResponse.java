package ssafy.SSAju.dto.response;

public record CareerTimingResponse(
        Long sajuResultId,
        String favoredPeriod,  // "H1" (상반기) or "H2" (하반기)
        int confidenceScore,   // 0-100
        String reasoning
) {
}
