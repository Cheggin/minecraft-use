package com.minecraftuse.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecraftuse.mail.MailService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin async wrapper around {@link MailService}. Preserves the original
 * sidecar-era JSON shape so {@link com.minecraftuse.gui.MailScreen} keeps
 * working unchanged: each method returns a {@link CompletableFuture}
 * resolving to a {@link JsonObject} in the same shape the old
 * {@code /mail/*} HTTP endpoints produced.
 */
public class MailClient {

    private static final ExecutorService MAIL_POOL =
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "minecraft-use-mail");
            t.setDaemon(true);
            return t;
        });

    public MailClient() {}

    /** Backwards-compat constructor — baseUrl is no longer used. */
    public MailClient(String baseUrl) {
        this();
    }

    public CompletableFuture<JsonObject> status() {
        return async(() -> {
            JsonObject out = new JsonObject();
            MailService svc = MailService.get();
            out.addProperty("authenticated", svc.isConfigured());
            out.addProperty("address", svc.address());
            return out;
        });
    }

    public CompletableFuture<JsonObject> inbox(int limit) {
        return async(() -> {
            List<MailService.InboxItem> items = MailService.get().listInbox(limit);
            JsonArray arr = new JsonArray();
            for (MailService.InboxItem it : items) {
                JsonObject o = new JsonObject();
                o.addProperty("uid", it.uid());
                o.addProperty("from", it.from());
                o.addProperty("subject", it.subject());
                o.addProperty("snippet", "");
                o.addProperty("date", it.date());
                o.addProperty("read", it.read());
                arr.add(o);
            }
            JsonObject out = new JsonObject();
            out.addProperty("folder", "INBOX");
            out.add("messages", arr);
            return out;
        });
    }

    public CompletableFuture<JsonObject> message(long uid) {
        return async(() -> {
            MailService.EmailDetail d = MailService.get().getMessage(uid);
            JsonObject o = new JsonObject();
            o.addProperty("uid", d.uid());
            o.addProperty("from", d.from());
            o.addProperty("to", d.to());
            o.addProperty("subject", d.subject());
            o.addProperty("date", d.date());
            o.addProperty("body", d.body());
            o.addProperty("read", d.read());
            return o;
        });
    }

    public CompletableFuture<JsonObject> markRead(long uid, boolean read) {
        return async(() -> {
            MailService.get().markRead(uid, read);
            return ok();
        });
    }

    public CompletableFuture<JsonObject> archive(long uid) {
        return async(() -> {
            MailService.get().archive(uid);
            return ok();
        });
    }

    public CompletableFuture<JsonObject> trash(long uid) {
        return async(() -> {
            MailService.get().trash(uid);
            return ok();
        });
    }

    public CompletableFuture<JsonObject> send(String to, String subject, String body) {
        return async(() -> {
            MailService.get().send(to, subject, body);
            return ok();
        });
    }

    // ---------- internals ----------

    @FunctionalInterface
    private interface MailCall {
        JsonObject run() throws Exception;
    }

    private static CompletableFuture<JsonObject> async(MailCall call) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return call.run();
            } catch (Exception e) {
                JsonObject err = new JsonObject();
                err.addProperty("error", 500);
                err.addProperty("body", e.getMessage() == null ? e.toString() : e.getMessage());
                return err;
            }
        }, MAIL_POOL);
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }
}
