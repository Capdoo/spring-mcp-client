package com.rafaelnontol.mcpclient.model;

/**
 * Identidad del cliente, enviada como {@code clientInfo} en {@code initialize}.
 * En la especificación MCP este tipo se llama {@code Implementation}.
 */
public record ClientInfo(String name, String version) {
}
