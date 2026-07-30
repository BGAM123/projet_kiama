package com.docuai.security.jwt;

import com.docuai.security.config.JwtKeyProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Date;

/**
 * Émission/validation des JWT en RS256 (section 8 du prompt maître,
 * non-négociable : "JWT RS256"). Jeton d'accès courte durée (15 min par
 * défaut) + jeton de rafraîchissement longue durée (7 jours par défaut),
 * révocable via TokenBlacklistService.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final JwtKeyProperties properties;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtTokenProvider(RSAPrivateKey privateKey,
                             RSAPublicKey publicKey,
                             JwtKeyProperties properties,
                             TokenBlacklistService tokenBlacklistService) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.properties = properties;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    public String generateAccessToken(Authentication authentication) {
        return generateToken(authentication.getName(), properties.getAccessTtlSeconds(), TOKEN_TYPE_ACCESS);
    }

    public String generateRefreshToken(Authentication authentication) {
        return generateToken(authentication.getName(), properties.getRefreshTtlSeconds(), TOKEN_TYPE_REFRESH);
    }

    private String generateToken(String username, long ttlSeconds, String tokenType) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlSeconds * 1000);
        return Jwts.builder()
                .subject(username)
                .issuer(properties.getIssuer())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String getUsernameFromJWT(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isRefreshToken(String token) {
        try {
            return TOKEN_TYPE_REFRESH.equals(parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Durée restante avant expiration, utilisée pour aligner la TTL de la
     * blacklist Redis lors d'une déconnexion. Duration.ZERO si déjà expiré
     * ou illisible.
     */
    public Duration getRemainingValidity(String token) {
        try {
            Date expiration = parseClaims(token).getExpiration();
            long remainingMs = expiration.getTime() - System.currentTimeMillis();
            return remainingMs > 0 ? Duration.ofMillis(remainingMs) : Duration.ZERO;
        } catch (Exception ex) {
            return Duration.ZERO;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
            return !tokenBlacklistService.isBlacklisted(token);
        } catch (Exception ex) {
            // SignatureException, MalformedJwtException, ExpiredJwtException, etc.
            return false;
        }
    }
}
