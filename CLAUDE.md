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
│   ├── ApiKeyAuthFilter.java           # [OUR CODE] Bearer token auth (SHA256 hash comparison)
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

- **Fail-closed auth**: `DISCORD_MCP_API_KEY_HASH` env var must be set or all `/mcp` requests are rejected. Only `/actuator/health` is open.
- **Tool allowlist**: Only read+send tools enabled by default via `discord.mcp.tools.enabled` in `application-http.properties`. **Never expose moderation tools (ban, kick, delete) without explicit approval.**
- **Profile-gated**: `ApiKeyAuthFilter` and `SecurityConfig` use `@Profile("http")` — they only activate when `SPRING_PROFILES_ACTIVE=http`. The stdio mode is unaffected.
- **Non-root container**: Dockerfile runs as `appuser`, no secrets baked into image layers.

## Key Config Files

| File | Purpose |
|------|---------|
| `application.properties` | Default config (stdio mode) |
| `application-http.properties` | HTTP mode: auth hash, tool allowlist, MCP endpoint |
| `.env.example` | Template for environment variables |
| `docker-compose.yml` | Local Docker orchestration |

## Deploy

Railway with automatic HTTPS. Required env vars:
- `SPRING_PROFILES_ACTIVE=http`
- `DISCORD_TOKEN` (bot token)
- `DISCORD_GUILD_ID` (server ID)
- `DISCORD_MCP_API_KEY_HASH` (SHA256 hex hash of API key)

## Working in This Repo

1. **Minimal fork policy**: Don't refactor upstream code. Add new files when possible.
2. **All code in English** (en_US) — follows the parent workspace rule.
3. **Conventional Commits**: `feat(security):`, `docs:`, `fix:` — match upstream style with emojis.
4. **No `Co-Authored-By`** lines in commits.
5. **Never run `git push`** — only the developer pushes.
