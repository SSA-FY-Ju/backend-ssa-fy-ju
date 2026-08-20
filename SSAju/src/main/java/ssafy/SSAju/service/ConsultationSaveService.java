package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ssafy.SSAju.annotation.DistributedLock;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.mapper.ConsultationMapper;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.exception.ConsultationRecoveryFailedException;
import ssafy.SSAju.repository.CareerConsultationRepository;

import java.util.Optional;

/**
 * C-7: CareerConsultation 저장을 별도 서비스로 분리하여 @Transactional 보장.
 *
 * ConsultationService(외부 I/O 포함)와 달리 이 서비스는 순수하게 DB 저장만 담당.
 * 외부 API 호출이 없으므로 트랜잭션을 안전하게 적용할 수 있음.
 *
 * <p>(정본, 월) 단위 분산락({@code @DistributedLock}, US5, T036)이 동시 삽입 경합을 대부분
 * 막아주지만, Redisson 락은 고정된 임대시간(leaseTime, 기본 5000ms)이 지나면 워치독 갱신 없이
 * 무조건 풀린다 — 이 메서드 실행이 그보다 오래 걸리는 극단적인 상황(DB 커넥션 풀 고갈 등)까지
 * 완전히 배제할 수는 없으므로, 신규 삽입은 {@link ConsultationInsertService}의 REQUIRES_NEW
 * 트랜잭션으로 분리해 UNIQUE 제약 위반 시 안전하게 재조회로 복구한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationSaveService {

    private final ConsultationMapper consultationMapper;
    private final CareerConsultationRepository careerConsultationRepository;
    private final ConsultationInsertService consultationInsertService;

    /**
     * CareerConsultation을 저장하거나 기존 데이터를 업데이트.
     *
     * <ol>
     *   <li>해당 달의 데이터를 먼저 조회</li>
     *   <li>존재하면: 모델 버전이 다를 때만 업데이트, 같으면 건너뜀</li>
     *   <li>없으면: 새로 저장. 락 임대시간 만료 등으로 인한 극히 드문 경합(UNIQUE 위반) 시
     *       재조회 후 버전 비교하여 처리</li>
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
        return insertOrRecoverOnConflict(sajuResult, advice, modelVersion, consultationMonth);
    }

    /**
     * CareerConsultation 신규 삽입을 시도하고, 락 임대시간 만료 등으로 인한 극히 드문 경합
     * (UNIQUE 위반) 시 기존 데이터를 재조회합니다.
     *
     * <p>삽입은 {@link ConsultationInsertService#insert(CareerConsultation)}에 위임하여
     * REQUIRES_NEW 독립 트랜잭션에서 실행한다 — 이 메서드의 {@code @Transactional} 안에서
     * 직접 저장하면 UNIQUE 제약 위반 시 이 트랜잭션 자체가 rollback-only로 마킹되어,
     * catch block에서 재조회하더라도 최종 커밋이 실패한다.
     */
    private Long insertOrRecoverOnConflict(SajuResult sajuResult, CareerAdviceResponse advice,
                                           String modelVersion, Integer consultationMonth) {
        try {
            CareerConsultation newConsultation = consultationMapper.buildConsultation(
                    sajuResult, advice, modelVersion, consultationMonth);
            Long savedId = consultationInsertService.insert(newConsultation);
            log.info("새 CareerConsultation 저장 완료: sajuResultId={}, month={}",
                    sajuResult.getId(), consultationMonth);
            return savedId;
        } catch (DataIntegrityViolationException e) {
            log.warn("CareerConsultation 삽입 중 UNIQUE 제약 위반(락 임대시간 만료 경합 추정) — 재조회: "
                            + "sajuResultId={}, month={}",
                    sajuResult.getId(), consultationMonth);
            return careerConsultationRepository
                    .findBySajuResultAndConsultationMonth(sajuResult, consultationMonth)
                    .map(existing -> updateIfModelChanged(
                            existing, sajuResult, advice, modelVersion, consultationMonth))
                    .orElseThrow(() -> new ConsultationRecoveryFailedException(
                            "CareerConsultation 경합 복구 실패: sajuResultId=" +
                            sajuResult.getId() + ", month=" + consultationMonth));
        }
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
