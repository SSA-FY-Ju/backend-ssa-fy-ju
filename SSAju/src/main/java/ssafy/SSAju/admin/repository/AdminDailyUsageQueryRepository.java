package ssafy.SSAju.admin.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import ssafy.SSAju.admin.service.AdminBaseService;
import ssafy.SSAju.entity.DailyApiUsage;

import java.time.LocalDate;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AdminDailyUsageQueryRepository {

    private final EntityManager em;

    public Optional<DailyApiUsage> findUsageByUserAndDate(Long userId, LocalDate date) {
        return em.createQuery(
                        "SELECT d FROM DailyApiUsage d WHERE d.user.id = :userId AND d.usageDate = :date",
                        DailyApiUsage.class)
                .setParameter("userId", userId)
                .setParameter("date", date)
                .getResultStream()
                .findFirst();
    }

    public long countDailyLimitExhausted(LocalDate date, int dailyLimit) {
        Long count = em.createQuery(
                        "SELECT COUNT(d) FROM DailyApiUsage d WHERE d.usageDate = :date AND d.requestCount >= :limit",
                        Long.class)
                .setParameter("date", date)
                .setParameter("limit", dailyLimit)
                .getSingleResult();
        return count != null ? count : 0L;
    }

    public int resetDailyUsage(Long userId, LocalDate date) {
        return em.createQuery(
                        "UPDATE DailyApiUsage d SET d.requestCount = 0 WHERE d.user.id = :userId AND d.usageDate = :date")
                .setParameter("userId", userId)
                .setParameter("date", date)
                .executeUpdate();
    }

    public int decrementDailyUsage(Long userId, LocalDate date, int amount) {
        if (amount <= 0) return 0;
        return em.createQuery(
                        "UPDATE DailyApiUsage d SET d.requestCount = GREATEST(0, d.requestCount - :amount) " +
                        "WHERE d.user.id = :userId AND d.usageDate = :date")
                .setParameter("userId", userId)
                .setParameter("date", date)
                .setParameter("amount", amount)
                .executeUpdate();
    }
}
