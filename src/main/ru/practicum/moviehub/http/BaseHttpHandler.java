package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.api.ErrorResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected final Gson gson = new Gson();
    protected static final Charset UTF8 = StandardCharsets.UTF_8;
    protected static final String MOVIES_PATH = "/movies";

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        byte[] responseBody = json.getBytes(UTF8);
        ex.sendResponseHeaders(status, responseBody.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(responseBody);
        }
    }

    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(204, -1);
        try (OutputStream os = ex.getResponseBody()) {
            os.close();
        }
    }

    protected void sendError(HttpExchange ex, int status, String message) throws IOException {
        ErrorResponse error = new ErrorResponse(message, null);
        String json = gson.toJson(error);
        sendJson(ex, status, json);
    }

    protected String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), UTF8);
    }
}