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
        web.ignoring().requestMatchers("/api/openopus/**", "/api/documents/**", "/api/v1/**", "/api/resource/**");
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
