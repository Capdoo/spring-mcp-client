# spring-mcp-client

MCP Client básico, escrito a mano en **Java 21 + Spring Boot 3.5**, para aprender cómo funciona
el **Model Context Protocol** por dentro: JSON-RPC, lifecycle, transporte STDIO, `tools/list` y `tools/call`.

No usa Spring AI MCP ni ningún SDK de MCP: cada mensaje se construye, envía, recibe y correlaciona en este código.

> **Versión del protocolo:** el cliente implementa la especificación MCP **`2025-06-18`**
> (<https://modelcontextprotocol.io/specification/2025-06-18>). En `initialize` solicita esa versión
> y acepta también `2025-03-26` y `2024-11-05` si el servidor responde con alguna de ellas
> (para las operaciones usadas aquí son compatibles). Ver `DefaultMcpClient.PROTOCOL_VERSION`.

---

## 1. ¿Qué es MCP?

**MCP (Model Context Protocol)** es un protocolo abierto que estandariza cómo una aplicación basada en LLM
obtiene contexto y capacidades externas: **Tools** (funciones ejecutables), **Resources** (datos)
y **Prompts** (plantillas). En lugar de que cada aplicación invente su propia integración con cada sistema,
un sistema expone un *MCP Server* y cualquier aplicación compatible lo puede usar.

Por debajo, MCP es **JSON-RPC 2.0** sobre un transporte (STDIO o Streamable HTTP), más un conjunto de
métodos definidos (`initialize`, `tools/list`, `tools/call`, `resources/read`, ...).

## 2. Host, Client y Server

| Rol | Qué es | En este proyecto |
|---|---|---|
| **MCP Host** | La aplicación que el usuario usa (un IDE, Claude Desktop, un chatbot). Contiene el LLM y crea uno o varios clientes. | La app de consola (sin LLM todavía). |
| **MCP Client** | Componente dentro del Host que mantiene **una conexión 1:1** con un servidor y habla el protocolo. | `McpClient` / `DefaultMcpClient`. |
| **MCP Server** | Programa que expone Tools/Resources/Prompts. | Un servidor externo con la Tool `getUser` (no incluido). |

```
┌──────────── MCP Host (esta app) ────────────┐
│  Consola  ──▶  McpClient  ──▶  Transport ───┼──stdin/stdout──▶  MCP Server
└─────────────────────────────────────────────┘                   (proceso hijo)
```

## 3. Responsabilidad de este proyecto

- Lanzar un MCP Server como proceso hijo y comunicarse por STDIO.
- Realizar el handshake `initialize` y guardar versión, `serverInfo` y capacidades del servidor.
- Descubrir Tools (`tools/list`) y ejecutarlas (`tools/call`).
- Ofrecer una consola simple para probarlo con el caso de uso `getUser`.

**Fuera de alcance (por ahora):** LLM, Resources, Prompts, Sampling, Roots, Elicitation, Authorization,
HTTP transport, cancelación de requests, varios servidores.

## 4. Arquitectura

```
src/main/java/com/rafaelnontol/mcpclient/
├── McpClientApplication.java      Arranque de Spring Boot
├── config/
│   ├── McpClientProperties.java   Propiedades mcp.* (comando del servidor, timeout, clientInfo)
│   └── McpClientConfiguration.java Ensambla Transport + Codec + McpClient (único sitio con clases concretas)
├── console/
│   └── McpConsoleRunner.java      Interacción con el usuario (CommandLineRunner)
├── client/                        ── Lógica del protocolo MCP
│   ├── McpClient.java             Interfaz: connect / initialize / listTools / callTool / close
│   ├── DefaultMcpClient.java      Implementación manual sobre JSON-RPC
│   ├── McpClientState.java        DISCONNECTED → CONNECTED → READY
│   ├── McpMethods.java            Nombres de métodos MCP
│   ├── RequestIdGenerator.java    Ids correlativos 1, 2, 3...
│   └── McpException.java          Única excepción expuesta por el cliente
├── protocol/                      ── JSON-RPC 2.0 (no sabe nada de MCP)
│   ├── JsonRpcMessage.java        sealed: Request | Notification | Response
│   ├── JsonRpcRequest.java
│   ├── JsonRpcNotification.java
│   ├── JsonRpcResponse.java
│   ├── JsonRpcError.java
│   ├── JsonRpcCodec.java          Objetos ⇄ texto JSON (Jackson)
│   └── JsonRpcParseException.java
├── model/                         ── Tipos de datos MCP
│   ├── InitializeParams / InitializeResult
│   ├── ClientInfo / ServerInfo
│   ├── ClientCapabilities / ServerCapabilities
│   ├── Tool / ListToolsResult
│   └── CallToolParams / ToolResult
└── transport/                     ── Comunicación (no sabe nada de JSON-RPC)
    ├── Transport.java             connect / send / receive / close
    ├── StdioTransport.java        Proceso hijo + stdin/stdout, una línea = un mensaje
    ├── TransportException.java
    └── TransportTimeoutException.java
```

Reglas de dependencia:

```
console ──▶ client ──▶ protocol
              │  └───▶ model
              └──────▶ transport (solo la interfaz Transport)
```

- `Transport` mueve **strings**. No parsea JSON.
- `JsonRpcCodec` convierte strings ⇄ objetos JSON-RPC. No conoce MCP.
- `DefaultMcpClient` implementa el **protocolo MCP**: estados, métodos, correlación de ids, errores.
- La consola solo usa la interfaz `McpClient` y los modelos; no ve JSON-RPC ni STDIO.

### Preparado para evolucionar

- **Otro transporte** (`HttpTransport`): implementar `Transport` y cambiar el bean en `McpClientConfiguration`.
- **Spring AI MCP**: crear un adaptador `SpringAiMcpClient implements McpClient` que traduzca sus tipos
  a los de `model/`. La consola no cambia.
- **LLM**: `Tool.inputSchema` y `InitializeResult.instructions` se conservan tal cual para pasárselos a un modelo.
- **Resources / Prompts**: `ServerCapabilities` ya conserva esas capacidades como `JsonNode`.
- **Sampling / Roots**: son capacidades **del cliente** (el servidor las solicita al cliente), por eso
  sus huecos están en `ClientCapabilities`, no en `ServerCapabilities`.

## 5. Lifecycle

```
START
  │
  ▼
CONNECT ─────────── transport.connect(): arranca el proceso del servidor     [CONNECTED]
  │
  ▼
INITIALIZE
  │  1. C → S  request  initialize   {protocolVersion, capabilities, clientInfo}
  │  2. S → C  response              {protocolVersion, capabilities, serverInfo}
  │  3. C → S  notification notifications/initialized
  ▼
READY ───────────────────────────────────────────────────────────────────── [READY]
  │
  ├─▶ tools/list   (descubrir)
  ├─▶ tools/call   (ejecutar)
  ▼
CLOSE ──────────── se cierra stdin del servidor → esperar → SIGTERM → SIGKILL [DISCONNECTED]
```

Detalles de la especificación respetados:

- Antes de terminar `initialize` no se envían otras requests (`tools/*` exige estado `READY`).
- Si el servidor responde con una versión de protocolo que no soportamos, el cliente se desconecta.
- MCP **no tiene un mensaje de cierre**: la sesión termina cerrando el transporte.
- Si el servidor nos envía `ping`, respondemos `{}`; otras requests del servidor reciben `-32601 Method not found`
  porque no hemos anunciado capacidades de cliente.

## 6. JSON-RPC 2.0

Tres tipos de mensaje:

```jsonc
// Request: tiene id, espera respuesta
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{...}}

// Notification: sin id, no tiene respuesta
{"jsonrpc":"2.0","method":"notifications/initialized"}

// Response: mismo id que la request; result O error
{"jsonrpc":"2.0","id":1,"result":{...}}
{"jsonrpc":"2.0","id":3,"error":{"code":-32602,"message":"Unknown tool: deleteUser"}}
```

**Ids:** `RequestIdGenerator` genera 1, 2, 3... Nunca se reutilizan en una sesión. El cliente espera
la respuesta cuyo `id` coincide con el de su request; las respuestas con otro id se ignoran (por ejemplo,
una respuesta tardía tras un timeout) y las notificaciones del servidor se registran en el log.

**Transporte STDIO:** cada mensaje es **una línea** UTF-8 terminada en `\n`, sin saltos de línea internos.
Lo que el servidor escriba en **stderr** son logs (el cliente los muestra como `[server stderr] ...`).

### `initialize`

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-06-18",
    "capabilities": {},
    "clientInfo": { "name": "basic-mcp-client", "version": "1.0.0" }
  }
}
```

`capabilities` va vacío: aún no ofrecemos sampling, roots ni elicitation.

## 7. `tools/list`

```json
{"jsonrpc":"2.0","id":2,"method":"tools/list"}
```

```json
{"jsonrpc":"2.0","id":2,"result":{"tools":[{
  "name":"getUser",
  "description":"Obtiene un usuario por ID",
  "inputSchema":{"type":"object","properties":{"id":{"type":"integer"}},"required":["id"]}
}]}}
```

La respuesta está **paginada**: si trae `nextCursor`, el cliente repite la request con
`"params":{"cursor":"..."}` hasta obtener todas las Tools.

## 8. `tools/call`

```json
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"getUser","arguments":{"id":10}}}
```

```json
{"jsonrpc":"2.0","id":3,"result":{
  "content":[{"type":"text","text":"{\"id\":10,\"name\":\"Grace Hopper\"}"}],
  "structuredContent":{"id":10,"name":"Grace Hopper","email":"grace@example.com"},
  "isError":false
}}
```

`ToolResult` no asume ninguna estructura de negocio: guarda `content` (bloques `text`, `image`, ...)
como JSON, `structuredContent` opcional e `isError`.

**Dos tipos de error, distintos a propósito:**

| Situación | Cómo llega | Qué hace el cliente |
|---|---|---|
| Tool inexistente, argumentos inválidos | Error JSON-RPC (`-32602`) | Lanza `McpException` (con `getJsonRpcError()`) |
| La Tool se ejecutó pero falló (p.ej. usuario no encontrado) | `result` con `isError: true` | Devuelve el `ToolResult` |

Además, si ya se hizo `tools/list`, el cliente rechaza localmente una Tool que no está en la lista.

### Manejo de errores

| Error | Resultado |
|---|---|
| No se puede arrancar el servidor | `McpException: Could not connect...` |
| Servidor desconectado | `McpException: Connection lost...` → estado `DISCONNECTED` |
| Respuesta inválida (no JSON, sin `result`...) | `McpException: Invalid ...` |
| Error JSON-RPC | `McpException` con el `JsonRpcError` |
| Timeout (`mcp.request-timeout`, 30s) | `McpException: Timed out...` (la conexión sigue abierta) |

## 9. Cómo ejecutar

Requisitos: JDK 21 y Maven (o el wrapper `mvnw`).

```bash
./mvnw test        # tests unitarios
./mvnw package     # genera target/spring-mcp-client-0.0.1-SNAPSHOT.jar
```

### MCP Server provisional (Node.js en Docker)

Mientras no exista el MCP Server propio en Spring Boot, `mcp-server-node/` contiene un servidor
mínimo en Node.js (sin dependencias) con la Tool `getUser` (usuarios 1, 2 y 10). Construye la imagen
una vez (con Docker Desktop arrancado):

```bash
docker build -t basic-user-mcp-server mcp-server-node
```

`application.properties` ya está configurado para lanzarlo:

```properties
mcp.server.command=docker,run,-i,--rm,basic-user-mcp-server
```

- `-i` es obligatorio: mantiene abierto el stdin del contenedor, que es el canal STDIO de MCP.
- No uses `-t`: una TTY modifica los saltos de línea y rompe los mensajes.
- `--rm` elimina el contenedor cuando el cliente cierra la sesión (al cerrar stdin el servidor termina).

Sin Docker también puedes lanzarlo directamente: `--mcp.server.command=node,mcp-server-node/server.js`.

### Otro MCP Server

Configura en `src/main/resources/application.properties` cómo lanzar tu MCP Server
(ejecutable y argumentos separados por comas):

```properties
mcp.server.command=java,-jar,../basic-user-mcp-server/target/basic-user-mcp-server.jar
```

o pásalo al arrancar:

```bash
java -jar target/spring-mcp-client-0.0.1-SNAPSHOT.jar --mcp.server.command=java,-jar,/ruta/al/server.jar
```

Para ver cada mensaje JSON-RPC (`-->` enviado, `<--` recibido):

```bash
java -jar target/spring-mcp-client-0.0.1-SNAPSHOT.jar --logging.level.com.rafaelnontol.mcpclient=DEBUG
```

> En DEBUG se imprimen los payloads completos (incluidos los argumentos de las Tools). A nivel INFO
> solo se registran nombres de métodos y Tools.

## 10. Ejemplo de interacción por consola

Comandos: `connect`, `tools`, `getUser <id>`, `call <tool> [json]`, `status`, `help`, `exit`.

```text
Basic MCP Client - type 'help' for commands
> connect
Initializing MCP session...
INFO  StdioTransport - MCP server process started: docker (pid=10672)
INFO  DefaultMcpClient - MCP connection established
INFO  DefaultMcpClient - Initializing MCP session (protocolVersion=2025-06-18)
INFO  DefaultMcpClient - Server initialized: basic-user-mcp-server 0.1.0 (protocol 2025-06-18, capabilities [tools])

