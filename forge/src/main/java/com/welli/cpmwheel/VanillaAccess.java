package com.welli.cpmwheel;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** 1.5.7 world-pass arm: private vanilla members (Forge 1.20.1: reflection by SRG name, works in dev and in the game). */
final class VanillaAccess {
    private VanillaAccess() {}
    private static final Method FOV = ObfuscationReflectionHelper.findMethod(GameRenderer.class, "m_109141_", Camera.class, float.class, boolean.class);
    private static final Method BOB_HURT = ObfuscationReflectionHelper.findMethod(GameRenderer.class, "m_109117_", PoseStack.class, float.class);
    private static final Method BOB_VIEW = ObfuscationReflectionHelper.findMethod(GameRenderer.class, "m_109138_", PoseStack.class, float.class);
    private static final Field RENDER_HAND = ObfuscationReflectionHelper.findField(GameRenderer.class, "f_109070_");
    private static final Field PANORAMIC = ObfuscationReflectionHelper.findField(GameRenderer.class, "f_109076_");
    private static final Field MAIN_ITEM = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109300_");
    private static final Field OFF_ITEM = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109301_");
    private static final Field MAIN_H = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109302_");
    private static final Field O_MAIN_H = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109303_");
    private static final Field OFF_H = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109304_");
    private static final Field O_OFF_H = ObfuscationReflectionHelper.findField(ItemInHandRenderer.class, "f_109305_");

    private static RuntimeException wrap(Throwable t) { return t instanceof RuntimeException r ? r : new RuntimeException(t); }
    static double fov(GameRenderer gr, Camera c, float pt) { try { return (Double) FOV.invoke(gr, c, pt, false); } catch (Throwable t) { throw wrap(t); } }
    static void bobHurt(GameRenderer gr, PoseStack ps, float pt) { try { BOB_HURT.invoke(gr, ps, pt); } catch (Throwable t) { throw wrap(t); } }
    static void bobView(GameRenderer gr, PoseStack ps, float pt) { try { BOB_VIEW.invoke(gr, ps, pt); } catch (Throwable t) { throw wrap(t); } }
    static boolean renderHand(GameRenderer gr) { try { return RENDER_HAND.getBoolean(gr); } catch (Throwable t) { throw wrap(t); } }
    static boolean panoramic(GameRenderer gr) { try { return PANORAMIC.getBoolean(gr); } catch (Throwable t) { throw wrap(t); } }
    static ItemStack mainItem(ItemInHandRenderer r) { try { return (ItemStack) MAIN_ITEM.get(r); } catch (Throwable t) { throw wrap(t); } }
    static ItemStack offItem(ItemInHandRenderer r) { try { return (ItemStack) OFF_ITEM.get(r); } catch (Throwable t) { throw wrap(t); } }
    static float mainH(ItemInHandRenderer r) { try { return MAIN_H.getFloat(r); } catch (Throwable t) { throw wrap(t); } }
    static float oMainH(ItemInHandRenderer r) { try { return O_MAIN_H.getFloat(r); } catch (Throwable t) { throw wrap(t); } }
    static float offH(ItemInHandRenderer r) { try { return OFF_H.getFloat(r); } catch (Throwable t) { throw wrap(t); } }
    static float oOffH(ItemInHandRenderer r) { try { return O_OFF_H.getFloat(r); } catch (Throwable t) { throw wrap(t); } }
}
