package com.rafaelnontol.mcpclient.client;

import com.rafaelnontol.mcpclient.transport.Transport;
import com.rafaelnontol.mcpclient.transport.TransportException;
import com.rafaelnontol.mcpclient.transport.TransportTimeoutException;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Transport falso para tests: guarda lo que el cliente envía y devuelve respuestas guionizadas.
 * Si no queda ninguna respuesta encolada, {@link #receive} simula un timeout.
 */
class FakeTransport implements Transport {

    final List<String> sent = new ArrayList<>();
    private final Deque<Object> script = new ArrayDeque<>();
    private boolean connected;
    int closeCount;

    /** Encola un mensaje que el "servidor" enviará. */
    FakeTransport respond(String json) {
        script.add(json);
        return this;
    }

    /** Encola un fallo de conexión. */
    FakeTransport fail(TransportException exception) {
        script.add(exception);
        return this;
    }

    @Override
    public void connect() {
        connected = true;
    }

    @Override
    public void send(String message) {
        if (!connected) {
            throw new TransportException("Not connected");
        }
        sent.add(message);
    }

    @Override
    public String receive(Duration timeout) {
        Object next = script.poll();
        if (next == null) {
            throw new TransportTimeoutException("No scripted response");
        }
        if (next instanceof TransportException e) {
            throw e;
        }
        return (String) next;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void close() {
        connected = false;
        closeCount++;
    }
}
