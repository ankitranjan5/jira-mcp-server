package com.mcp.jira.repository;

import com.mcp.jira.modals.JiraToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JiraTokenRepository extends JpaRepository<JiraToken, String> {

}
