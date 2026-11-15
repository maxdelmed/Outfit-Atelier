package dev.memoria.outfitatelier.client;

import dev.memoria.outfitatelier.network.Network;
import dev.memoria.outfitatelier.service.DesignSummary;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CatalogScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 222;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 42;
    private static final int LIST_WIDTH = 186;
    private static final int ROW_HEIGHT = 20;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private List<DesignSummary> entries = List.of();
    private DesignSummary selected;
    private String query = "";
    private int page;
    private int pageCount = 1;
    private int total;
    private boolean confirmingDelete;
    private Component notice = Component.empty();
    private boolean noticeIsError;

    private int left;
    private int top;
    private PlayerPreview preview;
    private EditBox searchBox;
    private Button copyButton;
    private Button deleteButton;

    public CatalogScreen() {
        super(Component.translatable("outfitatelier.gui.catalog_title"));
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        preview = new PlayerPreview(left + 202, top + 22, 130, 146);
        searchBox = addRenderableWidget(new EditBox(font, left + LIST_X, top + 22, LIST_WIDTH, 14,
                Component.translatable("outfitatelier.gui.search")));
        searchBox.setMaxLength(32);
        searchBox.setValue(query);
        searchBox.setHint(Component.translatable("outfitatelier.gui.search"));
        searchBox.setResponder(this::onSearchTyped);
        addRenderableWidget(Button.builder(Component.literal("<"), button -> requestPage(page - 1))
                .bounds(left + LIST_X, top + 186, 14, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> requestPage(page + 1))
                .bounds(left + LIST_X + LIST_WIDTH - 14, top + 186, 14, 16).build());
        copyButton = addRenderableWidget(Button.builder(Component.translatable("outfitatelier.gui.take_copy"), button -> sendAction(Network.CatalogAction.TAKE_COPY))
                .bounds(left + 202, top + 174, 64, 16).build());
        deleteButton = addRenderableWidget(Button.builder(Component.empty(), button -> pressDelete())
                .bounds(left + 268, top + 174, 64, 16).build());
        updateWidgets();
        requestPage(page);
    }

    private void onSearchTyped(String text) {
        if (!text.equals(query)) {
            query = text;
            requestPage(0);
        }
    }

    private void requestPage(int requested) {
        Network.sendToServer(new Network.CatalogQueryMessage(query, Math.max(0, requested)));
    }

    public void showPage(Network.CatalogPageMessage message) {
        entries = message.entries();
        page = message.page();
        pageCount = message.pageCount();
        total = message.total();
        if (selected != null) {
            selected = entries.stream().filter(entry -> entry.id().equals(selected.id())).findFirst().orElse(null);
        }
        select(selected);
    }

    public void showNotice(Component message, boolean isError) {
        notice = message;
        noticeIsError = isError;
    }

    private void select(DesignSummary entry) {
        selected = entry;
        confirmingDelete = false;
        updateWidgets();
    }

    private void updateWidgets() {
        boolean usable = selected != null && !selected.deleted();
        copyButton.active = usable;
        deleteButton.active = usable;
        deleteButton.setMessage(Component.translatable(confirmingDelete ? "outfitatelier.gui.confirm_delete" : "outfitatelier.gui.delete"));
    }

    private void pressDelete() {
        if (confirmingDelete) {
            sendAction(Network.CatalogAction.DELETE);
        }
        confirmingDelete = !confirmingDelete;
        updateWidgets();
    }

    private void sendAction(Network.CatalogAction action) {
        if (selected != null) {
            Network.sendToServer(new Network.CatalogActionMessage(action, selected.id(), query, page));
        }
    }

    private void updatePreview() {
        ClientOutfits.Loaded loaded = selected == null || selected.deleted() ? null : ClientOutfits.getOrRequest(selected.id());
        ClientWardrobes.setPreview(loaded == null ? null : new ClientWardrobes.Preview(loaded.design().template, loaded.texture()));
    }

    @Override
    public void tick() {
        searchBox.tick();
        updatePreview();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF241E28);
        graphics.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, 0xFF453C4C);
        graphics.drawString(font, title, left + 8, top + 8, 0xFFFFFF, false);
        for (int row = 0; row < entries.size(); row++) {
            renderRow(graphics, entries.get(row), top + LIST_Y + row * ROW_HEIGHT);
        }
        graphics.drawCenteredString(font, Component.translatable("outfitatelier.gui.page", page + 1, pageCount, total),
                left + LIST_X + LIST_WIDTH / 2, top + 190, 0xFFFFFF);
        preview.render(graphics);
        graphics.drawString(font, font.plainSubstrByWidth(notice.getString(), PANEL_WIDTH - 16), left + 8, top + 208,
                noticeIsError ? 0xFF7070 : 0x90E090, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRow(GuiGraphics graphics, DesignSummary entry, int y) {
        boolean isSelected = selected != null && selected.id().equals(entry.id());
        graphics.fill(left + LIST_X, y, left + LIST_X + LIST_WIDTH, y + ROW_HEIGHT - 1, isSelected ? 0xFF7A5C8E : 0xFF352D3B);
        Component name = entry.deleted()
                ? Component.translatable("outfitatelier.gui.deleted_entry", entry.name())
                : Component.literal(entry.name());
        graphics.drawString(font, font.plainSubstrByWidth(name.getString(), LIST_WIDTH - 6), left + LIST_X + 3, y + 1,
                entry.deleted() ? 0x909090 : 0xFFFFFF, false);
        String details = Component.translatable("outfitatelier.gui.entry_details", entry.authorName(),
                Component.translatable(entry.template().translationKey()), DATE_FORMAT.format(Instant.ofEpochMilli(entry.createdAt()))).getString();
        graphics.drawString(font, font.plainSubstrByWidth(details, LIST_WIDTH - 6), left + LIST_X + 3, y + 10, 0xB0B0B0, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = (int) Math.floor((mouseY - top - LIST_Y) / ROW_HEIGHT);
        boolean inList = mouseX >= left + LIST_X && mouseX < left + LIST_X + LIST_WIDTH && mouseY >= top + LIST_Y;
        if (button == 0 && inList && row < entries.size()) {
            select(entries.get(row));
            return true;
        }
        if (button == 0 && preview.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return preview.mouseDragged(dragX) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        preview.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        ClientWardrobes.setPreview(null);
    }
}
