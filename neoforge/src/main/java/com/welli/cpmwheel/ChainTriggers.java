package com.welli.cpmwheel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;

/** 1.8.0 "Encadear animações" runtime: watches the "from" animations every client tick and plays the targets (ChainLogic). */
public final class ChainTriggers {
    private ChainTriggers() {}
    private static final Map<String, ChainLogic.State> ST = new HashMap<>();

    public static void tick(Minecraft mc, WheelConfig cfg) {
        if (cfg == null || !cfg.chainTrigOn || cfg.chainRules.isEmpty() || mc.player == null) { if (!ST.isEmpty()) ST.clear(); return; }
        long now = System.currentTimeMillis();
        Set<String> seen = new HashSet<>();
        Map<String, Integer> cache = new HashMap<>();
        for (ChainLogic.Rule r : cfg.chainRules) {
            seen.add(r.id);
            ChainLogic.State s = ST.computeIfAbsent(r.id, k -> new ChainLogic.State());
            int pv = r.from.isBlank() ? -1 : cache.computeIfAbsent(r.from, CpmBridge::playing);
            if (ChainLogic.step(r, s, pv, now)) fire(cfg, r);
        }
        ST.keySet().retainAll(seen);
    }

    static void fire(WheelConfig cfg, ChainLogic.Rule r) {
        if (EffectTriggers.held(r.to)) return; // a trigger holds this animation
        int cur = CpmBridge.playing(r.to);
        int v = ChainLogic.target(r.op, cur, EffectTriggers.onValue(cfg, r.to, r.value));
        CpmBridge.play(r.to, v);
        Hud.invalidate();
    }

    /** "Testar" button: fire once now */
    public static void test(WheelConfig cfg, ChainLogic.Rule r) { if (r != null && !r.to.isBlank()) fire(cfg, r); }
}
