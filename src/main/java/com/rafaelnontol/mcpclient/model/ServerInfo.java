package com.rafaelnontol.mcpclient.model;

/**
 * Identidad del servidor, recibida como {@code serverInfo} en la respuesta a {@code initialize}.
 * En la especificación MCP este tipo se llama {@code Implementation}.
 */
public record ServerInfo(String name, String version) {
}
