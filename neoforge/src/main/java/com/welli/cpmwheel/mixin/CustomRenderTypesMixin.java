package com.welli.cpmwheel.mixin;

import com.welli.cpmwheel.HandGlow;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In the first-person hand pass, CPM glow parts use a normal entity-translucent render type instead of "eyes".
 * Iris maps "eyes" and "entity_translucent_emissive" to its eyes program, which has no hand variant,
 * so the shader pack drops them on the hand. entity_translucent has HAND_* programs. Light is forced
 * to full-bright by BatchedBuffersMixin.
 */
@Mixin(targets = "com.tom.cpm.client.CustomRenderTypes", remap = false)
public abstract class CustomRenderTypesMixin {
    @Inject(method = "glowingEyes", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void cpmwheel$handGlow(ResourceLocation rl, CallbackInfoReturnable<RenderType> cir) {
        try {
            HandGlow.hookFired("CustomRenderTypes.glowingEyes");
            if (rl != null && HandGlow.active()) {
                RenderType rt = RenderType.entityTranslucent(rl, false);
                HandGlow.markGlow(rt);
                cir.setReturnValue(rt);
            }
        } catch (Throwable t) { HandGlow.fail("glowingEyes", t); }
    }
}
