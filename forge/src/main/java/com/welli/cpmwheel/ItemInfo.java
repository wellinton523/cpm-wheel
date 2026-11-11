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
}
