package com.welli.cpmwheel;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Wraps a vertex consumer and forces full-bright light (glow parts on the first-person hand). */
public final class GlowLight implements VertexConsumer {
    public static final int FULL = 0xF000F0;
    private final VertexConsumer d;
    public GlowLight(VertexConsumer d) { this.d = d; }
    @Override public VertexConsumer addVertex(float x, float y, float z) { d.addVertex(x, y, z); return this; }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { d.setColor(r, g, b, a); return this; }
    @Override public VertexConsumer setUv(float u, float v) { d.setUv(u, v); return this; }
    @Override public VertexConsumer setUv1(int u, int v) { d.setUv1(u, v); return this; }
    @Override public VertexConsumer setUv2(int u, int v) { d.setUv2(FULL & 0xFFFF, FULL >> 16); return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { d.setNormal(x, y, z); return this; }
}
