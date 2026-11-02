package ssafy.SSAju.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import ssafy.SSAju.career.entity.UserProfile;
import ssafy.SSAju.entity.enums.UserRole;
import ssafy.SSAju.entity.enums.UserStatus;

import java.time.Instant;
import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "terms_agreed_at", nullable = false)
    private Instant termsAgreedAt;

    @Column(name = "privacy_agreed_at", nullable = false)
    private Instant privacyAgreedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * 분석 요청 화면에서 먼저 제안할 기본 출생정보. 설정하지 않으면 {@code null}이며 가입 직후에도
     * 비어 있다.
     *
     * <p>분석 결과의 소유자나 실제 분석에 쓴 출생정보가 아니다 — 실제 분석 프로필은
     * {@code saju → manse → user_profile}로 조회한다. 여러 사용자가 같은 출생정보 프로필을 기본값으로
     * 가리킬 수 있어 UNIQUE가 아니며, 공유 행이므로 삭제 연쇄 전파도 두지 않는다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id")
    private UserProfile userProfile;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public User(String email, String passwordHash, String name, UserRole role, UserStatus status,
                Instant termsAgreedAt, Instant privacyAgreedAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role != null ? role : UserRole.USER;
        this.status = status != null ? status : UserStatus.ACTIVE;
        this.termsAgreedAt = termsAgreedAt;
        this.privacyAgreedAt = privacyAgreedAt;
    }

    /**
     * 기본 출생정보 참조를 교체한다. 공유 프로필 행 자체는 수정하지 않고 참조만 바꾼다.
     * 분석 수행은 이 메서드를 호출하지 않는다 — 기본값은 사용자의 명시적인 저장으로만 바뀐다.
     */
    public void changeDefaultProfile(UserProfile userProfile) {
        this.userProfile = userProfile;
    }

    /** 기본 출생정보 참조만 해제한다. 공유 프로필과 기존 분석은 그대로 둔다. */
    public void releaseDefaultProfile() {
        this.userProfile = null;
    }

    public void updateLastLoginAt() {
        this.lastLoginAt = Instant.now();
    }

    public void softDelete() {
        // Instant 기반으로 통일: 타임존 독립적인 고유 epoch 초 값 + deletedAt 일관성 보장
        Instant now = Instant.now();
        this.name = "탈퇴한 사용자";
        this.email = "deleted_" + this.id + "_" + now.getEpochSecond() + "@deleted.local";
        this.status = UserStatus.INACTIVE;
        this.deletedAt = now;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }
}
