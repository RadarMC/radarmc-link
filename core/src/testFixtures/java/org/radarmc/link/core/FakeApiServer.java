package org.radarmc.link.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.radarmc.link.core.io.TextFiles;

public final class FakeApiServer implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 1 << 20;

    public static final class Request {
        private final String method;
        private final String path;
        private final String query;
        private final Map<String, List<String>> headers;
        private final String body;

        Request(String method, String path, String query, Map<String, List<String>> headers, String body) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
            this.body = body;
        }

        public String method() {
            return method;
        }

        public String path() {
            return path;
        }

        public String query() {
            return query;
        }

        public String body() {
            return body;
        }

        public String header(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue().get(0);
                }
            }
            return null;
        }
    }

    public static final class Response {
        private final int status;
        private final String body;
        private final Map<String, String> headers;

        public Response(int status, String body, Map<String, String> headers) {
            this.status = status;
            this.body = body;
            this.headers = headers;
        }

        public static Response json(int status, String body) {
            return new Response(status, body, Collections.<String, String>emptyMap());
        }
    }

    private final HttpServer server;
    public final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Response> responses = new ConcurrentHashMap<>();

    public FakeApiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public URI url() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    public void respond(String method, String pathSuffix, Response response) {
        responses.put(method + " " + pathSuffix, response);
    }

    public List<Request> requestsTo(String pathSuffix) {
        List<Request> out = new ArrayList<>();
        for (Request r : requests) {
            if (r.path().endsWith(pathSuffix)) {
                out.add(r);
            }
        }
        return out;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = TextFiles.readUtf8(exchange.getRequestBody(), MAX_BODY_BYTES);
        String path = exchange.getRequestURI().getPath();
        requests.add(new Request(
                exchange.getRequestMethod(),
                path,
                exchange.getRequestURI().getRawQuery(),
                Collections.unmodifiableMap(new HashMap<>(exchange.getRequestHeaders())),
                body));
        Response response = find(exchange.getRequestMethod(), path);
        byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
        for (Map.Entry<String, String> h : response.headers.entrySet()) {
            exchange.getResponseHeaders().add(h.getKey(), h.getValue());
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private Response find(String method, String path) {
        for (Map.Entry<String, Response> e : responses.entrySet()) {
            String[] key = e.getKey().split(" ", 2);
            if (key[0].equals(method) && path.endsWith(key[1])) {
                return e.getValue();
            }
        }
        if (path.endsWith("/joins")) {
            return Response.json(200, "{\"accepted\":0,\"ignored\":0}");
        }
        if (path.endsWith("/votes/pending")) {
            return Response.json(200, "{\"votes\":[],\"next_cursor\":\"\"}");
        }
        return Response.json(200, "{\"ok\":true}");
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
