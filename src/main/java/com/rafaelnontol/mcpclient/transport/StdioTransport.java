package com.rafaelnontol.mcpclient.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Transporte STDIO de MCP (especificación 2025-06-18, sección "Transports").
 *
 * <pre>
 *   MCP Client ──stdin──▶ MCP Server (proceso hijo)
 *   MCP Client ◀─stdout── MCP Server
 *              ◀─stderr── logs del servidor (no son mensajes MCP)
 * </pre>
 *
 * Reglas de la especificación que implementa esta clase:
 * <ul>
 *   <li>El cliente lanza el servidor como subproceso.</li>
 *   <li>Cada mensaje es una línea de texto UTF-8 terminada en {@code \n};
 *       los mensajes NO pueden contener saltos de línea internos.</li>
 *   <li>Lo que el servidor escriba en stderr son logs, no protocolo.</li>
 *   <li>Cierre: cerrar el stdin del servidor, esperar a que termine y, si no lo hace,
 *       terminarlo (SIGTERM) y finalmente forzarlo (SIGKILL).</li>
 * </ul>
 *
 * <p>Un hilo lee stdout continuamente y deja cada línea en una cola; {@link #receive}
 * simplemente espera en esa cola con un timeout. Así nunca nos bloqueamos indefinidamente.
 */
public class StdioTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(StdioTransport.class);

    private static final Duration SHUTDOWN_GRACE_PERIOD = Duration.ofSeconds(2);

    /** Elemento de la cola: una línea recibida, o la marca de fin de stream. */
    private record Incoming(String line) {
    }

    private static final Incoming END_OF_STREAM = new Incoming(null);

    private final List<String> command;

    private Process process;
    // Una cola nueva por conexión: un hilo lector de una conexión anterior nunca escribe en la actual.
    private volatile BlockingQueue<Incoming> incoming = new LinkedBlockingQueue<>();
    private Writer stdin;

    /**
     * @param command programa y argumentos para lanzar el servidor,
     *                p.ej. {@code ["java", "-jar", "mcp-server.jar"]}
     */
    public StdioTransport(List<String> command) {
        this.command = command == null ? List.of() : List.copyOf(command);
    }

    @Override
    public synchronized void connect() {
        if (isConnected()) {
            return;
        }
        if (command.isEmpty()) {
            throw new TransportException("No MCP server command configured (property 'mcp.server.command')");
        }
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new TransportException("Could not start MCP server process '" + command.getFirst() + "': " + e.getMessage(), e);
        }
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        BlockingQueue<Incoming> queue = new LinkedBlockingQueue<>();
        incoming = queue;

        Process started = process;
        Thread.ofPlatform().daemon().name("mcp-stdout-reader").start(() -> readStdout(started.getInputStream(), queue));
        Thread.ofPlatform().daemon().name("mcp-stderr-reader").start(() -> readStderr(started.getErrorStream()));

        // Solo registramos el ejecutable: los argumentos podrían contener datos sensibles.
        log.info("MCP server process started: {} (pid={})", command.getFirst(), process.pid());
    }

    @Override
    public synchronized void send(String message) {
        if (!isConnected()) {
            throw new TransportException("Not connected to MCP server");
        }
        if (message.indexOf('\n') >= 0 || message.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("STDIO messages must not contain embedded newlines");
        }
        try {
            stdin.write(message);
            stdin.write('\n'); // '\n' explícito: newLine() usaría "\r\n" en Windows
            stdin.flush();
        } catch (IOException e) {
            throw new TransportException("Failed to write to MCP server (has the process exited?)", e);
        }
    }

    @Override
    public String receive(Duration timeout) {
        if (process == null) {
            throw new TransportException("Not connected to MCP server");
        }
        BlockingQueue<Incoming> queue = incoming;
        Incoming next;
        try {
            next = queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("Interrupted while waiting for MCP server", e);
        }
        if (next == null) {
            throw new TransportTimeoutException("No message received from MCP server within " + timeout);
        }
        if (next == END_OF_STREAM) {
            queue.offer(END_OF_STREAM); // las siguientes llamadas también deben ver el cierre
            throw new TransportException("MCP server closed the connection" + exitCodeDescription());
        }
        return next.line();
    }

    @Override
    public boolean isConnected() {
        return process != null && process.isAlive();
    }

    @Override
    public synchronized void close() {
        if (process == null) {
            return;
        }
        log.info("Closing STDIO transport");
        try {
            stdin.close(); // 1. cerrar stdin: el servidor debería terminar por sí mismo
        } catch (IOException ignored) {
            // el proceso pudo haber terminado ya
        }
        try {
            if (!process.waitFor(SHUTDOWN_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroy(); // 2. SIGTERM
                if (!process.waitFor(SHUTDOWN_GRACE_PERIOD.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly(); // 3. SIGKILL
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        log.info("MCP server process stopped{}", exitCodeDescription());
        process = null;
        stdin = null;
    }

    private void readStdout(InputStream stream, BlockingQueue<Incoming> queue) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    queue.offer(new Incoming(line));
                }
            }
        } catch (IOException e) {
            log.debug("Stopped reading server stdout: {}", e.getMessage());
        } finally {
            queue.offer(END_OF_STREAM);
        }
    }

    private void readStderr(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[server stderr] {}", line);
            }
        } catch (IOException e) {
            log.debug("Stopped reading server stderr: {}", e.getMessage());
        }
    }

    private String exitCodeDescription() {
        Process p = process;
        return p != null && !p.isAlive() ? " (exit code " + p.exitValue() + ")" : "";
    }
}
