package com.embabel.impromptu.javelit;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

/**
 * Shell commands for the Javelit web-based chat UI.
 */
@ShellComponent
public record JavelitShell(JavelitChatUI javelitChatUI) {

    @ShellMethod(value = "Launch web-based chat UI", key = "uichat")
    public String uichat() {
        if (javelitChatUI.isRunning()) {
            return "Chat UI is already running. Use 'uichat-stop' to stop it first.";
        }

        String url = javelitChatUI.start();
        return "Chat UI started at " + url + "\nOpen this URL in your browser to chat.";
    }

    @ShellMethod(value = "Stop the web-based chat UI", key = "uichat-stop")
    public String uichatStop() {
        if (!javelitChatUI.isRunning()) {
            return "Chat UI is not running.";
        }

        javelitChatUI.stop();
        return "Chat UI stopped.";
    }

    @ShellMethod(value = "Check if web-based chat UI is running", key = "uichat-status")
    public String uichatStatus() {
        if (javelitChatUI.isRunning()) {
            return "Chat UI is running.";
        } else {
            return "Chat UI is not running. Use 'uichat' to start it.";
        }
    }
}
