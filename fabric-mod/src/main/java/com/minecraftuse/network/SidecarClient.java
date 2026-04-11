package com.minecraftuse.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecraftuse.MinecraftUseMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class SidecarClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson;

    public SidecarClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = new Gson();
    }

    public CompletableFuture<JsonArray> search(String query) {
        JsonObject body = new JsonObject();
        body.addProperty("query", query);

        return postAsync("/search", body.toString())
                .thenApply(response -> gson.fromJson(response, JsonObject.class).getAsJsonArray("results"));
    }

    public CompletableFuture<JsonObject> download(String query) {
        JsonObject body = new JsonObject();
        body.addProperty("query", query);

        return postAsync("/download", body.toString())
                .thenApply(response -> gson.fromJson(response, JsonObject.class));
    }

    public CompletableFuture<JsonArray> list() {
        return getAsync("/list")
                .thenApply(response -> gson.fromJson(response, JsonObject.class).getAsJsonArray("schematics"));
    }

    public CompletableFuture<Boolean> ping() {
        return getAsync("/ping")
                .thenApply(response -> true)
                .exceptionally(e -> false);
    }

    private CompletableFuture<String> postAsync(String path, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(120))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Sidecar returned " + response.statusCode() + ": " + response.body());
                    }
                    return response.body();
                });
    }

    private CompletableFuture<String> getAsync(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Sidecar returned " + response.statusCode() + ": " + response.body());
                    }
                    return response.body();
                });
    }
}
