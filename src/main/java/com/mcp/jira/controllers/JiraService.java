package com.mcp.jira.controllers;

import com.mcp.jira.managers.TokenManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
public class JiraService {

    @Autowired
    TokenManager tokenManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Helper method to get the Cloud ID for the current user.
     * This avoids repeating logic in every tool.
     */
    private String getCloudId(String accessToken) throws JsonProcessingException {
        String accessibleJson = WebClient.create()
                .get()
                .uri("https://api.atlassian.com/oauth/token/accessible-resources")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode arr = objectMapper.readTree(accessibleJson);
        if (arr.isEmpty()) {
            throw new RuntimeException("No accessible JIRA resources found for this user.");
        }
        return arr.get(0).get("id").asText();
    }

    private String getUriInstance(String accessToken) throws JsonProcessingException {
        String accessibleJson = WebClient.create()
                .get()
                .uri("https://api.atlassian.com/oauth/token/accessible-resources")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode arr = objectMapper.readTree(accessibleJson);
        if (arr.isEmpty()) {
            throw new RuntimeException("No accessible JIRA resources found for this user.");
        }
        return arr.get(0).get("url").asText();
    }

    /**
     * Helper to get the current authenticated user's access token.
     */
    private String getAccessToken() {
        String principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        return tokenManager.getToken(principal);
    }

    // ==================================================================================
    // TOOL 1: GET ISSUE (Filtered Response)
    // ==================================================================================
    @Tool(description = "Get Jira issue details by issue ID (e.g., PROJ-123)")
    public String getIssue(String issueId) {
        try {
            String accessToken = getAccessToken();
            String cloudId = getCloudId(accessToken);

            String responseJson = WebClient.create()
                    .get()
                    .uri("https://api.atlassian.com/ex/jira/" + cloudId + "/rest/api/3/issue/" + issueId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // --- Filter Logic: Extract only what Claude needs ---
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode fields = root.path("fields");

            String summary = fields.path("summary").asText("No Summary");
            String status = fields.path("status").path("name").asText("Unknown");
            String description = fields.path("description").path("content").findPath("text").asText("No description");
            String assignee = fields.path("assignee").path("displayName").asText("Unassigned");
            String priority = fields.path("priority").path("name").asText("None");

            // Return a clean, markdown-formatted string
            return String.format("""
                **Issue:** %s
                **Summary:** %s
                **Status:** %s
                **Priority:** %s
                **Assignee:** %s
                **Description:** %s
                """, root.path("key").asText(), summary, status, priority, assignee, description);

        } catch (Exception e) {
            return "Error fetching issue: " + e.getMessage();
        }
    }

    // ==================================================================================
    // TOOL 2: CREATE ISSUE
    // ==================================================================================
    @Tool(description = "Create a new Jira issue. Requires project key, summary, and issue type (e.g., Task, Bug).")
    public String createIssue(String projectKey, String summary, String issueType, String description) {
        try {
            String accessToken = getAccessToken();
            String cloudId = getCloudId(accessToken);

            // Construct the JSON payload for JIRA
            // Atlassian Document Format (ADF) is complex, using a simple string for description here for brevity
            // Note: Real production apps might need full ADF construction for description.
            Map<String, Object> fields = new HashMap<>();
            fields.put("project", Map.of("key", projectKey));
            fields.put("summary", summary);
            fields.put("issuetype", Map.of("name", issueType));

            // Simple description object
            // For rich text, JIRA requires the specific "doc" structure.
            // Keeping it simple (null) or just summary for MVP to avoid ADF parsing errors.
            // fields.put("description", description);

            Map<String, Object> payload = Map.of("fields", fields);

            String response = WebClient.create()
                    .post()
                    .uri("https://api.atlassian.com/ex/jira/" + cloudId + "/rest/api/2/issue")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return "Successfully created issue: " + root.path("key").asText() + " (ID: " + root.path("id").asText() + ")";

        } catch (Exception e) {
            return "Error creating issue: " + e.getMessage();
        }
    }

    // ==================================================================================
    // TOOL 3: UPDATE ISSUE
    // ==================================================================================
    @Tool(description = "Update an existing Jira issue summary.")
    public String updateIssueSummary(String issueKey, String newSummary) {
        try {
            String accessToken = getAccessToken();
            String cloudId = getCloudId(accessToken);

            // Construct update payload
            Map<String, Object> fields = Map.of("summary", newSummary);
            Map<String, Object> payload = Map.of("fields", fields);

            WebClient.create()
                    .put()
                    .uri("https://api.atlassian.com/ex/jira/" + cloudId + "/rest/api/3/issue/" + issueKey)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity() // PUT usually returns 204 No Content
                    .block();

            return "Successfully updated summary for issue: " + issueKey;

        } catch (Exception e) {
            return "Error updating issue: " + e.getMessage();
        }
    }

}