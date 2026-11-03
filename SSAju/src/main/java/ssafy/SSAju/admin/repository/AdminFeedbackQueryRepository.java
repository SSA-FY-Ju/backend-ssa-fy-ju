package ssafy.SSAju.admin.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.admin.dto.FeedbackListDTO;
import ssafy.SSAju.admin.dto.FeedbackStatDTO;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.SatisfactionStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 관리자 피드백 조회.
 *
 * <p>분석 유형은 피드백이 아니라 연결된 {@code saju}에서 읽는다 — 피드백 테이블에는 유형 컬럼이
 * 없다. 목록·count·통계가 모두 같은 조인 기준을 쓰므로 필터 결과와 건수가 어긋나지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class AdminFeedbackQueryRepository {

    private final EntityManager em;

    public Page<FeedbackListDTO> findFeedbackByType(AnalysisType feedbackType, Pageable pageable) {
        String whereClause = feedbackType != null ? "WHERE s.analysisType = :type " : "";

        TypedQuery<UserSatisfactionFeedback> query = em.createQuery(
                "SELECT f FROM UserSatisfactionFeedback f JOIN FETCH f.saju s "
                        + whereClause + "ORDER BY f.createdAt DESC",
                UserSatisfactionFeedback.class);
        TypedQuery<Long> countQuery = em.createQuery(
                "SELECT COUNT(f) FROM UserSatisfactionFeedback f JOIN f.saju s " + whereClause,
                Long.class);

        if (feedbackType != null) {
            query.setParameter("type", feedbackType);
            countQuery.setParameter("type", feedbackType);
        }

        query.setFirstResult(Math.toIntExact(pageable.getOffset()));
        query.setMaxResults(pageable.getPageSize());

        List<FeedbackListDTO> content = query.getResultList().stream()
                .map(f -> new FeedbackListDTO(
                        f.getId(),
                        f.getUser() != null ? f.getUser().getId() : null,
                        f.getFeedbackContent(),
                        f.getSatisfactionStatus(),
                        f.getSaju().getAnalysisType(),
                        f.getCreatedAt()
                ))
                .toList();

        return new PageImpl<>(content, pageable, countQuery.getSingleResult());
    }

    public FeedbackStatDTO findFeedbackStats() {
        List<Object[]> rows = em.createQuery("""
                        SELECT s.analysisType, f.satisfactionStatus, COUNT(f)
                        FROM UserSatisfactionFeedback f JOIN f.saju s
                        GROUP BY s.analysisType, f.satisfactionStatus
                        """, Object[].class)
                .getResultList();

        Map<String, Long> satisfiedCount = new HashMap<>();
        Map<String, Long> unsatisfiedCount = new HashMap<>();
        long total = 0L;

        for (Object[] row : rows) {
            AnalysisType type = (AnalysisType) row[0];
            SatisfactionStatus status = (SatisfactionStatus) row[1];
            long count = (Long) row[2];
            total += count;

            String key = type.name();
            if (status == SatisfactionStatus.SATISFIED) {
                satisfiedCount.merge(key, count, Long::sum);
            } else {
                unsatisfiedCount.merge(key, count, Long::sum);
            }
        }

        return new FeedbackStatDTO(satisfiedCount, unsatisfiedCount, total);
    }

    public Optional<UserSatisfactionFeedback> findFeedbackWithAnalysis(Long feedbackId) {
        return em.createQuery(
                        "SELECT f FROM UserSatisfactionFeedback f JOIN FETCH f.saju WHERE f.id = :id",
                        UserSatisfactionFeedback.class)
                .setParameter("id", feedbackId)
                .getResultList()
                .stream()
                .findFirst();
    }
}
