package com.ekusys.exam.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(LoginUser user) {
        Instant now = Instant.now();
        Instant expires = now.plus(properties.getAccessTokenExpireMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
            .issuer(properties.getIssuer())
            .subject(user.getUsername())
            .claim("uid", user.getUserId())
            .claim("roles", user.getRoles())
            .claim("typ", "access")
            .issuedAt(Date.from(now))
            .expiration(Date.from(expires))
            .signWith(getSignKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    public String createRefreshToken(LoginUser user) {
        Instant now = Instant.now();
        Instant expires = now.plus(properties.getRefreshTokenExpireDays(), ChronoUnit.DAYS);
        return Jwts.builder()
            .issuer(properties.getIssuer())
            .subject(user.getUsername())
            .claim("uid", user.getUserId())
            .claim("roles", user.getRoles())
            .claim("typ", "refresh")
            .issuedAt(Date.from(now))
            .expiration(Date.from(expires))
            .signWith(getSignKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSignKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public LoginUser parseLoginUser(String token) {
        Claims claims = parseClaims(token);
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles", List.class);
        return LoginUser.builder()
            .userId(Long.valueOf(claims.get("uid").toString()))
            .username(claims.getSubject())
            .enabled(true)
            .roles(roles)
            .build();
    }

    public boolean isRefreshToken(String token) {
        Claims claims = parseClaims(token);
        return "refresh".equals(claims.get("typ", String.class));
    }

    private SecretKey getSignKey() {
        byte[] raw = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(raw.length >= 32 ? raw : java.util.Arrays.copyOf(raw, 32));
    }
}
