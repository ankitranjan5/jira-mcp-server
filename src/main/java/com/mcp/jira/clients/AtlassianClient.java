package com.mcp.jira.clients; // New package

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.jira.managers.TokenManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AtlassianClient {

    @Autowired
    private TokenManager tokenManager;

    private final WebClient webClient = WebClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache for Cloud IDs
    private final Map<String, String> cloudIdCache = new ConcurrentHashMap<>();

    /**
     * Get the Access Token for the current security context.
     */
    public String getAccessToken() {
        String principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        // Ideally add @Cacheable inside TokenManager, not here
        return tokenManager.getToken(principal);
    }

    /**
     * CloudID Fetcher with Caching
     */
    public String getCloudId(String accessToken) {
        // 1. Check Cache
        if (cloudIdCache.containsKey(accessToken)) {
            return cloudIdCache.get(accessToken);
        }

        // 2. Fetch from API
        try {
            String accessibleJson = webClient.get()
                    .uri("https://api.atlassian.com/oauth/token/accessible-resources")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode arr = objectMapper.readTree(accessibleJson);
            if (arr.isEmpty()) {
                throw new RuntimeException("No accessible JIRA resources found.");
            }

            String cloudId = arr.get(0).get("id").asText();

            // 3. Update Cache
            cloudIdCache.put(accessToken, cloudId);
            return cloudId;

        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve Cloud ID: " + e.getMessage());
        }
    }

}