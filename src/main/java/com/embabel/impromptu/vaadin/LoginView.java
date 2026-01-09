package com.embabel.impromptu.vaadin;

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
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        addClassName("login-view");

        // Container for content
        var container = new Div();
        container.addClassName("login-container");

        // Concert hall image
        var image = new Image(
                "https://upload.wikimedia.org/wikipedia/commons/7/73/Sydney_Opera_House_concert_hall_October_2018.jpg",
                "Sydney Opera House Concert Hall"
        );
        image.addClassName("login-image");

        // Title - elegant musical typography
        var title = new H1("Impromptu");
        title.addClassName("login-title");

        // Subtitle
        var subtitle = new Paragraph("Classical Music Explorer");
        subtitle.addClassName("login-subtitle");

        // Google login button - use setRouterIgnore to bypass Vaadin routing
        var loginLink = new Anchor("/oauth2/authorization/google", "Sign in with Google");
        loginLink.setRouterIgnore(true);
        loginLink.addClassName("login-button");

        container.add(image, title, subtitle, loginLink);
        add(container);
    }
}
