package com.rafaelnontol.mcpclient.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaelnontol.mcpclient.model.ClientCapabilities;
import com.rafaelnontol.mcpclient.model.ClientInfo;
import com.rafaelnontol.mcpclient.model.InitializeParams;
import com.rafaelnontol.mcpclient.model.InitializeResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonRpcCodecTest {

    private final JsonRpcCodec codec = new JsonRpcCodec();
    private final ObjectMapper mapper = new ObjectMapper();

    // ------------------------------------------------------------------ serialización

    @Test
    void serializesRequestWithParams() throws Exception {
        var params = new InitializeParams("2025-06-18", ClientCapabilities.none(), new ClientInfo("basic-mcp-client", "1.0.0"));

        String json = codec.encode(new JsonRpcRequest(1L, "initialize", params));

        assertThat(mapper.readTree(json)).isEqualTo(mapper.readTree("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-06-18",
                  "capabilities":{},
                  "clientInfo":{"name":"basic-mcp-client","version":"1.0.0"}}}"""));
    }

    @Test
    void omitsParamsWhenNull() throws Exception {
        String json = codec.encode(new JsonRpcRequest(2L, "tools/list", null));

        assertThat(mapper.readTree(json)).isEqualTo(mapper.readTree("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}"""));
    }

    @Test
    void serializedMessageIsASingleLine() {
        String json = codec.encode(new JsonRpcRequest(1L, "tools/call",
                java.util.Map.of("name", "echo", "arguments", java.util.Map.of("text", "line1\nline2"))));

        assertThat(json).doesNotContain("\n");
    }

    @Test
    void serializesNotificationWithoutId() throws Exception {
        JsonNode node = mapper.readTree(codec.encode(new JsonRpcNotification("notifications/initialized", null)));

        assertThat(node.has("id")).isFalse();
        assertThat(node.path("method").asText()).isEqualTo("notifications/initialized");
    }

    // ------------------------------------------------------------------ deserialización

    @Test
    void decodesSuccessResponse() {
        JsonRpcMessage message = codec.decode("""
                {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18",
                 "capabilities":{"tools":{}},"serverInfo":{"name":"srv","version":"1.0"},"extraField":true}}""");

        assertThat(message).isInstanceOfSatisfying(JsonRpcResponse.class, response -> {
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.hasError()).isFalse();
            InitializeResult result = codec.convert(response.result(), InitializeResult.class);
            assertThat(result.serverInfo().name()).isEqualTo("srv");
            assertThat(result.capabilities().supportsTools()).isTrue();
        });
    }

    @Test
    void decodesErrorResponse() {
        JsonRpcMessage message = codec.decode("""
                {"jsonrpc":"2.0","id":3,"error":{"code":-32602,"message":"Unknown tool: x","data":{"tool":"x"}}}""");

        assertThat(message).isInstanceOfSatisfying(JsonRpcResponse.class, response -> {
            assertThat(response.hasError()).isTrue();
            assertThat(response.result()).isNull();
            assertThat(response.error().code()).isEqualTo(JsonRpcError.INVALID_PARAMS);
            assertThat(response.error().message()).isEqualTo("Unknown tool: x");
            assertThat(response.error().data().path("tool").asText()).isEqualTo("x");
        });
    }

    @Test
    void decodesNotificationAndServerRequest() {
        assertThat(codec.decode("""
                {"jsonrpc":"2.0","method":"notifications/tools/list_changed"}"""))
                .isInstanceOf(JsonRpcNotification.class);

        assertThat(codec.decode("""
                {"jsonrpc":"2.0","id":"abc","method":"ping"}"""))
                .isInstanceOfSatisfying(JsonRpcRequest.class, request -> assertThat(request.id()).isEqualTo("abc"));
    }

    @Test
    void rejectsInvalidMessages() {
        assertThatThrownBy(() -> codec.decode("not json")).isInstanceOf(JsonRpcParseException.class);
        assertThatThrownBy(() -> codec.decode("[1,2]")).isInstanceOf(JsonRpcParseException.class);
        assertThatThrownBy(() -> codec.decode("""
                {"id":1,"result":{}}""")).hasMessageContaining("jsonrpc");
        assertThatThrownBy(() -> codec.decode("""
                {"jsonrpc":"2.0","id":1}""")).hasMessageContaining("message type");
    }

    @Test
    void serializesErrorResponseRoundTrip() {
        String json = codec.encode(JsonRpcResponse.failure("srv-1", new JsonRpcError(JsonRpcError.METHOD_NOT_FOUND, "nope")));

        JsonRpcResponse decoded = (JsonRpcResponse) codec.decode(json);
        assertThat(decoded.id()).isEqualTo("srv-1");
        assertThat(decoded.error().code()).isEqualTo(-32601);
        assertThat(json).doesNotContain("\"result\"").doesNotContain("\"data\"");
    }
}
