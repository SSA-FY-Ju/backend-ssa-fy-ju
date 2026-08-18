package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;

/**
 * 기업 궁합 분석 결과 저장 및 완료 상태 관리를 담당합니다.
 *
 * <p><strong>@Transactional(REQUIRES_NEW) 적용 이유</strong>:
 * <ul>
 *   <li>{@code CompanyMatchingService}는 @Transactional 없음(외부 I/O 중 커넥션 점유 방지) →
 *       호출자 트랜잭션이 없으므로 REQUIRES_NEW와 사실상 동일하게 동작하지만,
 *       명시적으로 선언하여 향후 @Transactional 추가 시에도 독립 트랜잭션을 보장합니다.</li>
 *   <li>같은 클래스 내 self-invocation에서는 Spring AOP 프록시가 작동하지 않으므로
 *       반드시 별도 빈(Bean)으로 분리해야 @Transactional이 적용됩니다.</li>
 * </ul>
 *
 * <p><strong>completed 플래그 (Option A)</strong>:
 * {@code resultJson} 저장과 {@code completed = true} 전환을 같은 트랜잭션에서 수행하여
 * {@link CompatibilityChildReadService#buildFromExisting}의 캐시 재사용을 허용합니다.
 * 저장 중 예외 발생 시 트랜잭션 롤백으로 JSON + completed 업데이트가 모두 취소됩니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompatibilityChildSaveService {

    private final CompanyCompatibilityRepository compatibilityRepository;

    /**
     * 분석 결과를 JSON으로 저장하고 완료 플래그를 업데이트합니다.
     *
     * @param saved        저장된 root 엔티티
     * @param analysisData 분석 결과 내부 VO
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAllAndMarkCompleted(CompanyCompatibility saved,
                                        CompatibilityAnalysisData analysisData) {
        saved.assignResultJsonAndMarkCompleted(analysisData);
        compatibilityRepository.save(saved);
        log.info("궁합 분석 결과 JSON 저장 완료 및 completed 플래그 업데이트 (compatibilityId={})", saved.getId());
    }
}
