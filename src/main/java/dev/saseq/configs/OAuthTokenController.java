package dev.saseq.configs;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

@Profile("http")
@RestController
public class OAuthTokenController {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenController.class);
    private static final long TOKEN_EXPIRY_SECONDS = 3600;
    private static final long CODE_EXPIRY_SECONDS = 300;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, AuthCode> pendingCodes = new ConcurrentHashMap<>();

    @Value("${discord.mcp.oauth.client-id:}")
    private String expectedClientId;

    @Value("${discord.mcp.oauth.client-secret:}")
    private String expectedClientSecret;

    @GetMapping("/authorize")
    public void authorize(@RequestParam("response_type") String responseType,
                          @RequestParam("client_id") String clientId,
                          @RequestParam("redirect_uri") String redirectUri,
                          @RequestParam(value = "code_challenge", required = false) String codeChallenge,
                          @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
                          @RequestParam(value = "state", required = false) String state,
                          HttpServletResponse response) throws IOException {

        if (!validateConfig()) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "server_error", "OAuth credentials not configured");
            return;
        }

        if (!"code".equals(responseType)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "unsupported_response_type", "Only response_type=code is supported");
            return;
        }

        if (!expectedClientId.equals(clientId)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "invalid_client", "Unknown client_id");
            return;
        }

        cleanExpiredCodes();

        String code = generateCode();
        pendingCodes.put(code, new AuthCode(
                codeChallenge, codeChallengeMethod, redirectUri, Instant.now().plusSeconds(CODE_EXPIRY_SECONDS)));

        String redirect = redirectUri + "?code=" + code;
        if (state != null && !state.isBlank()) {
            redirect += "&state=" + state;
        }

        log.info("OAuth authorize: issued code for client_id={}, redirecting", clientId);
        response.sendRedirect(redirect);
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public void authServerMetadata(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        String base = baseUrl(request);
        response.getWriter().write(
                "{\"issuer\":\"" + base + "\","
                + "\"authorization_endpoint\":\"" + base + "/authorize\","
                + "\"token_endpoint\":\"" + base + "/token\","
                + "\"response_types_supported\":[\"code\"],"
                + "\"grant_types_supported\":[\"authorization_code\",\"client_credentials\"],"
                + "\"code_challenge_methods_supported\":[\"S256\"],"
                + "\"token_endpoint_auth_methods_supported\":[\"client_secret_post\"]}");
    }

    @GetMapping("/.well-known/oauth-protected-resource")
    public void protectedResourceMetadata(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        String base = baseUrl(request);
        response.getWriter().write(
                "{\"resource\":\"" + base + "\","
                + "\"authorization_servers\":[\"" + base + "\"]}");
    }

    private String baseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null) scheme = request.getScheme();
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null) host = request.getHeader("Host");
        return scheme + "://" + host;
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void tokenAlias(@RequestParam("grant_type") String grantType,
                           @RequestParam(value = "client_id", required = false) String clientId,
                           @RequestParam(value = "client_secret", required = false) String clientSecret,
                           @RequestParam(value = "code", required = false) String code,
                           @RequestParam(value = "code_verifier", required = false) String codeVerifier,
                           @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                           HttpServletRequest request, HttpServletResponse response) throws IOException {
        token(grantType, clientId, clientSecret, code, codeVerifier, redirectUri, request, response);
    }

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void token(@RequestParam("grant_type") String grantType,
                      @RequestParam(value = "client_id", required = false) String clientId,
                      @RequestParam(value = "client_secret", required = false) String clientSecret,
                      @RequestParam(value = "code", required = false) String code,
                      @RequestParam(value = "code_verifier", required = false) String codeVerifier,
                      @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {

        response.setContentType("application/json");

        if (!validateConfig()) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "server_error", "OAuth credentials not configured");
            return;
        }

        switch (grantType) {
            case "authorization_code" -> handleAuthorizationCode(code, codeVerifier, redirectUri, clientId, response);
            case "client_credentials" -> handleClientCredentials(clientId, clientSecret, response);
            default -> sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "unsupported_grant_type", "Supported: authorization_code, client_credentials");
        }
    }

    private void handleAuthorizationCode(String code, String codeVerifier, String redirectUri,
                                         String clientId, HttpServletResponse response) throws IOException {
        if (code == null || code.isBlank()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_request", "Missing code");
            return;
        }

        AuthCode authCode = pendingCodes.remove(code);
        if (authCode == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_grant", "Invalid or expired code");
            return;
        }

        if (authCode.expiresAt().isBefore(Instant.now())) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_grant", "Code expired");
            return;
        }

        if (authCode.redirectUri() != null && !authCode.redirectUri().equals(redirectUri)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_grant", "redirect_uri mismatch");
            return;
        }

        if (authCode.codeChallenge() != null && !authCode.codeChallenge().isBlank()) {
            if (codeVerifier == null || codeVerifier.isBlank()) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_grant", "Missing code_verifier");
                return;
            }
            if (!verifyPkce(codeVerifier, authCode.codeChallenge(), authCode.codeChallengeMethod())) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "invalid_grant", "PKCE verification failed");
                return;
            }
        }

        issueToken(response);
    }

    private void handleClientCredentials(String clientId, String clientSecret,
                                         HttpServletResponse response) throws IOException {
        if (!expectedClientId.equals(clientId) || !expectedClientSecret.equals(clientSecret)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_client", "Invalid client credentials");
            return;
        }
        issueToken(response);
    }

    private void issueToken(HttpServletResponse response) throws IOException {
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

    private boolean verifyPkce(String codeVerifier, String codeChallenge, String method) {
        if ("S256".equals(method)) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
                String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
                return computed.equals(codeChallenge);
            } catch (NoSuchAlgorithmException e) {
                return false;
            }
        }
        return codeVerifier.equals(codeChallenge);
    }

    private boolean validateConfig() {
        return expectedClientId != null && !expectedClientId.isBlank()
                && expectedClientSecret != null && !expectedClientSecret.isBlank();
    }

    private void cleanExpiredCodes() {
        Instant now = Instant.now();
        pendingCodes.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    private String generateCode() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
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

    private record AuthCode(String codeChallenge, String codeChallengeMethod,
                             String redirectUri, Instant expiresAt) {}
}
