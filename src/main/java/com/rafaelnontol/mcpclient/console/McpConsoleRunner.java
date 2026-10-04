package com.rafaelnontol.mcpclient.console;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaelnontol.mcpclient.client.McpClient;
import com.rafaelnontol.mcpclient.client.McpClientState;
import com.rafaelnontol.mcpclient.client.McpException;
import com.rafaelnontol.mcpclient.model.InitializeResult;
import com.rafaelnontol.mcpclient.model.Tool;
import com.rafaelnontol.mcpclient.model.ToolResult;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Interfaz de consola. Traduce comandos de texto a llamadas a {@link McpClient} y pinta
 * los resultados. No contiene lógica MCP: solo interacción con el usuario.
 *
 * <p>{@code getUser} es un atajo de este caso de uso; {@code call} permite invocar cualquier Tool.
 */
@Component
@ConditionalOnProperty(name = "mcp.console.enabled", havingValue = "true", matchIfMissing = true)
public class McpConsoleRunner implements CommandLineRunner {

    private static final String HELP = """
            Commands:
              connect                      Start the MCP server and initialize the session
              tools                        List available tools (tools/list)
              getUser <id>                 Call the 'getUser' tool (tools/call)
              call <tool> [jsonArguments]  Call any tool, e.g. call getUser {"id": 10}
              status                       Show connection state
              help                         Show this help
              exit                         Close the session and quit
            """;

    private final McpClient client;
    private final ObjectMapper json = new ObjectMapper();
    private final PrintStream out = System.out;

    public McpConsoleRunner(McpClient client) {
        this.client = client;
    }

    @Override
    public void run(String... args) throws IOException {
        out.println("Basic MCP Client - type 'help' for commands");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        while (true) {
            out.print("> ");
            out.flush();
            String line = in.readLine();
            if (line == null) { // fin de la entrada (Ctrl+D / Ctrl+Z)
                exit();
                return;
            }
            String[] parts = line.trim().split("\\s+", 2);
            String command = parts[0];
            String argument = parts.length > 1 ? parts[1].trim() : "";

            try {
                switch (command) {
                    case "" -> { }
                    case "connect" -> connect();
                    case "tools" -> listTools();
                    case "getUser" -> getUser(argument);
                    case "call" -> call(argument);
                    case "status" -> status();
                    case "help" -> out.print(HELP);
                    case "exit", "quit" -> {
                        exit();
                        return;
                    }
                    default -> out.println("Unknown command '" + command + "'. Type 'help'.");
                }
            } catch (McpException | IllegalArgumentException e) {
                out.println("Error: " + e.getMessage());
            }
        }
    }

    private void connect() {
        if (client.getState() != McpClientState.DISCONNECTED) {
            out.println("Already connected.");
            return;
        }
        out.println("Initializing MCP session...");
        client.connect();
        InitializeResult result = client.initialize();
        out.println();
        out.println("Connected to:");
        out.println("  " + result.serverInfo().name() + " " + nullToEmpty(result.serverInfo().version()));
        out.println("  Protocol version: " + result.protocolVersion());
        out.println("  Capabilities:     " + result.capabilities().advertised());
        out.println();
    }

    private void listTools() {
        List<Tool> tools = client.listTools();
        out.println();
        out.println("Available tools:");
        if (tools.isEmpty()) {
            out.println("  (none)");
        }
        for (Tool tool : tools) {
            out.println("  * " + tool.name());
            out.println("    " + nullToEmpty(tool.description()));
        }
        out.println();
    }

    private void getUser(String argument) {
        long id;
        try {
            id = Long.parseLong(argument);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Usage: getUser <id>  (id must be a number)");
        }
        invoke("getUser", Map.of("id", id));
    }

    private void call(String argument) {
        if (argument.isEmpty()) {
            throw new IllegalArgumentException("Usage: call <tool> [jsonArguments]");
        }
        String[] parts = argument.split("\\s+", 2);
        Map<String, Object> arguments = parts.length > 1 ? parseArguments(parts[1]) : Map.of();
        invoke(parts[0], arguments);
    }

    private void invoke(String toolName, Map<String, Object> arguments) {
        out.println("Calling tool: " + toolName);
        ToolResult result = client.callTool(toolName, arguments);
        out.println();
        out.println(result.isError() ? "Tool returned an error:" : "Result:");
        String text = result.textContent();
        if (!text.isEmpty()) {
            out.println(text);
        } else if (!result.content().isEmpty()) {
            out.println(pretty(json.valueToTree(result.content())));
        }
        if (result.structuredContent() != null) {
            out.println("Structured content:");
            out.println(pretty(result.structuredContent()));
        }
        out.println();
    }

    private void status() {
        out.println("State: " + client.getState());
        if (client.getServerInfo() != null) {
            out.println("Server: " + client.getServerInfo().name() + " (protocol " + client.getProtocolVersion() + ")");
        }
    }

    private void exit() {
        out.println("Closing MCP session...");
        client.close();
    }

    private Map<String, Object> parseArguments(String rawJson) {
        try {
            return json.readValue(rawJson, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Arguments must be a JSON object, e.g. {\"id\": 10}");
        }
    }

    private String pretty(JsonNode node) {
        return node.toPrettyString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
