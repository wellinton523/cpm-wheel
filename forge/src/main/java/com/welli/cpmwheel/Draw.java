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

    static int argb(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }
}
