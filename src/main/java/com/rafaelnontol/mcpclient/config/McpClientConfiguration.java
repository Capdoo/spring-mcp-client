package com.rafaelnontol.mcpclient.config;

import com.rafaelnontol.mcpclient.client.DefaultMcpClient;
import com.rafaelnontol.mcpclient.client.McpClient;
import com.rafaelnontol.mcpclient.client.RequestIdGenerator;
import com.rafaelnontol.mcpclient.model.ClientInfo;
import com.rafaelnontol.mcpclient.protocol.JsonRpcCodec;
import com.rafaelnontol.mcpclient.transport.StdioTransport;
import com.rafaelnontol.mcpclient.transport.Transport;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensambla las piezas. Es el único lugar que conoce la implementación concreta de
 * {@link Transport} y de {@link McpClient}: para pasar a HTTP o a Spring AI MCP bastará
 * con cambiar los beans de esta clase.
 *
 * <p>Ambos beans implementan {@code AutoCloseable}, así que Spring llama a {@code close()}
 * al apagar la aplicación y el proceso del servidor nunca queda huérfano.
 */
@Configuration
@EnableConfigurationProperties(McpClientProperties.class)
public class McpClientConfiguration {

    @Bean
    public Transport transport(McpClientProperties properties) {
        return new StdioTransport(properties.server().command());
    }

    @Bean
    public JsonRpcCodec jsonRpcCodec() {
        return new JsonRpcCodec();
    }

    @Bean
    public McpClient mcpClient(Transport transport, JsonRpcCodec codec, McpClientProperties properties) {
        ClientInfo clientInfo = new ClientInfo(properties.client().name(), properties.client().version());
        return new DefaultMcpClient(transport, codec, new RequestIdGenerator(), clientInfo, properties.requestTimeout());
    }
}
