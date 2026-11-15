package com.welli.cpmwheel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.5.7 world-pass arm: same fov / camera bob the vanilla (and Iris) hand pass uses. */
@Mixin(GameRenderer.class)
public interface GameRendererAccess {
    @Invoker("getFov") double cpmwheel$getFov(Camera camera, float partialTicks, boolean useFovSetting);
    @Invoker("bobHurt") void cpmwheel$bobHurt(PoseStack poseStack, float partialTicks);
    @Invoker("bobView") void cpmwheel$bobView(PoseStack poseStack, float partialTicks);
    @Accessor("renderHand") boolean cpmwheel$renderHand();
    @Accessor("panoramicMode") boolean cpmwheel$panoramic();
}
