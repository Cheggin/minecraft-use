package com.minecraftuse.mail;

import net.minecraft.client.MinecraftClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.sun.mail.imap.IMAPFolder;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Direct IMAP + SMTP mail handling. Replaces the Python sidecar implementation.
 *
 * - Credentials loaded from .env (walked up from Minecraft run dir), from
 *   ~/.minecraft-code/.env, or from process env vars.
 * - One long-lived IMAP store + INBOX folder; methods are synchronized and
 *   auto-reconnect once on errors.
 * - SMTP opens a fresh TLS connection per send.
 */
public final class MailService {

    private static MailService instance;

    public static synchronized MailService get() {
        if (instance == null) {
            instance = new MailService();
        }
        return instance;
    }

    private final Properties config;
    private Store store;
    private IMAPFolder inbox;
    private ScheduledExecutorService keepAlive;

    private MailService() {
        this.config = loadConfig();
    }

    /** Send a NOOP periodically so Gmail doesn't drop the idle connection. */
    private void startKeepAlive() {
        if (keepAlive != null) return;
        keepAlive = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "minecraft-use-mail-keepalive");
            t.setDaemon(true);
            return t;
        });
        keepAlive.scheduleAtFixedRate(() -> {
            synchronized (MailService.this) {
                try {
                    if (store != null && store.isConnected() && inbox != null && inbox.isOpen()) {
                        inbox.doCommand(p -> { p.simpleCommand("NOOP", null); return null; });
                    }
                } catch (Exception ignored) {
                    disconnectQuietly();
                }
            }
        }, 4, 4, TimeUnit.MINUTES);
    }

    public boolean isConfigured() {
        return !config.getProperty("GMAIL_ADDRESS", "").isEmpty()
            && !config.getProperty("GMAIL_APP_PASSWORD", "").isEmpty();
    }

    public String address() {
        return config.getProperty("GMAIL_ADDRESS", "");
    }

    /** Inbox refresh cadence (seconds) while the mail GUI is open.
     *  Configurable via MAIL_POLL_SECONDS in .env. Clamped to [1, 300]. */
    public int pollSeconds() {
        try {
            int n = Integer.parseInt(config.getProperty("MAIL_POLL_SECONDS", "1"));
            return Math.max(1, Math.min(300, n));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Fire-and-forget connection warm-up. Call from mod init so the first
     * {@code /mail} open doesn't eat the 1-3 s IMAP handshake. Also prefetches
     * the first batch of envelopes so the inbox screen renders instantly.
     * Safe to call before credentials are configured — silently no-ops.
     */
    public void warmUp() {
        if (!isConfigured()) return;
        Thread t = new Thread(() -> {
            try {
                synchronized (this) {
                    IMAPFolder f = openInbox();
                    int total = f.getMessageCount();
                    if (total > 0) {
                        int limit = 25;
                        int start = Math.max(1, total - limit + 1);
                        Message[] msgs = f.getMessages(start, total);
                        FetchProfile fp = new FetchProfile();
                        fp.add(FetchProfile.Item.ENVELOPE);
                        fp.add(FetchProfile.Item.FLAGS);
                        fp.add(UIDFolder.FetchProfileItem.UID);
                        f.fetch(msgs, fp);
                    }
                }
            } catch (Exception ignored) {}
        }, "minecraft-use-mail-warmup");
        t.setDaemon(true);
        t.start();
    }

    // ---------- public API ----------

    public synchronized List<InboxItem> listInbox(int limit) throws MessagingException {
        return withRetry(() -> {
            IMAPFolder f = openInbox();
            int total = f.getMessageCount();
            if (total <= 0) return new ArrayList<InboxItem>();
            int start = Math.max(1, total - limit + 1);
            Message[] msgs = f.getMessages(start, total);

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(FetchProfile.Item.FLAGS);
            fp.add(UIDFolder.FetchProfileItem.UID);  // prefetch UIDs in the same round-trip
            f.fetch(msgs, fp);

            List<InboxItem> out = new ArrayList<>(msgs.length);
            for (int i = msgs.length - 1; i >= 0; i--) {
                Message m = msgs[i];
                out.add(new InboxItem(
                    f.getUID(m),
                    headerFrom(m),
                    safe(m.getSubject(), "(no subject)"),
                    Instant.ofEpochMilli(m.getSentDate() != null ? m.getSentDate().getTime() : 0).toString(),
                    m.isSet(Flags.Flag.SEEN)));
            }
            return out;
        });
    }

    public synchronized EmailDetail getMessage(long uid) throws MessagingException {
        return withRetry(() -> {
            IMAPFolder f = openInbox();
            Message m = f.getMessageByUID(uid);
            if (m == null) throw new MessagingException("message " + uid + " not found");

            String body = extractBody(m);
            List<Attachment> atts = extractAttachments(m);
            return new EmailDetail(
                uid,
                headerFrom(m),
                recipientsString(m, Message.RecipientType.TO),
                safe(m.getSubject(), "(no subject)"),
                Instant.ofEpochMilli(m.getSentDate() != null ? m.getSentDate().getTime() : 0).toString(),
                body,
                m.isSet(Flags.Flag.SEEN),
                atts);
        });
    }

    public synchronized File downloadAttachment(long uid, String filename) throws MessagingException, IOException {
        try {
            return withRetry(() -> {
                try {
                    return downloadAttachmentImpl(uid, filename);
                } catch (IOException e) {
                    throw new MessagingException(e.getMessage(), e);
                }
            });
        } catch (MessagingException e) {
            if (e.getCause() instanceof IOException ioe) throw ioe;
            throw e;
        }
    }

    private File downloadAttachmentImpl(long uid, String filename) throws MessagingException, IOException {
        IMAPFolder f = openInbox();
        Message m = f.getMessageByUID(uid);
        if (m == null) throw new MessagingException("message " + uid + " not found");
        BodyPart part = findAttachmentPart(m, filename);
        if (part == null) throw new IOException("attachment not found: " + filename);

        File outDir = new File(System.getProperty("user.home"), "Downloads/mailbox");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("could not create " + outDir);
        }
        File out = new File(outDir, sanitize(filename));
        // Avoid clobbering: if target exists, append a counter.
        if (out.exists()) {
            String base = out.getName();
            int dot = base.lastIndexOf('.');
            String stem = dot > 0 ? base.substring(0, dot) : base;
            String ext = dot > 0 ? base.substring(dot) : "";
            int n = 1;
            while (out.exists()) {
                out = new File(outDir, stem + "-" + n + ext);
                n++;
            }
        }
        try (InputStream in = part.getInputStream();
             FileOutputStream os = new FileOutputStream(out)) {
            in.transferTo(os);
        }
        return out;
    }

    private static BodyPart findAttachmentPart(Part p, String filename) throws MessagingException, IOException {
        Object content = p.getContent();
        if (!(content instanceof MimeMultipart)) return null;
        MimeMultipart mp = (MimeMultipart) content;
        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart part = mp.getBodyPart(i);
            String disp = part.getDisposition();
            String fn = part.getFileName();
            if (fn != null && filename.equals(fn)
                    && (Part.ATTACHMENT.equalsIgnoreCase(disp) || Part.INLINE.equalsIgnoreCase(disp))) {
                return part;
            }
            // Recurse into nested multipart
            if (part.getContent() instanceof MimeMultipart) {
                BodyPart nested = findAttachmentPart(part, filename);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static List<Attachment> extractAttachments(Part p) {
        List<Attachment> out = new ArrayList<>();
        try {
            Object content = p.getContent();
            if (!(content instanceof MimeMultipart)) return out;
            MimeMultipart mp = (MimeMultipart) content;
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart part = mp.getBodyPart(i);
                String disp = part.getDisposition();
                String fn = part.getFileName();
                boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(disp)
                    || (Part.INLINE.equalsIgnoreCase(disp) && fn != null);
                if (isAttachment && fn != null) {
                    String ct = part.getContentType();
                    if (ct != null) {
                        int semi = ct.indexOf(';');
                        if (semi > 0) ct = ct.substring(0, semi).trim();
                    }
                    out.add(new Attachment(fn, ct == null ? "application/octet-stream" : ct, part.getSize()));
                }
                if (part.getContent() instanceof MimeMultipart) {
                    out.addAll(extractAttachments(part));
                }
            }
        } catch (IOException | MessagingException ignored) {}
        return out;
    }

    private static String sanitize(String filename) {
        // Strip path separators and null bytes to prevent directory traversal
        return filename.replaceAll("[\\\\/\\x00]", "_");
    }

    public synchronized void markRead(long uid, boolean read) throws MessagingException {
        withRetry(() -> {
            IMAPFolder f = openInbox();
            Message m = f.getMessageByUID(uid);
            if (m != null) m.setFlag(Flags.Flag.SEEN, read);
            return null;
        });
    }

    public synchronized void archive(long uid) throws MessagingException {
        moveTo(uid, config.getProperty("MAIL_ARCHIVE_FOLDER", "[Gmail]/All Mail"));
    }

    public synchronized void trash(long uid) throws MessagingException {
        moveTo(uid, config.getProperty("MAIL_TRASH_FOLDER", "[Gmail]/Trash"));
    }

    public void send(String to, String subject, String body) throws MessagingException {
        if (!isConfigured()) throw new MessagingException("mail not configured");

        String addr = config.getProperty("GMAIL_ADDRESS");
        String pw = config.getProperty("GMAIL_APP_PASSWORD", "").replace(" ", "");
        String host = config.getProperty("SMTP_HOST", "smtp.gmail.com");
        int port = Integer.parseInt(config.getProperty("SMTP_PORT", "587"));

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(addr, pw);
            }
        });

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(addr));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        msg.setSubject(subject);
        msg.setText(body);
        Transport.send(msg);
    }

    // ---------- internals ----------

    private void moveTo(long uid, String destFolderName) throws MessagingException {
        withRetry(() -> {
            IMAPFolder f = openInbox();
            Message m = f.getMessageByUID(uid);
            if (m == null) return null;
            Folder dest = store.getFolder(destFolderName);
            // IMAPFolder.copyMessages + deleteMessages approximates a move for
            // providers that don't support MOVE natively; Gmail's label model
            // treats this as an archive (remove INBOX label).
            f.copyMessages(new Message[] { m }, dest);
            m.setFlag(Flags.Flag.DELETED, true);
            f.expunge();
            return null;
        });
    }

    private IMAPFolder openInbox() throws MessagingException {
        if (store == null || !store.isConnected()) connect();
        if (inbox == null || !inbox.isOpen()) {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            inbox = (IMAPFolder) folder;
        }
        return inbox;
    }

    private void connect() throws MessagingException {
        if (!isConfigured()) {
            throw new MessagingException("GMAIL_ADDRESS and GMAIL_APP_PASSWORD must be set");
        }
        String addr = config.getProperty("GMAIL_ADDRESS");
        String pw = config.getProperty("GMAIL_APP_PASSWORD", "").replace(" ", "");
        String host = config.getProperty("IMAP_HOST", "imap.gmail.com");

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "10000");

        Session session = Session.getInstance(props);
        store = session.getStore("imaps");
        store.connect(host, addr, pw);
        startKeepAlive();
    }

    private void disconnectQuietly() {
        try { if (inbox != null && inbox.isOpen()) inbox.close(false); } catch (Exception ignored) {}
        try { if (store != null && store.isConnected()) store.close(); } catch (Exception ignored) {}
        inbox = null;
        store = null;
    }

    @FunctionalInterface
    private interface ImapOp<T> { T run() throws MessagingException; }

    private <T> T withRetry(ImapOp<T> op) throws MessagingException {
        try {
            return op.run();
        } catch (FolderClosedException | StoreClosedException e) {
            disconnectQuietly();
            return op.run();
        } catch (MessagingException e) {
            // Try once more after a full reconnect
            disconnectQuietly();
            return op.run();
        }
    }

    // ---------- helpers ----------

    private static String headerFrom(Message m) throws MessagingException {
        Address[] a = m.getFrom();
        return (a != null && a.length > 0) ? a[0].toString() : "";
    }

    private static String recipientsString(Message m, Message.RecipientType t) throws MessagingException {
        Address[] a = m.getRecipients(t);
        if (a == null || a.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(a[i].toString());
        }
        return sb.toString();
    }

    private static String safe(String s, String fallback) {
        return (s == null || s.isEmpty()) ? fallback : s;
    }

    private static String extractBody(Part p) {
        try {
            Object content = p.getContent();
            if (content instanceof String) {
                String s = (String) content;
                if (p.isMimeType("text/html")) return normalize(htmlToText(s));
                return normalize(s);
            }
            if (content instanceof MimeMultipart) {
                MimeMultipart mp = (MimeMultipart) content;
                // Prefer text/plain, then fall back to first text/html
                String html = null;
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart part = mp.getBodyPart(i);
                    if (part.isMimeType("text/plain")) {
                        return normalize((String) part.getContent());
                    }
                    if (part.isMimeType("text/html") && html == null) {
                        html = (String) part.getContent();
                    }
                    if (part.getContent() instanceof MimeMultipart) {
                        String nested = extractBody(part);
                        if (!nested.isEmpty()) return nested;
                    }
                }
                if (html != null) return normalize(htmlToText(html));
            }
        } catch (IOException | MessagingException e) {
            return "";
        }
        return "";
    }

    private static String htmlToText(String html) {
        if (html == null || html.isEmpty()) return "";
        Document doc = Jsoup.parse(html);
        doc.select("script, style").remove();
        return doc.wholeText();
    }

    private static String normalize(String text) {
        if (text == null) return "";
        String s = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t' || c == '\n' || c >= 0x20) out.append(c);
        }
        return out.toString();
    }

    // ---------- config discovery ----------

    private static Properties loadConfig() {
        Properties p = new Properties();

        // 1. Explicit override
        String explicit = System.getenv("MINECRAFT_CODE_ENV");
        if (explicit != null && !explicit.isEmpty()) loadEnvFile(new File(explicit), p);

        // 2. ~/.minecraft-code/.env
        loadEnvFile(new File(System.getProperty("user.home"), ".minecraft-code/.env"), p);

        // 3. Walk up from Minecraft run dir looking for .env (useful in dev)
        try {
            File dir = MinecraftClient.getInstance().runDirectory;
            if (dir != null) dir = dir.getAbsoluteFile();
            for (int i = 0; i < 6 && dir != null; i++) {
                File env = new File(dir, ".env");
                if (env.exists()) { loadEnvFile(env, p); break; }
                dir = dir.getParentFile();
            }
        } catch (Throwable ignored) {
            // MinecraftClient may not be available in some test contexts
        }

        // 4. Process env always wins for configured keys
        String[] keys = {
            "GMAIL_ADDRESS", "GMAIL_APP_PASSWORD",
            "IMAP_HOST", "SMTP_HOST", "SMTP_PORT",
            "MAIL_ARCHIVE_FOLDER", "MAIL_TRASH_FOLDER",
            "MAIL_POLL_SECONDS",
        };
        for (String k : keys) {
            String v = System.getenv(k);
            if (v != null && !v.isEmpty()) p.setProperty(k, v);
        }
        return p;
    }

    private static void loadEnvFile(File f, Properties into) {
        if (f == null || !f.exists() || !f.isFile()) return;
        try (FileInputStream in = new FileInputStream(f)) {
            Properties raw = new Properties() {
                @Override
                public synchronized Object put(Object key, Object value) {
                    String v = value == null ? "" : value.toString().trim();
                    // Strip matching surrounding quotes
                    if (v.length() >= 2
                        && ((v.startsWith("\"") && v.endsWith("\""))
                            || (v.startsWith("'") && v.endsWith("'")))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    return super.put(key, v);
                }
            };
            raw.load(in);
            for (String name : raw.stringPropertyNames()) {
                if (!into.containsKey(name)) into.setProperty(name, raw.getProperty(name));
            }
        } catch (IOException ignored) {}
    }

    // ---------- result types ----------

    public record InboxItem(long uid, String from, String subject,
                             String date, boolean read) {}

    public record EmailDetail(long uid, String from, String to,
                               String subject, String date, String body,
                               boolean read, List<Attachment> attachments) {}

    public record Attachment(String filename, String contentType, long size) {}
}
