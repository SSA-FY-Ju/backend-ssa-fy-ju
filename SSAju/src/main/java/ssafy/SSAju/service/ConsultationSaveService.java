package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.mapper.ConsultationMapper;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.repository.CareerConsultationRepository;

/**
 * C-7: CareerConsultation 저장을 별도 서비스로 분리하여 @Transactional 보장.
 *
 * ConsultationService(외부 I/O 포함)와 달리 이 서비스는 순수하게 DB 저장만 담당.
 * 외부 API 호출이 없으므로 트랜잭션을 안전하게 적용할 수 있음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationSaveService {

    private static final String CONSULTATION_MONTH_UNIQUE_CONSTRAINT = "uq_career_consultation_result_month";

    private final ConsultationMapper consultationMapper;
    private final CareerConsultationRepository careerConsultationRepository;
    private final ConsultationUpdateService consultationUpdateService;

    /**
     * CareerConsultation을 트랜잭션 내에서 저장.
     *
     * <ul>
     *   <li>동일 달 데이터가 없으면 신규 저장</li>
     *   <li>UNIQUE 제약 위반 시: Tainted Session 방지를 위해 새 트랜잭션(REQUIRES_NEW)에서
     *       모델 버전을 비교 후 다르면 업데이트, 같으면 건너뜀</li>
     * </ul>
     *
     * @param sajuResult        저장 대상 SajuResult
     * @param advice            OpenAI 응답
     * @param modelVersion      사용 모델 버전
     * @param consultationMonth 대상 월 (yyyy-MM)
     */
    @Transactional
    public void save(SajuResult sajuResult, CareerAdviceResponse advice,
                     String modelVersion, String consultationMonth) {
        CareerConsultation consultation = consultationMapper.buildConsultation(
                sajuResult, advice, modelVersion, consultationMonth);
        try {
            careerConsultationRepository.save(consultation);
            log.info("CareerConsultation 저장 완료: sajuResultId={}, month={}", sajuResult.getId(), consultationMonth);
        } catch (DataIntegrityViolationException e) {
            if (!isConsultationMonthConstraintViolation(e)) {
                throw e;
            }
            // UNIQUE 제약 위반: Tainted Session 방지를 위해 새 트랜잭션에서 업데이트 처리
            consultationUpdateService.updateIfModelChanged(sajuResult, advice, modelVersion, consultationMonth);
        }
    }

    private boolean isConsultationMonthConstraintViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException cve
                && CONSULTATION_MONTH_UNIQUE_CONSTRAINT.equals(cve.getConstraintName());
    }
}
