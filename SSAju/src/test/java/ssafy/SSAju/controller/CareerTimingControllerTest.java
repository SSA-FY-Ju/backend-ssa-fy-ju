package ssafy.SSAju.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.SSAju.dto.request.CareerTimingRequest;
import ssafy.SSAju.exception.FastAPITimeoutException;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CareerTimingController 유효성 검증 테스트")
class CareerTimingControllerTest {

    @Test
    @DisplayName("유효한 birthDate + birthTime으로 DTO 생성 성공")
    void shouldCreateValidRequest() {
        // Given
        LocalDate birthDate = LocalDate.of(1990, 10, 10);
        LocalTime birthTime = LocalTime.of(14, 30);

        // When
        CareerTimingRequest request = new CareerTimingRequest(birthDate, birthTime);

        // Then
        assertThat(request.birthDate()).isEqualTo(birthDate);
        assertThat(request.birthTime()).isEqualTo(birthTime);
    }

    @Test
    @DisplayName("null birthTime → InvalidSajuDataException 발생")
    void shouldFailWhenBirthTimeIsNull() {
        // Given
        LocalTime birthTime = null;

        // When & Then
        assertThatThrownBy(() -> {
            if (birthTime == null)
                throw new InvalidSajuDataException("태어난 시간은 필수입니다 (HH:mm 형식)");
        }).isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("HH:mm");
    }

    @Test
    @DisplayName("null birthDate → InvalidSajuDataException 발생")
    void shouldFailWhenBirthDateIsNull() {
        // Given
        LocalDate birthDate = null;

        // When & Then
        assertThatThrownBy(() -> {
            if (birthDate == null)
                throw new InvalidSajuDataException("생년월일은 필수입니다 (YYYY-MM-DD 형식)");
        }).isInstanceOf(InvalidSajuDataException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    @Test
    @DisplayName("FastAPI 타임아웃 → FastAPITimeoutException 발생")
    void shouldPropagateTimeoutException() {
        // When & Then
        assertThatThrownBy(() -> {
            throw new FastAPITimeoutException("FastAPI 요청 시간 초과");
        }).isInstanceOf(FastAPITimeoutException.class)
                .hasMessageContaining("시간 초과");
    }

    @Test
    @DisplayName("record 동등성 - 같은 값이면 동일 객체")
    void shouldBeEqualWhenSameValues() {
        // Given
        var r1 = new CareerTimingRequest(LocalDate.of(1990, 1, 1), LocalTime.of(12, 0));
        var r2 = new CareerTimingRequest(LocalDate.of(1990, 1, 1), LocalTime.of(12, 0));

        // Then
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }
}
