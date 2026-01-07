package com.embabel.impromptu.vaadin.components;

import com.embabel.impromptu.youtube.YouTubeService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal YouTube panel that opens videos in a new tab.
 * Shows current video info with a link to open on YouTube.
 * This avoids embedding restrictions and preserves YouTube Premium benefits.
 */
public class YouTubePlayerPanel extends HorizontalLayout {

    private static final Logger logger = LoggerFactory.getLogger(YouTubePlayerPanel.class);

    private final Image thumbnail;
    private final Span titleLabel;
    private final Span channelLabel;
    private final Button openButton;
    private final Anchor youtubeLink;

    private String currentVideoId;
    private String currentTitle;

    public YouTubePlayerPanel(YouTubeService youTubeService) {
        setWidthFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);
        setVisible(false); // Hidden until a video is loaded
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)");

        // YouTube brand
        var brandLabel = new Span("YouTube");
        brandLabel.getStyle()
                .set("font-weight", "bold")
                .set("color", "#FF0000")
                .set("min-width", "60px");

        // Thumbnail
        thumbnail = new Image();
        thumbnail.setWidth("80px");
        thumbnail.setHeight("45px");
        thumbnail.getStyle()
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("object-fit", "cover")
                .set("cursor", "pointer");
        thumbnail.addClickListener(e -> openInNewTab());
        thumbnail.setVisible(false);

        // Video info
        var infoLayout = new VerticalLayout();
        infoLayout.setPadding(false);
        infoLayout.setSpacing(false);

        titleLabel = new Span();
        titleLabel.getStyle()
                .set("font-weight", "500")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("white-space", "nowrap")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("max-width", "300px");

        channelLabel = new Span();
        channelLabel.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)");

        infoLayout.add(titleLabel, channelLabel);

        // Hidden anchor for link (we'll use button click instead)
        youtubeLink = new Anchor();
        youtubeLink.setTarget("_blank");
        youtubeLink.getStyle().set("display", "none");

        // Open button
        openButton = new Button("Watch", VaadinIcon.EXTERNAL_LINK.create());
        openButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        openButton.addClickListener(e -> openInNewTab());
        openButton.getStyle().set("margin-left", "auto");

        add(brandLabel, thumbnail, infoLayout, youtubeLink, openButton);
        setFlexGrow(1, infoLayout);
    }

    /**
     * Load a video - shows info and opens in new tab.
     */
    public void loadVideo(String videoId, String title, String channelTitle) {
        loadVideo(videoId, title, channelTitle, null);
    }

    /**
     * Load a video with optional thumbnail.
     */
    public void loadVideo(String videoId, String title, String channelTitle, String thumbnailUrl) {
        this.currentVideoId = videoId;
        this.currentTitle = title;

        titleLabel.setText(title);
        channelLabel.setText(channelTitle);

        String url = "https://www.youtube.com/watch?v=" + videoId;
        youtubeLink.setHref(url);

        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            thumbnail.setSrc(thumbnailUrl);
            thumbnail.setVisible(true);
        } else {
            // Use YouTube's default thumbnail
            thumbnail.setSrc("https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg");
            thumbnail.setVisible(true);
        }

        setVisible(true);

        // Auto-open in new tab
        openInNewTab();

        logger.info("YouTube video ready: {} - {}", videoId, title);
    }

    private void openInNewTab() {
        if (currentVideoId != null) {
            // Use regular watch URL (embed has restrictions)
            // User can click fullscreen on video for clean viewing
            String url = "https://www.youtube.com/watch?v=" + currentVideoId + "&autoplay=1";
            // Open as popup window sized to 60% of screen
            // Using a named window so subsequent videos reuse the same popup
            UI.getCurrent().getPage().executeJs("""
                    var w = Math.round(screen.width * 0.6);
                    var h = Math.round(screen.height * 0.6);
                    var left = Math.round((screen.width - w) / 2);
                    var top = Math.round((screen.height - h) / 2);
                    window.open($0, 'youtube_player',
                        'width=' + w + ',height=' + h + ',left=' + left + ',top=' + top +
                        ',menubar=no,toolbar=no,location=no,status=no,resizable=yes');
                    """,
                    url
            );
        }
    }

    public String getCurrentVideoId() {
        return currentVideoId;
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    public boolean hasVideo() {
        return currentVideoId != null;
    }
}
