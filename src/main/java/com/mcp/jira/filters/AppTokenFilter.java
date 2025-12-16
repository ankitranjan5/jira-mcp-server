package com.mcp.jira.filters;

import com.mcp.jira.repository.AtlassianTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AppTokenFilter extends OncePerRequestFilter {

    @Autowired
    private AtlassianTokenRepository atlassianTokenRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Check for the Header
        // Claude will send: "Authorization: Bearer <YOUR_UUID_TOKEN>"

        String authHeader = request.getHeader("Authorization");


        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String appToken = authHeader.substring(7); // Remove "Bearer "

            // 2. Validate the Token
            if (atlassianTokenRepository.existsById(appToken)) {

                // 3. Create the Authentication Object
                // We manually construct the "User" object.
                // The 'principal' is the appToken (the UUID).
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                appToken,
                                null,
                                AuthorityUtils.createAuthorityList("ROLE_USER") // Give them basic role
                        );

                // 4. Set the Context (Log them in)
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute("AUTHENTICATED_USER", appToken);
                System.out.println("MCP Filter: Authenticated request for User " + appToken);
            }
        }

        // 5. Continue the Chain
        // If we logged them in above, Spring Security downstream will see them as authenticated.
        // If not, Spring Security will treat them as anonymous and likely force OAuth login.
        filterChain.doFilter(request, response);
    }
}
