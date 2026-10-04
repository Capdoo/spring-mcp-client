package com.rafaelnontol.mcpclient.protocol;

/**
 * El texto recibido no es un mensaje JSON-RPC 2.0 válido, o su contenido no encaja
 * con el modelo esperado.
 */
public class JsonRpcParseException extends RuntimeException {

    public JsonRpcParseException(String message) {
        super(message);
    }

    public JsonRpcParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
