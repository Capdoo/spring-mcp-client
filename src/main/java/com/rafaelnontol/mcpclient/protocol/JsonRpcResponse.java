package com.rafaelnontol.mcpclient.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Respuesta JSON-RPC 2.0. Contiene {@code result} (éxito) o {@code error} (fallo), nunca ambos.
 *
 * <pre>
 * { "jsonrpc": "2.0", "id": 1, "result": { ... } }
 * { "jsonrpc": "2.0", "id": 1, "error": { "code": -32602, "message": "Unknown tool" } }
 * </pre>
 *
 * <p>{@code result} se mantiene como {@link JsonNode} porque su forma depende del método
 * invocado; {@code McpClient} lo convierte después al modelo MCP correspondiente.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse(String jsonrpc, Object id, JsonNode result, JsonRpcError error) implements JsonRpcMessage {

    public static JsonRpcResponse success(Object id, JsonNode result) {
        return new JsonRpcResponse(VERSION, id, result != null ? result : JsonNodeFactory.instance.objectNode(), null);
    }

    public static JsonRpcResponse failure(Object id, JsonRpcError error) {
        return new JsonRpcResponse(VERSION, id, null, error);
    }

    public boolean hasError() {
        return error != null;
    }
}
