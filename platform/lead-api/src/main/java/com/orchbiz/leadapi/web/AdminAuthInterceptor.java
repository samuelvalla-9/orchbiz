package com.orchbiz.leadapi.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.orchbiz.leadapi.config.OrchbizProperties;
import com.orchbiz.leadapi.lead.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Protects /api/admin/** with a Bearer token from ORCHBIZ_ADMIN_TOKEN (constant-time comparison). */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final OrchbizProperties properties;

    public AdminAuthInterceptor(OrchbizProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.adminEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "admin_disabled");
        }
        String header = request.getHeader("Authorization");
        String presented = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : "";
        boolean ok = MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                properties.adminToken().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }
        return true;
    }
}