Connected to:
  basic-user-mcp-server 0.1.0
  Protocol version: 2025-06-18
  Capabilities:     [tools]

> tools
INFO  DefaultMcpClient - Fetching available tools
INFO  DefaultMcpClient - Discovered 1 tool(s)

Available tools:
  * getUser
    Obtiene un usuario por ID

> getUser 10
Calling tool: getUser
INFO  DefaultMcpClient - Calling tool: getUser

Result:
{"id":10,"name":"Grace Hopper","email":"grace@example.com"}
Structured content:
{
  "id" : 10,
  "name" : "Grace Hopper",
  "email" : "grace@example.com"
}

> getUser 5
Calling tool: getUser
INFO  DefaultMcpClient - Calling tool: getUser
WARN  DefaultMcpClient - Tool 'getUser' reported an execution error

Tool returned an error:
User 5 not found

> call deleteUser {"id": 1}
Calling tool: deleteUser
Error: Unknown tool 'deleteUser'. Available tools: [getUser]

> exit
Closing MCP session...
INFO  DefaultMcpClient - Closing MCP session
INFO  StdioTransport - Closing STDIO transport
INFO  StdioTransport - MCP server process stopped (exit code 0)
```

## 11. Tests

| Test | Qué cubre |
|---|---|
| `JsonRpcCodecTest` | Serialización de requests/notificaciones, deserialización de respuestas y errores, mensajes inválidos |
| `RequestIdGeneratorTest` | Ids correlativos y únicos (también en concurrencia) |
| `DefaultMcpClientTest` | Construcción de `initialize`, `tools/list` y `tools/call`; notificación `initialized`; paginación; errores JSON-RPC; `isError`; Tool inexistente; timeout; desconexión; `ping`; estados |
| `ToolResultTest` | Bloques de contenido y valores por defecto |
| `McpClientApplicationTests` | El contexto de Spring arranca (con la consola desactivada) |

`DefaultMcpClientTest` usa `FakeTransport`, un Transport falso que registra lo enviado y devuelve
respuestas guionizadas: así se prueba el protocolo sin lanzar ningún proceso.
