package com.welli.cpmwheel.mixin;

import com.welli.cpmwheel.HandGlow;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In the first-person hand pass, CPM glow parts use an emissive entity render type instead of "eyes". */
@Mixin(targets = "com.tom.cpm.client.CustomRenderTypes", remap = false)
public abstract class CustomRenderTypesMixin {
    @Inject(method = "glowingEyes", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void cpmwheel$handGlow(ResourceLocation rl, CallbackInfoReturnable<RenderType> cir) {
        try {
            if (rl != null && HandGlow.active()) cir.setReturnValue(RenderType.entityTranslucentEmissive(rl));
        } catch (Throwable ignored) {}
    }
}
