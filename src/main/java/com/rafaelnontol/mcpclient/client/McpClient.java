package com.rafaelnontol.mcpclient.client;

import com.rafaelnontol.mcpclient.model.InitializeResult;
import com.rafaelnontol.mcpclient.model.ServerCapabilities;
import com.rafaelnontol.mcpclient.model.ServerInfo;
import com.rafaelnontol.mcpclient.model.Tool;
import com.rafaelnontol.mcpclient.model.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * Cliente MCP: lo que el resto de la aplicación (consola, y en el futuro un LLM) ve de MCP.
 *
 * <p>Lifecycle esperado:
 * <pre>
 *   connect() → initialize() → listTools() → callTool(...) → close()
 * </pre>
 *
 * <p>Esta interfaz solo usa modelos propios ({@code model.*}). Así, más adelante se podrá
 * escribir una implementación basada en Spring AI MCP (un adaptador) sin tocar la consola.
 * La implementación manual actual es {@link DefaultMcpClient}.
 *
 * <p>Todos los errores se lanzan como {@link McpException}.
 */
public interface McpClient extends AutoCloseable {

    /** Abre el transporte (en STDIO: arranca el proceso del servidor). */
    void connect();

    /** Handshake MCP: negocia versión de protocolo y capacidades. */
    InitializeResult initialize();

    /** {@code tools/list}: descubre las Tools del servidor. */
    List<Tool> listTools();

    /** {@code tools/call}: ejecuta una Tool con los argumentos dados. */
    ToolResult callTool(String toolName, Map<String, Object> arguments);

    McpClientState getState();

    /** Disponibles tras {@link #initialize()}; {@code null} antes. */
    ServerInfo getServerInfo();

    ServerCapabilities getServerCapabilities();

    String getProtocolVersion();

    /** Cierra la sesión y el transporte. Idempotente. */
    @Override
    void close();
}
