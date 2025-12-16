package com.mcp.jira.controllers;

import com.mcp.jira.modals.AtlassianToken;
import com.mcp.jira.repository.AtlassianTokenRepository;
import com.mcp.jira.service.AtlassianTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Controller
public class AtlassianCallbackController {
    @Autowired
    private AtlassianTokenRepository atlassianTokenRepository;

    @Autowired
    private StringEncryptor stringEncryptor;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private AtlassianTokenService atlassianTokenService;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    @GetMapping("/")
    @ResponseBody
    public String home() {
        return """
            <html>
            <head><title>Atlassian MCP Server</title></head>
            <body style="font-family: sans-serif; padding: 50px; text-align: center;">
                <h1>Atlassian MCP Server Setup</h1>
                <p>To let Claude access Atlassian, you need to generate a connection token.</p>
                <a href="/auth/atlassian" style="background-color: #0052cc; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; font-size: 18px;">
                    Connect to Atlassian
                </a>
            </body>
            </html>
            """;
    }

    @GetMapping("/auth/atlassian")
    public void startJiraAuth(HttpServletResponse response) throws IOException {
        String authorizationUrl = "http://localhost:8080/oauth2/authorization/atlassian";
        response.sendRedirect(authorizationUrl);
    }

    @GetMapping("/auth/atlassian/callback")
    @ResponseBody
    public String atlassianCallback(@RequestParam String code,@RequestParam("state") String state, Authentication authentication, HttpServletRequest request,
                               HttpServletResponse response) {

        if (authentication == null) {
            String newUserId = UUID.randomUUID().toString();

            authentication = new UsernamePasswordAuthenticationToken(
                    newUserId,
                    null,
                    AuthorityUtils.createAuthorityList("ROLE_USER")
            );

            // Log them in
            SecurityContextHolder.getContext().setAuthentication(authentication);
            HttpSession session = request.getSession(true);
            session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
        }
        //Get name of Principal
        String principalName = authentication.getName();

        //Get access token
        ClientRegistration atlassianRegistration = clientRegistrationRepository.findByRegistrationId("atlassian");
        OAuth2AccessTokenResponse tokenResponse = atlassianTokenService.exchangeCodeForToken(code, atlassianRegistration);

        String accessToken = tokenResponse.getAccessToken().getTokenValue();
        String refreshToken = tokenResponse.getRefreshToken().getTokenValue();
        Instant expiresAt = tokenResponse.getAccessToken().getExpiresAt();

        String encryptedAccessToken = stringEncryptor.encrypt(accessToken);
        String encryptedRefreshToken = stringEncryptor.encrypt(refreshToken);

        AtlassianToken tokenEntity = new AtlassianToken(principalName, encryptedAccessToken, encryptedRefreshToken, expiresAt);
        atlassianTokenRepository.save(tokenEntity);

        OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(
                atlassianRegistration,
                principalName,
                tokenResponse.getAccessToken(),
                tokenResponse.getRefreshToken()
        );

        authorizedClientService.saveAuthorizedClient(authorizedClient, authentication);

        request.getSession().setAttribute("jiraConnectionToken", principalName);


        return """
            <html>
            <head><title>Connected!</title></head>
            <body style="font-family: sans-serif; padding: 50px; text-align: center;">
                <h1 style="color: green;">✅ Successfully Connected to Atlassian</h1>
                <p>Your connection token has been generated.</p>
                
                <div style="background: #f4f4f4; padding: 20px; border-radius: 8px; display: inline-block; margin: 20px 0;">
                    <strong>JIRA_CONNECTION_TOKEN</strong><br/>
                    <code style="font-size: 24px; color: #d63384;">%s</code>
                </div>
                
                <p>Copy the token above and paste it into your <code>claude_desktop_config.json</code> file.</p>
                <p>You can close this window.</p>
            </body>
            </html>
            """.formatted(principalName);
    }

}

