package com.rafaelnontol.mcpclient.protocol;

/**
 * Cualquier mensaje JSON-RPC 2.0.
 *
 * <p>JSON-RPC define exactamente tres tipos de mensaje, y MCP usa los tres:
 * <ul>
 *   <li>{@link JsonRpcRequest}: tiene {@code id} y {@code method}; espera una respuesta.</li>
 *   <li>{@link JsonRpcNotification}: tiene {@code method} pero NO {@code id}; no espera respuesta.</li>
 *   <li>{@link JsonRpcResponse}: tiene {@code id} y {@code result} o {@code error}.</li>
 * </ul>
 *
 * <p>Es una interfaz {@code sealed}, de modo que un {@code switch} sobre un mensaje recibido
 * obliga al compilador a verificar que tratamos los tres casos.
 */
public sealed interface JsonRpcMessage permits JsonRpcRequest, JsonRpcNotification, JsonRpcResponse {

    String VERSION = "2.0";

    String jsonrpc();
}
