package com.embabel.impromptu.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Service for looking up geographic location from IP addresses.
 * Uses the free ip-api.com service (no API key required).
 */
@Service
public class GeoLocationService {

    private static final Logger logger = LoggerFactory.getLogger(GeoLocationService.class);
    private static final String IP_API_URL = "http://ip-api.com/json/";

    private final RestClient restClient = RestClient.create();

    /**
     * Look up location data for an IP address.
     *
     * @param ipAddress The IP address to look up (or null/empty for the caller's IP)
     * @return Location data, or null if lookup fails
     */
    public LocationData lookup(String ipAddress) {
        try {
            String url = IP_API_URL + (ipAddress != null ? ipAddress : "");

            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || !"success".equals(response.get("status"))) {
                logger.warn("IP geolocation failed for {}: {}",
                        ipAddress, response != null ? response.get("message") : "null response");
                return null;
            }

            var location = new LocationData(
                    (String) response.get("countryCode"),
                    (String) response.get("country"),
                    (String) response.get("regionName"),
                    (String) response.get("city"),
                    (String) response.get("timezone"),
                    response.get("lat") instanceof Number n ? n.doubleValue() : null,
                    response.get("lon") instanceof Number n ? n.doubleValue() : null
            );

            logger.info("Geolocated IP {} to {}, {} ({})",
                    ipAddress, location.city(), location.countryCode(), location.timezone());

            return location;

        } catch (Exception e) {
            logger.warn("Failed to geolocate IP {}: {}", ipAddress, e.getMessage());
            return null;
        }
    }

    /**
     * Location data from IP geolocation.
     */
    public record LocationData(
            String countryCode,
            String country,
            String region,
            String city,
            String timezone,
            Double latitude,
            Double longitude
    ) {}
}
