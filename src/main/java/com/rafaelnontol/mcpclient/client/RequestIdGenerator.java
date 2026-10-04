package com.rafaelnontol.mcpclient.client;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Genera ids JSON-RPC correlativos: 1, 2, 3...
 *
 * <p>El id sirve para emparejar cada respuesta con su request. MCP exige que un id no se
 * reutilice dentro de una sesión; un contador creciente lo garantiza de forma trivial.
 */
public class RequestIdGenerator {

    private final AtomicLong next = new AtomicLong(1);

    public long nextId() {
        return next.getAndIncrement();
    }
}
