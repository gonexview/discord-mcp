package dev.saseq.configs;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Profile("http")
@RestController
public class OAuthTokenController {

    private static final long TOKEN_EXPIRY_SECONDS = 3600;

    @Value("${discord.mcp.oauth.client-id:}")
    private String expectedClientId;

    @Value("${discord.mcp.oauth.client-secret:}")
    private String expectedClientSecret;

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void token(@RequestParam("grant_type") String grantType,
                      @RequestParam("client_id") String clientId,
                      @RequestParam("client_secret") String clientSecret,
                      HttpServletResponse response) throws IOException {

        response.setContentType("application/json");

        if (expectedClientId == null || expectedClientId.isBlank()
                || expectedClientSecret == null || expectedClientSecret.isBlank()) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "server_error", "OAuth credentials not configured");
            return;
        }

        if (!"client_credentials".equals(grantType)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "unsupported_grant_type", "Only client_credentials is supported");
            return;
        }

        if (!expectedClientId.equals(clientId) || !expectedClientSecret.equals(clientSecret)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "invalid_client", "Invalid client credentials");
            return;
        }

        Instant now = Instant.now();
        String accessToken = Jwts.builder()
                .subject("mcp-client")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(TOKEN_EXPIRY_SECONDS)))
                .signWith(getSigningKey())
                .compact();

        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(
                "{\"access_token\":\"" + accessToken + "\","
                + "\"token_type\":\"Bearer\","
                + "\"expires_in\":" + TOKEN_EXPIRY_SECONDS + "}");
    }

    static SecretKey getSigningKey() {
        String secret = System.getenv("DISCORD_MCP_OAUTH_CLIENT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("DISCORD_MCP_OAUTH_CLIENT_SECRET not set");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(padKey(keyBytes));
    }

    private static byte[] padKey(byte[] key) {
        if (key.length >= 32) return key;
        byte[] padded = new byte[32];
        System.arraycopy(key, 0, padded, 0, key.length);
        return padded;
    }

    private void sendError(HttpServletResponse response, int status,
                           String error, String description) throws IOException {
        response.setStatus(status);
        response.getWriter().write(
                "{\"error\":\"" + error + "\",\"error_description\":\"" + description + "\"}");
    }
}
