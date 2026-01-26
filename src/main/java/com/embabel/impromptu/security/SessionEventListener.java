package com.embabel.impromptu.security;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs session creation and destruction events to help diagnose session issues.
 */
@Component
public class SessionEventListener implements HttpSessionListener {

    private static final Logger logger = LoggerFactory.getLogger(SessionEventListener.class);

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        logger.info("HttpSession created: id={}, maxInactiveInterval={}s",
                event.getSession().getId(),
                event.getSession().getMaxInactiveInterval());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        logger.warn("HttpSession destroyed: id={}", event.getSession().getId());
        if (logger.isDebugEnabled()) {
            logger.debug("Session destruction stack trace:", new Exception("Stack trace"));
        }
    }
}
