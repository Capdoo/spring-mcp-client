package com.rafaelnontol.mcpclient.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaelnontol.mcpclient.model.ClientInfo;
import com.rafaelnontol.mcpclient.model.InitializeResult;
import com.rafaelnontol.mcpclient.model.Tool;
import com.rafaelnontol.mcpclient.model.ToolResult;
import com.rafaelnontol.mcpclient.protocol.JsonRpcCodec;
import com.rafaelnontol.mcpclient.protocol.JsonRpcError;
import com.rafaelnontol.mcpclient.transport.TransportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultMcpClientTest {

    private static final String INITIALIZE_RESPONSE = """
            {"jsonrpc":"2.0","id":1,"result":{
              "protocolVersion":"2025-06-18",
              "capabilities":{"tools":{"listChanged":false}},
              "serverInfo":{"name":"basic-user-mcp-server","version":"0.1.0"}}}""";

    private static final String TOOLS_LIST_RESPONSE = """
            {"jsonrpc":"2.0","id":2,"result":{"tools":[{
              "name":"getUser",
              "description":"Obtiene un usuario por ID",
              "inputSchema":{"type":"object","properties":{"id":{"type":"integer"}},"required":["id"]}}]}}""";

    private final ObjectMapper mapper = new ObjectMapper();
    private FakeTransport transport;
    private DefaultMcpClient client;

    @BeforeEach
    void setUp() {
        transport = new FakeTransport();
        client = new DefaultMcpClient(transport, new JsonRpcCodec(), new RequestIdGenerator(),
                new ClientInfo("basic-mcp-client", "1.0.0"), Duration.ofSeconds(1));
    }

    // ------------------------------------------------------------------ initialize

    @Test
    void initializeSendsSpecCompliantRequestFollowedByInitializedNotification() throws Exception {
        transport.respond(INITIALIZE_RESPONSE);
        client.connect();

        client.initialize();

        assertThat(transport.sent).hasSize(2);
        JsonNode request = sent(0);
        assertThat(request.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(request.path("id").asLong()).isEqualTo(1);
        assertThat(request.path("method").asText()).isEqualTo("initialize");
        assertThat(request.path("params").path("protocolVersion").asText()).isEqualTo("2025-06-18");
        assertThat(request.path("params").path("capabilities").isObject()).isTrue();
        assertThat(request.path("params").path("capabilities").isEmpty()).isTrue();
        assertThat(request.path("params").path("clientInfo").path("name").asText()).isEqualTo("basic-mcp-client");
        assertThat(request.path("params").path("clientInfo").path("version").asText()).isEqualTo("1.0.0");

        JsonNode notification = sent(1);
        assertThat(notification.path("method").asText()).isEqualTo("notifications/initialized");
        assertThat(notification.has("id")).as("notifications have no id").isFalse();
    }

    @Test
    void initializeStoresServerInformation() {
        transport.respond(INITIALIZE_RESPONSE);
        client.connect();

        InitializeResult result = client.initialize();

        assertThat(client.getState()).isEqualTo(McpClientState.READY);
        assertThat(result.serverInfo().name()).isEqualTo("basic-user-mcp-server");
        assertThat(client.getServerInfo().version()).isEqualTo("0.1.0");
        assertThat(client.getProtocolVersion()).isEqualTo("2025-06-18");
        assertThat(client.getServerCapabilities().supportsTools()).isTrue();
        assertThat(client.getServerCapabilities().advertised()).containsExactly("tools");
    }

    @Test
    void initializeRequiresConnect() {
        assertThatThrownBy(() -> client.initialize())
                .isInstanceOf(McpException.class)
                .hasMessageContaining("connect()");
    }

    @Test
    void unsupportedProtocolVersionDisconnects() {
        transport.respond("""
                {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"1999-01-01",
                 "capabilities":{},"serverInfo":{"name":"old","version":"1"}}}""");
        client.connect();

        assertThatThrownBy(() -> client.initialize())
                .isInstanceOf(McpException.class)
                .hasMessageContaining("unsupported protocol version");
        assertThat(client.getState()).isEqualTo(McpClientState.DISCONNECTED);
        assertThat(transport.closeCount).isEqualTo(1);
    }

    // ------------------------------------------------------------------ tools/list

    @Test
    void listToolsSendsRequestWithoutParamsAndParsesTools() throws Exception {
        initialized();
        transport.respond(TOOLS_LIST_RESPONSE);

        List<Tool> tools = client.listTools();

        JsonNode request = sent(2);
        assertThat(request.path("id").asLong()).isEqualTo(2);
        assertThat(request.path("method").asText()).isEqualTo("tools/list");
        assertThat(request.has("params")).isFalse();

        assertThat(tools).singleElement().satisfies(tool -> {
            assertThat(tool.name()).isEqualTo("getUser");
            assertThat(tool.description()).isEqualTo("Obtiene un usuario por ID");
            assertThat(tool.inputSchema().path("required").get(0).asText()).isEqualTo("id");
        });
    }

    @Test
    void listToolsFollowsPaginationCursor() throws Exception {
        initialized();
        transport.respond("""
                {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"a","inputSchema":{}}],"nextCursor":"page-2"}}""");
        transport.respond("""
                {"jsonrpc":"2.0","id":3,"result":{"tools":[{"name":"b","inputSchema":{}}]}}""");

        List<Tool> tools = client.listTools();

        assertThat(tools).extracting(Tool::name).containsExactly("a", "b");
        assertThat(sent(3).path("params").path("cursor").asText()).isEqualTo("page-2");
    }

    @Test
    void listToolsRequiresInitialize() {
        client.connect();

        assertThatThrownBy(() -> client.listTools())
                .isInstanceOf(McpException.class)
                .hasMessageContaining("initialize()");
    }

    @Test
    void listToolsFailsWhenServerDoesNotAdvertiseTools() {
        transport.respond("""
                {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18",
                 "capabilities":{"resources":{}},"serverInfo":{"name":"no-tools","version":"1"}}}""");
        client.connect();
        client.initialize();

        assertThatThrownBy(() -> client.listTools())
                .isInstanceOf(McpException.class)
                .hasMessageContaining("'tools' capability");
    }

    // ------------------------------------------------------------------ tools/call

    @Test
    void callToolSendsNameAndArguments() throws Exception {
        initialized();
        transport.respond("""
                {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"User 10: Ada"}],"isError":false}}""");

        ToolResult result = client.callTool("getUser", Map.of("id", 10));

        JsonNode request = sent(2);
        assertThat(request.path("id").asLong()).isEqualTo(2);
        assertThat(request.path("method").asText()).isEqualTo("tools/call");
        assertThat(request.path("params").path("name").asText()).isEqualTo("getUser");
        assertThat(request.path("params").path("arguments").path("id").asInt()).isEqualTo(10);

        assertThat(result.isError()).isFalse();
        assertThat(result.textContent()).isEqualTo("User 10: Ada");
    }

    @Test
    void toolExecutionErrorIsReturnedAsResultNotException() {
        initialized();
        transport.respond("""
                {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"User 99 not found"}],"isError":true}}""");

        ToolResult result = client.callTool("getUser", Map.of("id", 99));

        assertThat(result.isError()).isTrue();
        assertThat(result.textContent()).isEqualTo("User 99 not found");
    }

    @Test
    void jsonRpcErrorIsThrownAsMcpException() {
        initialized();
        transport.respond("""
                {"jsonrpc":"2.0","id":2,"error":{"code":-32602,"message":"Unknown tool: deleteUser"}}""");

        assertThatThrownBy(() -> client.callTool("deleteUser", Map.of()))
                .isInstanceOfSatisfying(McpException.class, e -> {
                    assertThat(e.getJsonRpcError().code()).isEqualTo(JsonRpcError.INVALID_PARAMS);
                    assertThat(e.getMessage()).contains("Unknown tool: deleteUser");
                });
    }

    @Test
    void unknownToolIsRejectedLocallyAfterDiscovery() {
        initialized();
        transport.respond(TOOLS_LIST_RESPONSE);
        client.listTools();

        assertThatThrownBy(() -> client.callTool("deleteUser", Map.of()))
                .isInstanceOf(McpException.class)
                .hasMessageContaining("Unknown tool 'deleteUser'");
        assertThat(transport.sent).as("nothing sent to the server").hasSize(3);
    }

    // ------------------------------------------------------------------ message handling & errors

    @Test
    void requestIdsAreCorrelative() throws Exception {
        initialized();
        transport.respond(TOOLS_LIST_RESPONSE);
        transport.respond("""
                {"jsonrpc":"2.0","id":3,"result":{"content":[]}}""");

        client.listTools();
        client.callTool("getUser", Map.of("id", 10));

        assertThat(sent(0).path("id").asLong()).isEqualTo(1); // initialize
        assertThat(sent(2).path("id").asLong()).isEqualTo(2); // tools/list
        assertThat(sent(3).path("id").asLong()).isEqualTo(3); // tools/call
    }

    @Test
    void notificationsAndPingsAreHandledWhileWaitingForResponse() throws Exception {
        initialized();
        transport.respond("""
                {"jsonrpc":"2.0","method":"notifications/message","params":{"level":"info","data":"hi"}}""");
        transport.respond("""
                {"jsonrpc":"2.0","id":"srv-1","method":"ping"}""");
        transport.respond(TOOLS_LIST_RESPONSE);

        List<Tool> tools = client.listTools();

        assertThat(tools).hasSize(1);
        JsonNode pong = sent(3);
        assertThat(pong.path("id").asText()).isEqualTo("srv-1");
        assertThat(pong.path("result").isObject()).isTrue();
    }

    @Test
    void responsesWithUnexpectedIdAreIgnored() {
        initialized();
        transport.respond("""
                {"jsonrpc":"2.0","id":999,"result":{}}""");
        transport.respond(TOOLS_LIST_RESPONSE);

        assertThat(client.listTools()).hasSize(1);
    }

    @Test
    void timeoutRaisesMcpException() {
        initialized();
        // no response scripted → FakeTransport simulates a timeout

        assertThatThrownBy(() -> client.listTools())
                .isInstanceOf(McpException.class)
                .hasMessageContaining("Timed out");
        assertThat(client.getState()).isEqualTo(McpClientState.READY);
    }

    @Test
    void serverDisconnectionMovesClientToDisconnected() {
        initialized();
        transport.fail(new TransportException("MCP server closed the connection"));

        assertThatThrownBy(() -> client.listTools())
                .isInstanceOf(McpException.class)
                .hasMessageContaining("Connection lost");
        assertThat(client.getState()).isEqualTo(McpClientState.DISCONNECTED);
    }

    @Test
    void invalidResponseRaisesMcpException() {
        initialized();
        transport.respond("this is not json");

        assertThatThrownBy(() -> client.listTools())
                .isInstanceOf(McpException.class)
                .hasMessageContaining("Invalid message");
    }

    @Test
    void closeResetsStateAndIsIdempotent() {
        initialized();

        client.close();
        client.close();

        assertThat(client.getState()).isEqualTo(McpClientState.DISCONNECTED);
        assertThat(client.getServerInfo()).isNull();
        assertThat(transport.closeCount).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private void initialized() {
        transport.respond(INITIALIZE_RESPONSE);
        client.connect();
        client.initialize();
    }

    private JsonNode sent(int index) throws Exception {
        return mapper.readTree(transport.sent.get(index));
    }
}
