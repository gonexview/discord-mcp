#!/usr/bin/env bash
# Generate OAuth 2.0 Client Credentials for discord-mcp
# Usage: ./generate_oauth_credentials.sh

set -euo pipefail

CLIENT_ID=$(openssl rand -hex 16)
CLIENT_SECRET=$(openssl rand -hex 32)

echo "=== Discord MCP OAuth Credentials ==="
echo ""
echo "DISCORD_MCP_OAUTH_CLIENT_ID=${CLIENT_ID}"
echo "DISCORD_MCP_OAUTH_CLIENT_SECRET=${CLIENT_SECRET}"
echo ""
echo "--- Railway / docker-compose (.env) ---"
echo ""
echo "Copy the lines above into your .env file or Railway environment variables."
echo ""
echo "--- Claude Teams Custom Connector ---"
echo ""
echo "  Name:                Discord MCP"
echo "  Remote MCP URL:      https://<your-app>.railway.app/mcp"
echo "  OAuth Client ID:     ${CLIENT_ID}"
echo "  OAuth Client Secret: ${CLIENT_SECRET}"
echo ""
echo "--- Test the token endpoint ---"
echo ""
echo "curl -s -X POST http://localhost:8085/oauth/token \\"
echo "  -d \"grant_type=client_credentials&client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}\""
