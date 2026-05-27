package ssafy.SSAju.util;

/**
 * 데이터베이스 UNIQUE 제약 조건 이름 상수 모음.
 *
 * <p>Hibernate {@code ConstraintViolationException.getConstraintName()} 비교 시 사용합니다.
 * 스키마 변경(제약 조건 이름 변경)이 발생하면 이 파일도 함께 수정해야 합니다.
 *
 * <p><b>MySQL 명명 규칙:</b> {@code @Column(unique = true)}로 생성된 제약은
 * 컬럼명을 그대로 사용합니다. {@code @UniqueConstraint(name = "...")}으로
 * 명시적으로 지정한 경우에는 해당 이름을 사용합니다.
 */
public final class DatabaseConstraints {

    private DatabaseConstraints() {}

    /**
     * {@code users} 테이블 {@code email} 컬럼 UNIQUE 제약 조건명.
     *
     * <p>MySQL에서 {@code @Column(unique = true)}로 생성 시 컬럼명({@code "email"})이 제약명이 됩니다.
     */
    public static final String USER_EMAIL_UNIQUE = "email";
}
