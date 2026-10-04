package com.rafaelnontol.mcpclient;

import com.rafaelnontol.mcpclient.client.McpClient;
import com.rafaelnontol.mcpclient.client.McpClientState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba que el contexto arranca sin lanzar la consola ni el proceso del servidor. */
@SpringBootTest(properties = "mcp.console.enabled=false")
class McpClientApplicationTests {

    @Autowired
    private McpClient client;

    @Test
    void contextLoadsWithDisconnectedClient() {
        assertThat(client.getState()).isEqualTo(McpClientState.DISCONNECTED);
    }
}
