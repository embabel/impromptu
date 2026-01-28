/*
 * Copyright 2024-2025 Embabel Pty Ltd.
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
package com.embabel.impromptu.data.openopus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * REST endpoint for loading Open Opus data into Neo4j.
 * <p>
 * Usage:
 * <pre>
 * # Load data (streams progress)
 * curl -X POST http://localhost:8888/api/openopus/load
 *
 * # Clear all data
 * curl -X DELETE http://localhost:8888/api/openopus
 * </pre>
 */
@RestController
@RequestMapping("/api/openopus")
public class OpenOpusController {

    private static final Logger logger = LoggerFactory.getLogger(OpenOpusController.class);

    private final OpenOpusService openOpusService;

    public OpenOpusController(OpenOpusService openOpusService) {
        this.openOpusService = openOpusService;
    }

    /**
     * Log to both server and stream to client.
     */
    private void log(PrintWriter writer, String message) {
        logger.info(message);
        writer.println(message);
        writer.flush();
    }

    @PostMapping(value = "/load", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody load() {
        return outputStream -> {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

            try {
                openOpusService.load(msg -> log(writer, msg));
                log(writer, "Done!");
            } catch (Exception e) {
                log(writer, "Error: " + e.getMessage());
                logger.error("OpenOpus load failed", e);
            }
        };
    }

    @DeleteMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody delete() {
        return outputStream -> {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

            try {
                openOpusService.delete(msg -> log(writer, msg));
            } catch (Exception e) {
                log(writer, "Error: " + e.getMessage());
                logger.error("OpenOpus delete failed", e);
            }
        };
    }
}
