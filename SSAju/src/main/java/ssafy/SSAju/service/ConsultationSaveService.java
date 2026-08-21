package ssafy.SSAju.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ssafy.SSAju.annotation.DistributedLock;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;
import ssafy.SSAju.career.mapper.ConsultationMapper;
import ssafy.SSAju.dto.external.CareerAdviceResponse;
import ssafy.SSAju.exception.ConsultationRecoveryFailedException;
import ssafy.SSAju.repository.CareerConsultationRepository;

import java.util.Optional;

/**
 * C-7: CareerConsultation 저장을 별도 서비스로 분리(ConsultationService의 외부 I/O와 DB
 * 저장을 분리).
 *
 * <p>이 클래스 자체엔 {@code @Transactional}이 없다 — (정본, 월) 단위 분산락
 * ({@code @DistributedLock}, US5, T036)이 메서드 전체를 감싸고, 그 안의 개별 저장소 호출은
 * Spring Data JPA의 기본 트랜잭션으로 각자 실행된다. 이렇게 하면 "바깥 트랜잭션 안에 안쪽
 * 트랜잭션을 분리해 넣는" 중첩 구조 자체가 없어진다 — REQUIRES_NEW로 격리할 대상이 애초에
 * 없으므로, 삽입 중 UNIQUE 제약 위반이 발생해도 재조회가 오염될 트랜잭션이 없어 항상 안전하다
 * ({@code UserProfileProvider}/{@code SajuResultProvider}와 동일한 패턴).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationSaveService {

    private final ConsultationMapper consultationMapper;
    private final CareerConsultationRepository careerConsultationRepository;

    /**
     * saveOrUpdate 호출 결과. {@code persisted}는 이 호출이 실제로 새 내용을 DB에 썼는지를
     * 나타낸다 — 락 안 재확인/경합 복구로 인해 이미 있던(다른 요청이 만든) 데이터를 그대로
     * 반환만 한 경우엔 false. 호출자는 이 값으로 "따닥"(동일 요청 동시 도착) 시 자신이 이미
     * 차감한 일일 API 쿼터를 보상 복원할지 판단한다.
     */
    public record SaveOutcome(Long consultationId, boolean persisted) {}

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
    public SaveOutcome saveOrUpdate(SajuResult sajuResult, CareerAdviceResponse advice,
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
     * (UNIQUE 위반) 시 기존 데이터를 재조회합니다. 이 클래스에 @Transactional이 없으므로
     * save()는 그 자체로 독립된 트랜잭션이라, 실패해도 재조회를 오염시키지 않는다.
     */
    private SaveOutcome insertOrRecoverOnConflict(SajuResult sajuResult, CareerAdviceResponse advice,
                                           String modelVersion, Integer consultationMonth) {
        try {
            CareerConsultation newConsultation = consultationMapper.buildConsultation(
                    sajuResult, advice, modelVersion, consultationMonth);
            CareerConsultation saved = careerConsultationRepository.save(newConsultation);
            log.info("새 CareerConsultation 저장 완료: sajuResultId={}, month={}",
                    sajuResult.getId(), consultationMonth);
            return new SaveOutcome(saved.getId(), true);
        } catch (DataIntegrityViolationException e) {
            log.warn("CareerConsultation 삽입 중 UNIQUE 제약 위반(락 임대시간 만료 경합 추정) — 재조회: "
                            + "sajuResultId={}, month={}",
                    sajuResult.getId(), consultationMonth, e);
            return careerConsultationRepository
                    .findBySajuResultAndConsultationMonth(sajuResult, consultationMonth)
                    .map(existing -> updateIfModelChanged(
                            existing, sajuResult, advice, modelVersion, consultationMonth))
                    .orElseThrow(() -> new ConsultationRecoveryFailedException(
                            "CareerConsultation 경합 복구 실패: sajuResultId=" +
                            sajuResult.getId() + ", month=" + consultationMonth, e));
        }
    }

    /**
     * 기존 엔티티는 이전 조회의 트랜잭션이 이미 끝나 detached 상태다 — 필드만 바꾸고 끝내면
     * DB에 반영되지 않으므로, 명시적으로 save()해 merge한다.
     */
    private SaveOutcome updateIfModelChanged(CareerConsultation existing, SajuResult sajuResult,
                                      CareerAdviceResponse advice, String modelVersion, Integer consultationMonth) {
        if (!existing.getOpenaiModelVersion().equals(modelVersion)) {
            log.info("모델 버전 변경 감지 — 기존 컨설팅 결과 업데이트: " +
                            "sajuResultId={}, month={}, 구버전={}, 신버전={}",
                    sajuResult.getId(), consultationMonth,
                    existing.getOpenaiModelVersion(), modelVersion);
            consultationMapper.updateConsultation(existing, advice, modelVersion);
            return new SaveOutcome(careerConsultationRepository.save(existing).getId(), true);
        }
        log.info("같은 모델 버전 — 기존 컨설팅 결과 유지: sajuResultId={}, month={}",
                sajuResult.getId(), consultationMonth);
        return new SaveOutcome(existing.getId(), false);
    }
}
