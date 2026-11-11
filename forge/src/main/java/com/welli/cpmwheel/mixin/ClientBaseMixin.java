package com.welli.cpmwheel.mixin;

import com.welli.cpmwheel.HandGlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks CPM's first-person hand render (renderHand ... renderHandPost). */
@Mixin(targets = "com.tom.cpm.client.ClientBase", remap = false)
public abstract class ClientBaseMixin {
    @Inject(method = "renderHand", at = @At("HEAD"), require = 0, remap = false)
    private void cpmwheel$handPre(CallbackInfo ci) { HandGlow.inHand = true; HandGlow.hookFired("ClientBase.renderHand"); }

    @Inject(method = "renderHandPost", at = @At("HEAD"), require = 0, remap = false)
    private void cpmwheel$handPost(CallbackInfo ci) { HandGlow.inHand = false; }
}
