package com.welli.cpmwheel.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.welli.cpmwheel.GlowLight;
import com.welli.cpmwheel.HandGlow;
import java.util.function.BiFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Wraps the vertex buffer CPM gets for our hand-glow render type so its light is full-bright. */
@Mixin(targets = "com.tom.cpm.shared.model.render.BatchedBuffers", remap = false)
public abstract class BatchedBuffersMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = {"lambda$nextBatch$0", "lambda$nextBatch$1"}, require = 0, remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Ljava/util/function/BiFunction;apply(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object cpmwheel$glowBuffer(BiFunction f, Object src, Object rt) {
        Object out = f.apply(src, rt);
        try {
            HandGlow.hookFired("BatchedBuffers.getBuffer");
            if (out instanceof VertexConsumer vc && !(out instanceof GlowLight) && HandGlow.isGlow(rt)) {
                HandGlow.logOnce("light", "hand glow: full-bright light applied to glow buffer");
                return new GlowLight(vc);
            }
        } catch (Throwable t) { HandGlow.fail("BatchedBuffers", t); }
        return out;
    }
}
