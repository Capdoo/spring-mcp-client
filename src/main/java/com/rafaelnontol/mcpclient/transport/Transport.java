package com.rafaelnontol.mcpclient.transport;

import java.time.Duration;

/**
 * Canal de comunicación con un MCP Server.
 *
 * <p>Un Transport solo mueve mensajes: recibe y entrega strings que contienen UN mensaje
 * JSON-RPC cada uno. No sabe nada de JSON-RPC, de MCP ni de Tools. Gracias a eso
 * {@code McpClient} funciona igual sobre STDIO, HTTP o un transporte falso en los tests.
 */
public interface Transport extends AutoCloseable {

    /** Abre la conexión (en STDIO: arranca el proceso del servidor). */
    void connect();

    /** Envía un mensaje completo. */
    void send(String message);

    /**
     * Espera el siguiente mensaje del servidor.
     *
     * @throws TransportTimeoutException si no llega nada dentro de {@code timeout}
     * @throws TransportException        si la conexión se cerró o falló
     */
    String receive(Duration timeout);

    boolean isConnected();

    /** Cierra la conexión. Debe ser idempotente. */
    @Override
    void close();
}
