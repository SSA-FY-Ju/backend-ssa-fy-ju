package ssafy.SSAju.career.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.career.entity.*;
import ssafy.SSAju.dto.response.ConsultationResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("ConsultationMapper.toResponseFromEntity() 단위 테스트")
class ConsultationMapperTest {

    private final ConsultationMapper mapper = new ConsultationMapper();

    @Test
    @DisplayName("CareerConsultation 엔티티 → ConsultationResponse 정상 복원")
    void shouldRestoreConsultationResponse_FromEntity() {
        // Given
        var sajuResult = mock(SajuResult.class);
        var sfd = mock(SajuFullData.class);
        var cf = mock(CareerFortune.class);
        var cc = mock(CareerConsultation.class);

        var tenGodData = TenGodData.builder()
                .tenGodName("정관")
                .score(2)
                .build();

        given(sajuResult.getSajuFullData()).willReturn(sfd);
        given(sajuResult.getCareerFortune()).willReturn(cf);
        given(sajuResult.getTenGodDataList()).willReturn(List.of(tenGodData));

        given(sfd.getDayMaster()).willReturn("己");
        given(sfd.getFiveElements()).willReturn(Map.of("木", 1, "火", 2, "土", 2, "金", 2, "水", 1));

        given(cf.getFavoredPeriod()).willReturn("H1");
        given(cf.getConfidenceScore()).willReturn(80);
        given(cf.getReasoning()).willReturn("상반기 유리");

        given(cc.getId()).willReturn(42L);
        given(cc.getSajuResult()).willReturn(sajuResult);
        given(cc.getOpenaiModelVersion()).willReturn("gpt-4o-mini");
        given(cc.getDayMasterDescription()).willReturn("己土 - 수용적 성향");
        given(cc.getFiveElementsAnalysis()).willReturn("金 강세");

        given(cc.getIndustries()).willReturn(List.of());
        given(cc.getInterviewTips()).willReturn(List.of());
        given(cc.getStrengths()).willReturn(List.of());
        given(cc.getCautions()).willReturn(List.of());
        given(cc.getKeyTenGods()).willReturn(List.of());
        given(cc.getWealthStyle()).willReturn(null);
        given(cc.getRoadmap()).willReturn(null);
        given(cc.getPersonalBranding()).willReturn(null);
        given(cc.getPowerKeywords()).willReturn(null);
        given(cc.getMentalCare()).willReturn(null);
        given(cc.getEnvironmentFit()).willReturn(null);
        given(cc.getWorkStyle()).willReturn(null);
        given(cc.getRelationshipStrategy()).willReturn(null);
        given(cc.getCareerTimeline()).willReturn(null);

        // When
        ConsultationResponse result = mapper.toResponseFromEntity(cc);

        // Then
        assertThat(result.consultationId()).isEqualTo(42L);
        assertThat(result.openaiModelVersion()).isEqualTo("gpt-4o-mini");
        assertThat(result.favoredPeriod()).isEqualTo("H1");
        assertThat(result.confidenceScore()).isEqualTo(80);
        assertThat(result.reasoning()).isEqualTo("상반기 유리");
        assertThat(result.sajuProfile()).isNotNull();
        assertThat(result.sajuProfile().dayMaster()).isEqualTo("己");
        assertThat(result.sajuProfile().fiveElements()).containsKey("木");
        assertThat(result.sajuProfile().tenGodDistribution()).containsKey("정관");
        assertThat(result.sajuProfile().tenGodDistribution().get("정관")).isEqualTo(2);
    }

