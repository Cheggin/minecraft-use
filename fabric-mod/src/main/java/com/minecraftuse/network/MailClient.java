package com.minecraftuse.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class MailClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public MailClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public CompletableFuture<JsonObject> status() {
        return getAsync("/mail/status");
    }

    public CompletableFuture<JsonObject> inbox(int limit) {
        return getAsync("/mail/inbox?limit=" + limit);
    }

    public CompletableFuture<JsonObject> message(long uid) {
        return getAsync("/mail/message/" + uid);
    }

    public CompletableFuture<JsonObject> markRead(long uid, boolean read) {
        JsonObject body = new JsonObject();
        body.addProperty("uid", uid);
        body.addProperty("read", read);
        return postAsync("/mail/mark-read", body.toString());
    }

    public CompletableFuture<JsonObject> archive(long uid) {
        JsonObject body = new JsonObject();
        body.addProperty("uid", uid);
        return postAsync("/mail/archive", body.toString());
    }

    public CompletableFuture<JsonObject> trash(long uid) {
        JsonObject body = new JsonObject();
        body.addProperty("uid", uid);
        return postAsync("/mail/trash", body.toString());
    }

    public CompletableFuture<JsonObject> send(String to, String subject, String body) {
        JsonObject b = new JsonObject();
        b.addProperty("to", to);
        b.addProperty("subject", subject);
        b.addProperty("body", body);
        return postAsync("/mail/send", b.toString());
    }

    private CompletableFuture<JsonObject> postAsync(String path, String body) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();
        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parse);
    }

    private CompletableFuture<JsonObject> getAsync(String path) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .timeout(Duration.ofSeconds(30))
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
