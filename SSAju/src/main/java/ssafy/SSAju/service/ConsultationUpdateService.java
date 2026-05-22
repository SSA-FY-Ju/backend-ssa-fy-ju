package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.mapper.ConsultationMapper;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.repository.CareerConsultationRepository;

/**
 * CareerConsultation 업데이트를 별도 트랜잭션으로 처리.
 *
 * <p>ConsultationSaveService에서 UNIQUE 제약 위반(Tainted Session) 이후
 * 안전하게 업데이트를 수행하기 위해 REQUIRES_NEW 트랜잭션으로 분리.</p>
 *
 * <p>Spring AOP 프록시 특성상 같은 클래스 내 self-invocation으로는
 * REQUIRES_NEW 트랜잭션이 적용되지 않으므로 별도 빈으로 분리.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationUpdateService {

    private final ConsultationMapper consultationMapper;
    private final CareerConsultationRepository careerConsultationRepository;

    /**
     * UNIQUE 제약 위반 후 새 트랜잭션에서 모델 버전 비교 후 조건부 업데이트.
     *
     * <p>REQUIRES_NEW: Tainted 상태의 기존 트랜잭션과 완전히 분리된 새 세션에서 실행.</p>
     *
     * @param sajuResult        대상 SajuResult
     * @param advice            새 OpenAI 응답
     * @param modelVersion      현재 모델 버전
     * @param consultationMonth 대상 월 (yyyy-MM)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateIfModelChanged(SajuResult sajuResult, CareerAdviceResponse advice,
                                     String modelVersion, String consultationMonth) {
        careerConsultationRepository
                .findBySajuResultAndConsultationMonth(sajuResult, consultationMonth)
                .ifPresent(existing -> {
                    if (existing.getOpenaiModelVersion().equals(modelVersion)) {
                        log.info("같은 모델 버전 — 기존 컨설팅 결과 유지: sajuResultId={}, month={}",
                                sajuResult.getId(), consultationMonth);
                        return;
                    }
                    log.info("모델 버전 변경 감지 — 기존 컨설팅 결과 업데이트: " +
                                    "sajuResultId={}, month={}, 구버전={}, 신버전={}",
                            sajuResult.getId(), consultationMonth,
                            existing.getOpenaiModelVersion(), modelVersion);
                    consultationMapper.updateConsultation(existing, advice, modelVersion);
                });
    }
}
