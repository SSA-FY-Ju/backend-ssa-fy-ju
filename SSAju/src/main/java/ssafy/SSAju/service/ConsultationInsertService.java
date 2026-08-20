package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.repository.CareerConsultationRepository;

/**
 * CareerConsultation 단건 삽입을 REQUIRES_NEW 트랜잭션으로 처리하는 컴포넌트.
 *
 * <p>{@code ConsultationSaveService.saveOrUpdate}(@Transactional)에서 직접 save()를 호출하면,
 * UNIQUE 제약 위반(DataIntegrityViolationException) 발생 시 호출자 트랜잭션이
 * rollback-only로 마킹됩니다. {@code @DistributedLock}이 정상적으로는 이 경합 자체를
 * 막아주지만, Redisson 락은 고정된 임대시간(leaseTime, 기본 5000ms)이 지나면 무조건
 * 풀리므로 — 이 메서드 실행이 그보다 오래 걸리는 극단적인 상황(DB 커넥션 풀 고갈 등)에서는
 * 완전히 배제할 수 없습니다. 삽입을 별도 빈의 REQUIRES_NEW 트랜잭션으로 분리해두면, 그런
 * 경합이 실제로 발생해도 호출자 트랜잭션을 오염시키지 않고 재조회로 안전하게 복구할 수
 * 있습니다.
 *
 * @see ConsultationSaveService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsultationInsertService {

    private final CareerConsultationRepository careerConsultationRepository;

    /**
     * CareerConsultation을 새로운 독립 트랜잭션에서 저장하고 생성된 ID를 반환합니다.
     *
     * <p>UNIQUE 제약 위반 시 이 트랜잭션만 롤백되므로,
     * 호출자(ConsultationSaveService)의 트랜잭션은 rollback-only 마킹 없이 유지됩니다.
     *
     * @param newConsultation 저장할 CareerConsultation
     * @return 저장된 엔티티의 생성된 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long insert(CareerConsultation newConsultation) {
        CareerConsultation saved = careerConsultationRepository.saveAndFlush(newConsultation);
        return saved.getId();
    }
}
