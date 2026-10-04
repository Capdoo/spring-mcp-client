package com.rafaelnontol.mcpclient.model;

import java.util.Map;

/** {@code params} de la request {@code tools/call}. */
public record CallToolParams(String name, Map<String, Object> arguments) {
}
