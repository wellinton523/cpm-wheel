package com.welli.cpmwheel;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Loader/version specific item helpers (1.21.1). */
final class ItemInfo {
    private ItemInfo() {}
    /** main-hand attack damage incl. the player's base 1.0 */
    static double attackDamage(ItemStack st) {
        if (st == null || st.isEmpty()) return 1.0;
        double d = 1.0;
        ItemAttributeModifiers mods = st.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry e : mods.modifiers())
            if (e.attribute().equals(Attributes.ATTACK_DAMAGE) && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) d += e.modifier().amount();
        return d;
    }
}
