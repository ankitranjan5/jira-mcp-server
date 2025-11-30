package com.mcp.jira.managers;

import com.mcp.jira.modals.JiraToken;
import com.mcp.jira.repository.JiraTokenRepository;
import com.mcp.jira.service.JiraTokenService;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import java.time.Instant;
import java.util.Optional;

@Configuration
public class TokenManager {

    @Autowired
    private JiraTokenRepository jiraTokenRepository;

    @Autowired
    private StringEncryptor stringEncryptor;

    @Autowired
    private JiraTokenService jiraTokenService;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    public String getToken(String principalName) {
        Optional<JiraToken> jiraToken =  jiraTokenRepository.findById(principalName);

        String encryptedAccessToken = jiraToken.get().getEncryptedAccessToken();
        String encryptedRefreshToken = jiraToken.get().getEncryptedRefreshToken();
        Instant expiresAt = jiraToken.get().getExpiresAt();

        String refreshToken = stringEncryptor.decrypt(encryptedRefreshToken);

       if(expiresAt.isBefore(Instant.now())) {
           ClientRegistration jiraRegistration = clientRegistrationRepository.findByRegistrationId("jira");

           OAuth2AccessTokenResponse tokenResponse = jiraTokenService.getRefreshedTokens(refreshToken, jiraRegistration);

           String newAccessToken = tokenResponse.getAccessToken().getTokenValue();
           String newRefreshToken = tokenResponse.getRefreshToken().getTokenValue();
           Instant newExpiresAt = tokenResponse.getAccessToken().getExpiresAt();

           String newEncryptedAccessToken = stringEncryptor.encrypt(newAccessToken);
           String newEncryptedRefreshToken = stringEncryptor.encrypt(newRefreshToken);

           JiraToken updatedJiraToken = new JiraToken(principalName, newEncryptedAccessToken, newEncryptedRefreshToken, newExpiresAt);
           jiraTokenRepository.save(updatedJiraToken);

           return newAccessToken;
       } else {
           return stringEncryptor.decrypt(encryptedAccessToken);
       }

    }
}
