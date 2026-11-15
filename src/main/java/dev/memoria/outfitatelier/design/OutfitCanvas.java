package dev.memoria.outfitatelier.design;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class OutfitCanvas {
    public static final int PAINT_EMPTY = -1;
    private static final int SIZE = GarmentTemplate.TEXTURE_SIZE;
    private static final int MAX_UNDO = 30;

    private record Snapshot(GarmentTemplate template, byte[] pixels, List<Integer> palette) {}

    private final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final List<Integer> palette = new ArrayList<>();
    private GarmentTemplate template;
    private byte[] pixels = new byte[OutfitDesign.PIXEL_COUNT];

    public OutfitCanvas(GarmentTemplate template) {
        this.template = template;
    }

    public static OutfitCanvas of(OutfitDesign design) {
        OutfitDesign.Artwork artwork = design.artwork();
        OutfitCanvas canvas = new OutfitCanvas(artwork.template());
        canvas.pixels = artwork.pixels();
        for (int color : artwork.palette()) {
            canvas.palette.add(color);
        }
        return canvas;
    }

    public GarmentTemplate template() {
        return template;
    }

    public int argbAt(int x, int y) {
        int index = pixels[y * SIZE + x];
        return index == OutfitDesign.EMPTY ? 0 : 0xFF000000 | palette.get(index - 1);
    }

    public int paintAt(int x, int y) {
        int index = pixels[y * SIZE + x];
        return index == OutfitDesign.EMPTY ? PAINT_EMPTY : palette.get(index - 1);
    }

    public void beginStroke() {
        undoStack.push(new Snapshot(template, pixels.clone(), new ArrayList<>(palette)));
        if (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
    }

    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        Snapshot snapshot = undoStack.pop();
        template = snapshot.template();
        pixels = snapshot.pixels();
        palette.clear();
        palette.addAll(snapshot.palette());
        return true;
    }

    public void changeTemplate(GarmentTemplate newTemplate) {
        template = newTemplate;
        pixels = new byte[OutfitDesign.PIXEL_COUNT];
        palette.clear();
    }

    public boolean setPixel(GarmentTemplate.Panel panel, int x, int y, int paint) {
        int index = indexOf(paint);
        if (index >= 0 && panel.contains(x, y)) {
            pixels[y * SIZE + x] = (byte) index;
        }
        return index >= 0;
    }

    public boolean drawLine(GarmentTemplate.Panel panel, int fromX, int fromY, int toX, int toY, int paint) {
        int deltaX = Math.abs(toX - fromX);
        int deltaY = -Math.abs(toY - fromY);
        int stepX = fromX < toX ? 1 : -1;
        int stepY = fromY < toY ? 1 : -1;
        int error = deltaX + deltaY;
        int x = fromX;
        int y = fromY;
        while (true) {
            if (!setPixel(panel, x, y, paint)) {
                return false;
            }
            if (x == toX && y == toY) {
                return true;
            }
            int doubled = 2 * error;
            if (doubled >= deltaY) {
                error += deltaY;
                x += stepX;
            }
            if (doubled <= deltaX) {
                error += deltaX;
                y += stepY;
            }
        }
    }

    public boolean fill(GarmentTemplate.Panel panel, int startX, int startY, int paint) {
        int replacement = indexOf(paint);
        if (replacement < 0 || !panel.contains(startX, startY)) {
            return replacement >= 0;
        }
        int target = pixels[startY * SIZE + startX];
        if (target == replacement) {
            return true;
        }
        Deque<int[]> pending = new ArrayDeque<>();
        pending.push(new int[] {startX, startY});
        while (!pending.isEmpty()) {
            int[] point = pending.pop();
            int x = point[0];
            int y = point[1];
            if (panel.contains(x, y) && pixels[y * SIZE + x] == target) {
                pixels[y * SIZE + x] = (byte) replacement;
                pending.push(new int[] {x + 1, y});
                pending.push(new int[] {x - 1, y});
                pending.push(new int[] {x, y + 1});
                pending.push(new int[] {x, y - 1});
            }
        }
        return true;
    }

    public void mirrorSleeve(GarmentTemplate.Panel from, GarmentTemplate.Panel to) {
        int faceWidth = from.texelWidth() / 4;
        for (int y = 0; y < from.texelHeight(); y++) {
            for (int face = 0; face < 4; face++) {
                int mirroredFace = face == 0 ? 2 : face == 2 ? 0 : face;
                for (int x = 0; x < faceWidth; x++) {
                    int source = (from.top() + y) * SIZE + from.left() + face * faceWidth + x;
                    int target = (to.top() + y) * SIZE + to.left() + mirroredFace * faceWidth + faceWidth - 1 - x;
                    pixels[target] = pixels[source];
                }
            }
        }
    }

    private int indexOf(int paint) {
        if (paint == PAINT_EMPTY) {
            return OutfitDesign.EMPTY;
        }
        int color = paint & 0xFFFFFF;
        int known = palette.indexOf(color);
        if (known >= 0) {
            return known + 1;
        }
        if (palette.size() >= OutfitDesign.MAX_COLORS) {
            dropUnusedColors();
        }
        if (palette.size() >= OutfitDesign.MAX_COLORS) {
            return -1;
        }
        palette.add(color);
        return palette.size();
    }

    private void dropUnusedColors() {
        OutfitDesign.Artwork compact = toArtwork();
        pixels = compact.pixels();
        palette.clear();
        for (int color : compact.palette()) {
            palette.add(color);
        }
    }

    public OutfitDesign.Artwork toArtwork() {
        int[] remap = new int[palette.size() + 1];
        List<Integer> usedColors = new ArrayList<>();
        byte[] compactPixels = new byte[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int index = pixels[i];
            if (index != OutfitDesign.EMPTY && remap[index] == 0) {
                usedColors.add(palette.get(index - 1));
                remap[index] = usedColors.size();
            }
            compactPixels[i] = (byte) remap[index];
        }
        return new OutfitDesign.Artwork(template, usedColors.stream().mapToInt(Integer::intValue).toArray(), compactPixels);
    }
}
