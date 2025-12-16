package com.mcp.jira.repository;

import com.mcp.jira.modals.AtlassianToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AtlassianTokenRepository extends JpaRepository<AtlassianToken, String> {

}
