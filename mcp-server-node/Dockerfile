# MCP Server provisional (Node.js) con la Tool getUser, transporte STDIO.
#
# Build:  docker build -t basic-user-mcp-server mcp-server-node
# Uso:    el cliente lo lanza con  docker run -i --rm basic-user-mcp-server
#         (-i mantiene stdin abierto; NO usar -t: una TTY alteraría los mensajes)
FROM node:22-alpine

WORKDIR /app
COPY server.js .

USER node
ENTRYPOINT ["node", "server.js"]
