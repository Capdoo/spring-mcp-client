package com.rafaelnontol.mcpclient.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.rafaelnontol.mcpclient.model.CallToolParams;
import com.rafaelnontol.mcpclient.model.ClientCapabilities;
import com.rafaelnontol.mcpclient.model.ClientInfo;
import com.rafaelnontol.mcpclient.model.InitializeParams;
import com.rafaelnontol.mcpclient.model.InitializeResult;
import com.rafaelnontol.mcpclient.model.ListToolsResult;
import com.rafaelnontol.mcpclient.model.ServerCapabilities;
import com.rafaelnontol.mcpclient.model.ServerInfo;
import com.rafaelnontol.mcpclient.model.Tool;
import com.rafaelnontol.mcpclient.model.ToolResult;
import com.rafaelnontol.mcpclient.protocol.JsonRpcCodec;
import com.rafaelnontol.mcpclient.protocol.JsonRpcError;
import com.rafaelnontol.mcpclient.protocol.JsonRpcMessage;
import com.rafaelnontol.mcpclient.protocol.JsonRpcNotification;
import com.rafaelnontol.mcpclient.protocol.JsonRpcParseException;
import com.rafaelnontol.mcpclient.protocol.JsonRpcRequest;
import com.rafaelnontol.mcpclient.protocol.JsonRpcResponse;
import com.rafaelnontol.mcpclient.transport.Transport;
import com.rafaelnontol.mcpclient.transport.TransportException;
import com.rafaelnontol.mcpclient.transport.TransportTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementación manual de {@link McpClient} sobre JSON-RPC 2.0.
 *
 * <p>Responsabilidad: la lógica del protocolo MCP (lifecycle, métodos, correlación de ids).
 * No sabe cómo viajan los bytes (eso es {@link Transport}) ni cómo se escribe JSON
 * (eso es {@link JsonRpcCodec}).
 *
 * <p>El cliente es síncrono: envía una request y espera su respuesta antes de seguir.
 * Mientras espera puede recibir otros mensajes del servidor (notificaciones o requests
 * como {@code ping}); se atienden y se sigue esperando.
 */
