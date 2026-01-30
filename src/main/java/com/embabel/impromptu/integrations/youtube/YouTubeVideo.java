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
package com.embabel.impromptu.integrations.youtube;

import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.impromptu.domain.performance.Playable;

/**
 * A YouTube video that can be persisted as a NamedEntity.
 * Represents a single playable video (typically the entire performance for YouTube).
 */
public interface YouTubeVideo extends NamedEntity, Playable {

    /**
     * YouTube video ID.
     */
    String getVideoId();

    /**
     * Video title.
     */
    String getTitle();

    /**
     * Channel that uploaded the video.
     */
    String getChannelTitle();

    /**
     * Video description.
     */
    String getVideoDescription();

    /**
     * URL to thumbnail image.
     */
    String getThumbnailUrl();

    /**
     * Duration in seconds. Returns long because Neo4j stores integers as Long.
     */
    long getDurationSeconds();

    @Override
    default String title() {
        return getTitle();
    }

    @Override
    default int durationSeconds() {
        return (int) getDurationSeconds();
    }

    @Override
    default String url() {
        return "https://www.youtube.com/watch?v=" + getVideoId();
    }

    @Override
    default String source() {
        return "youtube";
    }

    @Override
    default String playbackInfo() {
        return """
                {"source":"youtube","videoId":"%s","title":"%s","channelTitle":"%s","url":"%s","durationSeconds":%d}
                """.formatted(getVideoId(), getTitle(), getChannelTitle(), url(), durationSeconds()).trim();
    }
}
