package ssafy.SSAju.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ssafy.SSAju.career.entity.CareerConsultation;
import ssafy.SSAju.career.entity.SajuResult;

import java.util.List;
import java.util.Optional;

public interface CareerConsultationRepository extends JpaRepository<CareerConsultation, Long> {

    List<CareerConsultation> findBySajuResult(SajuResult sajuResult);

    /**
     * M-9: 같은 달 캐시 히트 조회. consultationMonth 포맷은 YYYYMM 정수 (예: 202605).
     */
    Optional<CareerConsultation> findBySajuResultAndConsultationMonth(SajuResult sajuResult,
                                                                       Integer consultationMonth);

    /**
     * C-4: 피드백 저장 시 소유권 DB 레벨 검증 — IDOR 방지.
     * FeedbackService의 lazy loading chain(filter + getSajuResult().getUser().getId())을
     * DB JOIN으로 대체하여 트랜잭션 의존 없이 안전한 소유권 확인을 보장합니다.
     */
    Optional<CareerConsultation> findByIdAndSajuResult_User_Id(Long id, Long userId);

    /**
     * 마이페이지 상세 조회 전용: SajuResult 및 UserProfile을 한 번의 쿼리로 fetch join.
     * UserService.buildCareerConsultationDetail()의 레이지 로딩 체인(3개 SELECT)을 1개 쿼리로 개선.
     * 소유자 확인(user.equals)과 UserProfile 접근이 추가 쿼리 없이 가능.
     */
    @Query("SELECT cc FROM CareerConsultation cc " +
           "LEFT JOIN FETCH cc.sajuResult sr " +
           "LEFT JOIN FETCH sr.userProfile " +
           "WHERE cc.id = :id")
    Optional<CareerConsultation> findByIdWithSajuResultAndProfile(@Param("id") Long id);

    /**
     * M-5: SajuResult 교체 시 연관된 CareerConsultation 전체 삭제 (FK 제약 위반 방지).
     */
    @Modifying
    @Query("DELETE FROM CareerConsultation cc WHERE cc.sajuResult.id = :sajuResultId")
    void deleteBySajuResultId(@Param("sajuResultId") Long sajuResultId);
}
