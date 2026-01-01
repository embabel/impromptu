package com.embabel.impromptu.vaadin;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.Theme;

/**
 * Vaadin app shell configuration with Push enabled for async UI updates.
 * Using LONG_POLLING transport to avoid WebSocket configuration issues.
 * Custom dark theme with classical music aesthetics.
 */
@Push(value = PushMode.AUTOMATIC, transport = Transport.LONG_POLLING)
@Theme("impromptu")
public class AppShellConfig implements AppShellConfigurator {
}
