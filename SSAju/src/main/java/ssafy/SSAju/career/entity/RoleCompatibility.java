package ssafy.SSAju.career.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "role_compatibility")
public class RoleCompatibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compatibility_id", nullable = false)
    private CompanyCompatibility companyCompatibility;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "tag")
    private String tag;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public RoleCompatibility(CompanyCompatibility companyCompatibility, String roleName,
                              Integer score, String reason, String tag) {
        this.companyCompatibility = companyCompatibility;
        this.roleName = roleName;
        this.score = score;
        this.reason = reason;
        this.tag = tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoleCompatibility that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
