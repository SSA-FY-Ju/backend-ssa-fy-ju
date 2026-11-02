package ssafy.SSAju.career.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ssafy.SSAju.career.enums.AnalysisType;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.Instant;

/**
 * 성공한 분석 요청 한 건. 세 유형(관운·커리어 컨설팅·기업 궁합)이 이 한 테이블을 쓴다.
 *
 * <p>같은 입력으로 다시 요청해도 기존 행을 갱신하거나 재사용하지 않고 새 행을 INSERT한다. 따라서
 * 입력·월·회사·모델 기준의 유일 제약이나 "완료 상태" 컬럼이 없고, 결과를 바꾸는 메서드도 두지
 * 않는다. 프로필·만세력만 재사용한다.
 *
 * <p>소유자는 {@code user_id} 값만 보관하고 {@link ssafy.SSAju.entity.User} 연관으로 매핑하지
 * 않는다 — User에는 {@code @SQLRestriction("deleted_at IS NULL")}이 걸려 있어 연관으로 매핑하면
 * 탈퇴 사용자의 분석을 관리자 조회·집계에서 읽을 때 행이 사라지거나 프록시 로딩이 실패한다.
 * 분석 자료는 탈퇴 후에도 유지하는 정책이므로 소유자 식별자만 저장한다.
 *
 * <p>실제 분석에 쓴 출생정보는 {@code manse → user_profile}로 조회한다. 같은 프로필을 가리키는
 * FK를 이 테이블에 한 번 더 두지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(
        name = "saju",
        indexes = {
                @Index(name = "idx_saju_user_created", columnList = "user_id, created_at desc, id desc"),
                @Index(name = "idx_saju_user_type_created",
                        columnList = "user_id, analysis_type, created_at desc, id desc"),
                @Index(name = "idx_saju_created", columnList = "created_at desc, id desc"),
                @Index(name = "idx_saju_type_created", columnList = "analysis_type, created_at desc, id desc")
        })
public class Saju {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 결과의 직접 소유자. 기본 출생정보 공유와 무관하게 이 사용자만 조회·피드백할 수 있다. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 32)
    private AnalysisType analysisType;

    /** 이 분석이 실제로 사용한 만세력. 모든 유형에서 필수다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manse_id", nullable = false)
    private Manse manse;

    /** 목록·상세·기간 필터·정렬·관리자 집계가 모두 공유하는 단일 시각 컬럼. */
    @CreatedDate
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 유형별 요청값과 실제 계산에 쓴 입력 스냅샷(JSON 문자열). */
    @Column(name = "input_json", columnDefinition = "json", nullable = false)
    private String inputJson;

    /** 검증을 마친 완성 결과 스냅샷(JSON 문자열, 분석 ID 제외). */
    @Column(name = "result_json", columnDefinition = "json", nullable = false)
    private String resultJson;

    @Builder
    public Saju(Long userId, AnalysisType analysisType, Manse manse, String inputJson, String resultJson) {
        if (userId == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.UNAUTHORIZED.getMessage());
        }
        if (analysisType == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_TYPE_UNSUPPORTED.getMessage());
        }
        if (manse == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.MANSE_DATA_REQUIRED.getMessage());
        }
        if (inputJson == null || inputJson.isBlank() || resultJson == null || resultJson.isBlank()) {
            throw new InvalidSajuDataException(ErrorMessageConstants.ANALYSIS_SNAPSHOT_REQUIRED.getMessage());
        }
        this.userId = userId;
        this.analysisType = analysisType;
        this.manse = manse;
        this.inputJson = inputJson;
        this.resultJson = resultJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Saju that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
