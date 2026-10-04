package com.rafaelnontol.mcpclient.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Convierte mensajes JSON-RPC entre objetos Java y texto JSON (usando Jackson).
 *
 * <p>Es el único punto donde el cliente toca JSON "en crudo". El Transport mueve strings
 * y McpClient trabaja con objetos; este codec es el puente entre ambos.
 */
public class JsonRpcCodec {

    private final ObjectMapper mapper;

    public JsonRpcCodec() {
        this(defaultObjectMapper());
    }

    public JsonRpcCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * <ul>
     *   <li>Ignora propiedades desconocidas: versiones nuevas del protocolo añaden campos
     *       y no queremos fallar por ello.</li>
     *   <li>Deserializa los enteros sin tipo como {@code Long}, así un {@code id} numérico
     *       siempre se compara de forma consistente ({@code 1L} vs {@code 1}).</li>
     * </ul>
     */
    public static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.USE_LONG_FOR_INTS);
    }

    public String encode(JsonRpcMessage message) {
        try {
            // Jackson escapa los saltos de línea dentro de strings, así que el resultado
            // siempre ocupa una sola línea (requisito del transporte STDIO de MCP).
            return mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize JSON-RPC message", e);
        }
    }

    /**
     * Interpreta un mensaje recibido y decide de qué tipo es mirando sus campos:
     * <ul>
     *   <li>{@code method} + {@code id} → request (el servidor nos pide algo, p.ej. ping)</li>
     *   <li>{@code method} sin {@code id} → notificación</li>
     *   <li>{@code result} o {@code error} → respuesta a una de nuestras requests</li>
     * </ul>
     */
    public JsonRpcMessage decode(String json) {
        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new JsonRpcParseException("Message is not valid JSON", e);
        }
        if (node == null || !node.isObject()) {
            throw new JsonRpcParseException("JSON-RPC message must be a JSON object");
        }
        if (!JsonRpcMessage.VERSION.equals(node.path("jsonrpc").asText())) {
            throw new JsonRpcParseException("Missing or unsupported 'jsonrpc' version (expected \"2.0\")");
        }

        Class<? extends JsonRpcMessage> type;
        if (node.has("method")) {
            type = node.has("id") ? JsonRpcRequest.class : JsonRpcNotification.class;
        } else if (node.has("result") || node.has("error")) {
            type = JsonRpcResponse.class;
        } else {
            throw new JsonRpcParseException("Cannot determine JSON-RPC message type (no 'method', 'result' or 'error')");
        }
        return convert(node, type);
    }

    /** Convierte un fragmento JSON (por ejemplo el {@code result} de una respuesta) a un modelo. */
    public <T> T convert(JsonNode node, Class<T> type) {
        try {
            return mapper.treeToValue(node, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new JsonRpcParseException("Could not map JSON to " + type.getSimpleName() + ": " + originalMessage(e), e);
        }
    }

    private static String originalMessage(Exception e) {
        return e instanceof JsonProcessingException jpe ? jpe.getOriginalMessage() : e.getMessage();
    }
}
