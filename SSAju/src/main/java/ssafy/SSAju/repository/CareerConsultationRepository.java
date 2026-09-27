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
     * C-4/B1: 피드백 저장 시 소유권 DB 레벨 검증 — IDOR 방지.
     * SajuResult가 여러 사용자가 공유하는 정본으로 바뀌었으므로(B1), 소유권은
     * SajuResult.user가 아니라 UserSajuAccess 매핑 존재 여부로 EXISTS 서브쿼리를 통해
     * 트랜잭션 의존 없이 DB 레벨에서 확인합니다.
     */
    @Query("SELECT cc FROM CareerConsultation cc " +
           "WHERE cc.id = :id AND EXISTS (" +
           "  SELECT 1 FROM UserSajuAccess usa " +
           "  WHERE usa.sajuResult = cc.sajuResult AND usa.user.id = :userId)")
    Optional<CareerConsultation> findByIdAndAccessibleByUser(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 마이페이지 상세 조회 전용: SajuResult 및 UserProfile을 한 번의 쿼리로 fetch join,
     * 소유권(UserSajuAccess)까지 EXISTS 서브쿼리로 함께 확인.
     * UserService.buildCareerConsultationDetail()의 레이지 로딩 체인(3개 SELECT)을 1개 쿼리로 개선.
     */
    @Query("SELECT cc FROM CareerConsultation cc " +
           "LEFT JOIN FETCH cc.sajuResult sr " +
           "LEFT JOIN FETCH sr.userProfile " +
           "WHERE cc.id = :id AND EXISTS (" +
           "  SELECT 1 FROM UserSajuAccess usa " +
           "  WHERE usa.sajuResult = sr AND usa.user.id = :userId)")
    Optional<CareerConsultation> findByIdAndUserIdWithSajuResultAndProfile(@Param("id") Long id,
                                                                            @Param("userId") Long userId);

    /**
     * M-5: SajuResult 교체 시 연관된 CareerConsultation 전체 삭제 (FK 제약 위반 방지).
     */
    @Modifying
    @Query("DELETE FROM CareerConsultation cc WHERE cc.sajuResult.id = :sajuResultId")
    void deleteBySajuResultId(@Param("sajuResultId") Long sajuResultId);
}
