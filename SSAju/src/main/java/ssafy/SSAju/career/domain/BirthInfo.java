package ssafy.SSAju.career.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 분석에 실제로 사용할 출생정보. 유형별 요청 DTO에서 정규화해 뽑아낸 값이다.
 *
 * <p>궁합의 출생 시각 누락처럼 유형마다 다른 정규화 규칙이 있어, 공통 실행 서비스가 유형별로
 * 분기하지 않도록 각 전략이 이 값을 만들어 돌려준다.
 */
public record BirthInfo(LocalDate birthDate, LocalTime birthTime) {
}
