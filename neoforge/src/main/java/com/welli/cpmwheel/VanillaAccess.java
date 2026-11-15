package com.welli.cpmwheel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.welli.cpmwheel.mixin.GameRendererAccess;
import com.welli.cpmwheel.mixin.ItemInHandAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;

/** 1.5.7 world-pass arm: private vanilla members (NeoForge: mixin accessors). */
final class VanillaAccess {
    private VanillaAccess() {}
    static double fov(GameRenderer gr, Camera c, float pt) { return ((GameRendererAccess) gr).cpmwheel$getFov(c, pt, false); }
    static void bobHurt(GameRenderer gr, PoseStack ps, float pt) { ((GameRendererAccess) gr).cpmwheel$bobHurt(ps, pt); }
    static void bobView(GameRenderer gr, PoseStack ps, float pt) { ((GameRendererAccess) gr).cpmwheel$bobView(ps, pt); }
    static boolean renderHand(GameRenderer gr) { return ((GameRendererAccess) gr).cpmwheel$renderHand(); }
    static boolean panoramic(GameRenderer gr) { return ((GameRendererAccess) gr).cpmwheel$panoramic(); }
    static ItemStack mainItem(ItemInHandRenderer r) { return ((ItemInHandAccess) r).cpmwheel$mainItem(); }
    static ItemStack offItem(ItemInHandRenderer r) { return ((ItemInHandAccess) r).cpmwheel$offItem(); }
    static float mainH(ItemInHandRenderer r) { return ((ItemInHandAccess) r).cpmwheel$mainH(); }
    static float oMainH(ItemInHandRenderer r) { return ((ItemInHandAccess) r).cpmwheel$oMainH(); }
    static float offH(ItemInHandRenderer r) { return ((ItemInHandAccess) r).cpmwheel$offH(); }
    static float oOffH(ItemInHandRenderer r) { return ((ItemInHandAccess) r).cpmwheel$oOffH(); }
}
