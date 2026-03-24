package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.util.Optional;

public class MovieDetailsHandler extends BaseHttpHandler {
    private final MoviesStore store;

    public MovieDetailsHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();

        if (!requestPath.startsWith(MOVIES_PATH + "/")) {
            exchange.sendResponseHeaders(404, -1);
            exchange.getResponseBody().close();
            return;
        }

        String idString = requestPath.substring((MOVIES_PATH +"/").length());
        int movieId;
        try {
            movieId = Integer.parseInt(idString);
        } catch (NumberFormatException exception) {
            sendError(exchange, 400, "Некорректный ID");
            return;
        }

        String httpMethod = exchange.getRequestMethod();

        switch (httpMethod.toUpperCase()) {
            case "GET":
                handleGetById(exchange, movieId);
                break;
            case "DELETE":
                handleDeleteById(exchange, movieId);
                break;
            default:
                exchange.sendResponseHeaders(405, -1);
                exchange.getResponseBody().close();
                break;
        }
    }

    private void handleGetById(HttpExchange exchange, int movieId) throws IOException {
        Optional<Movie> foundMovie = store.getById(movieId);
        if (foundMovie.isPresent()) {
            String jsonResponse = gson.toJson(foundMovie.get());
            sendJson(exchange, 200, jsonResponse);
        } else {
            sendError(exchange, 404, "Фильм не найден");
        }
    }

    private void handleDeleteById(HttpExchange exchange, int movieId) throws IOException {
        boolean wasDeleted = store.delete(movieId);
        if (wasDeleted) {
            sendNoContent(exchange);
        } else {
            sendError(exchange, 404, "Фильм не найден");
        }
    }
}