package com.rafaelnontol.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Capacidades que el CLIENTE anuncia al servidor durante {@code initialize}.
 *
 * <p>Ojo: {@code roots} y {@code sampling} son capacidades del cliente (el servidor se las
 * pide al cliente), no del servidor. Aún no las implementamos, por eso siempre van a
 * {@code null} y, gracias a {@code NON_NULL}, se serializan como un objeto vacío: {@code {}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientCapabilities(JsonNode roots, JsonNode sampling) {

    public static ClientCapabilities none() {
        return new ClientCapabilities(null, null);
    }
}
