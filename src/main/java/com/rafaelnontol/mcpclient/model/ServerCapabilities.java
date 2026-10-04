package com.rafaelnontol.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Capacidades que el SERVIDOR anuncia en la respuesta a {@code initialize}.
 *
 * <p>Que una capacidad esté presente (aunque sea un objeto vacío) significa que el servidor
 * la soporta. Por ahora solo modelamos en detalle {@code tools}; el resto se guarda como
 * {@link JsonNode} para poder evolucionar sin romper nada.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerCapabilities(
        ToolsCapability tools,
        JsonNode resources,
        JsonNode prompts,
        JsonNode logging) {

    /** {@code listChanged}: el servidor avisará con una notificación si cambia su lista de Tools. */
    public record ToolsCapability(Boolean listChanged) {
    }

    public boolean supportsTools() {
        return tools != null;
    }

    /** Nombres de las capacidades anunciadas, útil para mostrarlas por consola. */
    public List<String> advertised() {
        List<String> names = new ArrayList<>();
        if (tools != null) names.add("tools");
        if (resources != null) names.add("resources");
        if (prompts != null) names.add("prompts");
        if (logging != null) names.add("logging");
        return names;
    }
}
