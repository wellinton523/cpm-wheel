package com.welli.cpmwheel;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

final class Draw {
    private Draw() {}

    /** Filled ring sector (angles in radians, 0 = up, clockwise). */
    static void sector(GuiGraphics g, float cx, float cy, float r0, float r1, double a0, double a1, int argb) {
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int a = argb >>> 24, r = (argb >> 16) & 255, gr = (argb >> 8) & 255, bl = argb & 255;
        int steps = Math.max(4, (int) Math.ceil((a1 - a0) * 24));
        for (int i = 0; i <= steps; i++) {
            double t = a0 + (a1 - a0) * i / steps;
            float sx = (float) Math.sin(t), sy = (float) -Math.cos(t);
            b.vertex(m, cx + sx * r1, cy + sy * r1, 0).color(r, gr, bl, a).endVertex();
            b.vertex(m, cx + sx * r0, cy + sy * r0, 0).color(r, gr, bl, a).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.disableBlend();
    }


    private static BufferBuilder start(VertexFormat.Mode mode) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(mode, DefaultVertexFormat.POSITION_COLOR);
        return b;
    }
    private static void v(BufferBuilder b, Matrix4f m, float x, float y, int argb) {
        b.vertex(m, x, y, 0).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24).endVertex();
    }
    private static void finish(BufferBuilder b) {
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** convex polygon as a triangle fan around (fx, fy) */
    static void poly(GuiGraphics g, float[] xs, float[] ys, int n, float fx, float fy, int argb) {
        if (n < 3) return;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = start(VertexFormat.Mode.TRIANGLE_FAN);
        v(b, m, fx, fy, argb);
        for (int i = n - 1; i >= 0; i--) v(b, m, xs[i], ys[i], argb); // counter-clockwise on screen
        v(b, m, xs[n - 1], ys[n - 1], argb);
        finish(b);
    }

    private static void quad(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        v(b, m, x0, y1, argb); v(b, m, x1, y1, argb); v(b, m, x1, y0, argb); v(b, m, x0, y0, argb);
    }

    /** dotted grid in one draw call */
    static void grid(GuiGraphics g, int x0, int y0, int x1, int y1, int step, int size, int argb) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = start(VertexFormat.Mode.QUADS);
        for (int y = y0 + step / 2; y < y1; y += step) for (int x = x0 + step / 2; x < x1; x += step) quad(b, m, x, y, x + size, y + size, argb);
        finish(b);
    }

    /** horizontal scanlines in one draw call */
    static void scanlines(GuiGraphics g, int w, int h, int step, int argb) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = start(VertexFormat.Mode.QUADS);
        for (int y = 0; y < h; y += step) quad(b, m, 0, y, w, y + 1, argb);
        finish(b);
    }

    static int argb(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }
}
