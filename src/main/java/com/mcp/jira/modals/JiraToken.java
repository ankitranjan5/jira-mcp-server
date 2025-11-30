package com.mcp.jira.modals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import java.time.Instant;

@Entity
public class JiraToken {

    @Id
    private String principalName; // The user's unique name (from Google/GitHub)

    @Lob
    @Column(columnDefinition = "TEXT")
    private String encryptedAccessToken;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    private Instant expiresAt;

    // Constructors, Getters, and Setters
    public JiraToken() {}

    public JiraToken(String principalName, String encryptedAccessToken, String encryptedRefreshToken, Instant expiresAt) {
        this.principalName = principalName;
        this.encryptedAccessToken = encryptedAccessToken;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.expiresAt = expiresAt;
    }

    // --- Getters and Setters ---
    public String getPrincipalName() { return principalName; }
    public void setPrincipalName(String principalName) { this.principalName = principalName; }
    public String getEncryptedAccessToken() { return encryptedAccessToken; }
    public void setEncryptedAccessToken(String encryptedAccessToken) { this.encryptedAccessToken = encryptedAccessToken; }
    public String getEncryptedRefreshToken() { return encryptedRefreshToken; }
    public void setEncryptedRefreshToken(String encryptedRefreshToken) { this.encryptedRefreshToken = encryptedRefreshToken; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}