package com.mcp.jira.clients;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

import java.io.IOException;

public class OAuth2ClientInterceptor implements ClientHttpRequestInterceptor {

    private final OAuth2AuthorizedClientManager manager;
    private final ClientRegistration clientRegistration;
    private final String principalName; // E.g., "user-123" or "system-service"

    public OAuth2ClientInterceptor(OAuth2AuthorizedClientManager manager,
                                   ClientRegistration clientRegistration,
                                   String principalName) {
        this.manager = manager;
        this.clientRegistration = clientRegistration;
        this.principalName = principalName;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {

        // 1. Build an authorization request
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(clientRegistration.getRegistrationId())
                .principal(principalName)
                .build();

        // 2. Ask the manager for a valid token (handles refresh automatically)
        OAuth2AuthorizedClient authorizedClient = manager.authorize(authorizeRequest);

        // 3. Inject the token into the Authorization header
        if (authorizedClient != null) {
            String token = authorizedClient.getAccessToken().getTokenValue();
            request.getHeaders().setBearerAuth(token);
        }

        return execution.execute(request, body);
    }
}
