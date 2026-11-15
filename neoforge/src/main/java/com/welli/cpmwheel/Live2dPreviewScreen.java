package com.welli.cpmwheel;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 1.8.0 "Prévia ao vivo": graphs of each link's angle and value over the last 5 s, an animated drawing of the chain,
 * and simulated inputs (added to the real ones while this screen is open) to tune standing still.
 */
public class Live2dPreviewScreen extends MiniScreen {
    private final String chainId;
    static final String[] SIMS = {"fwd", "side", "vert", "headYaw", "windF", "windS", "lookYaw", "voice"};
    static final int[][] RANGE = {{-60, 60}, {-60, 60}, {-80, 80}, {-90, 90}, {-60, 60}, {-60, 60}, {-60, 60}, {0, 45}};

    public Live2dPreviewScreen(Screen parent, WheelConfig cfg, String chainId) { super("cpmwheel.l2d.prev.title", parent, cfg); this.chainId = chainId; }

    private Live2dMath.Chain chain() { for (Live2dMath.Chain c : cfg.l2dChains) if (c.id.equals(chainId)) return c; return null; }

    @Override
    protected void init() {
        Live2d.use(cfg);
        Live2d.simOn = true;
        int x = 8, y = 26;
        for (int i = 0; i < SIMS.length; i++) {
            final String k = SIMS[i];
            slider(x, y, 150, "cpmwheel.l2d.sim." + k, RANGE[i][0], RANGE[i][1], 1,
                    () -> Math.round(Live2d.SIM.getOrDefault(k, 0f)), v -> { if (v == 0) Live2d.SIM.remove(k); else Live2d.SIM.put(k, (float) v); }, "", "cpmwheel.l2d.sim.help");
            y += 22;
        }
        btn("cpmwheel.l2d.sim.kick", b -> Live2d.simKick(40), x, y, 74, "cpmwheel.l2d.sim.kick.help");
        btn("cpmwheel.l2d.sim.zero", b -> { Live2d.SIM.clear(); rebuildWidgets(); }, x + 76, y, 74, null);
        y += 22;
        btn(Component.translatable("gui.done"), b -> onClose(), x, height - 26, 150, 20, null);
    }

    @Override public void removed() { Live2d.simOn = false; Live2d.SIM.clear(); super.removed(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Live2dMath.Chain ch = chain();
        int gx = 168, gy = 26, gw = Math.min(Live2d.HN * 2, width - gx - 120), gh = 34;
        if (!cfg.l2dOn) g.drawString(font, Component.translatable("cpmwheel.l2d.prev.off"), gx, height - 20, 0xFF6060);
        if (ch == null || ch.links.isEmpty()) { g.drawString(font, Component.translatable("cpmwheel.l2d.prev.none"), gx, gy, 0x7FA8B8); return; }
        int rows = Math.max(1, Math.min(ch.links.size(), (height - gy - 30) / (gh + 12)));
        int idx = Live2d.historyIndex();
        float[] angles = new float[ch.links.size()];
        for (int i = 0; i < rows; i++) {
            Live2dMath.Link l = ch.links.get(i);
            int top = gy + i * (gh + 12);
            float[] lv = Live2d.live(ch.id, i);
            if (lv != null) angles[i] = lv[0];
            String head = (i + 1) + ". " + (l.anim.isBlank() ? "\u2014" : l.anim) + (lv == null ? "" : "  " + Math.round(lv[0]) + "\u00B0" + (lv[1] >= 0 ? " \u2192 " + (int) lv[1] + "/" + (int) lv[2] : ""));
            g.drawString(font, font.plainSubstrByWidth(head, gw), gx, top, 0xCFE8F0);
            int bt = top + 10;
            g.fill(gx, bt, gx + gw, bt + gh, 0x60000000);
            float range = Math.max(10, Math.max(Math.abs(l.min), Math.abs(l.max)));
            int mid = bt + gh / 2;
            g.fill(gx, mid, gx + gw, mid + 1, 0x40FFFFFF);
            float[][] h = Live2d.history(ch.id, i);
            if (h == null) continue;
            int n = Live2d.HN, px0 = -1, pa = 0, pv = 0;
            for (int s = 0; s < n; s++) {
                int k = (idx + s) % n;   // oldest first
                int px = gx + (int) ((long) s * (gw - 1) / (n - 1));
                int ya = mid - Math.round(Math.max(-1, Math.min(1, h[0][k] / range)) * (gh / 2 - 1));
                int yv = bt + gh - 1 - Math.round(Math.max(0, Math.min(1, h[1][k])) * (gh - 2));
                if (px0 >= 0) { line(g, px0, pv, px, yv, 0xFFFFC04A); line(g, px0, pa, px, ya, 0xFF2EE6FF); }
                px0 = px; pa = ya; pv = yv;
            }
        }
        g.drawString(font, Component.translatable("cpmwheel.l2d.prev.legend"), gx, height - 32, 0x7FA8B8);
        // animated chain: each link bends by its output angle, relative to the one above
        int cx = Math.min(width - 60, gx + gw + 60), cy = gy + 20, len = Math.max(14, Math.min(34, (height - 80) / Math.max(1, ch.links.size())));
        double a = Math.PI / 2; float x0 = cx, y0 = cy;
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFFCFE8F0);
        for (int i = 0; i < ch.links.size(); i++) {
            a += Math.toRadians(angles[i]);
            float x1 = x0 + (float) (Math.cos(a) * len), y1 = y0 + (float) (Math.sin(a) * len);
            line(g, Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), i % 2 == 0 ? 0xFF2EE6FF : 0xFFFFC04A);
            g.fill(Math.round(x1) - 2, Math.round(y1) - 2, Math.round(x1) + 2, Math.round(y1) + 2, 0xFFFFFFFF);
            x0 = x1; y0 = y1;
        }
    }

    /** 1-px line from small fills */
    static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        for (int guard = 0; guard < 2000; guard++) {
            g.fill(x0, y0, x0 + 1, y0 + 1, col);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }
}
