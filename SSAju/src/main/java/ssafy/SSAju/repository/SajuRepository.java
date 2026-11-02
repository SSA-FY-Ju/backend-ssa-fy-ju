package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.career.entity.Saju;
import ssafy.SSAju.career.enums.AnalysisType;

import java.util.Optional;

@Repository
public interface SajuRepository extends JpaRepository<Saju, Long> {

    /**
     * 분석 ID·소유자·유형이 모두 일치하는 결과만 돌려준다. 없는 ID·타인 ID·유형 불일치를
     * 호출자가 구분하지 않고 동일한 미존재(404)로 처리하기 위해 세 조건을 한 번에 건다.
     */
    Optional<Saju> findByIdAndUserIdAndAnalysisType(Long id, Long userId, AnalysisType analysisType);

    Optional<Saju> findByIdAndUserId(Long id, Long userId);
}
