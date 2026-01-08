package com.embabel.impromptu.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for async request handling timeouts.
 */
@Configuration
public class AsyncConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 10 minutes for long-running operations like OpenOpus data load
        configurer.setDefaultTimeout(600_000);
    }
}
