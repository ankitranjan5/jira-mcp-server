# Atlassian MCP Server

A lightweight Spring Boot service that handles OAuth2 authentication with Atlassian (Jira & Confluence) and exposes small "tools" that let a Claude-like assistant access and manage Jira issues and Confluence pages on behalf of a user.

This project:
- Performs Atlassian OAuth2 authorization and token exchange.
- Stores encrypted access and refresh tokens in a PostgreSQL database.
- Automatically refreshes tokens when expired.
- Exposes a small set of programmatic "tools" (in `AtlassianService`) for assistants: get issue, create issue, update issue summary, search Confluence pages, fetch page content, list spaces, and create pages.
- Integrates with Spring AI MCP Server (SSE-based transport) so an LLM agent can call these tools.

Quick links
- Main app: `src/main/java/com/mcp/jira/AtlassianApplication.java`
- OAuth callback/controller: `src/main/java/com/mcp/jira/controllers/AtlassianCallbackController.java`
- Token management: `src/main/java/com/mcp/jira/managers/TokenManager.java`
- Tools for assistant: `src/main/java/com/mcp/jira/controllers/AtlassianService.java`
- Token entity: `src/main/java/com/mcp/jira/modals/AtlassianToken.java`
- Configuration: `src/main/resources/application.yaml`

Requirements / Versions
- Java 17
- Maven (project includes `mvnw` wrapper)
- PostgreSQL (or any JDBC-compatible DB configured in `application.yaml`)
- Atlassian OAuth app credentials (client id + secret)

What it does (high-level)
- Provides a local web endpoint to start Atlassian OAuth flow (`/auth/atlassian` / `/oauth2/authorization/atlassian`).
- After successful OAuth handshake, the app stores encrypted tokens in the DB and exposes a short-lived connection token (a `principalName` / UUID) shown on the success page — this is the connection token the agent uses.
- Saves encrypted access and refresh tokens in the database per `principalName` and refreshes tokens automatically when they expire.
- Exposes tools that use the stored access token (and refresh when needed) to call the Jira and Confluence REST APIs:
  - Jira: `getIssue(issueId)`, `createIssue(projectKey, summary, issueType, description)`, `updateIssueSummary(issueKey, newSummary)`.
  - Confluence: `searchConfluencePages(cql)`, `getConfluencePageContent(pageId)`, `getConfluenceSpaces()`, `createConfluencePage(spaceId, title, content)`.
- Integrates with Spring AI MCP Server so these methods can be registered and invoked by an LLM agent over SSE.

Configuration / Environment variables
- The sample configuration is in `src/main/resources/application.yaml`. Values shown there are examples; override in your environment or an external config for production.

Important environment variables used by the project (examples):
- `ATLASSIAN_CLIENT_ID` — Atlassian OAuth app client id
- `ATLASSIAN_CLIENT_SECRET` — Atlassian OAuth app client secret
- `ATLASSIAN_CALLBACK_URL` (or `ATLASSIAN_REDIRECT_URI`) — OAuth redirect/callback URL (must match Atlassian app settings, e.g. `http://localhost:8080/auth/atlassian/callback`)
- `JASYPT_ENCRYPTOR_PASSWORD` or `jasypt.encryptor.password` — password used to encrypt stored tokens (do not keep in source)
- DB overrides: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`

Local development — setup & run
1. Export required env vars (zsh example):

```bash
export ATLASSIAN_CLIENT_ID=your_atlassian_client_id
export ATLASSIAN_CLIENT_SECRET=your_atlassian_client_secret
export ATLASSIAN_CALLBACK_URL=http://localhost:8080/auth/atlassian/callback
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
- Home page: `http://localhost:8080/` — click "Connect to Atlassian" (or similar) to start OAuth.
- After consent, you'll be shown a connection token (`principalName`) on the success page.

Using the connection token with an agent
- Copy the connection token from the OAuth success page into your agent configuration (for example `examples/claude_desktop_config.json`) so the agent can call this server on behalf of the principal.

Endpoints & Tools of interest
- OAuth start: `GET /auth/atlassian` (redirects to Atlassian authorization endpoint)
- OAuth callback: `GET /auth/atlassian/callback` — handles the token exchange and saves tokens
- The LLM tools are exposed via the `AtlassianService` bean and registered with the MCP server (no separate REST endpoints for those; agents call the MCP SSE endpoint and invoke the tools).

Confluence integration (added)
- The project now integrates with Confluence Cloud (via the Atlassian Cloud APIs). Implemented capabilities (see `AtlassianService`):
  - Search pages using CQL (`searchConfluencePages`).
  - Fetch a page's content in storage format (`getConfluencePageContent`) and produce a text summary.
  - List spaces (`getConfluenceSpaces`).
  - Create pages (`createConfluencePage`).
- Notes:
  - Confluence uses separate scopes; ensure your OAuth app requests appropriate Confluence scopes (for example `read:confluence-content.summary`, `read:confluence-content.all`, `search:confluence`, and write scopes if you create pages).
  - If you see 401 responses with a message like `{"code":401,"message":"Unauthorized; scope does not match"}`, verify the scopes returned by Atlassian during the token exchange (the app logs them) and make sure the app has those scopes configured in the Atlassian Developer console.
  - The code uses encoded URIs for Confluence page requests and captures the raw HTTP response (status, headers, and body) for easier debugging; check logs when debugging 401 errors.

Database
- The app persists `JiraToken` entities (encrypted) in the configured database.
- Entity: `src/main/java/com/mcp/jira/modals/AtlassianToken.java`
- Quick dev Postgres example:

```bash
docker run --name jira-pg -e POSTGRES_USER=myuser -e POSTGRES_PASSWORD=mysecretpassword -e POSTGRES_DB=jiratokensdb -p 5432:5432 -d postgres:15
```


Troubleshooting & tips
- Ensure the callback URL exactly matches the URL configured in your Atlassian app (scheme, host, port, and path).
- Ensure the OAuth app includes `offline_access` (for refresh tokens) and the Confluence scopes you need.
- To inspect the exact HTTP reply from Atlassian when troubleshooting (status, headers, body), the project logs the raw response for Confluence page fetches; enable logging and review the app console output.

License
- This repository includes a minimal MIT license in `LICENSE`.

Contributing
- Open PRs or issues. Consider adding tests around token refresh and the `AtlassianService` tool methods.

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
- You must still provide Atlassian credentials. Set `ATLASSIAN_CLIENT_ID`, `ATLASSIAN_CLIENT_SECRET`, and `ATLASSIAN_CALLBACK_URL` either in a local `.env` file or export them in your shell before starting the app service.

Example `.env` (create in repo root and DO NOT commit):

```env
ATLASSIAN_CLIENT_ID=your_atlassian_client_id
ATLASSIAN_CLIENT_SECRET=your_atlassian_client_secret
ATLASSIAN_CALLBACK_URL=http://localhost:8080/auth/atlassian/callback
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
        "Authorization: Bearer <YOUR_ATLASSIAN_CONNECTION_TOKEN_HERE>"
      ]
    }
  }
}

```

Place the real `ATLASSIAN_CONNECTION_TOKEN` (the `principalName` shown on the OAuth success page) under `mcpServers.jira-server.args.Authorization`.

## Blog / Writeup

Blog post: https://medium.com/@ankit.rishu06/bridging-the-gap-building-an-enterprise-grade-jira-agent-with-java-spring-ai-and-the-model-817b7a611e27
