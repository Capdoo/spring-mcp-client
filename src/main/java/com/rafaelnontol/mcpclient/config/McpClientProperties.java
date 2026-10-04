package com.rafaelnontol.mcpclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Propiedades {@code mcp.*} de application.properties.
 *
 * @param server         cómo lanzar el MCP Server
 * @param client         identidad que el cliente envía en {@code initialize}
 * @param requestTimeout tiempo máximo de espera por cada respuesta
 */
@ConfigurationProperties(prefix = "mcp")
public record McpClientProperties(
        @DefaultValue Server server,
        @DefaultValue Client client,
        @DefaultValue("30s") Duration requestTimeout) {

    /** @param command ejecutable y argumentos, p.ej. {@code java,-jar,mcp-server.jar} */
    public record Server(List<String> command) {
    }

    public record Client(
            @DefaultValue("basic-mcp-client") String name,
            @DefaultValue("1.0.0") String version) {
    }
}
