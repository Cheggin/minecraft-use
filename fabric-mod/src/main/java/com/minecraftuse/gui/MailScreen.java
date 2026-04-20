package com.minecraftuse.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecraftuse.network.MailClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailScreen extends Screen {

    private static final int MAX_PW = 460;
    private static final int MIN_PW = 360;
    private static final int PANEL_HEIGHT_MAX = 340;
    private static final int PANEL_HEIGHT_MIN = 260;

    // Layout Y-offsets relative to panel top
    private static final int STATUS_Y = 36;                    // account email + counts
    private static final int SEARCH_LABEL_Y = 60;              // "INBOX" / filter label
    private static final int SEARCH_FIELD_Y = 74;
    private static final int LIST_Y = 102;

    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 26;

    private static final int BG_COLOR = 0xCC0A0A12;
    private static final int PANEL_COLOR = 0xFF161622;
    private static final int CARD_COLOR = 0xFF1F1F2E;
    private static final int CARD_BORDER = 0xFF333355;
    private static final int ACCENT = 0xFFE8D5A0;           // warm parchment
    private static final int ACCENT_DIM = 0xFF8A7A54;
    private static final int UNREAD_DOT = 0xFF1DB954;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int MUTED_COLOR = 0xFF888888;
    private static final int LABEL_COLOR = 0xFFAAAA00;

    private static final int POLL_INTERVAL_TICKS = 600;      // 30 seconds
    private static final int BODY_LINE_HEIGHT = 10;

    private static final Pattern URL_PATTERN = Pattern.compile(
        "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
        Pattern.CASE_INSENSITIVE);

    private final MailClient client;

    private TextFieldWidget searchField;
    private ButtonWidget backButton;
    private ButtonWidget archiveButton;
    private ButtonWidget trashButton;
    private ButtonWidget toggleReadButton;
    private ButtonWidget composeButton;
    private ButtonWidget replyButton;

    private TextFieldWidget toField;
    private TextFieldWidget subjectField;
    private EditBoxWidget bodyField;
    private ButtonWidget sendButton;
    private ButtonWidget cancelComposeButton;
    private boolean composing = false;
    private boolean sending = false;

    private List<InboxItem> items = new ArrayList<>();
    private int listScroll = 0;
    private int tickCount = 0;
    private String statusText = "Loading\u2026";
    private String accountEmail = "";
    private boolean authenticated = false;

    private EmailDetail detail = null;
    private List<String> detailWrappedBody = List.of();
    private int detailScroll = 0;
    private boolean inboxLoaded = false;

    private int panelHeight() {
        return Math.max(PANEL_HEIGHT_MIN, Math.min(PANEL_HEIGHT_MAX, height - 16));
    }

    private int panelWidth() {
        return Math.max(MIN_PW, Math.min(MAX_PW, width - 16));
    }

    public MailScreen(MailClient client) {
        super(Text.literal("Mailbox"));
        this.client = client;
    }

    @Override
    protected void init() {
        int panelX = (width - panelWidth()) / 2;
        int panelY = (height - panelHeight()) / 2;

        searchField = new TextFieldWidget(textRenderer,
            panelX + PADDING + 8, panelY + SEARCH_FIELD_Y,
            panelWidth() - PADDING * 2 - 16, 20, Text.literal(""));
        searchField.setPlaceholder(Text.literal("Filter inbox\u2026"));
        searchField.setMaxLength(128);
        searchField.setChangedListener(s -> listScroll = 0);
        addDrawableChild(searchField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"),
            b -> close()).dimensions(panelX + panelWidth() - 60, panelY + 6, 52, 18).build());

        composeButton = ButtonWidget.builder(Text.literal("\u270E Compose"),
            b -> openCompose(null, null, null))
            .dimensions(panelX + panelWidth() - 132, panelY + 6, 66, 18).build();
        addDrawableChild(composeButton);

        backButton = ButtonWidget.builder(Text.literal("\u2190 Back"),
            b -> exitDetail())
            .dimensions(panelX + PADDING + 8, panelY + SEARCH_FIELD_Y, 60, 20).build();
        backButton.visible = false;
        addDrawableChild(backButton);

        replyButton = ButtonWidget.builder(Text.literal("Reply"),
            b -> openComposeAsReply())
            .dimensions(panelX + PADDING + 164, panelY + SEARCH_FIELD_Y, 58, 20).build();
        replyButton.visible = false;
        addDrawableChild(replyButton);

        toggleReadButton = ButtonWidget.builder(Text.literal("Mark unread"),
            b -> toggleCurrentRead())
            .dimensions(panelX + PADDING + 76, panelY + SEARCH_FIELD_Y, 80, 20).build();
        toggleReadButton.visible = false;
        addDrawableChild(toggleReadButton);

        archiveButton = ButtonWidget.builder(Text.literal("Archive"),
            b -> archiveCurrent())
            .dimensions(panelX + panelWidth() - PADDING - 148, panelY + SEARCH_FIELD_Y, 68, 20).build();
        archiveButton.visible = false;
        addDrawableChild(archiveButton);

        trashButton = ButtonWidget.builder(Text.literal("Trash"),
            b -> trashCurrent())
            .dimensions(panelX + panelWidth() - PADDING - 76, panelY + SEARCH_FIELD_Y, 68, 20).build();
        trashButton.visible = false;
        addDrawableChild(trashButton);

        // ---- Compose mode widgets (hidden by default) ----
        int composeLeft = panelX + PADDING + 8;
        int composeWidth = panelWidth() - PADDING * 2 - 16;

        toField = new TextFieldWidget(textRenderer,
            composeLeft, panelY + 78, composeWidth, 20,
            Text.literal(""));
        toField.setPlaceholder(Text.literal("recipient@example.com"));
        toField.setMaxLength(256);
        toField.visible = false;
        addDrawableChild(toField);

        subjectField = new TextFieldWidget(textRenderer,
            composeLeft, panelY + 114, composeWidth, 20,
            Text.literal(""));
        subjectField.setPlaceholder(Text.literal("Subject"));
        subjectField.setMaxLength(256);
        subjectField.visible = false;
        addDrawableChild(subjectField);

        int bodyTop = panelY + 150;
        // Leave extra room below the body: EditBoxWidget draws its "N/max" counter
        // below its visible bounds, and the Send / Cancel buttons need clearance.
        int bodyBottom = panelY + panelHeight() - PADDING - 48;
        bodyField = new EditBoxWidget(textRenderer,
            composeLeft, bodyTop, composeWidth, Math.max(40, bodyBottom - bodyTop),
            Text.literal("Write your message\u2026"),
            Text.literal(""));
        bodyField.setMaxLength(8192);
        bodyField.visible = false;
        addDrawableChild(bodyField);

        cancelComposeButton = ButtonWidget.builder(Text.literal("Cancel"),
            b -> exitCompose())
            .dimensions(panelX + panelWidth() - PADDING - 136, panelY + panelHeight() - PADDING - 24,
                64, 20).build();
        cancelComposeButton.visible = false;
        addDrawableChild(cancelComposeButton);

        sendButton = ButtonWidget.builder(Text.literal("Send"),
            b -> doSend())
            .dimensions(panelX + panelWidth() - PADDING - 68, panelY + panelHeight() - PADDING - 24,
                64, 20).build();
        sendButton.visible = false;
        addDrawableChild(sendButton);

        checkStatus();
        refreshInbox();
    }

    @Override
    public void tick() {
        tickCount++;
        if (detail == null && tickCount >= POLL_INTERVAL_TICKS) {
            tickCount = 0;
            refreshInbox();
        }
    }

    // ---------- data loading ----------

    private void checkStatus() {
        client.status().thenAccept(json -> MinecraftClient.getInstance().execute(() -> {
            authenticated = json != null && json.has("authenticated")
                && json.get("authenticated").getAsBoolean();
            accountEmail = json != null && json.has("address")
                ? json.get("address").getAsString() : "";
        })).exceptionally(err -> null);
    }

    private void refreshInbox() {
        statusText = "Loading\u2026";
        client.inbox(25).thenAccept(json -> MinecraftClient.getInstance().execute(() -> {
            if (json == null || json.has("error")) {
                statusText = "Failed to load inbox";
                return;
            }
            JsonArray msgs = json.has("messages")
                ? json.getAsJsonArray("messages") : new JsonArray();
            List<InboxItem> next = new ArrayList<>();
            for (int i = 0; i < msgs.size(); i++) {
                next.add(InboxItem.from(msgs.get(i).getAsJsonObject()));
            }
            items = next;
            inboxLoaded = true;
            int unread = 0;
            for (InboxItem it : items) if (!it.read) unread++;
            statusText = items.size() + " messages \u00B7 " + unread + " unread";
        })).exceptionally(err -> {
            MinecraftClient.getInstance().execute(() -> statusText = "Failed to load");
            return null;
        });
    }

    private void openMessage(InboxItem item) {
        statusText = "Opening\u2026";
        client.message(item.uid).thenAccept(json -> MinecraftClient.getInstance().execute(() -> {
            if (json == null || json.has("error")) {
                statusText = "Failed to load message";
                return;
            }
            detail = EmailDetail.from(json);
            detailScroll = 0;
            rewrapDetailBody();
            applyDetailVisibility();
            statusText = "";
            // Mark read in the background if not already
            if (!item.read) {
                client.markRead(item.uid, true).thenRun(() -> {});
                item.read = true;
            }
        })).exceptionally(err -> {
            MinecraftClient.getInstance().execute(() -> statusText = "Failed to load");
            return null;
        });
    }

    private void exitDetail() {
        detail = null;
        detailWrappedBody = List.of();
        detailScroll = 0;
        applyDetailVisibility();
        refreshInbox();
    }

    private void archiveCurrent() {
        if (detail == null) return;
        long uid = detail.uid;
        items.removeIf(it -> it.uid == uid);
        detail = null;
        applyDetailVisibility();
        client.archive(uid).thenRun(() -> {});
        statusText = "Archived";
        refreshInbox();
    }

    private void trashCurrent() {
        if (detail == null) return;
        long uid = detail.uid;
        items.removeIf(it -> it.uid == uid);
        detail = null;
        applyDetailVisibility();
        client.trash(uid).thenRun(() -> {});
        statusText = "Moved to Trash";
        refreshInbox();
    }

    private void toggleCurrentRead() {
        if (detail == null) return;
        boolean next = !detail.read;
        detail.read = next;
        client.markRead(detail.uid, next).thenRun(() -> {});
        for (InboxItem it : items) if (it.uid == detail.uid) it.read = next;
        toggleReadButton.setMessage(Text.literal(next ? "Mark unread" : "Mark read"));
    }

    private void applyDetailVisibility() {
        boolean inDetail = detail != null && !composing;
        boolean inList = detail == null && !composing;

        // Title-bar Compose button — only visible in LIST view
        composeButton.visible = inList;

        // LIST widgets
        searchField.visible = inList;

        // DETAIL widgets
        backButton.visible = inDetail;
        archiveButton.visible = inDetail;
        trashButton.visible = inDetail;
        toggleReadButton.visible = inDetail;
        replyButton.visible = inDetail;

        // COMPOSE widgets
        toField.visible = composing;
        subjectField.visible = composing;
        bodyField.visible = composing;
        sendButton.visible = composing;
        sendButton.active = composing && !sending;
        cancelComposeButton.visible = composing;

        if (detail != null && !composing) {
            toggleReadButton.setMessage(Text.literal(detail.read ? "Mark unread" : "Mark read"));
        }
    }

    // ---------- compose ----------

    private void openCompose(String to, String subject, String body) {
        composing = true;
        toField.setText(to == null ? "" : to);
        subjectField.setText(subject == null ? "" : subject);
        bodyField.setText(body == null ? "" : body);
        sending = false;
        statusText = "";
        applyDetailVisibility();
        setInitialFocus(toField);
    }

    private void openComposeAsReply() {
        if (detail == null) return;
        String to = extractEmail(detail.fromAddr);
        String subject = detail.subject == null ? "" : detail.subject;
        if (!subject.toLowerCase().startsWith("re:")) subject = "Re: " + subject;

        StringBuilder quoted = new StringBuilder("\n\n");
        String when = detail.date == null ? "" : detail.date;
        quoted.append("On ").append(when).append(", ").append(detail.fromAddr).append(" wrote:\n");
        for (String line : (detail.body == null ? "" : detail.body).split("\n", -1)) {
            quoted.append("> ").append(line).append("\n");
        }

        // Leave detail loaded so Cancel returns to it
        openCompose(to, subject, quoted.toString());
    }

    private void exitCompose() {
        composing = false;
        sending = false;
        toField.setText("");
        subjectField.setText("");
        bodyField.setText("");
        statusText = "";
        applyDetailVisibility();
    }

    private void doSend() {
        if (sending) return;
        String to = toField.getText().trim();
        String subject = subjectField.getText().trim();
        String body = bodyField.getText();
        if (to.isEmpty()) { statusText = "Recipient required"; return; }
        if (subject.isEmpty()) subject = "(no subject)";

        sending = true;
        sendButton.active = false;
        statusText = "Sending\u2026";
        client.send(to, subject, body).thenAccept(json -> MinecraftClient.getInstance().execute(() -> {
            if (json != null && json.has("error")) {
                sending = false;
                sendButton.active = true;
                statusText = "Send failed";
                return;
            }
            statusText = "Sent";
            exitCompose();
            refreshInbox();
        })).exceptionally(err -> {
            MinecraftClient.getInstance().execute(() -> {
                sending = false;
                sendButton.active = true;
                statusText = "Send failed";
            });
            return null;
        });
    }

    private static String extractEmail(String addr) {
        if (addr == null) return "";
        int lt = addr.indexOf('<');
        int gt = addr.indexOf('>', lt);
        if (lt >= 0 && gt > lt) return addr.substring(lt + 1, gt).trim();
        return addr.trim();
    }

    // ---------- input ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // URL clicks in the detail body
        if (detail != null && button == 0 && !detailWrappedBody.isEmpty()) {
            int ph = panelHeight();
            int panelX = (width - panelWidth()) / 2;
            int panelY = (height - ph) / 2;
            int bodyX = panelX + PADDING + 8;
            int bodyY = panelY + LIST_Y + 54 + 6;
            int bodyW = panelWidth() - PADDING * 2 - 16;
            int bodyH = panelY + ph - PADDING - bodyY;
            int textLeft = bodyX + 8;
            int textTop = bodyY + 8;
            int textRight = bodyX + bodyW - 8;
            int textBottom = bodyY + bodyH - 8;

            if (mouseX >= textLeft && mouseX < textRight
                    && mouseY >= textTop && mouseY < textBottom) {
                int lineIdx = ((int) (mouseY - textTop)) / BODY_LINE_HEIGHT + detailScroll;
                if (lineIdx >= 0 && lineIdx < detailWrappedBody.size()) {
                    Text styled = styleLine(detailWrappedBody.get(lineIdx));
                    int pixelX = (int) (mouseX - textLeft);
                    Style s = textRenderer.getTextHandler().getStyleAt(styled, pixelX);
                    if (s != null && s.getClickEvent() != null
                            && handleTextClick(s)) {
                        return true;
                    }
                }
            }
        }

        if (detail == null) {
            int ph = panelHeight();
            int panelX = (width - panelWidth()) / 2;
            int panelY = (height - ph) / 2;
            int listX = panelX + PADDING + 8;
            int listY = panelY + LIST_Y;
            int listW = panelWidth() - PADDING * 2 - 16;
            int listH = panelY + ph - PADDING - listY;

            List<InboxItem> visible = displayItems();
            if (button == 0 && !visible.isEmpty()
                    && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= listY && mouseY < listY + listH) {
                int index = ((int) (mouseY - listY)) / ROW_HEIGHT + listScroll;
                if (index >= 0 && index < visible.size()) {
                    openMessage(visible.get(index));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int dy = -(int) verticalAmount;
        if (detail != null) {
            detailScroll = Math.max(0,
                Math.min(Math.max(0, detailWrappedBody.size() - 1), detailScroll + dy));
            return true;
        }
        List<InboxItem> visible = displayItems();
        if (visible.isEmpty()) return false;
        listScroll = Math.max(0, Math.min(visible.size() - 1, listScroll + dy));
        return true;
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        rewrapDetailBody();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------- rendering ----------

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // overridden to skip default blur
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BG_COLOR);

        int ph = panelHeight();
        int panelX = (width - panelWidth()) / 2;
        int panelY = (height - ph) / 2;

        context.fill(panelX - 1, panelY - 1, panelX + panelWidth() + 1, panelY + ph + 1, CARD_BORDER);
        context.fill(panelX, panelY, panelX + panelWidth(), panelY + ph, PANEL_COLOR);

        // Title bar
        context.fill(panelX, panelY, panelX + panelWidth(), panelY + 28, CARD_COLOR);
        context.drawText(textRenderer, Text.literal("\u2709 MAILBOX"),
            panelX + PADDING, panelY + 10, ACCENT, true);

        // Status line
        String statusLine = accountEmail.isEmpty() ? "Not signed in" : accountEmail;
        context.drawText(textRenderer, Text.literal(statusLine),
            panelX + PADDING + 8, panelY + STATUS_Y, TEXT_COLOR, false);
        if (!statusText.isEmpty()) {
            int sw = textRenderer.getWidth(statusText);
            context.drawText(textRenderer, Text.literal(statusText),
                panelX + panelWidth() - PADDING - 8 - sw,
                panelY + STATUS_Y, MUTED_COLOR, false);
        }

        if (composing) {
            renderCompose(context, panelX, panelY);
        } else if (detail != null) {
            renderDetail(context, panelX, panelY, mouseX, mouseY);
        } else {
            renderInbox(context, panelX, panelY, mouseX, mouseY);
        }

        // Widgets on top
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderInbox(DrawContext context, int panelX, int panelY, int mouseX, int mouseY) {
        // Section label
        context.drawText(textRenderer, Text.literal("INBOX"),
            panelX + PADDING + 8, panelY + SEARCH_LABEL_Y, LABEL_COLOR, false);

        // Search field card behind the field
        int sfX = searchField.getX();
        int sfY = searchField.getY();
        int sfW = searchField.getWidth();
        int sfH = searchField.getHeight();
        boolean focused = searchField.isFocused();
        context.fill(sfX - 2, sfY - 2, sfX + sfW + 2, sfY + sfH + 2,
            focused ? ACCENT : CARD_BORDER);
        context.fill(sfX - 1, sfY - 1, sfX + sfW + 1, sfY + sfH + 1, 0xFF0E0E18);

        int listX = panelX + PADDING + 8;
        int listY = panelY + LIST_Y;
        int listW = panelWidth() - PADDING * 2 - 16;
        int listH = panelY + panelHeight() - PADDING - listY;
        context.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, CARD_BORDER);
        context.fill(listX, listY, listX + listW, listY + listH, CARD_COLOR);

        List<InboxItem> visible = displayItems();
        if (visible.isEmpty()) {
            String hint;
            if (!inboxLoaded) hint = "Loading\u2026";
            else if (items.isEmpty()) hint = authenticated ? "Inbox is empty" : "Not authenticated";
            else hint = "No matches";
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(hint),
                listX + listW / 2, listY + listH / 2 - 4, MUTED_COLOR);
            return;
        }

        int visibleRows = listH / ROW_HEIGHT;
        for (int i = 0; i < visibleRows && (i + listScroll) < visible.size(); i++) {
            InboxItem it = visible.get(i + listScroll);
            int rowY = listY + i * ROW_HEIGHT;
            boolean hover = mouseX >= listX && mouseX < listX + listW
                && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hover) {
                context.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT, 0xFF2A2A45);
            }

            ItemStack icon = new ItemStack(it.read ? Items.WRITTEN_BOOK : Items.WRITABLE_BOOK);
            context.drawItem(icon, listX + 4, rowY + 5);

            if (!it.read) {
                // Tiny unread dot
                context.fill(listX + 20, rowY + 6, listX + 22, rowY + 8, UNREAD_DOT);
            }

            int textX = listX + 26;
            int senderW = Math.min(170, listW / 3);
            int subjectW = listW - senderW - 80;

            int senderColor = it.read ? MUTED_COLOR : TITLE_COLOR;
            int subjectColor = it.read ? TEXT_COLOR : TITLE_COLOR;

            context.drawText(textRenderer, Text.literal(truncate(it.fromDisplay(), senderW)),
                textX, rowY + 4, senderColor, !it.read);
            context.drawText(textRenderer, Text.literal(truncate(it.subject, subjectW)),
                textX + senderW + 6, rowY + 4, subjectColor, !it.read);

            context.drawText(textRenderer, Text.literal(truncate(it.snippet, listW - 32)),
                textX, rowY + 14, MUTED_COLOR, false);

            // Relative date right-aligned
            String when = it.shortDate();
            int ww = textRenderer.getWidth(when);
            context.drawText(textRenderer, Text.literal(when),
                listX + listW - 6 - ww, rowY + 4, MUTED_COLOR, false);
        }

        if (visible.size() > visibleRows) {
            String ind = (listScroll + 1) + "\u2013"
                + Math.min(listScroll + visibleRows, visible.size())
                + " / " + visible.size();
            context.drawText(textRenderer, Text.literal(ind),
                listX + listW - textRenderer.getWidth(ind) - 4,
                listY + listH - 12, MUTED_COLOR, false);
        }
    }

    private void renderCompose(DrawContext context, int panelX, int panelY) {
        // Section label
        context.drawText(textRenderer, Text.literal("NEW MESSAGE"),
            panelX + PADDING + 8, panelY + 60, LABEL_COLOR, false);

        // Field labels
        context.drawText(textRenderer, Text.literal("To"),
            panelX + PADDING + 8, panelY + 68, MUTED_COLOR, false);
        context.drawText(textRenderer, Text.literal("Subject"),
            panelX + PADDING + 8, panelY + 104, MUTED_COLOR, false);
        context.drawText(textRenderer, Text.literal("Body"),
            panelX + PADDING + 8, panelY + 140, MUTED_COLOR, false);

        // Focus-aware bordered cards behind the input widgets
        drawFieldCard(context, toField);
        drawFieldCard(context, subjectField);
        drawBodyCard(context);
    }

    private void drawFieldCard(DrawContext context, TextFieldWidget f) {
        int fx = f.getX();
        int fy = f.getY();
        int fw = f.getWidth();
        int fh = f.getHeight();
        boolean focused = f.isFocused();
        context.fill(fx - 2, fy - 2, fx + fw + 2, fy + fh + 2,
            focused ? ACCENT : CARD_BORDER);
        context.fill(fx - 1, fy - 1, fx + fw + 1, fy + fh + 1, 0xFF0E0E18);
    }

    private void drawBodyCard(DrawContext context) {
        int bx = bodyField.getX();
        int by = bodyField.getY();
        int bw = bodyField.getWidth();
        int bh = bodyField.getHeight();
        boolean focused = bodyField.isFocused();
        context.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2,
            focused ? ACCENT : CARD_BORDER);
        context.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF0E0E18);
    }

    private void renderDetail(DrawContext context, int panelX, int panelY, int mouseX, int mouseY) {
        int headerX = panelX + PADDING + 8;
        int headerY = panelY + LIST_Y;
        int headerW = panelWidth() - PADDING * 2 - 16;
        int headerH = 54;
        context.fill(headerX - 1, headerY - 1, headerX + headerW + 1, headerY + headerH + 1, CARD_BORDER);
        context.fill(headerX, headerY, headerX + headerW, headerY + headerH, CARD_COLOR);

        // Icon
        ItemStack icon = new ItemStack(Items.WRITTEN_BOOK);
        context.getMatrices().push();
        context.getMatrices().translate(headerX + 10, headerY + 10, 0);
        context.getMatrices().scale(2.0f, 2.0f, 1.0f);
        context.drawItem(icon, 0, 0);
        context.getMatrices().pop();

        int textX = headerX + 50;
        context.drawText(textRenderer, Text.literal(truncate(detail.subject, headerW - 60)),
            textX, headerY + 6, TITLE_COLOR, true);
        context.drawText(textRenderer, Text.literal("from " + truncate(detail.fromAddr, headerW - 90)),
            textX, headerY + 20, TEXT_COLOR, false);
        String dateLine = detail.date == null ? "" : detail.date;
        context.drawText(textRenderer, Text.literal(dateLine),
            textX, headerY + 32, MUTED_COLOR, false);

        // Body card
        int bodyX = headerX;
        int bodyY = headerY + headerH + 6;
        int bodyW = headerW;
        int bodyH = panelY + panelHeight() - PADDING - bodyY;
        context.fill(bodyX - 1, bodyY - 1, bodyX + bodyW + 1, bodyY + bodyH + 1, CARD_BORDER);
        context.fill(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH, CARD_COLOR);

        int padded = 8;
        int linesFit = (bodyH - padded * 2) / BODY_LINE_HEIGHT;
        int textLeft = bodyX + padded;
        int textTop = bodyY + padded;

        if (detailWrappedBody.isEmpty()) {
            context.drawText(textRenderer, Text.literal("(empty body)"),
                textLeft, textTop, MUTED_COLOR, false);
            return;
        }

        int maxScroll = Math.max(0, detailWrappedBody.size() - linesFit);
        int scroll = Math.min(detailScroll, maxScroll);
        for (int i = 0; i < linesFit && (i + scroll) < detailWrappedBody.size(); i++) {
            String line = detailWrappedBody.get(i + scroll);
            context.drawText(textRenderer, styleLine(line),
                textLeft, textTop + i * BODY_LINE_HEIGHT, TEXT_COLOR, false);
        }

        if (detailWrappedBody.size() > linesFit) {
            String ind = (scroll + 1) + "\u2013"
                + Math.min(scroll + linesFit, detailWrappedBody.size())
                + " / " + detailWrappedBody.size();
            context.drawText(textRenderer, Text.literal(ind),
                bodyX + bodyW - textRenderer.getWidth(ind) - 4,
                bodyY + bodyH - 12, MUTED_COLOR, false);
        }
    }

    // ---------- helpers ----------

    private List<InboxItem> displayItems() {
        String q = searchField == null ? "" : searchField.getText().trim();
        if (q.isEmpty()) return items;
        List<InboxItem> out = new ArrayList<>();
        for (InboxItem it : items) {
            String hay = (it.fromDisplay() + " " + it.subject + " " + it.snippet);
            if (fuzzyMatch(hay, q)) out.add(it);
        }
        return out;
    }

    private static boolean fuzzyMatch(String haystack, String needle) {
        if (needle.isEmpty()) return true;
        String h = haystack.toLowerCase();
        String n = needle.toLowerCase();
        int hi = 0, ni = 0;
        while (hi < h.length() && ni < n.length()) {
            if (h.charAt(hi) == n.charAt(ni)) ni++;
            hi++;
        }
        return ni == n.length();
    }

    private void rewrapDetailBody() {
        if (detail == null) {
            detailWrappedBody = List.of();
            return;
        }
        int bodyW = panelWidth() - PADDING * 2 - 16 - 16;
        List<String> out = new ArrayList<>();
        for (String para : detail.body.split("\n", -1)) {
            if (para.isEmpty()) { out.add(""); continue; }
            out.addAll(wrap(para, bodyW));
        }
        detailWrappedBody = out;
    }

    private List<String> wrap(String text, int maxPx) {
        List<String> out = new ArrayList<>();
        String remaining = text;
        while (textRenderer.getWidth(remaining) > maxPx && remaining.length() > 1) {
            int cut = remaining.length();
            while (cut > 1 && textRenderer.getWidth(remaining.substring(0, cut)) > maxPx) {
                cut--;
            }
            int space = remaining.lastIndexOf(' ', cut);
            if (space > 0) cut = space;
            out.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut).stripLeading();
        }
        if (!remaining.isEmpty()) out.add(remaining);
        return out;
    }

    private static Text styleLine(String line) {
        MutableText out = Text.empty();
        Matcher m = URL_PATTERN.matcher(line);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                out.append(Text.literal(line.substring(last, m.start())));
            }
            String url = m.group();
            out.append(Text.literal(url).styled(s -> s
                .withColor(Formatting.BLUE)
                .withUnderline(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))));
            last = m.end();
        }
        if (last < line.length()) {
            out.append(Text.literal(line.substring(last)));
        }
        return out;
    }

    private static String truncate(String text, int pixelWidth) {
        if (text == null) return "";
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.textRenderer.getWidth(text) <= pixelWidth) return text;
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c);
            if (mc.textRenderer.getWidth(sb.toString() + "\u2026") > pixelWidth) {
                sb.deleteCharAt(sb.length() - 1);
                break;
            }
        }
        return sb.toString() + "\u2026";
    }

    // ---------- inner types ----------

    private static final class InboxItem {
        long uid;
        String fromRaw;
        String subject;
        String snippet;
        String date;
        boolean read;

        static InboxItem from(JsonObject json) {
            InboxItem it = new InboxItem();
            it.uid = json.has("uid") && !json.get("uid").isJsonNull() ? json.get("uid").getAsLong() : 0L;
            it.fromRaw = json.has("from") ? json.get("from").getAsString() : "";
            it.subject = json.has("subject") ? json.get("subject").getAsString() : "(no subject)";
            it.snippet = json.has("snippet") ? json.get("snippet").getAsString() : "";
            it.date = json.has("date") && !json.get("date").isJsonNull() ? json.get("date").getAsString() : "";
            it.read = json.has("read") && json.get("read").getAsBoolean();
            return it;
        }

        String fromDisplay() {
            // Prefer display name before "<addr>", fall back to raw address.
            String s = fromRaw;
            if (s == null) return "";
            int lt = s.indexOf('<');
            if (lt > 0) return s.substring(0, lt).trim().replaceAll("^\"|\"$", "");
            return s.trim();
        }

        String shortDate() {
            // ISO-8601 date → "MMM d" or "HH:mm" if today. Very best-effort.
            if (date == null || date.isEmpty()) return "";
            try {
                java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(date);
                java.time.LocalDate today = java.time.LocalDate.now();
                if (odt.toLocalDate().equals(today)) {
                    return String.format("%02d:%02d", odt.getHour(), odt.getMinute());
                }
                return odt.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"));
            } catch (Exception e) {
                return date.length() >= 10 ? date.substring(0, 10) : date;
            }
        }
    }

    private static final class EmailDetail {
        long uid;
        String fromAddr;
        String subject;
        String date;
        String body;
        boolean read;

        static EmailDetail from(JsonObject json) {
            EmailDetail d = new EmailDetail();
            d.uid = json.has("uid") && !json.get("uid").isJsonNull() ? json.get("uid").getAsLong() : 0L;
            d.fromAddr = json.has("from") ? json.get("from").getAsString() : "";
            d.subject = json.has("subject") ? json.get("subject").getAsString() : "(no subject)";
            d.date = json.has("date") && !json.get("date").isJsonNull() ? json.get("date").getAsString() : "";
            d.body = json.has("body") ? json.get("body").getAsString() : "";
            d.read = json.has("read") && json.get("read").getAsBoolean();
            return d;
        }
    }
}
