package com.embabel.impromptu.vaadin;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.Theme;

/**
 * Vaadin app shell configuration with Push enabled for async UI updates.
 * Using WebSocket transport for reliable real-time updates.
 * Custom dark theme with classical music aesthetics.
 */
@Push(value = PushMode.AUTOMATIC, transport = Transport.WEBSOCKET_XHR)
@Theme("impromptu")
public class AppShellConfig implements AppShellConfigurator {
}
