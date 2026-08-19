package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.annotation.DistributedLock;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.mapper.ConsultationMapper;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.repository.CareerConsultationRepository;

import java.util.Optional;

/**
 * C-7: CareerConsultation 저장을 별도 서비스로 분리하여 @Transactional 보장.
 *
 * ConsultationService(외부 I/O 포함)와 달리 이 서비스는 순수하게 DB 저장만 담당.
 * 외부 API 호출이 없으므로 트랜잭션을 안전하게 적용할 수 있음.
 *
 * <p>(정본, 월) 단위 분산락({@code @DistributedLock}, US5, T036)이 동시 삽입 경합 자체를
 * 막으므로, 제약조건 이름을 직접 검사해 재조회하던 복구 로직과 REQUIRES_NEW로 분리했던
 * {@code ConsultationInsertService}는 더 이상 필요하지 않다(T037).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationSaveService {

    private final ConsultationMapper consultationMapper;
    private final CareerConsultationRepository careerConsultationRepository;

    /**
     * CareerConsultation을 저장하거나 기존 데이터를 업데이트.
     *
     * <ol>
     *   <li>해당 달의 데이터를 먼저 조회</li>
     *   <li>존재하면: 모델 버전이 다를 때만 업데이트, 같으면 건너뜀</li>
     *   <li>없으면: 새로 저장</li>
     * </ol>
     *
     * @param sajuResult        저장 대상 SajuResult
     * @param advice            OpenAI 응답
     * @param modelVersion      사용 모델 버전
     * @param consultationMonth 대상 월 (YYYYMM 형식 정수, 예: 202605)
     */
    @DistributedLock(key = "'lock:career-consultation:' + #sajuResult.id + ':' + #consultationMonth")
    @Transactional
    public Long saveOrUpdate(SajuResult sajuResult, CareerAdviceResponse advice,
                             String modelVersion, Integer consultationMonth) {
        Optional<CareerConsultation> existingOpt = careerConsultationRepository
                .findBySajuResultAndConsultationMonth(sajuResult, consultationMonth);

        if (existingOpt.isPresent()) {
            return updateIfModelChanged(existingOpt.get(), sajuResult, advice, modelVersion, consultationMonth);
        }

        CareerConsultation newConsultation = consultationMapper.buildConsultation(
                sajuResult, advice, modelVersion, consultationMonth);
        CareerConsultation saved = careerConsultationRepository.save(newConsultation);
        log.info("새 CareerConsultation 저장 완료: sajuResultId={}, month={}",
                sajuResult.getId(), consultationMonth);
        return saved.getId();
    }

    private Long updateIfModelChanged(CareerConsultation existing, SajuResult sajuResult,
                                      CareerAdviceResponse advice, String modelVersion, Integer consultationMonth) {
        if (!existing.getOpenaiModelVersion().equals(modelVersion)) {
            log.info("모델 버전 변경 감지 — 기존 컨설팅 결과 업데이트: " +
                            "sajuResultId={}, month={}, 구버전={}, 신버전={}",
                    sajuResult.getId(), consultationMonth,
                    existing.getOpenaiModelVersion(), modelVersion);
            consultationMapper.updateConsultation(existing, advice, modelVersion);
        } else {
            log.info("같은 모델 버전 — 기존 컨설팅 결과 유지: sajuResultId={}, month={}",
                    sajuResult.getId(), consultationMonth);
        }
        return existing.getId();
    }
}
