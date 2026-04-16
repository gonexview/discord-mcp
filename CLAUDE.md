# CLAUDE.md — discord-mcp

## Overview

Third-party fork of [saseq/discord-mcp](https://github.com/SaseQ/discord-mcp). MCP server that exposes Discord tools for AI assistants via the Model Context Protocol.

**This is an upstream repo with local security additions. Keep changes minimal — prefer new files over modifying existing ones.**

## Tech Stack

- Java 17, Spring Boot 4.0.2, Spring Security 7.x
- Spring AI 2.0.0-M4 (MCP server + webmvc transport)
- JDA 5.6.1 (Discord API)
- Maven build, Docker multi-stage

## Build

```bash
mvn clean package -DskipTests   # No tests exist in the repo
docker build -t discord-mcp .   # Full container build
```

## Architecture

```
src/main/java/dev/saseq/
├── DiscordMcpApplication.java          # Spring Boot entry point
├── configs/
│   ├── DiscordMcpConfig.java           # Registers service beans as MCP tools + allowlist filtering
│   ├── OAuthTokenController.java       # [OUR CODE] POST /oauth/token (Client Credentials → JWT)
│   ├── ApiKeyAuthFilter.java           # [OUR CODE] JWT Bearer token validation
│   ├── SecurityConfig.java             # [OUR CODE] Spring Security filter chain
│   ├── FilteredToolCallbackProvider.java  # [OUR CODE] Tool allowlist wrapper
│   └── McpTransportStartupLogger.java  # Startup log for transport mode
└── services/
    ├── MessageService.java             # send/edit/delete/read messages, reactions
    ├── ChannelService.java             # CRUD channels
    ├── ModerationService.java          # kick/ban/timeout (BLOCKED by default)
    ├── RoleService.java                # CRUD roles (BLOCKED by default)
    └── ... (14 service classes total, each with @Tool-annotated methods)
```

Files marked `[OUR CODE]` are the security additions. Everything else is upstream.

## Security Rules

- **OAuth 2.0**: `DISCORD_MCP_OAUTH_CLIENT_ID` + `DISCORD_MCP_OAUTH_CLIENT_SECRET` must be set. Supports two grant types:
  - **Authorization Code + PKCE** (used by Claude Teams) — flow: `/.well-known/oauth-authorization-server` → `/authorize` → `/token` → `/mcp`
  - **Client Credentials** (used by scripts) — direct `POST /token` with credentials
- **Open paths** (no auth): `/actuator/**`, `/.well-known/**`, `/authorize`, `/token`, `/oauth/token`. Everything else requires a valid JWT.
- **Tool allowlist**: Only read+send tools enabled by default via `discord.mcp.tools.enabled` in `application-http.properties`. **Never expose moderation tools (ban, kick, delete) without explicit approval.**
- **Discord bot permissions**: separate layer from MCP/OAuth. The bot itself needs `VIEW_CHANNEL` + `READ_MESSAGE_HISTORY` on private channels, otherwise tools fail with `Missing permission: VIEW_CHANNEL`.
- **Profile-gated**: `ApiKeyAuthFilter`, `SecurityConfig`, and `OAuthTokenController` use `@Profile("http")` — they only activate when `SPRING_PROFILES_ACTIVE=http`. The stdio mode is unaffected.
- **Non-root container**: Dockerfile runs as `appuser`, no secrets baked into image layers.

## Key Config Files

| File | Purpose |
|------|---------|
| `application.properties` | Default config (stdio mode) |
| `application-http.properties` | HTTP mode: OAuth credentials, tool allowlist, MCP endpoint |
| `.env.example` | Template for environment variables |
| `docker-compose.yml` | Local Docker orchestration |

## Deploy

Railway with automatic HTTPS. Required env vars:
- `SPRING_PROFILES_ACTIVE=http`
- `DISCORD_TOKEN` (bot token)
- `DISCORD_GUILD_ID` (server ID)
- `DISCORD_MCP_OAUTH_CLIENT_ID` (OAuth client ID)
- `DISCORD_MCP_OAUTH_CLIENT_SECRET` (OAuth client secret, also used as JWT signing key)

## Working in This Repo

1. **Minimal fork policy**: Don't refactor upstream code. Add new files when possible.
2. **All code in English** (en_US) — follows the parent workspace rule.
3. **Conventional Commits**: `feat(security):`, `docs:`, `fix:` — match upstream style with emojis.
4. **No `Co-Authored-By`** lines in commits.
5. **Never run `git push`** — only the developer pushes.
