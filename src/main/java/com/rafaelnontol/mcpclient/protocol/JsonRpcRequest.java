package com.rafaelnontol.mcpclient.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request JSON-RPC 2.0.
 *
 * <pre>
 * { "jsonrpc": "2.0", "id": 1, "method": "initialize", "params": { ... } }
 * </pre>
 *
 * <p>{@code id} es {@code Object} porque JSON-RPC permite números o strings. Este cliente
 * siempre genera ids numéricos ({@code Long}), pero el servidor también puede enviarnos
 * requests (por ejemplo {@code ping}) con ids de tipo string.
 *
 * <p>{@code params} es opcional: si es {@code null} no se serializa (ver {@code tools/list}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcRequest(String jsonrpc, Object id, String method, Object params) implements JsonRpcMessage {

    public JsonRpcRequest(Object id, String method, Object params) {
        this(VERSION, id, method, params);
    }
}
