package com.saivandan.crm;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    public static Map<String, String> query(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        return parsePairs(raw == null ? "" : raw);
    }

    public static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            return parseMultipart(exchange);
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parsePairs(body);
    }

    private static Map<String, String> parseMultipart(HttpExchange exchange) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String boundary = getBoundary(contentType);
        if (boundary == null) {
            return values;
        }
        
        byte[] data = exchange.getRequestBody().readAllBytes();
        String body = new String(data, StandardCharsets.ISO_8859_1);
        String delimiter = "--" + boundary;
        
        String[] parts = body.split(Pattern.quote(delimiter));
        for (String part : parts) {
            if (part.trim().isEmpty() || part.trim().equals("--")) continue;
            
            int headerEnd = part.indexOf("\r\n\r\n");
            if (headerEnd == -1) continue;
            
            String headers = part.substring(0, headerEnd);
            String content = part.substring(headerEnd + 4);
            if (content.endsWith("\r\n")) {
                content = content.substring(0, content.length() - 2);
            }
            
            String name = extractName(headers);
            if (name != null) {
                values.put(name, content);
            }
        }
        return values;
    }

    private static String getBoundary(String contentType) {
        Pattern pattern = Pattern.compile("boundary=([^;]+)");
        Matcher matcher = pattern.matcher(contentType);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private static String extractName(String headers) {
        Pattern pattern = Pattern.compile("name=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(headers);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static Map<String, String> parsePairs(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";
            values.put(key, value);
        }
        return values;
    }

    public static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static void redirect(HttpExchange exchange, String location) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Location", location);
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    public static void sendHtml(HttpExchange exchange, String html) throws IOException {
        send(exchange, 200, "text/html; charset=utf-8", html);
    }

    public static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        if (status == 204 || status == 304) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static int parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
