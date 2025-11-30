package com.mcp.jira.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class JiraTokenService {

    private final String clientId = System.getenv("JIRA_CLIENT_ID");
    private final String clientSecret = System.getenv("JIRA_CLIENT_SECRET");
    private final String redirectUri = System.getenv("JIRA_REDIRECT_URI");
    @Value("${spring.security.oauth2.client.provider.jira.token-uri}")
    private String tokenUri;
    Base64.Encoder encoder = Base64.getEncoder();


    private final String id = clientId + ":" + clientSecret;
    private String secretHash = encoder.encodeToString(id.getBytes());






    public OAuth2AccessTokenResponse exchangeCodeForToken(String code, ClientRegistration clientRegistration) {
        // Atlassian expects JSON body, so a Map is perfect.
        Map<String, String> payload = new HashMap<>();
        payload.put("grant_type", "authorization_code");
        payload.put("client_id", clientRegistration.getClientId());
        payload.put("client_secret", clientRegistration.getClientSecret());
        payload.put("code", code);
        payload.put("redirect_uri", clientRegistration.getRedirectUri());

        // 1. Use WebClient correctly
        Map responseMap = WebClient.create()
                .post()
                .uri(clientRegistration.getProviderDetails().getTokenUri())
                // Atlassian uses JSON, not form-urlencoded
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class) // Deserialize to Map first
                .block();

        // 2. Manually map to OAuth2AccessTokenResponse
        // This avoids the complex Jackson deserialization issues with the Spring class
        return OAuth2AccessTokenResponse.withToken((String) responseMap.get("access_token"))
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(Long.parseLong(String.valueOf(responseMap.get("expires_in"))))
//                .scopes((Set<String>) responseMap.get("scope")) // You might need to split string if space-delimited
                .refreshToken((String) responseMap.get("refresh_token"))
                .build();
    }


    public OAuth2AccessTokenResponse getRefreshedTokens(String refreshToken, ClientRegistration clientRegistration) {
        // Atlassian expects JSON body, so a Map is perfect.
        Map<String, String> payload = new HashMap<>();
        payload.put("grant_type", "refresh_token");
        payload.put("client_id", clientRegistration.getClientId());
        payload.put("client_secret", clientRegistration.getClientSecret());
        payload.put("refresh_token", refreshToken);

        // 1. Use WebClient correctly
        Map responseMap = WebClient.create()
                .post()
                .uri(clientRegistration.getProviderDetails().getTokenUri())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class) // Deserialize to Map first
                .block();

        // 2. Manually map to OAuth2AccessTokenResponse
        // This avoids the complex Jackson deserialization issues with the Spring class
        return OAuth2AccessTokenResponse.withToken((String) responseMap.get("access_token"))
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(Long.parseLong(String.valueOf(responseMap.get("expires_in"))))
//                .scopes((Set<String>) responseMap.get("scope")) // You might need to split string if space-delimited
                .refreshToken((String) responseMap.get("refresh_token"))
                .build();
    }

}
