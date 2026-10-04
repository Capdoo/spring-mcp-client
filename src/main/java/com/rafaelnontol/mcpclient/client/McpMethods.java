package com.rafaelnontol.mcpclient.client;

/** Nombres de métodos MCP (especificación 2025-06-18) usados por este cliente. */
public final class McpMethods {

    public static final String INITIALIZE = "initialize";
    public static final String NOTIFICATION_INITIALIZED = "notifications/initialized";
    public static final String PING = "ping";
    public static final String TOOLS_LIST = "tools/list";
    public static final String TOOLS_CALL = "tools/call";

    private McpMethods() {
    }
}
