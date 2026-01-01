package com.embabel.impromptu.vaadin;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;

/**
 * Vaadin app shell configuration with Push enabled for async UI updates.
 * Using LONG_POLLING transport to avoid WebSocket configuration issues.
 */
@Push(value = PushMode.AUTOMATIC, transport = Transport.LONG_POLLING)
public class AppShellConfig implements AppShellConfigurator {
}
