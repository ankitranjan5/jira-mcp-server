package com.mcp.jira.filters;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FilterChainDebugger implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        System.out.println("\n========================================");
        System.out.println("REQUEST: " + method + " " + path);
        System.out.println("Thread: " + Thread.currentThread().getName());
        System.out.println("About to enter filter chain...");

        long start = System.currentTimeMillis();

        try {
            chain.doFilter(request, response);
            long duration = System.currentTimeMillis() - start;
            System.out.println("Filter chain completed in " + duration + "ms");
        } catch (Exception e) {
            System.err.println("ERROR in filter chain: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            System.out.println("========================================\n");
        }
    }
}