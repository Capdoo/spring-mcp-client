package com.rafaelnontol.mcpclient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code result} de {@code tools/call}.
 *
 * <ul>
 *   <li>{@code content}: lista de bloques de contenido. Cada bloque tiene un {@code type}
 *       ({@code text}, {@code image}, {@code audio}, {@code resource_link}, {@code resource}).
 *       Se guardan como {@link JsonNode} para no asumir ninguna estructura de negocio.</li>
 *   <li>{@code structuredContent}: JSON estructurado opcional (añadido en 2025-06-18).</li>
 *   <li>{@code isError}: {@code true} si la Tool se ejecutó pero falló. Esto NO es un error
 *       JSON-RPC: la request fue válida, el fallo es de la Tool en sí.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResult(
        List<JsonNode> content,
        JsonNode structuredContent,
        @JsonProperty("isError") boolean isError) {

    public ToolResult {
        content = content == null ? List.of() : List.copyOf(content);
    }

    /** Concatena el texto de todos los bloques de tipo {@code text}. */
    public String textContent() {
        return content.stream()
                .filter(block -> "text".equals(block.path("type").asText()))
                .map(block -> block.path("text").asText())
                .collect(Collectors.joining("\n"));
    }
}
