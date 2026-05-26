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
 * <p>ConsultationSaveService(@Transactional)에서 직접 saveAndFlush()를 호출하면,
 * UNIQUE 제약 위반(DataIntegrityViolationException) 발생 시 호출자 트랜잭션이
 * rollback-only로 마킹됩니다. 이후 catch block에서 재조회·업데이트를 시도해도
 * 최종 커밋 시 UnexpectedRollbackException이 발생합니다.
 *
 * <p>이 클래스를 별도 Spring bean으로 분리하고 REQUIRES_NEW 전파 수준을 적용하면,
 * 삽입 실패 시 독립 트랜잭션만 롤백되고 호출자 트랜잭션은 오염되지 않습니다.
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
     * @param newConsultation 저장할 CareerConsultation (자식 엔티티 포함, CascadeType.ALL 적용)
     * @return 저장된 엔티티의 생성된 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long insert(CareerConsultation newConsultation) {
        CareerConsultation saved = careerConsultationRepository.saveAndFlush(newConsultation);
        return saved.getId();
    }
}
