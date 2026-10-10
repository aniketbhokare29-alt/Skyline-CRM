package com.saivandan.crm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static com.saivandan.crm.Utils.send;

public class App {
    private static final int PORT = settingInt("PORT", 8080);
    private static final Path UPLOAD_DIR = Paths.get(setting("CRM_UPLOAD_DIR", "uploads")).toAbsolutePath().normalize();
    private static final Path FRONTEND_DIR = Paths.get(setting("CRM_FRONTEND_DIR", "frontend")).toAbsolutePath().normalize();

    /** Origin of the static frontend that is allowed to call this API. */
    private static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:5500",
            "http://127.0.0.1:5500"
    );

    /** Supports pages cached from the earlier server-rendered frontend. */
    private static final Set<String> LEGACY_API_RESOURCES = Set.of(
            "login", "register", "logout", "session", "nav", "dashboard", "finance-dashboard",
            "enquiries", "properties", "followups", "qualification", "site-visits", "negotiations",
            "bookings", "documents", "loans", "agreements", "payments", "possession", "support",
            "employees", "attendance", "leaves", "salary", "vendors", "purchase-orders", "vendor-bills",
            "vendor-payments", "petty-cash", "petty-cash-approvals"
    );

    public static final DataStore STORE = new DataStore();

    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int settingInt(String name, int fallback) {
        try {
            return Integer.parseInt(setting(name, String.valueOf(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", App::route);
        server.createContext("/uploads", App::serveUpload);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Skyline CRM API running at http://localhost:" + PORT);
    }

    private static void route(HttpExchange exchange) throws IOException {
        try {
            addCorsHeaders(exchange);

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            // Older cached pages posted to /attendance instead of /api/attendance.
            // Normalize those requests before routing so they still use the same
            // authenticated API and database code.
            if (!path.startsWith("/api/") && path.startsWith("/")
                    && LEGACY_API_RESOURCES.contains(path.substring(1))) {
                path = "/api" + path;
            }

            if (method.equals("OPTIONS")) {
                send(exchange, 204, "text/plain; charset=utf-8", "");
                return;
            }

            if (Api.handle(exchange, method, path)) {
                return;
            }

            if (method.equals("GET") && serveFrontend(exchange, path)) {
                return;
            }

            send(exchange, 404, "application/json; charset=utf-8",
                    Json.write(java.util.Map.of(
                            "error", "Not found",
                            "hint", "This server only exposes the JSON API. Open the frontend at http://localhost:5500"
                    )));
        } catch (Exception ex) {
            System.err.println("HTTP request failed: " + exchange.getRequestMethod() + " "
                    + exchange.getRequestURI());
            ex.printStackTrace(System.err);
            send(exchange, 500, "text/plain; charset=utf-8", "Server error. Check api.err.log for details.");
        }
    }

    private static boolean serveFrontend(HttpExchange exchange, String requestPath) throws IOException {
        if (requestPath.startsWith("/api/") || requestPath.startsWith("/uploads/")) {
            return false;
        }
        String relative = requestPath.equals("/") || requestPath.isBlank()
                ? "index.html" : requestPath.substring(1);
        relative = URLDecoder.decode(relative, StandardCharsets.UTF_8);
        Path file = FRONTEND_DIR.resolve(relative).normalize();
        if (!file.startsWith(FRONTEND_DIR) || !Files.isRegularFile(file)) {
            return false;
        }
        String contentType = contentType(file);
        byte[] content = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
        return true;
    }

    private static String contentType(Path file) throws IOException {
        String detected = Files.probeContentType(file);
        if (detected != null) return detected;
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }

    private static void serveUpload(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith("/uploads/")) {
            send(exchange, 404, "application/json; charset=utf-8", Json.write(Map.of("error", "Not found")));
            return;
        }
        String filename = path.substring("/uploads/".length());
        Path filePath = UPLOAD_DIR.resolve(filename).normalize();
        
        // Security: prevent directory traversal
        if (!filePath.startsWith(UPLOAD_DIR)) {
            send(exchange, 403, "application/json; charset=utf-8", Json.write(Map.of("error", "Forbidden")));
            return;
        }
        
        if (!Files.exists(filePath)) {
            send(exchange, 404, "application/json; charset=utf-8", Json.write(Map.of("error", "File not found")));
            return;
        }
        
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        
        byte[] content = Files.readAllBytes(filePath);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + filePath.getFileName() + "\"");
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(content);
        }
    }

    /**
     * The frontend is served from a different port, so the browser needs
     * explicit CORS permission to call this API.
     */
    private static void addCorsHeaders(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && isAllowedOrigin(origin)) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, X-Session-Token");
            exchange.getResponseHeaders().add("Access-Control-Max-Age", "86400");
        }
    }

    /** Fixed list plus any localhost/127.0.0.1 port (LAN testing, IPv6 safe). */
    private static boolean isAllowedOrigin(String origin) {
        if (ALLOWED_ORIGINS.contains(origin)) {
            return true;
        }
        return origin.matches("http://localhost(:\\d+)?")
                || origin.matches("http://127\\.0\\.0\\.1(:\\d+)?")
                || origin.matches("http://\\[::1\\](:\\d+)?")
                || java.util.Arrays.stream(setting("CRM_ALLOWED_ORIGINS", "").split(","))
                .map(String::trim).anyMatch(origin::equals);
    }
}
