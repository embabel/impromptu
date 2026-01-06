package com.embabel.impromptu.security;

import com.embabel.impromptu.user.GeoLocationService;
import com.embabel.impromptu.user.ImpromptuUserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Captures user's location from IP address on successful OAuth login.
 */
@Component
public class LocationCapturingAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(LocationCapturingAuthenticationSuccessHandler.class);

    private final ImpromptuUserService userService;
    private final GeoLocationService geoLocationService;

    public LocationCapturingAuthenticationSuccessHandler(
            ImpromptuUserService userService,
            GeoLocationService geoLocationService) {
        this.userService = userService;
        this.geoLocationService = geoLocationService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        // Get user's IP address
        String ipAddress = getClientIpAddress(request);
        logger.info("User logged in from IP: {}", ipAddress);

        // Get user now (security context won't be available in async thread)
        var user = userService.getAuthenticatedUser();
        if (user == null || "Anonymous".equals(user.getDisplayName())) {
            response.sendRedirect("/chat");
            return;
        }

        // Only look up location if not already set
        if (user.getTimezone() == null) {
            // Look up location asynchronously to not block the redirect
            new Thread(() -> {
                try {
                    // Skip localhost/private IPs - use empty string to get server's public IP
                    String lookupIp = isPrivateOrLocalIp(ipAddress) ? "" : ipAddress;

                    var location = geoLocationService.lookup(lookupIp);
                    if (location != null) {
                        user.setCountryCode(location.countryCode());
                        user.setCity(location.city());
                        user.setTimezone(location.timezone());
                        user.setLatitude(location.latitude());
                        user.setLongitude(location.longitude());
                        userService.save(user);
                        logger.info("Updated location for user {}: {}, {} ({})",
                                user.getDisplayName(), location.city(), location.countryCode(), location.timezone());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to update user location: {}", e.getMessage());
                }
            }).start();
        }

        // Redirect to chat
        response.sendRedirect("/chat");
    }

    private static final String[] PRIVATE_IP_PREFIXES = {
            "127.", "0:0:0:0:0:0:0:1", "::1",
            "10.", "192.168.",
            "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.",
            "172.24.", "172.25.", "172.26.", "172.27.",
            "172.28.", "172.29.", "172.30.", "172.31."
    };

    private boolean isPrivateOrLocalIp(String ip) {
        if (ip == null) return true;
        for (String prefix : PRIVATE_IP_PREFIXES) {
            if (ip.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Get the client's real IP address, handling proxies and load balancers.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // Check common proxy headers
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_CLIENT_IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs, take the first one
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }
}
