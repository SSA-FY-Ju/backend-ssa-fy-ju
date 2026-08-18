package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ssafy.SSAju.career.converter.CompatibilityResultConverter;
import ssafy.SSAju.career.domain.CompatibilityAnalysisData;
import ssafy.SSAju.career.util.JobCategoryEnum;
import ssafy.SSAju.entity.User;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(
        name = "company_compatibility",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_company_role_month",
                columnNames = {"user_id", "user_profile_id", "company_name", "target_role_category", "compatibility_month"}
        )
)
public class CompanyCompatibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role_category", nullable = false, length = 30)
    private JobCategoryEnum targetRoleCategory;

    @Column(name = "target_role_detail_name")
    private String targetRoleDetailName;

    @Column(name = "compatibility_score", nullable = false)
    private Integer compatibilityScore;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    /**
     * 궁합 분석 결과 상세(직무 적합도 점수, 오행 분포, 세부 점수, 전략, 면접 질문, 직무 궁합, 월별 운세, 유의사항).
     *
     * <p>이전에는 8개 자식 엔티티로 나뉘어 저장되던 값을 하나의 JSON으로 저장한다.
     * OpenAI가 생성한 서술형 텍스트(roleSynergy, weaknessDefense 등)와 내부에서 결정론적으로
     * 계산되는 점수(matchScore, 오행 분포, breakdown 점수 등)가 함께 들어있다 — 점수는
     * {@code CompatibilityNarrativeResponse}(OpenAI 응답 DTO)에 없는 필드이므로, 이 컬럼은
     * 그 DTO가 아니라 두 종류를 이미 조합해 둔 내부 VO({@link CompatibilityAnalysisData})를 저장 대상으로 삼는다.
     */
    @Convert(converter = CompatibilityResultConverter.class)
    @Column(name = "result_json", columnDefinition = "json")
    private CompatibilityAnalysisData resultJson;

    /**
     * 모든 자식 엔티티(TargetRoleAnalysis, FiveElementsAnalysis 등 8개)가
     * 정상적으로 저장 완료된 경우 true로 설정됩니다.
     *
     * <p>false 상태에서는 {@link ssafy.SSAju.service.CompatibilityChildReadService#buildFromExisting}의
     * 캐시 재사용을 차단하여 불완전한 데이터가 응답으로 반환되는 것을 방지합니다.
     */
    @Column(name = "completed", nullable = false)
    private boolean completed = false;

    /**
     * 분석이 수행된 월을 YYYYMM 형식의 정수로 저장합니다 (예: 202605 = 2026년 5월).
     * UNIQUE(user_id, user_profile_id, company_name, target_role_category, compatibility_month) 제약으로
     * 동일 사용자가 같은 달에 동일 기업/직무를 중복 분석하는 것을 방지합니다.
     * 새로운 달이 되면 신규 분석을 허용합니다 (월별 캐시 패턴).
     */
    @Column(name = "compatibility_month", nullable = false)
    private Integer compatibilityMonth;

    @Column(name = "analyzed_at", nullable = false, updatable = false)
    private Instant analyzedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public CompanyCompatibility(UserProfile userProfile, User user, String companyName,
                                 JobCategoryEnum targetRoleCategory, String targetRoleDetailName,
                                 Integer compatibilityScore, String summary, Integer compatibilityMonth) {
        this.userProfile = userProfile;
        this.user = user;
        this.companyName = companyName;
        this.targetRoleCategory = targetRoleCategory;
        this.targetRoleDetailName = targetRoleDetailName;
        this.compatibilityScore = compatibilityScore;
        this.summary = summary;
        this.compatibilityMonth = compatibilityMonth;
        this.completed = false;
        this.analyzedAt = Instant.now();
    }

    /**
     * 분석 결과 JSON을 저장하고 completed 상태를 true로 전환하여 캐시 재사용을 허용합니다.
     */
    public void assignResultJsonAndMarkCompleted(CompatibilityAnalysisData resultJson) {
        this.resultJson = resultJson;
        this.completed = true;
    }

    public void assignUser(User user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompanyCompatibility that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
