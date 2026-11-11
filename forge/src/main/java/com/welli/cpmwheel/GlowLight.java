package com.welli.cpmwheel;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Wraps a vertex consumer and forces full-bright light (glow parts on the first-person hand). */
public final class GlowLight implements VertexConsumer {
    public static final int FULL = 0xF000F0;
    private final VertexConsumer d;
    public GlowLight(VertexConsumer d) { this.d = d; }
    @Override public VertexConsumer vertex(double x, double y, double z) { d.vertex(x, y, z); return this; }
    @Override public VertexConsumer color(int r, int g, int b, int a) { d.color(r, g, b, a); return this; }
    @Override public VertexConsumer uv(float u, float v) { d.uv(u, v); return this; }
    @Override public VertexConsumer overlayCoords(int u, int v) { d.overlayCoords(u, v); return this; }
    @Override public VertexConsumer uv2(int u, int v) { d.uv2(FULL & 0xFFFF, FULL >> 16); return this; }
    @Override public VertexConsumer normal(float x, float y, float z) { d.normal(x, y, z); return this; }
    @Override public void endVertex() { d.endVertex(); }
    @Override public void defaultColor(int r, int g, int b, int a) { d.defaultColor(r, g, b, a); }
    @Override public void unsetDefaultColor() { d.unsetDefaultColor(); }
}