    @Test
    @DisplayName("SajuFullData가 null인 경우 → dayMaster 빈 문자열, fiveElements 빈 맵으로 안전하게 처리")
    void shouldHandleNullSajuFullData_Safely() {
        var sajuResult = mock(SajuResult.class);
        var cf = mock(CareerFortune.class);
        var cc = mock(CareerConsultation.class);

        given(sajuResult.getSajuFullData()).willReturn(null);
        given(sajuResult.getCareerFortune()).willReturn(cf);
        given(sajuResult.getTenGodDataList()).willReturn(List.of());

        given(cf.getFavoredPeriod()).willReturn("H2");
        given(cf.getConfidenceScore()).willReturn(60);
        given(cf.getReasoning()).willReturn("하반기 유리");

        given(cc.getId()).willReturn(1L);
        given(cc.getSajuResult()).willReturn(sajuResult);
        given(cc.getOpenaiModelVersion()).willReturn("gpt-4o");
        given(cc.getDayMasterDescription()).willReturn("");
        given(cc.getFiveElementsAnalysis()).willReturn("");

        given(cc.getIndustries()).willReturn(List.of());
        given(cc.getInterviewTips()).willReturn(List.of());
        given(cc.getStrengths()).willReturn(List.of());
        given(cc.getCautions()).willReturn(List.of());
        given(cc.getKeyTenGods()).willReturn(List.of());
        given(cc.getWealthStyle()).willReturn(null);
        given(cc.getRoadmap()).willReturn(null);
        given(cc.getPersonalBranding()).willReturn(null);
        given(cc.getPowerKeywords()).willReturn(null);
        given(cc.getMentalCare()).willReturn(null);
        given(cc.getEnvironmentFit()).willReturn(null);
        given(cc.getWorkStyle()).willReturn(null);
        given(cc.getRelationshipStrategy()).willReturn(null);
        given(cc.getCareerTimeline()).willReturn(null);

        ConsultationResponse result = mapper.toResponseFromEntity(cc);

        assertThat(result.sajuProfile().dayMaster()).isEmpty();
        assertThat(result.sajuProfile().fiveElements()).isEmpty();
        assertThat(result.favoredPeriod()).isEqualTo("H2");
    }

    @Test
    @DisplayName("CareerFortune이 null인 경우 → favoredPeriod null, confidenceScore 0으로 안전하게 처리")
    void shouldHandleNullCareerFortune_Safely() {
        var sajuResult = mock(SajuResult.class);
        var sfd = mock(SajuFullData.class);
        var cc = mock(CareerConsultation.class);

        given(sajuResult.getSajuFullData()).willReturn(sfd);
        given(sajuResult.getCareerFortune()).willReturn(null);
        given(sajuResult.getTenGodDataList()).willReturn(List.of());

        given(sfd.getDayMaster()).willReturn("甲");
        given(sfd.getFiveElements()).willReturn(Map.of("木", 3));

        given(cc.getId()).willReturn(2L);
        given(cc.getSajuResult()).willReturn(sajuResult);
        given(cc.getOpenaiModelVersion()).willReturn("gpt-4o-mini");
        given(cc.getDayMasterDescription()).willReturn("甲木");
        given(cc.getFiveElementsAnalysis()).willReturn("木 강세");

        given(cc.getIndustries()).willReturn(List.of());
        given(cc.getInterviewTips()).willReturn(List.of());
        given(cc.getStrengths()).willReturn(List.of());
        given(cc.getCautions()).willReturn(List.of());
        given(cc.getKeyTenGods()).willReturn(List.of());
        given(cc.getWealthStyle()).willReturn(null);
        given(cc.getRoadmap()).willReturn(null);
        given(cc.getPersonalBranding()).willReturn(null);
        given(cc.getPowerKeywords()).willReturn(null);
        given(cc.getMentalCare()).willReturn(null);
        given(cc.getEnvironmentFit()).willReturn(null);
        given(cc.getWorkStyle()).willReturn(null);
        given(cc.getRelationshipStrategy()).willReturn(null);
        given(cc.getCareerTimeline()).willReturn(null);

        ConsultationResponse result = mapper.toResponseFromEntity(cc);

        assertThat(result.favoredPeriod()).isNull();
        assertThat(result.confidenceScore()).isEqualTo(0);
        assertThat(result.reasoning()).isNull();
        // analysisSummary에 "(null)" 문자열이 노출되지 않아야 함
        assertThat(result.analysisSummary()).doesNotContain("(null)").contains("분석 미포함");
    }
}
