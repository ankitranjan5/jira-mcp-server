package com.mcp.jira.config;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Hooks;

@Configuration
public class ReactorConfiguration {

    @PostConstruct
    public void setup() {
        // 1. Tell Reactor to look for ThreadLocal values (like Trace IDs)
        Hooks.enableAutomaticContextPropagation();

        ContextRegistry.getInstance().registerThreadLocalAccessor(new ThreadLocalAccessor<SecurityContext>() {
            @Override
            public Object key() {
                return SecurityContext.class; // Unique key
            }

            @Override
            public SecurityContext getValue() {
                return SecurityContextHolder.getContext(); // CAPTURE: Take from current thread
            }

            @Override
            public void setValue(SecurityContext value) {
                SecurityContextHolder.setContext(value); // RESTORE: Put onto new thread
            }

            @Override
            public void reset() {
                SecurityContextHolder.clearContext(); // CLEANUP: Don't leak to next user
            }
        });
    }
}