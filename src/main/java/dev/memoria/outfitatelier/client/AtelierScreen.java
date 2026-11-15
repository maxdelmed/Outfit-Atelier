package dev.memoria.outfitatelier.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.memoria.outfitatelier.design.GarmentTemplate;
import dev.memoria.outfitatelier.design.GarmentTemplate.Panel;
import dev.memoria.outfitatelier.design.OutfitCanvas;
import dev.memoria.outfitatelier.design.OutfitDesign;
import dev.memoria.outfitatelier.network.Network;
import dev.memoria.outfitatelier.service.DesignSummary;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AtelierScreen extends Screen {
    private enum Tool { PENCIL, FILL, ERASE, PICK }

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 246;
    private static final int CANVAS_SIZE = 150;
    private static final int SIDE_X = 250;
    private static final int SWATCH_SIZE = 9;
    private static final int MAX_RECENT_COLORS = 8;
    private static final int TEXTURE_SIZE = GarmentTemplate.TEXTURE_SIZE;

    private final Map<Tool, Button> toolButtons = new EnumMap<>(Tool.class);
    private final List<Integer> recentColors = new ArrayList<>();
    private List<DesignSummary> designs;
    private OutfitCanvas canvas = new OutfitCanvas(GarmentTemplate.SHIRT);
    private int panelIndex;
    private int selectedDesign;
    private UUID pendingLoadId;
    private Tool tool = Tool.PENCIL;
    private int color = 0xC8324B;
    private boolean painting;
    private boolean updatingHex;
    private int lastX;
    private int lastY;
    private String designName = "";
    private Component notice = Component.empty();
    private boolean noticeIsError;

    private int left;
    private int top;
    private LiveTexture liveTexture;
    private ColorPicker colorPicker;
    private PlayerPreview preview;
    private EditBox hexBox;
    private EditBox nameBox;
    private Button templateButton;
    private Button mirrorButton;
    private Button loadButton;
    private Button copyButton;

    public AtelierScreen(List<DesignSummary> designs) {
        super(Component.translatable("block.outfitatelier.sewing_table"));
        this.designs = designs;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        closeTextures();
        liveTexture = new LiveTexture();
        colorPicker = new ColorPicker(left + SIDE_X, top + 78);
        colorPicker.setRgb(color);
        preview = new PlayerPreview(left + 164, top + 22, 80, CANVAS_SIZE);
        addToolButtons();
        addColorWidgets();
        addDesignWidgets();
        refreshArtwork();
        updateWidgets();
    }

    private void addToolButtons() {
        toolButtons.clear();
        for (Tool buttonTool : Tool.values()) {
            int index = buttonTool.ordinal();
            toolButtons.put(buttonTool, addRenderableWidget(Button.builder(
                            Component.translatable("outfitatelier.gui.tool." + buttonTool.name().toLowerCase()),
                            button -> selectTool(buttonTool))
                    .bounds(left + SIDE_X + index % 2 * 42, top + 22 + index / 2 * 18, 40, 16).build()));
        }
        addRenderableWidget(Button.builder(Component.translatable("outfitatelier.gui.undo"), button -> undo())
                .bounds(left + SIDE_X, top + 58, 40, 16).build());
        mirrorButton = addRenderableWidget(Button.builder(Component.translatable("outfitatelier.gui.mirror"), button -> mirrorSleeve())
                .bounds(left + SIDE_X + 42, top + 58, 40, 16).build());
    }

    private void addColorWidgets() {
        hexBox = addRenderableWidget(new EditBox(font, left + SIDE_X, top + 142, 58, 14,
                Component.translatable("outfitatelier.gui.hex")));
        hexBox.setMaxLength(7);
        hexBox.setResponder(this::onHexTyped);
        showColorInHexBox();
    }

    private void addDesignWidgets() {
        templateButton = addRenderableWidget(Button.builder(Component.empty(), button -> cycleTemplate())
                .bounds(left + 8, top + 176, 100, 16).build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> cyclePanel(-1))
                .bounds(left + 112, top + 176, 14, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> cyclePanel(1))
                .bounds(left + 230, top + 176, 14, 16).build());
        nameBox = addRenderableWidget(new EditBox(font, left + 8, top + 195, 150, 14,
                Component.translatable("outfitatelier.gui.name")));
        nameBox.setMaxLength(OutfitDesign.MAX_NAME_LENGTH);
        nameBox.setValue(designName);
        nameBox.setResponder(text -> designName = text);
        nameBox.setHint(Component.translatable("outfitatelier.gui.name"));
        addRenderableWidget(Button.builder(Component.translatable("outfitatelier.gui.finish"), button -> sendFinish())
                .bounds(left + SIDE_X, top + 194, 82, 16).build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> cycleDesign(-1))
                .bounds(left + 8, top + 213, 14, 16).build());
        loadButton = addRenderableWidget(Button.builder(Component.empty(), button -> loadSelectedDesign())
                .bounds(left + 24, top + 213, 110, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> cycleDesign(1))
                .bounds(left + 136, top + 213, 14, 16).build());
        copyButton = addRenderableWidget(Button.builder(Component.translatable("outfitatelier.gui.copy"), button -> sendCopy())
                .bounds(left + 154, top + 213, 44, 16).build());
        if (minecraft.player.hasPermissions(2)) {
            addRenderableWidget(Button.builder(Component.translatable("outfitatelier.gui.catalog"),
                            button -> minecraft.setScreen(new CatalogScreen()))
                    .bounds(left + SIDE_X, top + 213, 82, 16).build());
        }
    }

    private Panel panel() {
        return canvas.template().panels.get(panelIndex);
    }

    private void updateWidgets() {
        toolButtons.forEach((buttonTool, button) -> button.active = buttonTool != tool);
        templateButton.setMessage(Component.translatable(canvas.template().translationKey()));
        mirrorButton.active = otherSleeve() != null;
        loadButton.active = !designs.isEmpty();
        copyButton.active = !designs.isEmpty();
        loadButton.setMessage(designs.isEmpty()
                ? Component.translatable("outfitatelier.gui.no_designs")
                : Component.literal(font.plainSubstrByWidth(designs.get(selectedDesign).name(), 100)));
    }

    private void refreshArtwork() {
        liveTexture.refresh(canvas);
        ClientWardrobes.setPreview(new ClientWardrobes.Preview(canvas.template(), liveTexture.id()));
    }

    private void selectTool(Tool selected) {
        tool = selected;
        updateWidgets();
    }

    private void undo() {
        if (canvas.undo()) {
            panelIndex = Math.min(panelIndex, canvas.template().panels.size() - 1);
            refreshArtwork();
            updateWidgets();
        }
    }

    private Panel otherSleeve() {
        String key = panel().key();
        String otherKey = key.equals("right_sleeve") ? "left_sleeve" : key.equals("left_sleeve") ? "right_sleeve" : null;
        for (Panel candidate : canvas.template().panels) {
            if (candidate.key().equals(otherKey)) {
                return candidate;
            }
        }
        return null;
    }

    private void mirrorSleeve() {
        Panel other = otherSleeve();
        if (other != null) {
            canvas.beginStroke();
            canvas.mirrorSleeve(panel(), other);
            refreshArtwork();
            showNotice(Component.translatable("outfitatelier.message.mirrored"), false);
        }
    }

    private void cycleTemplate() {
        GarmentTemplate[] templates = GarmentTemplate.values();
        canvas.beginStroke();
        canvas.changeTemplate(templates[(canvas.template().ordinal() + 1) % templates.length]);
        panelIndex = 0;
        refreshArtwork();
        updateWidgets();
    }

    private void cyclePanel(int step) {
        panelIndex = Math.floorMod(panelIndex + step, canvas.template().panels.size());
        updateWidgets();
    }

    private void cycleDesign(int step) {
        if (!designs.isEmpty()) {
            selectedDesign = Math.floorMod(selectedDesign + step, designs.size());
            updateWidgets();
        }
    }

    private void loadSelectedDesign() {
        if (!designs.isEmpty()) {
            pendingLoadId = designs.get(selectedDesign).id();
            ClientOutfits.getOrRequest(pendingLoadId);
        }
    }

    private void finishPendingLoad() {
        ClientOutfits.Loaded loaded = pendingLoadId == null ? null : ClientOutfits.getOrRequest(pendingLoadId);
        if (loaded == null) {
            return;
        }
        pendingLoadId = null;
        canvas = OutfitCanvas.of(loaded.design());
        panelIndex = 0;
        refreshArtwork();
        updateWidgets();
        showNotice(Component.translatable("outfitatelier.message.loaded", loaded.design().name), false);
    }

    private void sendFinish() {
        Network.sendToServer(new Network.FinishMessage(designName, canvas.toArtwork()));
    }

    private void sendCopy() {
        if (!designs.isEmpty()) {
            Network.sendToServer(new Network.CopyMessage(designs.get(selectedDesign).id()));
        }
    }

    public void showResult(Network.ResultMessage result) {
        designs = result.designs();
        selectedDesign = Math.min(selectedDesign, Math.max(0, designs.size() - 1));
        showNotice(result.notice(), !result.success());
        updateWidgets();
    }

    private void showNotice(Component message, boolean isError) {
        notice = message;
        noticeIsError = isError;
    }

    private void setColor(int rgb) {
        color = rgb & 0xFFFFFF;
        colorPicker.setRgb(color);
        showColorInHexBox();
    }

    private void showColorInHexBox() {
        updatingHex = true;
        hexBox.setValue(String.format("#%06X", color));
        updatingHex = false;
    }

    private void onHexTyped(String text) {
        String digits = text.startsWith("#") ? text.substring(1) : text;
        if (!updatingHex && digits.matches("[0-9a-fA-F]{6}")) {
            color = Integer.parseInt(digits, 16);
            colorPicker.setRgb(color);
        }
    }

    private float canvasScale() {
        return Math.min((float) CANVAS_SIZE / panel().texelWidth(), (float) CANVAS_SIZE / panel().texelHeight());
    }

    private int canvasLeft() {
        return left + 8 + (CANVAS_SIZE - Math.round(panel().texelWidth() * canvasScale())) / 2;
    }

    private int canvasTop() {
        return top + 22 + (CANVAS_SIZE - Math.round(panel().texelHeight() * canvasScale())) / 2;
    }

    private int texelX(double mouseX) {
        return panel().left() + (int) Math.floor((mouseX - canvasLeft()) / canvasScale());
    }

    private int texelY(double mouseY) {
        return panel().top() + (int) Math.floor((mouseY - canvasTop()) / canvasScale());
    }

    private boolean isOverCanvas(double mouseX, double mouseY) {
        return mouseX >= left + 8 && mouseX < left + 8 + CANVAS_SIZE && mouseY >= top + 22 && mouseY < top + 22 + CANVAS_SIZE;
    }

    @Override
    public void tick() {
        hexBox.tick();
        nameBox.tick();
        finishPendingLoad();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF241E28);
        graphics.fill(left + 2, top + 2, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT - 2, 0xFF453C4C);
        graphics.drawString(font, title, left + 8, top + 8, 0xFFFFFF, false);
        renderCanvas(graphics);
        preview.render(graphics);
        colorPicker.render(graphics);
        renderSwatches(graphics);
        graphics.drawCenteredString(font, Component.translatable("outfitatelier.panel." + panel().key()), left + 178, top + 180, 0xFFFFFF);
        graphics.drawString(font, font.plainSubstrByWidth(notice.getString(), PANEL_WIDTH - 16), left + 8, top + 233,
                noticeIsError ? 0xFF7070 : 0x90E090, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCanvas(GuiGraphics graphics) {
        Panel shown = panel();
        int drawWidth = Math.round(shown.texelWidth() * canvasScale());
        int drawHeight = Math.round(shown.texelHeight() * canvasScale());
        graphics.fill(left + 7, top + 21, left + 9 + CANVAS_SIZE, top + 23 + CANVAS_SIZE, 0xFF202020);
        graphics.fill(left + 8, top + 22, left + 8 + CANVAS_SIZE, top + 22 + CANVAS_SIZE, 0xFF55606C);
        graphics.fill(canvasLeft(), canvasTop(), canvasLeft() + drawWidth, canvasTop() + drawHeight, 0xFFB9C2CC);
        RenderSystem.enableBlend();
        graphics.blit(liveTexture.id(), canvasLeft(), canvasTop(), drawWidth, drawHeight,
                shown.left(), shown.top(), shown.texelWidth(), shown.texelHeight(), TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void renderSwatches(GuiGraphics graphics) {
        graphics.fill(left + 312, top + 142, left + 332, top + 156, 0xFF000000);
        graphics.fill(left + 313, top + 143, left + 331, top + 155, 0xFF000000 | color);
        for (int index = 0; index < recentColors.size(); index++) {
            int x = left + SIDE_X + index * 10;
            graphics.fill(x, top + 160, x + SWATCH_SIZE, top + 160 + SWATCH_SIZE, 0xFF000000 | recentColors.get(index));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverCanvas(mouseX, mouseY)) {
            setFocused(null);
            startStroke(texelX(mouseX), texelY(mouseY));
            return true;
        }
        if (button == 0 && preview.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && colorPicker.mouseClicked(mouseX, mouseY)) {
            applyPickerColor();
            return true;
        }
        if (button == 0 && clickRecentColor(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickRecentColor(double mouseX, double mouseY) {
        int index = (int) Math.floor((mouseX - left - SIDE_X) / 10);
        boolean inRow = mouseY >= top + 160 && mouseY < top + 160 + SWATCH_SIZE && mouseX >= left + SIDE_X;
        if (inRow && index < recentColors.size()) {
            setColor(recentColors.get(index));
            return true;
        }
        return false;
    }

    private void applyPickerColor() {
        color = colorPicker.getRgb();
        showColorInHexBox();
    }

    private void startStroke(int x, int y) {
        if (!panel().contains(x, y)) {
            return;
        }
        if (tool == Tool.PICK) {
            int paint = canvas.paintAt(x, y);
            if (paint != OutfitCanvas.PAINT_EMPTY) {
                setColor(paint);
            }
            selectTool(Tool.PENCIL);
            return;
        }
        canvas.beginStroke();
        rememberColor();
        if (tool == Tool.FILL) {
            reportPaint(canvas.fill(panel(), x, y, color));
        } else {
            reportPaint(canvas.setPixel(panel(), x, y, currentPaint()));
            lastX = x;
            lastY = y;
            painting = true;
        }
        refreshArtwork();
    }

    private int currentPaint() {
        return tool == Tool.ERASE ? OutfitCanvas.PAINT_EMPTY : color;
    }

    private void rememberColor() {
        if (tool == Tool.ERASE) {
            return;
        }
        recentColors.remove((Integer) color);
        recentColors.add(0, color);
        if (recentColors.size() > MAX_RECENT_COLORS) {
            recentColors.remove(recentColors.size() - 1);
        }
    }

    private void reportPaint(boolean painted) {
        if (!painted) {
            showNotice(Component.translatable("outfitatelier.message.palette_full", OutfitDesign.MAX_COLORS), true);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (painting) {
            int x = texelX(mouseX);
            int y = texelY(mouseY);
            reportPaint(canvas.drawLine(panel(), lastX, lastY, x, y, currentPaint()));
            lastX = x;
            lastY = y;
            refreshArtwork();
            return true;
        }
        if (preview.mouseDragged(dragX)) {
            return true;
        }
        if (colorPicker.mouseDragged(mouseX, mouseY)) {
            applyPickerColor();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        painting = false;
        preview.mouseReleased();
        colorPicker.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        ClientWardrobes.setPreview(null);
        closeTextures();
    }

    private void closeTextures() {
        if (liveTexture != null) {
            liveTexture.close();
            colorPicker.close();
        }
    }
}
