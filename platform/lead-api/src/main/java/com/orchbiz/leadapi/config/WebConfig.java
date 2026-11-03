package com.orchbiz.leadapi.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.orchbiz.leadapi.web.AdminAuthInterceptor;
import com.orchbiz.leadapi.web.RateLimitInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final OrchbizProperties properties;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(OrchbizProperties properties, AdminAuthInterceptor adminAuthInterceptor,
                     RateLimitInterceptor rateLimitInterceptor) {
        this.properties = properties;
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = properties.cors().originList();
        if (!origins.isEmpty()) {
            registry.addMapping("/api/leads")
                    .allowedOrigins(origins.toArray(String[]::new))
                    .allowedMethods("POST")
                    .allowedHeaders("Content-Type")
                    .maxAge(3600);
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/leads");
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/api/admin/**");
    }
}
