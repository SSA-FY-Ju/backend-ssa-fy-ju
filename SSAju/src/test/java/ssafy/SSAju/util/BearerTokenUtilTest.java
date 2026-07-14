package ssafy.SSAju.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BearerTokenUtil 단위 테스트")
class BearerTokenUtilTest {

    @Test
    @DisplayName("Bearer 토큰이 정상 형식이면 토큰값을 반환한다")
    void extractBearerToken_validHeader_returnsToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc123");

        assertThat(BearerTokenUtil.extractBearerToken(request)).isEqualTo("abc123");
    }

    @Test
    @DisplayName("소문자 bearer 스킴도 대소문자 구분 없이 인식한다")
    void extractBearerToken_lowercaseScheme_returnsToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "bearer abc123");

        assertThat(BearerTokenUtil.extractBearerToken(request)).isEqualTo("abc123");
    }

    @Test
    @DisplayName("스킴 뒤에 토큰이 없으면(공백뿐이면) null을 반환한다")
    void extractBearerToken_schemeOnlyNoToken_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ");

        assertThat(BearerTokenUtil.extractBearerToken(request)).isNull();
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 null을 반환한다")
    void extractBearerToken_noHeader_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(BearerTokenUtil.extractBearerToken(request)).isNull();
    }

    @Test
    @DisplayName("Bearer 스킴이 아니면 null을 반환한다")
    void extractBearerToken_notBearerScheme_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        assertThat(BearerTokenUtil.extractBearerToken(request)).isNull();
    }

    @Test
    @DisplayName("헤더 값 앞뒤 공백과 토큰 앞뒤 공백을 모두 제거한다")
    void extractBearerToken_withSurroundingWhitespace_trimsToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "  Bearer   abc123  ");

        assertThat(BearerTokenUtil.extractBearerToken(request)).isEqualTo("abc123");
    }
}
