package ssafy.SSAju.exception;

public class TokenExpiredException extends AuthException {

    public TokenExpiredException(String message) {
        super(message);
    }
}
