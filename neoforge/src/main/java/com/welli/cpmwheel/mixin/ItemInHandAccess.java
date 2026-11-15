package com.welli.cpmwheel.mixin;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.5.7 world-pass arm: the stacks / equip heights vanilla's first-person hand uses (equip animation). */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandAccess {
    @Accessor("mainHandItem") ItemStack cpmwheel$mainItem();
    @Accessor("offHandItem") ItemStack cpmwheel$offItem();
    @Accessor("mainHandHeight") float cpmwheel$mainH();
    @Accessor("oMainHandHeight") float cpmwheel$oMainH();
    @Accessor("offHandHeight") float cpmwheel$offH();
    @Accessor("oOffHandHeight") float cpmwheel$oOffH();
}