public class DefaultMcpClient implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpClient.class);

    /** Versión de la especificación MCP que este cliente solicita. */
    public static final String PROTOCOL_VERSION = "2025-06-18";

    /**
     * Versiones que aceptamos si el servidor contesta con otra. En todas ellas
     * {@code initialize}, {@code tools/list} y {@code tools/call} son compatibles con este cliente.
     */
    public static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of("2025-06-18", "2025-03-26", "2024-11-05");

    private final Transport transport;
    private final JsonRpcCodec codec;
    private final RequestIdGenerator idGenerator;
    private final ClientInfo clientInfo;
    private final Duration requestTimeout;

    private McpClientState state = McpClientState.DISCONNECTED;
    private InitializeResult session;
    /** Tools descubiertas con el último tools/list; null si aún no se han pedido. */
    private List<Tool> knownTools;

    public DefaultMcpClient(Transport transport, JsonRpcCodec codec, RequestIdGenerator idGenerator,
                            ClientInfo clientInfo, Duration requestTimeout) {
        this.transport = transport;
        this.codec = codec;
        this.idGenerator = idGenerator;
        this.clientInfo = clientInfo;
        this.requestTimeout = requestTimeout;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public synchronized void connect() {
        if (state != McpClientState.DISCONNECTED) {
            throw new McpException("Already connected");
        }
        try {
            transport.connect();
        } catch (TransportException e) {
            throw new McpException("Could not connect to MCP server: " + e.getMessage(), e);
        }
        state = McpClientState.CONNECTED;
        log.info("MCP connection established");
    }

    /**
     * Handshake de inicialización (3 pasos):
     * <ol>
     *   <li>Cliente → {@code initialize} (versión de protocolo, capacidades, clientInfo)</li>
     *   <li>Servidor → respuesta (versión aceptada, capacidades, serverInfo)</li>
     *   <li>Cliente → notificación {@code notifications/initialized}</li>
     * </ol>
     */
    @Override
    public synchronized InitializeResult initialize() {
        requireState(McpClientState.CONNECTED, McpMethods.INITIALIZE);
        log.info("Initializing MCP session (protocolVersion={})", PROTOCOL_VERSION);

        InitializeParams params = new InitializeParams(PROTOCOL_VERSION, ClientCapabilities.none(), clientInfo);
        InitializeResult result = toModel(sendRequest(McpMethods.INITIALIZE, params), InitializeResult.class, McpMethods.INITIALIZE);

        if (result.protocolVersion() == null || result.serverInfo() == null || result.capabilities() == null) {
            throw new McpException("Invalid initialize result: 'protocolVersion', 'serverInfo' and 'capabilities' are required");
        }
        // Si el servidor no soporta nuestra versión responde con otra; si tampoco la soportamos
        // nosotros, la especificación indica que el cliente DEBERÍA desconectarse.
        if (!SUPPORTED_PROTOCOL_VERSIONS.contains(result.protocolVersion())) {
            disconnect();
            throw new McpException("Server requires unsupported protocol version " + result.protocolVersion()
                    + " (supported: " + SUPPORTED_PROTOCOL_VERSIONS + ")");
        }

        sendNotification(McpMethods.NOTIFICATION_INITIALIZED);

        session = result;
        state = McpClientState.READY;
        log.info("Server initialized: {} {} (protocol {}, capabilities {})",
                result.serverInfo().name(), result.serverInfo().version(),
                result.protocolVersion(), result.capabilities().advertised());
        return result;
    }

    @Override
    public synchronized void close() {
        if (state == McpClientState.DISCONNECTED && !transport.isConnected()) {
            return;
        }
        // MCP no define un mensaje de "shutdown": la sesión termina cerrando el transporte.
        log.info("Closing MCP session");
        disconnect();
    }

    // ------------------------------------------------------------------ tools

    @Override
    public synchronized List<Tool> listTools() {
        requireToolsSupport(McpMethods.TOOLS_LIST);
        log.info("Fetching available tools");

        // tools/list está paginado: se repite mientras el servidor devuelva nextCursor.
        List<Tool> tools = new ArrayList<>();
        String cursor = null;
        do {
            Object params = cursor == null ? null : Map.of("cursor", cursor);
            ListToolsResult page = toModel(sendRequest(McpMethods.TOOLS_LIST, params), ListToolsResult.class, McpMethods.TOOLS_LIST);
            tools.addAll(page.tools());
            cursor = page.nextCursor();
        } while (cursor != null);

        knownTools = List.copyOf(tools);
        log.info("Discovered {} tool(s)", knownTools.size());
        return knownTools;
    }

    /**
     * Ejecuta una Tool. Hay dos tipos de fallo distintos:
     * <ul>
     *   <li>Error de protocolo (Tool inexistente, argumentos inválidos...): el servidor responde
     *       con un error JSON-RPC → se lanza {@link McpException}.</li>
     *   <li>Error de ejecución de la Tool: la respuesta es un resultado normal con
     *       {@code isError=true} → se devuelve el {@link ToolResult}.</li>
     * </ul>
     */
    @Override
    public synchronized ToolResult callTool(String toolName, Map<String, Object> arguments) {
        requireToolsSupport(McpMethods.TOOLS_CALL);
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        // Si ya hicimos tools/list, detectamos aquí una Tool inexistente sin ir al servidor.
        // Si no, el servidor es quien decide (respondería con un error JSON-RPC -32602).
        if (knownTools != null && knownTools.stream().noneMatch(tool -> tool.name().equals(toolName))) {
            throw new McpException("Unknown tool '" + toolName + "'. Available tools: "
                    + knownTools.stream().map(Tool::name).toList());
        }

        // A nivel INFO no registramos los argumentos: pueden contener datos sensibles.
        log.info("Calling tool: {}", toolName);
        CallToolParams params = new CallToolParams(toolName, arguments == null ? Map.of() : arguments);
        ToolResult result = toModel(sendRequest(McpMethods.TOOLS_CALL, params), ToolResult.class, McpMethods.TOOLS_CALL);

        if (result.isError()) {
            log.warn("Tool '{}' reported an execution error", toolName);
        }
        return result;
    }

    // ------------------------------------------------------------------ server info

    @Override
    public synchronized McpClientState getState() {
        return state;
    }

    @Override
    public synchronized ServerInfo getServerInfo() {
        return session == null ? null : session.serverInfo();
    }

    @Override
    public synchronized ServerCapabilities getServerCapabilities() {
        return session == null ? null : session.capabilities();
    }

    @Override
    public synchronized String getProtocolVersion() {
        return session == null ? null : session.protocolVersion();
    }

    // ------------------------------------------------------------------ JSON-RPC plumbing

    /** Envía una request, espera su respuesta y devuelve el {@code result} (o lanza si hay {@code error}). */
    private JsonNode sendRequest(String method, Object params) {
        long id = idGenerator.nextId();
        write(new JsonRpcRequest(id, method, params));

        JsonRpcResponse response = awaitResponse(id, method);
        if (response.hasError()) {
            JsonRpcError error = response.error();
            log.warn("Server returned JSON-RPC error for '{}': {} (code {})", method, error.message(), error.code());
            throw new McpException("Server error on '" + method + "': " + error.message() + " (code " + error.code() + ")", error);
        }
        if (response.result() == null || response.result().isNull()) {
            throw new McpException("Invalid response to '" + method + "': missing 'result'");
        }
        return response.result();
    }

    private void sendNotification(String method) {
        write(new JsonRpcNotification(method, null));
    }

    private void write(JsonRpcMessage message) {
        String json = codec.encode(message);
        log.debug("--> {}", json);
        try {
            transport.send(json);
        } catch (TransportException e) {
            disconnect();
            throw new McpException("Connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Lee mensajes hasta encontrar la respuesta con el {@code id} esperado, atendiendo por el
     * camino cualquier otro mensaje del servidor. El timeout es para la request completa.
     */
    private JsonRpcResponse awaitResponse(long id, String method) {
        Instant deadline = Instant.now().plus(requestTimeout);
        while (true) {
            Duration remaining = Duration.between(Instant.now(), deadline);
            String raw;
            try {
                if (remaining.isNegative() || remaining.isZero()) {
                    throw new TransportTimeoutException("deadline reached");
                }
                raw = transport.receive(remaining);
            } catch (TransportTimeoutException e) {
                // La conexión sigue viva; una respuesta tardía con este id se descartará más adelante.
                throw new McpException("Timed out after " + requestTimeout.toSeconds() + "s waiting for response to '"
                        + method + "' (id=" + id + ")", e);
            } catch (TransportException e) {
                disconnect();
                throw new McpException("Connection lost while waiting for '" + method + "': " + e.getMessage(), e);
            }
            log.debug("<-- {}", raw);

            JsonRpcMessage message;
            try {
                message = codec.decode(raw);
            } catch (JsonRpcParseException e) {
                throw new McpException("Invalid message from server: " + e.getMessage(), e);
            }

            switch (message) {
                case JsonRpcResponse response when Long.valueOf(id).equals(response.id()) -> {
                    return response;
                }
                // Un error sin id (p.ej. -32700 Parse error) no se puede correlacionar; como el
                // cliente es síncrono, solo puede referirse a la request que está esperando.
                case JsonRpcResponse response when response.id() == null && response.hasError() -> {
                    return response;
                }
                case JsonRpcResponse response -> log.warn("Ignoring response with unexpected id {}", response.id());
                case JsonRpcNotification notification -> log.info("Server notification received: {}", notification.method());
                case JsonRpcRequest request -> answerServerRequest(request);
            }
        }
    }

    /**
     * El servidor también puede enviarnos requests. Solo respondemos a {@code ping}; para
     * el resto (sampling, roots, elicitation...) no hemos anunciado capacidades, así que
     * contestamos "Method not found".
     */
    private void answerServerRequest(JsonRpcRequest request) {
        if (McpMethods.PING.equals(request.method())) {
            log.debug("Answering ping from server");
            write(JsonRpcResponse.success(request.id(), null));
        } else {
            log.warn("Rejecting unsupported server request: {}", request.method());
            write(JsonRpcResponse.failure(request.id(),
                    new JsonRpcError(JsonRpcError.METHOD_NOT_FOUND, "Method not supported by client: " + request.method())));
        }
    }

    private <T> T toModel(JsonNode result, Class<T> type, String method) {
        try {
            return codec.convert(result, type);
        } catch (JsonRpcParseException e) {
            throw new McpException("Invalid response to '" + method + "': " + e.getMessage(), e);
        }
    }

    private void requireState(McpClientState expected, String operation) {
        if (state == expected) {
            return;
        }
        String hint = switch (state) {
            case DISCONNECTED -> "call connect() first";
            case CONNECTED -> "call initialize() first";
            case READY -> "session already initialized";
        };
        throw new McpException("Cannot perform '" + operation + "' while " + state + ": " + hint);
    }

    private void requireToolsSupport(String operation) {
        requireState(McpClientState.READY, operation);
        if (!session.capabilities().supportsTools()) {
            throw new McpException("Server does not advertise the 'tools' capability");
        }
    }

    private void disconnect() {
        transport.close();
        state = McpClientState.DISCONNECTED;
        session = null;
        knownTools = null;
    }
}
