package dev.nidza.statlog.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Draws the supplied 29x28 atlas: 13x12 top corners, 13x13 bottom corners, one-pixel edges and center. */
public final class NineSlice {
    public static void draw(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        int middleWidth = Math.max(1, width - 26);
        int middleHeight = Math.max(1, height - 25);
        part(graphics, texture, x, y, 13, 12, 0, 0, 13, 12);
        part(graphics, texture, x + 13, y, middleWidth, 12, 14, 0, 1, 12);
        part(graphics, texture, x + 13 + middleWidth, y, 13, 12, 16, 0, 13, 12);
        part(graphics, texture, x, y + 12, 13, middleHeight, 0, 13, 13, 1);
        part(graphics, texture, x + 13, y + 12, middleWidth, middleHeight, 14, 13, 1, 1);
        part(graphics, texture, x + 13 + middleWidth, y + 12, 13, middleHeight, 16, 13, 13, 1);
        part(graphics, texture, x, y + 12 + middleHeight, 13, 13, 0, 15, 13, 13);
        part(graphics, texture, x + 13, y + 12 + middleHeight, middleWidth, 13, 14, 15, 1, 13);
        part(graphics, texture, x + 13 + middleWidth, y + 12 + middleHeight, 13, 13, 16, 15, 13, 13);
    }
    private static void part(GuiGraphics graphics, ResourceLocation texture, int x, int y, int w, int h,
                             int u, int v, int uvW, int uvH) {
        graphics.blit(texture, x, y, w, h, u, v, uvW, uvH, 29, 28);
    }
    private NineSlice() {}
}
