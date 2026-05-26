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
