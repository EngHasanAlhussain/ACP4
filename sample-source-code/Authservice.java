package uk.ac.ed.acp4.sample;

/**
 * Sample bank AuthService - contains a hardcoded/expired JWT secret key.
 * This file is read by the AI agent to generate fix suggestions.
 */
public class AuthService {

    // BUG: secret key is hardcoded and has not been rotated
    private static final String JWT_SECRET = "hardcoded-secret-key-123-do-not-use";

    public String generateToken(String userId) {
        // In real code this would use a JWT library
        return sign(userId + ":token", JWT_SECRET);
    }

    public boolean verifyToken(String token, String userId) {
        try {
            String expected = sign(userId + ":token", JWT_SECRET);
            if (!expected.equals(token)) {
                throw new JWTVerificationException(
                        "Token signature invalid for user " + userId
                );
            }
            return true;
        } catch (JWTVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new JWTVerificationException("Unexpected error verifying token: " + e.getMessage());
        }
    }

    private String sign(String payload, String secret) {
        // Simplified - real impl would use HMAC-SHA256
        return Integer.toHexString((payload + secret).hashCode());
    }

    public static class JWTVerificationException extends RuntimeException {
        public JWTVerificationException(String message) {
            super(message);
        }
    }
}