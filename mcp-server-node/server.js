// MCP Server PROVISIONAL (Node.js, sin dependencias) para probar spring-mcp-client.
// Implementa lo mínimo de la especificación MCP 2025-06-18 sobre STDIO:
//   initialize, notifications/initialized, ping, tools/list, tools/call (Tool: getUser)
// stdout = mensajes JSON-RPC (una línea por mensaje) · stderr = logs
// Se sustituirá por un MCP Server propio en Spring Boot.

const readline = require("readline");

const SUPPORTED_VERSIONS = ["2025-06-18", "2025-03-26", "2024-11-05"];

const USERS = {
  1: { id: 1, name: "Ada Lovelace", email: "ada@example.com" },
  2: { id: 2, name: "Alan Turing", email: "alan@example.com" },
  10: { id: 10, name: "Grace Hopper", email: "grace@example.com" },
};

const TOOLS = [
  {
    name: "getUser",
    description: "Obtiene un usuario por ID",
    inputSchema: {
      type: "object",
      properties: { id: { type: "integer", description: "ID del usuario" } },
      required: ["id"],
    },
  },
];

const send = (message) => process.stdout.write(JSON.stringify(message) + "\n");
const log = (text) => process.stderr.write(text + "\n");
const result = (id, value) => send({ jsonrpc: "2.0", id, result: value });
const error = (id, code, message) => send({ jsonrpc: "2.0", id, error: { code, message } });

function handleRequest(msg) {
  switch (msg.method) {
    case "initialize": {
      const requested = msg.params?.protocolVersion;
      result(msg.id, {
        protocolVersion: SUPPORTED_VERSIONS.includes(requested) ? requested : SUPPORTED_VERSIONS[0],
        capabilities: { tools: { listChanged: false } },
        serverInfo: { name: "basic-user-mcp-server", version: "0.1.0" },
      });
      break;
    }
    case "ping":
      result(msg.id, {});
      break;
    case "tools/list":
      result(msg.id, { tools: TOOLS });
      break;
    case "tools/call":
      callTool(msg);
      break;
    default:
      error(msg.id, -32601, `Method not found: ${msg.method}`);
  }
}

function callTool(msg) {
  const { name, arguments: args = {} } = msg.params ?? {};
  if (name !== "getUser") {
    return error(msg.id, -32602, `Unknown tool: ${name}`);
  }
  if (!Number.isInteger(args.id)) {
    return error(msg.id, -32602, "Invalid arguments: 'id' must be an integer");
  }
  const user = USERS[args.id];
  if (!user) {
    // Error de EJECUCIÓN de la Tool: resultado normal con isError=true (no es error JSON-RPC)
    return result(msg.id, { content: [{ type: "text", text: `User ${args.id} not found` }], isError: true });
  }
  result(msg.id, {
    content: [{ type: "text", text: JSON.stringify(user) }],
    structuredContent: user,
    isError: false,
  });
}

readline.createInterface({ input: process.stdin }).on("line", (line) => {
  if (!line.trim()) return;
  let msg;
  try {
    msg = JSON.parse(line);
  } catch {
    return error(null, -32700, "Parse error");
  }
  log(`<- ${msg.method ?? "(response)"}`);
  if (msg.method && msg.id !== undefined) handleRequest(msg); // request
  // notificaciones (p.ej. notifications/initialized) no llevan respuesta
}).on("close", () => {
  log("stdin closed, exiting");
  process.exit(0);
});

log("basic-user-mcp-server (node) ready on stdio");
