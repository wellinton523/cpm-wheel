package com.welli.cpmwheel;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Loader/version specific item helpers (1.20.1). */
final class ItemInfo {
    private ItemInfo() {}
    /** main-hand attack damage incl. the player's base 1.0 */
    static double attackDamage(ItemStack st) {
        if (st == null || st.isEmpty()) return 1.0;
        double d = 1.0;
        for (AttributeModifier m : st.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE))
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) d += m.getAmount();
        return d;
    }

    /** 1.5.7 effect triggers: registry id of an active effect ("minecraft:poison") */
    static String effectId(net.minecraft.world.effect.MobEffectInstance e) {
        try { var k = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect()); return k == null ? "" : k.toString(); } catch (Throwable t) { return ""; }
    }

    /** 1.5.8 toasts: the effect's HUD icon */
    static net.minecraft.client.renderer.texture.TextureAtlasSprite effectSprite(String id) {
        try {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
            net.minecraft.world.effect.MobEffect e = rl == null ? null : net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(rl);
            return e == null ? null : net.minecraft.client.Minecraft.getInstance().getMobEffectTextures().get(e);
        } catch (Throwable t) { return null; }
    }
}
