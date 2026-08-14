package ssafy.SSAju.career.util;

import org.springframework.stereotype.Component;

/**
 * 직군별 역할 적합도 점수 계산을 담당합니다.
 *
 * <p>주 역할(전문가) 점수는 {@link JobRoleAnalyzer}가 이미 계산해 상한(100)까지 적용한
 * matchScore를 호출부에서 그대로 재사용한다(별도 산식 없음, 산식 통합) — 이 클래스는
 * 보조 역할(리드) 점수 산정만 담당한다.
 */
@Component
public class RoleCompatibilityCalculator {

    /**
     * 보조 역할(리드) 점수 = 주 역할 점수에서 페널티 차감.
     */
    public int calculateSecondary(int primaryScore) {
        return Math.max(primaryScore - AnalysisConstants.SECONDARY_ROLE_SCORE_PENALTY, AnalysisConstants.MIN_SCORE);
    }
}
