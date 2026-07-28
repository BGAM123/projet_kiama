package com.docuai.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:super_secret_default_key_that_is_at_least_256_bits_long_for_hs256}")
    private String jwtSecret;

    @Value("${jwt.expiration.access:900000}")
    private long jwtExpirationAccessMs;

    @Value("${jwt.expiration.refresh:604800000}")
    private long jwtExpirationRefreshMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateAccessToken(Authentication authentication) {
        return generateToken(authentication.getName(), jwtExpirationAccessMs);
    }

    public String generateRefreshToken(Authentication authentication) {
        return generateToken(authentication.getName(), jwtExpirationRefreshMs);
    }

    private String generateToken(String username, long expirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return claims.getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
            // Ici, nous pourrions aussi vérifier si le token est dans une blacklist Redis
            return true;
        } catch (Exception ex) {
            // Logger l'erreur en temps normal (SignatureException, MalformedJwtException, ExpiredJwtException, etc.)
            return false;
        }
    }
}
