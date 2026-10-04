package com.rafaelnontol.mcpclient.model;

import java.util.List;

/**
 * {@code result} de {@code tools/list}. Si hay más páginas, {@code nextCursor} no es null
 * y debe enviarse como {@code params.cursor} en la siguiente request.
 */
public record ListToolsResult(List<Tool> tools, String nextCursor) {

    public ListToolsResult {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
