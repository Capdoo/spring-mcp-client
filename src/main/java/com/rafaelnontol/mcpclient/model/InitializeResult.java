package com.rafaelnontol.mcpclient.model;

/**
 * {@code result} de la request {@code initialize}.
 *
 * @param protocolVersion versión del protocolo que el servidor acepta para esta sesión
 * @param capabilities    capacidades del servidor
 * @param serverInfo      nombre y versión del servidor
 * @param instructions    texto opcional con indicaciones de uso (pensado para un futuro LLM)
 */
public record InitializeResult(
        String protocolVersion,
        ServerCapabilities capabilities,
        ServerInfo serverInfo,
        String instructions) {
}
