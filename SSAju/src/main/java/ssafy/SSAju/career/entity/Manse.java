package ssafy.SSAju.career.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ssafy.SSAju.career.converter.ManseDataConverter;
import ssafy.SSAju.career.domain.ManseData;
import ssafy.SSAju.career.enums.ErrorMessageConstants;
import ssafy.SSAju.exception.InvalidSajuDataException;

import java.time.Instant;

/**
 * 출생정보(프로필) 하나에 대한 만세력. 같은 생년월일·시각이면 여러 사용자·여러 분석이 공유한다.
 *
 * <p>계산 데이터는 {@link ManseData} 전체를 {@code data_json} 한 컬럼에 담는다. 개별 계산 필드로
 * 검색·집계하지 않고 언제나 프로필로 찾아 전체를 읽기 때문이다.
 *
 * <p>프로필 참조는 생성 후 바꾸지 않는다 — 이미 저장된 분석 결과가 이 만세력을 통해 "어떤
 * 출생정보로 분석했는지"를 가리키므로, 참조를 바꾸면 과거 결과의 의미가 달라진다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(
        name = "manse",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_manse_user_profile",
                columnNames = {"user_profile_id"}
        ))
public class Manse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false, unique = true)
    private UserProfile userProfile;

    @Convert(converter = ManseDataConverter.class)
    @Column(name = "data_json", columnDefinition = "json", nullable = false)
    private ManseData data;

    /** 이 만세력의 근거가 된 FastAPI 응답을 받은 시각. */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @CreatedDate
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public Manse(UserProfile userProfile, ManseData data, Instant fetchedAt) {
        if (userProfile == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.SAJU_PROFILE_NULL.getMessage());
        }
        if (data == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.MANSE_DATA_REQUIRED.getMessage());
        }
        if (fetchedAt == null) {
            throw new InvalidSajuDataException(ErrorMessageConstants.MANSE_DATA_REQUIRED.getMessage());
        }
        this.userProfile = userProfile;
        this.data = data;
        this.fetchedAt = fetchedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Manse that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
