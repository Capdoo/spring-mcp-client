package com.rafaelnontol.mcpclient.model;

/** {@code params} de la request {@code initialize}. */
public record InitializeParams(String protocolVersion, ClientCapabilities capabilities, ClientInfo clientInfo) {
}
