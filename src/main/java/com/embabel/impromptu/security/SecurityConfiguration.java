/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.impromptu.security;

import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security configuration for Vaadin with Google OAuth2 authentication.
 */
@Configuration
@EnableWebSecurity
class SecurityConfiguration extends VaadinWebSecurity {

    private final LocationCapturingAuthenticationSuccessHandler successHandler;

    SecurityConfiguration(LocationCapturingAuthenticationSuccessHandler successHandler) {
        this.successHandler = successHandler;
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        // Allow unauthenticated access to APIs (DICE API uses its own API key auth)
        // Also allow theme CSS files to be loaded without auth
        web.ignoring().requestMatchers("/api/openopus/**", "/api/documents/**", "/api/v1/**", "/api/resource/**", "/api/themes/**", "/api/enhancers/**");
        super.configure(web);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Let Vaadin configure its defaults first
        super.configure(http);

        // Disable CSRF for voice APIs and Vaadin PUSH endpoint
        http.csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/tts/**", "/api/stt/**", "/VAADIN/push/**")
        );

        // Configure OAuth2 login - use our Vaadin login view as the login page
        http.oauth2Login(oauth2 -> oauth2
                .loginPage("/")
                .successHandler(successHandler)
                .permitAll()
        );

        // Configure logout
        http.logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                .permitAll()
        );

        // Prevent session fixation issues - migrateSession is safest for OAuth2
        http.sessionManagement(session -> session
                .sessionFixation().migrateSession()
        );

        // Note: /callback/spotify and /link/spotify are protected by default
        // since VaadinWebSecurity requires authentication for non-public routes
    }
}
