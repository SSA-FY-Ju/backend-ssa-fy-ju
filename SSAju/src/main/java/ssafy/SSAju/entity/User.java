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
