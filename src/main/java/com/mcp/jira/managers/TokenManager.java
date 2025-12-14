package com.mcp.jira.managers;

import com.mcp.jira.modals.AtlassianToken;
import com.mcp.jira.repository.AtlassianTokenRepository;
import com.mcp.jira.service.AtlassianTokenService;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import java.time.Instant;
import java.util.Optional;

@Configuration
public class TokenManager {

    @Autowired
    private AtlassianTokenRepository atlassianTokenRepository;

    @Autowired
    private StringEncryptor stringEncryptor;

    @Autowired
    private AtlassianTokenService atlassianTokenService;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Cacheable(value = "access_tokens", key = "#principal")
    public String getToken(String principalName) {
        Optional<AtlassianToken> jiraToken =  atlassianTokenRepository.findById(principalName);

        String encryptedAccessToken = jiraToken.get().getEncryptedAccessToken();
        String encryptedRefreshToken = jiraToken.get().getEncryptedRefreshToken();
        Instant expiresAt = jiraToken.get().getExpiresAt();

        String refreshToken = stringEncryptor.decrypt(encryptedRefreshToken);

       if(expiresAt.isBefore(Instant.now())) {
           ClientRegistration jiraRegistration = clientRegistrationRepository.findByRegistrationId("atlassian");

           OAuth2AccessTokenResponse tokenResponse = atlassianTokenService.getRefreshedTokens(refreshToken, jiraRegistration);

           String newAccessToken = tokenResponse.getAccessToken().getTokenValue();
           String newRefreshToken = tokenResponse.getRefreshToken().getTokenValue();
           Instant newExpiresAt = tokenResponse.getAccessToken().getExpiresAt();

           String newEncryptedAccessToken = stringEncryptor.encrypt(newAccessToken);
           String newEncryptedRefreshToken = stringEncryptor.encrypt(newRefreshToken);

           AtlassianToken updatedAtlassianToken = new AtlassianToken(principalName, newEncryptedAccessToken, newEncryptedRefreshToken, newExpiresAt);
           atlassianTokenRepository.save(updatedAtlassianToken);

           return newAccessToken;
       } else {
           return stringEncryptor.decrypt(encryptedAccessToken);
       }

    }
}
