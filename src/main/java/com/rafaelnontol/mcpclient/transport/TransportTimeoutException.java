package com.rafaelnontol.mcpclient.transport;

/** No se recibió ningún mensaje dentro del tiempo de espera. La conexión sigue abierta. */
public class TransportTimeoutException extends TransportException {

    public TransportTimeoutException(String message) {
        super(message);
    }
}
