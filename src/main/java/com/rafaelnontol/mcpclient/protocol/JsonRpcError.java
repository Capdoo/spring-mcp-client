package com.rafaelnontol.mcpclient.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Objeto {@code error} de una respuesta JSON-RPC 2.0.
 *
 * <p>Los códigos de -32768 a -32000 están reservados por la especificación JSON-RPC.
 * MCP usa, por ejemplo, {@link #INVALID_PARAMS} cuando se invoca una Tool inexistente.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcError(int code, String message, JsonNode data) {

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    public JsonRpcError(int code, String message) {
        this(code, message, null);
    }
}
