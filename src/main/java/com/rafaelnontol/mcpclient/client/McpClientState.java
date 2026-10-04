package com.rafaelnontol.mcpclient.client;

/**
 * Estados del lifecycle del cliente.
 *
 * <pre>
 *   DISCONNECTED ──connect()──▶ CONNECTED ──initialize()──▶ READY
 *        ▲                                                    │
 *        └──────────────── close() / error de conexión ───────┘
 * </pre>
 *
 * Según la especificación, antes de completar {@code initialize} el cliente no debe enviar
 * otras requests (salvo {@code ping}); por eso {@code tools/*} exige el estado {@link #READY}.
 */
public enum McpClientState {
    DISCONNECTED,
    CONNECTED,
    READY
}
