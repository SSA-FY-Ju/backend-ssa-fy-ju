package ssafy.SSAju.util;

/**
 * Redis 키 prefix 상수 모음.
 *
 * <p>세션(Refresh Token/Access Token 블랙리스트) 키는 모두 이 prefix +
 * 도메인 식별자(JTI, userProfileId 등) 조합으로 구성합니다. 매직 스트링 하드코딩을
 * 방지하기 위해 키를 다루는 모든 코드는 이 상수만 참조합니다.
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {}

    public static final String REFRESH_TOKEN_PREFIX = "refresh-token:";
    public static final String ACCESS_BLACKLIST_PREFIX = "access-blacklist:";

}
