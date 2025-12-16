package com.mcp.jira.modals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

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

            String baseUrl = root.path("_links").path("base").asText();

            JsonNode results = root.path("results");
            if (results.isArray()) {
                for (JsonNode node : results) {

                    String pageId = node.path("id").asText();
                    String title = node.path("title").asText();
                    String type = node.path("type").asText();
                    String spaceId = node.path("space").path("id").asText();


                    String relativePath = node.path("_links").path("webui").asText();
                    String fullUrl = baseUrl + relativePath;


                    cleanList.add(new ConfluencePageSummary(pageId, title, type, fullUrl, spaceId));
                }
            }

        } catch (Exception e) {
            System.err.println("Error parsing Confluence JSON: " + e.getMessage());
        }

        return cleanList;
    }


    public String getPageContentForSummary(String rawHtmlBody) {

        // use '?expand=body.storage' in the API call
        if (rawHtmlBody == null || rawHtmlBody.isEmpty()) {
            return "Error: Page content not found or empty.";
        }

        // 2. Clean the HTML using Jsoup
        String cleanText = convertHtmlToReadableText(rawHtmlBody);

        if (cleanText.length() > 20000) {
            cleanText = cleanText.substring(0, 20000) + "\n...[Content Truncated]...";
        }

        return cleanText;
    }

    private String convertHtmlToReadableText(String html) {
        // Parse the HTML
        Document doc = Jsoup.parse(html);


        doc.select("br").append("\\n");
        doc.select("p").prepend("\\n\\n");
        doc.select("h1, h2, h3, h4, h5, h6").prepend("\\n\\n# ");
        doc.select("li").prepend("\\n- ");

        return doc.text().trim();
    }

    public String parseSpaces(String jsonBody) {
        try {
            JsonNode results = mapper.readTree(jsonBody).path("results");
            StringBuilder output = new StringBuilder();
            output.append(String.format("%-15s | %-10s | %s%n", "Space ID", "Key", "Name"));

            if (results.isArray()) {
                for (JsonNode space : results) {
                    output.append(String.format("%-15s | %-10s | %s%n",
                            space.path("id").asText(),
                            space.path("key").asText(),
                            space.path("name").asText()));
                }
            }
            return output.toString();
        } catch (Exception e) {
            return "Error parsing spaces.";
        }
    }
}
