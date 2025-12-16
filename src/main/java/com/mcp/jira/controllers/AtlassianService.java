package com.mcp.jira.controllers;

import com.mcp.jira.clients.AtlassianClient;
import com.mcp.jira.managers.TokenManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.jira.modals.AtlassianUtils;
import io.micrometer.observation.annotation.Observed;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AtlassianService {

    @Autowired
    AtlassianClient atlassianClient;

    AtlassianUtils atlassianUtils = new AtlassianUtils();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.create(); // Reuse WebClient instance


    // --- JIRA TOOLS ---

    @Observed(name = "tool.jira.issue", contextualName = "search-issue-jira")
    @Tool(description = "Get Jira issue details by issue ID (e.g., PROJ-123)")
    public String getIssue(@RequestParam String issueId) {
        try {
            String accessToken = atlassianClient.getAccessToken();
            String cloudId = atlassianClient.getCloudId(accessToken);

            String responseJson = webClient.get()
                    .uri("https://api.atlassian.com/ex/jira/" + cloudId + "/rest/api/3/issue/" + issueId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode fields = root.path("fields");

            return String.format("""
                **Issue:** %s
                **Summary:** %s
                **Status:** %s
                **Priority:** %s
                **Assignee:** %s
                **Description:** %s
                """,
                    root.path("key").asText(),
                    fields.path("summary").asText("No Summary"),
                    fields.path("status").path("name").asText("Unknown"),
                    fields.path("priority").path("name").asText("None"),
                    fields.path("assignee").path("displayName").asText("Unassigned"),
                    fields.path("description").path("content").findPath("text").asText("No description"));

        } catch (Exception e) {
            return "Error fetching issue: " + e.getMessage();
        }
    }

    @Observed(name = "tool.jira.jql", contextualName = "searching-jira")
    @Tool(description = "Search for Jira issues using JQL.")
    public List<AtlassianUtils.JiraIssueSummary> searchJiraIssues(@RequestParam String jql) {
        try {
            String accessToken = atlassianClient.getAccessToken();
            String cloudId = atlassianClient.getCloudId(accessToken);

            String responseJson = webClient.get()
                    .uri(uriBuilder -> uriBuilder.scheme("https")
                            .host("api.atlassian.com")
                            .path("/ex/jira/" + cloudId + "/rest/api/3/search/jql")
                            .queryParam("jql", jql)
                            .queryParam("fields", "summary,status,description")
                            .queryParam("expand", "renderedFields")
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return atlassianUtils.parseJiraResponse(responseJson, cloudId);

        } catch (Exception e) {
            System.err.println("Error searching Jira: " + e.getMessage());
            return List.of();
        }
    }

    @Observed(name = "tool.jira.create", contextualName = "create-issue-jira")
    @Tool(description = "Create a new Jira issue.")
    public String createIssue(String projectKey, String summary, String issueType, String description) {
        try {
            String accessToken = atlassianClient.getAccessToken();
            String cloudId = atlassianClient.getCloudId(accessToken);

            Map<String, Object> fields = new HashMap<>();
            fields.put("project", Map.of("key", projectKey));
            fields.put("summary", summary);
            fields.put("issuetype", Map.of("name", issueType));
            if (description != null && !description.isEmpty()) {
                fields.put("description", description);
            }

            String response = webClient.post()
                    .uri("https://api.atlassian.com/ex/jira/" + cloudId + "/rest/api/2/issue")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("fields", fields))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return "Successfully created issue: " + root.path("key").asText();

        } catch (Exception e) {
            return "Error creating issue: " + e.getMessage();
        }
    }

    @Observed(name = "tool.jira.update", contextualName = "update-jira")
    @Tool(description = "Update an existing Jira issue summary.")
    public String updateIssueSummary(String issueKey, String newSummary) {
        try {
            String accessToken = atlassianClient.getAccessToken();
            String cloudId = atlassianClient.getCloudId(accessToken);

            webClient.put()
                    .uri("https://api.atlassian.com/ex/jira/" + cloudId + "/rest/api/3/issue/" + issueKey)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("fields", Map.of("summary", newSummary)))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            return "Successfully updated summary for issue: " + issueKey;

        } catch (Exception e) {
            return "Error updating issue: " + e.getMessage();
        }
    }

    // --- CONFLUENCE TOOLS ---

    @Observed(name = "tool.confluence.cql", contextualName = "searching-confluence")
    @Tool(description = "Search Confluence pages using CQL.")
    public List<AtlassianUtils.ConfluencePageSummary> searchConfluencePages(@RequestParam String cql) throws Exception {
        String accessToken = atlassianClient.getAccessToken();
        String cloudId = atlassianClient.getCloudId(accessToken);

        String responseJson = webClient.get()
                .uri("https://api.atlassian.com/ex/confluence/" + cloudId + "/wiki/rest/api/content/search?cql=" + cql + "&expand=space")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return AtlassianUtils.cleanResponse(responseJson);
    }

    @Observed(name = "tool.confluence.page", contextualName = "search-page-confluence")
    @Tool(description = "Get Confluence page content by page ID.")
    public String getConfluencePageContent(@RequestParam String pageId) {
        try {
            String accessToken = atlassianClient.getAccessToken();
            String cloudId = atlassianClient.getCloudId(accessToken);

            String responseJson = webClient.get()
                    .uri("https://api.atlassian.com/ex/confluence/" + cloudId + "/wiki/api/v2/pages/" + pageId + "?body-format=storage")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseJson);
            String rawHtmlBody = root.path("body").path("storage").path("value").asText();
            return atlassianUtils.getPageContentForSummary(rawHtmlBody);

        } catch (Exception e) {
            return "Error fetching page: " + e.getMessage();
        }
    }

    @Observed(name = "tool.confluence.spaces", contextualName = "search-spaces-confluence")
    @Tool(description = "Lists all available Confluence Spaces.")
    public String getConfluenceSpaces() {
        try {
            String accessToken = atlassianClient.getAccessToken();
            String cloudId = atlassianClient.getCloudId(accessToken);

            String responseJson = webClient.get()
                    .uri("https://api.atlassian.com/ex/confluence/" + cloudId + "/wiki/api/v2/spaces?limit=50")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return atlassianUtils.parseSpaces(responseJson);

        } catch (Exception e) {
            return "Error fetching spaces: " + e.getMessage();
        }
    }



    @Observed(name = "tool.confluence.create.page", contextualName = "create-confluence-page")
    @Tool(description = "Create a new Confluence page.")
    public String createConfluencePage(
            @RequestParam String spaceId,
            @RequestParam String title,
            @RequestBody String content) {
        try {
            String accessToken = atlassianClient.getAccessToken();
            String cloudId = atlassianClient.getCloudId(accessToken);

            Map<String, Object> bodyMap = Map.of("representation", "storage", "value", content);
            Map<String, Object> payload = new HashMap<>();
            payload.put("spaceId", spaceId);
            payload.put("status", "current");
            payload.put("title", title);
            payload.put("body", bodyMap);

            String responseJson = webClient.post()
                    .uri("https://api.atlassian.com/ex/confluence/" + cloudId + "/wiki/api/v2/pages")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseJson);
            String webUi = root.path("_links").path("webui").asText();
            String base = root.path("_links").path("base").asText();
            return "Page Created: " + base + webUi;

        } catch (Exception e) {
            return "Error creating page: " + e.getMessage();
        }
    }
}