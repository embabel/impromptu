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
