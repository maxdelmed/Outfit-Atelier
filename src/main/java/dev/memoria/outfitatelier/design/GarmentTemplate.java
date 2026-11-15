package dev.memoria.outfitatelier.design;

import java.util.List;

public enum GarmentTemplate {
    SHIRT(WardrobeSlot.TOP,
            List.of(torso(0.3F), rightSleeve(0.3F), leftSleeve(0.3F)),
            upperBodyPanels()),
    COAT(WardrobeSlot.OUTER,
            List.of(torso(0.55F), rightSleeve(0.55F), leftSleeve(0.55F),
                    new Part(Anchor.BODY, -4, 11, -2, 4, 20, 2, 0.55F, 1.0F, 0, 0, 32, false)),
            withPanel(upperBodyPanels(), new Panel("tails", 0, 36, 24, 9))),
    SKIRT(WardrobeSlot.BOTTOM,
            List.of(new Part(Anchor.BODY, -4, 9, -2, 4, 19, 2, 0.45F, 2.0F, 0, 0, 0, false)),
            List.of(new Panel("skirt", 0, 4, 24, 10))),
    DRESS(WardrobeSlot.TOP,
            List.of(torso(0.3F), rightSleeve(0.3F), leftSleeve(0.3F),
                    new Part(Anchor.BODY, -4, 10, -2, 4, 22, 2, 0.35F, 2.5F, 0, 0, 32, false)),
            withPanel(upperBodyPanels(), new Panel("skirt", 0, 36, 24, 12))),
    CAPE(WardrobeSlot.BACK,
            List.of(new Part(Anchor.BODY, -5, 0, 2.4F, 5, 16, 3.4F, 0, 0, 6.0F, 0, 0, true)),
            List.of(new Panel("cape_outside", 12, 1, 10, 16), new Panel("cape_inside", 1, 1, 10, 16)));

    public static final int TEXELS_PER_PIXEL = 2;
    public static final int TEXTURE_SIZE = 64 * TEXELS_PER_PIXEL;

    public enum Anchor { BODY, RIGHT_ARM, LEFT_ARM }

    public record Part(Anchor anchor, float x0, float y0, float z0, float x1, float y1, float z1,
                       float inflate, float flare, float tiltDegrees, int u, int v, boolean hasTop) {
        public int width() {
            return Math.round(x1 - x0);
        }

        public int height() {
            return Math.round(y1 - y0);
        }

        public int depth() {
            return Math.round(z1 - z0);
        }
    }

    public record Panel(String key, int u, int v, int width, int height) {
        public int left() {
            return u * TEXELS_PER_PIXEL;
        }

        public int top() {
            return v * TEXELS_PER_PIXEL;
        }

        public int texelWidth() {
            return width * TEXELS_PER_PIXEL;
        }

        public int texelHeight() {
            return height * TEXELS_PER_PIXEL;
        }

        public boolean contains(int x, int y) {
            return x >= left() && x < left() + texelWidth() && y >= top() && y < top() + texelHeight();
        }
    }

    public final WardrobeSlot slot;
    public final List<Part> parts;
    public final List<Panel> panels;

    GarmentTemplate(WardrobeSlot slot, List<Part> parts, List<Panel> panels) {
        this.slot = slot;
        this.parts = parts;
        this.panels = panels;
    }

    public String translationKey() {
        return "outfitatelier.template." + name().toLowerCase();
    }

    public static GarmentTemplate byName(String name) {
        for (GarmentTemplate template : values()) {
            if (template.name().equals(name)) {
                return template;
            }
        }
        return null;
    }

    private static Part torso(float inflate) {
        return new Part(Anchor.BODY, -4, 0, -2, 4, 12, 2, inflate, 0, 0, 0, 0, true);
    }

    private static Part rightSleeve(float inflate) {
        return new Part(Anchor.RIGHT_ARM, -3, -2, -2, 1, 10, 2, inflate, 0, 0, 0, 16, true);
    }

    private static Part leftSleeve(float inflate) {
        return new Part(Anchor.LEFT_ARM, -1, -2, -2, 3, 10, 2, inflate, 0, 0, 16, 16, true);
    }

    private static List<Panel> upperBodyPanels() {
        return List.of(
                new Panel("front", 4, 4, 8, 12),
                new Panel("back", 16, 4, 8, 12),
                new Panel("right_side", 0, 4, 4, 12),
                new Panel("left_side", 12, 4, 4, 12),
                new Panel("shoulders", 4, 0, 8, 4),
                new Panel("right_sleeve", 0, 16, 16, 16),
                new Panel("left_sleeve", 16, 16, 16, 16));
    }

    private static List<Panel> withPanel(List<Panel> panels, Panel extra) {
        List<Panel> combined = new java.util.ArrayList<>(panels);
        combined.add(extra);
        return List.copyOf(combined);
    }
}
