package com.minecraftuse.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SpotifyClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public SpotifyClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public CompletableFuture<JsonObject> nowPlaying() {
        return getAsync("/spotify/now-playing");
    }

    public CompletableFuture<JsonObject> playPause() {
        return postAsync("/spotify/playpause", "{}");
    }

    public CompletableFuture<JsonObject> next() {
        return postAsync("/spotify/next", "{}");
    }

    public CompletableFuture<JsonObject> previous() {
        return postAsync("/spotify/previous", "{}");
    }

    public CompletableFuture<JsonObject> setVolume(int volume) {
        JsonObject body = new JsonObject();
        body.addProperty("volume", volume);
        return postAsync("/spotify/volume", body.toString());
    }

    public CompletableFuture<JsonObject> playUri(String uri) {
        return playUri(uri, null, null);
    }

    public CompletableFuture<JsonObject> playUri(String uri, String contextUri, List<String> uris) {
        JsonObject body = new JsonObject();
        body.addProperty("uri", uri);
        if (contextUri != null) body.addProperty("context_uri", contextUri);
        if (uris != null && !uris.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String u : uris) arr.add(u);
            body.add("uris", arr);
        }
        return postAsync("/spotify/play-uri", body.toString());
    }

    public CompletableFuture<JsonObject> queue() {
        return getAsync("/spotify/queue");
    }

    public CompletableFuture<JsonObject> search(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return getAsync("/spotify/search?q=" + encoded + "&limit=20");
    }

    public CompletableFuture<JsonObject> authStatus() {
        return getAsync("/spotify/auth/status");
    }

    public CompletableFuture<JsonObject> authLogin() {
        return getAsync("/spotify/auth/login");
    }

    public CompletableFuture<JsonObject> playlists() {
        return getAsync("/spotify/playlists");
    }

    public CompletableFuture<JsonObject> playlistTracks(String playlistId) {
        return getAsync("/spotify/playlists/" + playlistId + "/tracks");
    }

    public CompletableFuture<JsonObject> likedTracks() {
        return getAsync("/spotify/liked");
    }

    private CompletableFuture<JsonObject> postAsync(String path, String body) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parse);
    }

    private CompletableFuture<JsonObject> getAsync(String path) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parse);
    }

    private JsonObject parse(HttpResponse<String> response) {
        if (response.statusCode() >= 400) {
            JsonObject err = new JsonObject();
            err.addProperty("error", response.statusCode());
            err.addProperty("body", response.body());
            return err;
        }
        return gson.fromJson(response.body(), JsonObject.class);
    }
}
