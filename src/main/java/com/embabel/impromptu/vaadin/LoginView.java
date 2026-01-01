package com.embabel.impromptu.vaadin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Login page with Google OAuth2 authentication.
 */
@Route("")
@PageTitle("Login - Impromptu")
@AnonymousAllowed
public class LoginView extends VerticalLayout {

    public LoginView() {
        // Load the font
        UI.getCurrent().getPage().addStyleSheet(
                "https://fonts.googleapis.com/css2?family=Crimson+Pro:wght@400;500;600;700&display=swap");

        setSizeFull();
        getElement().getStyle().set("min-height", "100vh");
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        getStyle()
                .set("background", "linear-gradient(135deg, #0a0a0f 0%, #1a1525 50%, #121218 100%)");

        // Container for content
        var container = new Div();
        container.getStyle()
                .set("text-align", "center")
                .set("max-width", "600px")
                .set("padding", "var(--lumo-space-xl)");

        // Concert hall image
        var image = new Image(
                "https://upload.wikimedia.org/wikipedia/commons/7/73/Sydney_Opera_House_concert_hall_October_2018.jpg",
                "Sydney Opera House Concert Hall"
        );
        image.setWidth("100%");
        image.getStyle()
                .set("border-radius", "16px")
                .set("box-shadow", "0 8px 32px rgba(0, 0, 0, 0.5)")
                .set("margin-bottom", "var(--lumo-space-l)");

        // Title
        var title = new H1("Impromptu");
        title.getStyle()
                .set("color", "#c9a227")
                .set("font-family", "'Crimson Pro', Georgia, serif")
                .set("margin", "var(--lumo-space-m) 0")
                .set("text-shadow", "0 0 30px rgba(201, 162, 39, 0.3)");

        // Subtitle
        var subtitle = new Paragraph("Classical Music Explorer");
        subtitle.getStyle()
                .set("color", "#f5f5f0")
                .set("font-family", "'Crimson Pro', Georgia, serif")
                .set("font-size", "1.25rem")
                .set("margin-bottom", "var(--lumo-space-l)");

        // Google login button - use setRouterIgnore to bypass Vaadin routing
        var loginLink = new Anchor("/oauth2/authorization/google", "Sign in with Google");
        loginLink.setRouterIgnore(true);
        loginLink.getStyle()
                .set("display", "inline-block")
                .set("padding", "var(--lumo-space-m) var(--lumo-space-xl)")
                .set("background", "linear-gradient(135deg, #c9a227 0%, #b8922a 100%)")
                .set("color", "#0a0a0f")
                .set("text-decoration", "none")
                .set("border-radius", "12px")
                .set("font-family", "'Crimson Pro', Georgia, serif")
                .set("font-weight", "600")
                .set("font-size", "1.1rem")
                .set("letter-spacing", "0.05em")
                .set("box-shadow", "0 4px 15px rgba(201, 162, 39, 0.4)")
                .set("transition", "all 0.3s ease");

        container.add(image, title, subtitle, loginLink);
        add(container);
    }
}
