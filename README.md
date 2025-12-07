# Jira MCP Server

A lightweight Spring Boot service that handles OAuth2 authentication with Atlassian (Jira) and exposes small "tools" that let a Claude-like assistant access and manage Jira issues on behalf of a user.

This project:
- Performs Atlassian OAuth2 authorization and token exchange.
- Stores encrypted access and refresh tokens in a PostgreSQL database.
- Automatically refreshes tokens when expired.
- Exposes a small set of programmatic "tools" (in `JiraService`) for assistants: get issue, create issue, update issue summary.
- Integrates with Spring AI MCP Server (SSE-based transport) so an LLM agent can call these tools.

Quick links
- Main app: `src/main/java/com/mcp/jira/JiraApplication.java`
- OAuth callback/controller: `src/main/java/com/mcp/jira/controllers/JiraCallbackController.java`
- Token management: `src/main/java/com/mcp/jira/managers/TokenManager.java`
- Tools for assistant: `src/main/java/com/mcp/jira/controllers/JiraService.java`
- Token entity: `src/main/java/com/mcp/jira/modals/JiraToken.java`
- Configuration: `src/main/resources/application.yaml`

Requirements / Versions
- Java 17
- Maven (project includes `mvnw` wrapper)
- PostgreSQL (or any JDBC-compatible DB configured in `application.yaml`)
- Atlassian OAuth app credentials (client id + secret)

What it does (high-level)
- Provides a local web endpoint to start Jira OAuth flow (`/auth/jira`).
- After successful OAuth handshake, user receives a generated connection token (a `principalName` UUID) shown on a success page — this is your `JIRA_CONNECTION_TOKEN`.
- Saves encrypted access and refresh tokens in the database per `principalName`.
- Exposes tools that use the stored access token (and refresh when needed) to call the Jira REST API:
  - `getIssue(issueId)` — returns a concise markdown summary.
  - `createIssue(projectKey, summary, issueType, description)` — creates an issue.
  - `updateIssueSummary(issueKey, newSummary)` — updates an issue summary.
- Integrates with Spring AI MCP Server to register the `JiraService` methods as callable tools for an LLM agent.

Configuration / Environment variables
- The sample configuration is in `src/main/resources/application.yaml`. Values shown there are examples; override in your environment or an external config for production.

Important environment variables:
- `JIRA_CLIENT_ID` — Atlassian OAuth app client id
- `JIRA_CLIENT_SECRET` — Atlassian OAuth app client secret
- `JIRA_CALLBACK_URL` — OAuth redirect/callback URL (must match Atlassian app settings, e.g. `http://localhost:8080/auth/jira/callback`)

Optional and helpful overrides:
- `JASYPT_ENCRYPTOR_PASSWORD` or set `jasypt.encryptor.password` to protect stored tokens (do not keep in source).
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` to override DB settings in `application.yaml`.

Local development — setup & run
1. Export required env vars (zsh example):

```bash
export JIRA_CLIENT_ID=your_atlassian_client_id
export JIRA_CLIENT_SECRET=your_atlassian_client_secret
export JIRA_CALLBACK_URL=http://localhost:8080/auth/jira/callback
# optional
export JASYPT_ENCRYPTOR_PASSWORD=change_this_securely
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jiratokensdb
export SPRING_DATASOURCE_USERNAME=myuser
export SPRING_DATASOURCE_PASSWORD=mysecretpassword
```

2. Build and run:

```bash
# build
./mvnw clean package -DskipTests

# run
./mvnw spring-boot:run
# OR run the fat jar
java -jar target/*.jar
```

3. Visit the app:
- Home page: `http://localhost:8080/` — click "Connect to JIRA" to start OAuth.
- After consent, you'll be shown a `JIRA_CONNECTION_TOKEN` on the success page.

Using the connection token with Claude
- Copy the `JIRA_CONNECTION_TOKEN` from the OAuth success page into your agent configuration (e.g. `claude_desktop_config.json`) so the agent can call this server on behalf of the principal.

Endpoints of interest
- `GET /` — simple HTML home with a "Connect to JIRA" button.
- `GET /auth/jira` — starts OAuth flow (redirects to `/oauth2/authorization/jira`).
- `GET /auth/jira/callback` — OAuth callback endpoint. After success, prints the generated `JIRA_CONNECTION_TOKEN`.
- The LLM tools are registered through Spring AI MCP Server using the `JiraService` bean (no separate REST endpoints for those; they are callable by the agent through MCP).

Database
- The app persists `JiraToken` entities (encrypted) in the configured database.
- Entity: `src/main/java/com/mcp/jira/modals/JiraToken.java`
- Quick dev Postgres example:

```bash
docker run --name jira-pg -e POSTGRES_USER=myuser -e POSTGRES_PASSWORD=mysecretpassword -e POSTGRES_DB=jiratokensdb -p 5432:5432 -d postgres:15
```


Troubleshooting & tips
- Ensure `JIRA_CALLBACK_URL` exactly matches the URL configured in your Atlassian app (scheme, host, port, and path).
- Ensure the OAuth app includes the `offline_access` scope so refresh tokens are issued.
- For production, use HTTPS and keep encryption passwords and secrets out of source control.

License
- This repository includes a minimal MIT license in `LICENSE`.

Contributing
- Open PRs or issues. Consider adding tests around token refresh and the `JiraService` tool methods.

## Docker Compose (Postgres) — quick dev setup

A minimal `docker-compose.yml` is included to bring up a PostgreSQL instance for local development.

Usage (from the project root):

```bash
# build images and start services
docker-compose up --build

# stop and remove containers
docker-compose down
```

Notes:
- The Compose file exposes Postgres on port `5432`.
- You must still provide Atlassian credentials. Set `JIRA_CLIENT_ID`, `JIRA_CLIENT_SECRET`, and `JIRA_CALLBACK_URL` either in a local `.env` file or export them in your shell before starting the app service.

Example `.env` (create in repo root and DO NOT commit):

```env
JIRA_CLIENT_ID=your_atlassian_client_id
JIRA_CLIENT_SECRET=your_atlassian_client_secret
JIRA_CALLBACK_URL=http://localhost:8080/auth/jira/callback
```

## Example `claude_desktop_config.json`

Below is a minimal example `claude_desktop_config.json` the agent can use to reference this server and the generated connection token. Save this file locally for your agent integration and replace the placeholder values.

```json
{
  "mcpServers": {
    "jira-server": {
      "command": "npx",
      "args":[
        "-y",
        "mcp-remote",
        "http://localhost:8080/sse",
        "--allow-http",
        "--header",
        "Authorization: Bearer <YOUR_JIRA_CONNECTION_TOKEN_HERE>"
      ]
    }
  }
}

```

Place the real `JIRA_CONNECTION_TOKEN` (the `principalName` shown on the OAuth success page) under `mcpServers.jira-server.args.Authorization`.

## Blog / Writeup

Blog post: https://medium.com/@ankit.rishu06/bridging-the-gap-building-an-enterprise-grade-jira-agent-with-java-spring-ai-and-the-model-817b7a611e27