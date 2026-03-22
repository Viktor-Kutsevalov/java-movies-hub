package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;
    private static final int MIN_YEAR = 1888;
    private static final int MAX_TITLE_LENGTH = 100;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();

        if (!requestPath.equals("/movies")) {
            exchange.sendResponseHeaders(404, -1);
            exchange.getResponseBody().close();
            return;
        }

        String httpMethod = exchange.getRequestMethod();

        switch (httpMethod.toUpperCase()) {
            case "GET":
                handleGetAll(exchange);
                break;
            case "POST":
                handleCreateMovie(exchange);
                break;
            default:
                exchange.sendResponseHeaders(405, -1);
                exchange.getResponseBody().close();
                break;
        }
    }

    private void handleGetAll(HttpExchange exchange) throws IOException {
        String queryString = exchange.getRequestURI().getQuery();
        List<Movie> resultMovies;

        if (queryString != null && !queryString.isEmpty()) {
            Map<String, String> queryParams = parseQueryParams(queryString);
            if (queryParams.containsKey("year")) {
                String yearValue = queryParams.get("year");
                try {
                    int targetYear = Integer.parseInt(yearValue);
                    resultMovies = store.getByYear(targetYear);
                } catch (NumberFormatException exception) {
                    sendError(exchange, 400, "Некорректный параметр запроса — 'year'");
                    return;
                }
            } else {
                resultMovies = store.getAll();
            }
        } else {
            resultMovies = store.getAll();
        }

        String jsonResponse = gson.toJson(resultMovies);
        sendJson(exchange, 200, jsonResponse);
    }

    private void handleCreateMovie(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendError(exchange, 415, "Unsupported Media Type");
            return;
        }

        String requestBody = readBody(exchange);

        if (requestBody == null || requestBody.isBlank()) {
            sendError(exchange, 400, "Тело запроса не может быть пустым");
            return;
        }

        Movie incomingMovie;
        try {
            incomingMovie = gson.fromJson(requestBody, Movie.class);
        } catch (Exception parsingError) {
            sendError(exchange, 400, "Некорректный JSON");
            return;
        }

        List<String> validationErrors = validateMovie(incomingMovie);
        if (!validationErrors.isEmpty()) {
            ErrorResponse detailedError = new ErrorResponse("Ошибка валидации", validationErrors);
            String jsonError = gson.toJson(detailedError);
            sendJson(exchange, 422, jsonError);
            return;
        }

        Movie savedMovie = store.add(incomingMovie);
        String jsonResponse = gson.toJson(savedMovie);
        sendJson(exchange, 201, jsonResponse);
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie == null) {
            errors.add("Данные фильма отсутствуют");
            return errors;
        }

        int currentYear = Year.now().getValue();

        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > MAX_TITLE_LENGTH) {
            errors.add("название не должно превышать 100 символов");
        }

        if (movie.getYear() < MIN_YEAR || movie.getYear() > currentYear + 1) {
            errors.add(String.format("год должен быть между %d и %d", MIN_YEAR, currentYear + 1));
        }

        return errors;
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> parameters = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                parameters.put(keyValue[0], keyValue[1]);
            }
        }
        return parameters;
    }
}