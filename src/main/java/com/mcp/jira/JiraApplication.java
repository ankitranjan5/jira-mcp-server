package com.mcp.jira;

import com.mcp.jira.controllers.JiraService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class JiraApplication {

	public static void main(String[] args) {
		SpringApplication.run(JiraApplication.class, args);
	}

	@Bean
	public ToolCallbackProvider JiraTools(JiraService jiraService){

		ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
				.toolObjects(jiraService)
				.build();

		return provider;
	}
}
