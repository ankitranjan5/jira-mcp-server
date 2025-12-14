package com.mcp.jira.modals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

public class AtlassianUtils {

    private static final ObjectMapper mapper = new ObjectMapper();


    public record ConfluencePageSummary(String pageId, String title, String type, String url, String spaceId) {

    }

    public record JiraIssueSummary(String key, String summary, String status, String description) {}

    public List<JiraIssueSummary> parseJiraResponse(String jsonBody, String cloudId) {
        List<JiraIssueSummary> summaries = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        try {
            JsonNode root = mapper.readTree(jsonBody);
            JsonNode issues = root.path("issues");

            if (issues.isArray()) {
                for (JsonNode issue : issues) {
                    String key = issue.path("id").asText();
                    String summary = issue.path("fields").path("summary").asText();
                    String status = issue.path("fields").path("status").path("name").asText();
                    String htmlDescription = issue.path("renderedFields").path("description").asText();

                    if (htmlDescription.isEmpty() || htmlDescription.equals("null")) {
                        htmlDescription = "No description provided.";
                    }
                    summaries.add(new JiraIssueSummary(key, summary, status, htmlDescription));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return summaries;
    }



    public static List<ConfluencePageSummary> cleanResponse(String jsonBody) {
        List<ConfluencePageSummary> cleanList = new ArrayList<>();

        try {
            JsonNode root = mapper.readTree(jsonBody);

            // 1. Extract the Base URL (e.g., "https://your-domain.atlassian.net/wiki")
            // We need this to build clickable links.
            String baseUrl = root.path("_links").path("base").asText();

            // 2. Iterate through the "results" array
            JsonNode results = root.path("results");
            if (results.isArray()) {
                for (JsonNode node : results) {

                    String pageId = node.path("id").asText();
                    String title = node.path("title").asText();
                    String type = node.path("type").asText();
                    String spaceId = node.path("space").path("id").asText();

                    // 3. Construct the Full URL
                    // API gives relative path: "/spaces/..."
                    String relativePath = node.path("_links").path("webui").asText();
                    String fullUrl = baseUrl + relativePath;

                    // 4. Add to list
                    cleanList.add(new ConfluencePageSummary(pageId, title, type, fullUrl, spaceId));
                }
            }

        } catch (Exception e) {
            System.err.println("Error parsing Confluence JSON: " + e.getMessage());
        }

        return cleanList;
    }


    public String getPageContentForSummary(String rawHtmlBody) {

        // 1. Fetch the raw HTML body (using the method we built previously)
        // Ensure you use '?expand=body.storage' in the API call
        if (rawHtmlBody == null || rawHtmlBody.isEmpty()) {
            return "Error: Page content not found or empty.";
        }

        // 2. Clean the HTML using Jsoup
        // This converts <h1>Title</h1><p>Text</p> into "Title Text"
        // while preserving structural line breaks.
        String cleanText = convertHtmlToReadableText(rawHtmlBody);

        // 3. (Optional) Truncate if too large for context window
        // e.g., limit to first 10,000 characters
        if (cleanText.length() > 20000) {
            cleanText = cleanText.substring(0, 20000) + "\n...[Content Truncated]...";
        }

        return cleanText;
    }

    private String convertHtmlToReadableText(String html) {
        // Parse the HTML
        Document doc = Jsoup.parse(html);

        // Optional: formatting tweaks to make it "Markdown-ish" for the LLM
        // Convert <br> and <p> to newlines before stripping tags
        doc.select("br").append("\\n");
        doc.select("p").prepend("\\n\\n");
        doc.select("h1, h2, h3, h4, h5, h6").prepend("\\n\\n# ");
        doc.select("li").prepend("\\n- ");

        // Extract text
        return doc.text().trim();
    }

    private String getCloudId(String accessToken) throws JsonProcessingException {
        String accessibleJson = WebClient.create()
                .get()
                .uri("https://api.atlassian.com/oauth/token/accessible-resources")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode arr = mapper.readTree(accessibleJson);
        if (arr.isEmpty()) {
            throw new RuntimeException("No accessible JIRA resources found for this user.");
        }
        return arr.get(0).get("id").asText();
    }
}
