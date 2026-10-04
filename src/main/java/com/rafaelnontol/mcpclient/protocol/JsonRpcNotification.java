package com.rafaelnontol.mcpclient.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Notificación JSON-RPC 2.0: un mensaje sin {@code id} que no recibe respuesta.
 *
 * <p>En esta etapa el cliente envía una sola: {@code notifications/initialized}, que cierra
 * el handshake de inicialización de MCP.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcNotification(String jsonrpc, String method, Object params) implements JsonRpcMessage {

    public JsonRpcNotification(String method, Object params) {
        this(VERSION, method, params);
    }
}
