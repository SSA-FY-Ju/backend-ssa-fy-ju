package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.entity.CompanyCompatibility;
import ssafy.SSAju.repository.CompanyCompatibilityRepository;

/**
 * CompanyCompatibility 단건 삽입(결과 JSON 할당 + completed 표시 포함)을 REQUIRES_NEW
 * 트랜잭션으로 처리하는 컴포넌트.
 *
 * <p>{@code CompanyCompatibilitySaveService.saveWithLock}에서 직접 저장하면, UNIQUE 제약
 * 위반(DataIntegrityViolationException) 발생 시 호출자 트랜잭션이 rollback-only로
 * 마킹된다. {@code @DistributedLock}이 정상적으로는 이 경합 자체를 막아주지만, Redisson
 * 락은 고정된 임대시간(leaseTime, 기본 5000ms)이 지나면 워치독 갱신 없이 무조건 풀리므로
 * — 완전히 배제할 수는 없다. 삽입을 별도 빈의 REQUIRES_NEW 트랜잭션으로 분리해두면, 그런
 * 경합이 실제로 발생해도 호출자를 오염시키지 않고 재조회로 안전하게 복구할 수 있다.
 *
 * @see CompanyCompatibilitySaveService
 */
@Component
@RequiredArgsConstructor
public class CompanyCompatibilityInsertService {

    private final CompanyCompatibilityRepository companyCompatibilityRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompanyCompatibility insert(CompanyCompatibility entity, CompatibilityAnalysisData analysisData) {
        entity.assignResultJsonAndMarkCompleted(analysisData);
        return companyCompatibilityRepository.saveAndFlush(entity);
    }
}
