package com.welli.cpmwheel;

import com.tom.cpm.shared.animation.AnimationRegistry;
import com.tom.cpm.shared.animation.AnimationTrigger;
import com.tom.cpm.shared.animation.IPose;

import java.util.ArrayList;
import java.util.List;

/** First-person hand follows CPM layer/value animations (see mixin.AnimationEngineMixin). */
public final class FirstPerson {
    private FirstPerson() {}
    private static IPose global;
    private static boolean globalFailed;

    private static IPose global() {
        if (global == null && !globalFailed) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object g = Enum.valueOf((Class) Class.forName("com.tom.cpm.shared.animation.VanillaPose"), "GLOBAL");
                global = (IPose) g;
            } catch (Throwable t) { globalFailed = true; CpmWheel.LOG.warn("[CPM Wheel] first-person: CPM pose not found", t); }
        }
        return global;
    }

    public static List<AnimationTrigger> extend(AnimationRegistry reg, IPose pose) {
        List<AnimationTrigger> base = reg.getPoseAnimations(pose);
        String m = FpState.mode;
        if ("off".equals(m) || !(pose instanceof Enum<?> e)) return base;
        String n = e.name();
        if (!n.equals("FIRST_PERSON_HAND") && !n.equals("VR_FIRST_PERSON")) return base;
        IPose g = global();
        if (g == null || g == pose) return base;
        try {
            List<AnimationTrigger> extra = reg.getPoseAnimations(g);
            if (extra == null || extra.isEmpty()) return base;
            List<AnimationTrigger> out = new ArrayList<>(base == null ? List.of() : base);
            for (AnimationTrigger t : extra) {
                if (t == null) continue;
                String c = t.getClass().getSimpleName();
                if ("all".equals(m) || c.equals("LayerTrigger") || c.equals("ValueTrigger")) out.add(t);
            }
            return out;
        } catch (Throwable t) { return base; }
    }
}
