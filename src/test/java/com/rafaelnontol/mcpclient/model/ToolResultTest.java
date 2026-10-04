package com.rafaelnontol.mcpclient.model;

import com.rafaelnontol.mcpclient.protocol.JsonRpcCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultTest {

    private final JsonRpcCodec codec = new JsonRpcCodec();

    @Test
    void joinsTextBlocksAndKeepsOtherContent() throws Exception {
        ToolResult result = codec.convert(JsonRpcCodec.defaultObjectMapper().readTree("""
                {"content":[
                  {"type":"text","text":"first"},
                  {"type":"image","data":"aGVsbG8=","mimeType":"image/png"},
                  {"type":"text","text":"second"}],
                 "structuredContent":{"id":10,"name":"Ada"}}"""), ToolResult.class);

        assertThat(result.textContent()).isEqualTo("first\nsecond");
        assertThat(result.content()).hasSize(3);
        assertThat(result.structuredContent().path("name").asText()).isEqualTo("Ada");
        assertThat(result.isError()).as("isError defaults to false").isFalse();
    }

    @Test
    void missingContentBecomesEmptyList() throws Exception {
        ToolResult result = codec.convert(JsonRpcCodec.defaultObjectMapper().readTree("{\"isError\":true}"), ToolResult.class);

        assertThat(result.content()).isEmpty();
        assertThat(result.isError()).isTrue();
    }
}
