package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MoviesStore {
    private final Map<Integer, Movie> storage = new HashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public List<Movie> getAll() {
        return new ArrayList<>(storage.values());
    }

    public Movie add(Movie movie) {
        int id = nextId.getAndIncrement();
        movie.setId(id);
        storage.put(id, movie);
        return movie;
    }

    public Optional<Movie> getById(int id) {
        return Optional.ofNullable(storage.get(id));
    }

    public boolean delete(int id) {
        return storage.remove(id) != null;
    }

    public List<Movie> getByYear(int year) {
        return storage.values().stream()
                .filter(movie -> movie.getYear() == year)
                .toList();
    }

    public void clear() {
        storage.clear();
        nextId.set(1);
    }
}