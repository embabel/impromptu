package com.embabel.impromptu;

import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security configuration for Vaadin with Google OAuth2 authentication.
 */
@Configuration
@EnableWebSecurity
class SecurityConfiguration extends VaadinWebSecurity {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Let Vaadin configure its defaults first
        super.configure(http);

        // Configure OAuth2 login - use our Vaadin login view as the login page
        http.oauth2Login(oauth2 -> oauth2
                .loginPage("/")
                .successHandler((request, response, authentication) -> {
                    response.sendRedirect("/chat");
                })
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
    }
}
