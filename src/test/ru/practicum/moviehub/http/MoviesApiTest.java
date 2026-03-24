package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static final String BASE_URL = "http://localhost:8080";
    private static final String CT_JSON = "application/json; charset=UTF-8";
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final String MOVIES_PATH = "/movies";
    private static MoviesServer server;
    private static HttpClient client;
    private static Gson gson;
    private static MoviesStore store;

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(UTF8));
    }

    private HttpResponse<String> sendPost(String path, Object body) throws Exception {
        String jsonBody = gson.toJson(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, UTF8))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(UTF8));
    }

    private HttpResponse<String> sendPostRaw(String path, String rawBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", CT_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(rawBody, UTF8))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(UTF8));
    }

    private HttpResponse<String> sendDelete(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .DELETE()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(UTF8));
    }

    private HttpResponse<String> sendPut(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(UTF8));
    }

    // GET /movies
    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = sendGet(MOVIES_PATH);

        assertEquals(200, resp.statusCode());
        assertEquals(CT_JSON, resp.headers().firstValue("Content-Type").orElse(""));

        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());
        assertTrue(movies.isEmpty());
    }

    @Test
    void getMovies_whenHasMovies_returnsMoviesList() throws Exception {
        store.add(new Movie("Начало", 2010));
        store.add(new Movie("Матрица", 1999));

        HttpResponse<String> resp = sendGet(MOVIES_PATH);

        assertEquals(200, resp.statusCode());
        assertEquals(CT_JSON, resp.headers().firstValue("Content-Type").orElse(""));

        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());
        assertEquals(2, movies.size());
    }

    // POST /movies
    @Test
    void postMovie_whenValid_returnsCreated() throws Exception {
        Movie newMovie = new Movie("Тёмный рыцарь", 2008);
        HttpResponse<String> resp = sendPost(MOVIES_PATH, newMovie);

        assertEquals(201, resp.statusCode());
        assertEquals(CT_JSON, resp.headers().firstValue("Content-Type").orElse(""));

        Movie created = gson.fromJson(resp.body(), Movie.class);
        assertNotNull(created.getId());
        assertEquals("Тёмный рыцарь", created.getTitle());
        assertEquals(2008, created.getYear());
    }

    @Test
    void postMovie_whenEmptyTitle_returns422() throws Exception {
        Movie invalid = new Movie("", 2020);
        HttpResponse<String> resp = sendPost(MOVIES_PATH, invalid);

        assertEquals(422, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", error.getError());
        assertTrue(error.getDetails().contains("название не должно быть пустым"));
    }

    @Test
    void postMovie_whenTitleTooLong_returns422() throws Exception {
        String longTitle = "а".repeat(101);
        Movie invalid = new Movie(longTitle, 2020);
        HttpResponse<String> resp = sendPost(MOVIES_PATH, invalid);

        assertEquals(422, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertTrue(error.getDetails().stream()
                .anyMatch(d -> d.contains("превышать 100 символов")));
    }

    @Test
    void postMovie_whenYearTooLow_returns422() throws Exception {
        Movie invalid = new Movie("Старый фильм", 1887);
        HttpResponse<String> resp = sendPost(MOVIES_PATH, invalid);

        assertEquals(422, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        int currentYear = Year.now().getValue();
        assertTrue(error.getDetails().stream()
                .anyMatch(d -> d.contains(String.format("между 1888 и %d", currentYear + 1))));
    }

    @Test
    void postMovie_whenYearTooHigh_returns422() throws Exception {
        int currentYear = Year.now().getValue();
        Movie invalid = new Movie("Фильм будущего", currentYear + 2);
        HttpResponse<String> resp = sendPost(MOVIES_PATH, invalid);

        assertEquals(422, resp.statusCode());

        ErrorResponse error = gson.fromJson(resp.body(), ErrorResponse.class);
        assertTrue(error.getDetails().stream()
                .anyMatch(d -> d.contains(String.format("между 1888 и %d", currentYear + 1))));
    }

    @Test
    void postMovie_whenWrongContentType_returns415() throws Exception {
        Movie newMovie = new Movie("Тестовый фильм", 2020);
        String jsonBody = gson.toJson(newMovie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + MOVIES_PATH))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, UTF8))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(UTF8));

        assertEquals(415, resp.statusCode());
    }

    @Test
    void postMovie_whenInvalidJson_returns400() throws Exception {
        HttpResponse<String> resp = sendPostRaw(MOVIES_PATH, "not a json");
        assertEquals(400, resp.statusCode());
    }

    //GET /movies/{id}
    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        Movie movie = store.add(new Movie("Интерстеллар", 2014));
        HttpResponse<String> resp = sendGet(MOVIES_PATH + "/" + movie.getId());
        assertEquals(200, resp.statusCode());
        assertEquals(CT_JSON, resp.headers().firstValue("Content-Type").orElse(""));
        Movie found = gson.fromJson(resp.body(), Movie.class);
        assertEquals(movie.getId(), found.getId());
        assertEquals("Интерстеллар", found.getTitle());
        assertEquals(2014, found.getYear());
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = sendGet(MOVIES_PATH + "/999");
        assertEquals(404, resp.statusCode());
    }

    @Test
    void getMovieById_whenInvalidId_returns400() throws Exception {
        HttpResponse<String> resp = sendGet(MOVIES_PATH + "/abc");
        assertEquals(400, resp.statusCode());
    }

    // DELETE /movies/{id}
    @Test
    void deleteMovieById_whenExists_returns204() throws Exception {
        Movie movie = store.add(new Movie("Бойцовский клуб", 1999));
        HttpResponse<String> resp = sendDelete(MOVIES_PATH + "/" + movie.getId());
        assertEquals(204, resp.statusCode());
        assertTrue(store.getById(movie.getId()).isEmpty());
    }

    @Test
    void deleteMovieById_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = sendDelete(MOVIES_PATH + "/999");
        assertEquals(404, resp.statusCode());
    }

    @Test
    void deleteMovieById_whenInvalidId_returns400() throws Exception {
        HttpResponse<String> resp = sendDelete(MOVIES_PATH + "/abc");
        assertEquals(400, resp.statusCode());
    }

    // GET /movies?year=YYYY
    @Test
    void getMovies_withYearFilter_returnsFilteredMovies() throws Exception {
        store.add(new Movie("Фильм 2000 А", 2000));
        store.add(new Movie("Фильм 2001", 2001));
        store.add(new Movie("Фильм 2000 Б", 2000));
        HttpResponse<String> resp = sendGet(MOVIES_PATH + "?year=2000");
        assertEquals(200, resp.statusCode());
        assertEquals(CT_JSON, resp.headers().firstValue("Content-Type").orElse(""));
        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());
        assertEquals(2, movies.size());
        assertTrue(movies.stream().allMatch(m -> m.getYear() == 2000));
    }

    @Test
    void getMovies_withYearFilterNoMatches_returnsEmptyArray() throws Exception {
        store.add(new Movie("Фильм", 2020));
        HttpResponse<String> resp = sendGet(MOVIES_PATH + "?year=1999");
        assertEquals(200, resp.statusCode());
        assertEquals(CT_JSON, resp.headers().firstValue("Content-Type").orElse(""));
        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());
        assertTrue(movies.isEmpty());
    }

    @Test
    void getMovies_withInvalidYear_returns400() throws Exception {
        HttpResponse<String> resp = sendGet(MOVIES_PATH + "?year=abc");
        assertEquals(400, resp.statusCode());
    }

    // Общие
    @Test
    void unsupportedMethod_returns405() throws Exception {
        HttpResponse<String> resp = sendPut(MOVIES_PATH);
        assertEquals(405, resp.statusCode());
    }
}