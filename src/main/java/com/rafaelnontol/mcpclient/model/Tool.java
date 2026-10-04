package com.rafaelnontol.mcpclient.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Una Tool expuesta por el servidor (elemento de la respuesta a {@code tools/list}).
 *
 * <p>{@code inputSchema} es un JSON Schema que describe los argumentos. Lo guardamos como
 * {@link JsonNode} porque su forma varía por Tool; en el futuro se lo pasaremos tal cual a un LLM.
 */
public record Tool(String name, String description, JsonNode inputSchema) {
}
