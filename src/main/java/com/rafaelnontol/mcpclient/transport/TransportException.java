package com.rafaelnontol.mcpclient.transport;

/** Fallo de comunicación: no se pudo conectar, el servidor se desconectó, error de E/S... */
public class TransportException extends RuntimeException {

    public TransportException(String message) {
        super(message);
    }

    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
