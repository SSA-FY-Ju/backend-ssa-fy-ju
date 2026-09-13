package ssafy.SSAju.admin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.admin.dto.FeedbackListDTO;
import ssafy.SSAju.admin.dto.FeedbackStatDTO;
import ssafy.SSAju.career.entity.UserSatisfactionFeedback;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.SatisfactionStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class AdminFeedbackQueryRepository {

    private final EntityManager em;

    public Page<FeedbackListDTO> findFeedbackByType(AnalysisType feedbackType, Pageable pageable) {
        String whereClause = feedbackType != null ? "WHERE f.feedbackType = :type " : "";

        String jpql = "SELECT f FROM UserSatisfactionFeedback f " + whereClause + "ORDER BY f.createdAt DESC";
        TypedQuery<UserSatisfactionFeedback> query = em.createQuery(jpql, UserSatisfactionFeedback.class);

        String countJpql = "SELECT COUNT(f) FROM UserSatisfactionFeedback f " + whereClause;
        TypedQuery<Long> countQuery = em.createQuery(countJpql, Long.class);

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
                        f.getFeedbackType(),
                        f.getCreatedAt()
                ))
                .toList();

        long total = countQuery.getSingleResult();
        return new PageImpl<>(content, pageable, total);
    }

    public FeedbackStatDTO findFeedbackStats() {
        String jpql = """
                SELECT f.feedbackType, f.satisfactionStatus, COUNT(f)
                FROM UserSatisfactionFeedback f
                GROUP BY f.feedbackType, f.satisfactionStatus
                """;

        List<Object[]> rows = em.createQuery(jpql, Object[].class).getResultList();

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

    public List<UserSatisfactionFeedback> findFeedbackWithAnalysis(Long feedbackId) {
        return em.createQuery(
                        "SELECT f FROM UserSatisfactionFeedback f " +
                        "LEFT JOIN FETCH f.careerConsultation " +
                        "LEFT JOIN FETCH f.companyCompatibility " +
                        "WHERE f.id = :id",
                        UserSatisfactionFeedback.class)
                .setParameter("id", feedbackId)
                .getResultList();
    }
}
