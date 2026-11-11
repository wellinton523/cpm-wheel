package com.welli.cpmwheel;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

import java.util.List;

/** Item-conditional slots: rule matching on the held items. Evaluated on click or on held-item change only. */
public final class Conditions {
    private Conditions() {}
    public static final String[] HANDS = {"main", "off", "any"};
    public static final String[] CATEGORIES = {"", "sword", "axe", "bow", "crossbow", "trident", "shield", "tool", "block", "empty"};

    public static String itemId(ItemStack st) {
        try {
            if (st == null || st.isEmpty()) return "minecraft:air";
            ResourceLocation k = BuiltInRegistries.ITEM.getKey(st.getItem());
            return k == null ? "" : k.toString();
        } catch (Throwable t) { return ""; }
    }

    static boolean category(ItemStack st, String c) {
        if (c == null || c.isBlank()) return true;
        if (st == null || st.isEmpty()) return c.equals("empty");
        Item it = st.getItem();
        return switch (c) {
            case "sword" -> it instanceof SwordItem;
            case "axe" -> it instanceof AxeItem;
            case "bow" -> it instanceof BowItem;
            case "crossbow" -> it instanceof CrossbowItem;
            case "trident" -> it instanceof TridentItem;
            case "shield" -> it instanceof ShieldItem;
            case "tool" -> it instanceof DiggerItem;
            case "block" -> it instanceof BlockItem;
            default -> false;
        };
    }

    static boolean matchStack(WheelConfig.Rule r, ItemStack st) {
        try {
            if (st == null) st = ItemStack.EMPTY;
            if (r.item != null && !r.item.isBlank() && !r.item.trim().equalsIgnoreCase(itemId(st))) return false;
            if (r.tag != null && !r.tag.isBlank()) {
                ResourceLocation rl = ResourceLocation.tryParse(r.tag.trim().replaceFirst("^#", ""));
                if (rl == null || st.isEmpty() || !st.is(TagKey.create(Registries.ITEM, rl))) return false;
            }
            if (!category(st, r.category)) return false;
            if (r.name != null && !r.name.isBlank()
                    && (st.isEmpty() || !st.getHoverName().getString().toLowerCase().contains(r.name.trim().toLowerCase()))) return false;
            if (r.dmgMin >= 0 || r.dmgMax >= 0) {
                double dmg = ItemInfo.attackDamage(st);
                if (r.dmgMin >= 0 && dmg < r.dmgMin) return false;
                if (r.dmgMax >= 0 && dmg > r.dmgMax) return false;
            }
            return true;
        } catch (Throwable t) { // modded items with odd data never crash the client
            CpmWheel.LOG.debug("[CPM Wheel] rule check failed", t);
            return false;
        }
    }

    static boolean match(WheelConfig.Rule r, Player p) {
        String h = r.hand == null ? "main" : r.hand;
        ItemStack main = p.getItemInHand(InteractionHand.MAIN_HAND), off = p.getItemInHand(InteractionHand.OFF_HAND);
        return switch (h) {
            case "off" -> matchStack(r, off);
            case "any" -> matchStack(r, main) || matchStack(r, off);
            default -> matchStack(r, main);
        };
    }

    /** index of the first matching rule with an animation, -1 = none */
    public static int evaluate(WheelConfig.Slot s) {
        Player p = Minecraft.getInstance().player;
        if (p == null || s == null) return -1;
        List<WheelConfig.Rule> l = s.ruleList();
        for (int i = 0; i < l.size(); i++) {
            WheelConfig.Rule r = l.get(i);
            if (r.anim != null && !r.anim.isBlank() && match(r, p)) return i;
        }
        return -1;
    }

    /** click: play the first matching rule's animation, or the fallback */
    public static void click(WheelConfig.Slot s) {
        int i = evaluate(s);
        List<WheelConfig.Rule> l = s.ruleList();
        if (i >= 0) {
            WheelConfig.Rule r = l.get(i);
            CpmBridge.play(r.anim, r.value);
            s.lastMatch = "#" + (i + 1) + " " + r.anim;
        } else if (s.fallback != null && !s.fallback.isBlank()) {
            CpmBridge.play(s.fallback, -1);
            s.lastMatch = "\u2205 " + s.fallback;
        } else s.lastMatch = "\u2205";
        Hud.invalidate();
    }

    /** auto mode, called when the held items changed: switch the matched pose on, the previous one off */
    public static void auto(WheelConfig.Slot s) {
        int i = evaluate(s);
        WheelConfig.Rule r = i >= 0 ? s.ruleList().get(i) : null;
        String want = r == null ? null : r.anim;
        if (want != null && want.equals(s.autoApplied)) return;
        if (s.autoApplied != null) CpmBridge.play(s.autoApplied, 0);
        if (want != null) CpmBridge.play(want, r.value >= 0 ? r.value : 1);
        s.autoApplied = want;
        s.lastMatch = want == null ? null : "#" + (i + 1) + " " + want;
        Hud.invalidate();
    }

    /** turn off whatever auto mode applied (slot switched off / config reloaded) */
    public static void autoOff(WheelConfig.Slot s) {
        if (s != null && s.autoApplied != null) { CpmBridge.play(s.autoApplied, 0); s.autoApplied = null; s.lastMatch = null; }
    }
}
