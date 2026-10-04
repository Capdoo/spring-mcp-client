package com.rafaelnontol.mcpclient.client;

import com.rafaelnontol.mcpclient.protocol.JsonRpcError;

/**
 * Única excepción que expone {@link McpClient}.
 *
 * <p>Si el servidor respondió con un error JSON-RPC, {@link #getJsonRpcError()} lo contiene;
 * si el problema fue de conexión, timeout o respuesta inválida, es {@code null} y la causa
 * original queda en {@link #getCause()}.
 */
public class McpException extends RuntimeException {

    private final JsonRpcError jsonRpcError;

    public McpException(String message) {
        this(message, null, null);
    }

    public McpException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public McpException(String message, JsonRpcError jsonRpcError) {
        this(message, jsonRpcError, null);
    }

    private McpException(String message, JsonRpcError jsonRpcError, Throwable cause) {
        super(message, cause);
        this.jsonRpcError = jsonRpcError;
    }

    public JsonRpcError getJsonRpcError() {
        return jsonRpcError;
    }
}
