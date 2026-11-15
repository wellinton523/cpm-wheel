package com.welli.cpmwheel;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(CpmWheel.MODID)
public class CpmWheel {
    public static final String MODID = "cpmwheel";
    public static final Logger LOG = LogUtils.getLogger();

    public static final KeyMapping KEY_WHEEL = new KeyMapping("key.cpmwheel.open",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.cpmwheel");
    public static final KeyMapping KEY_EDIT = new KeyMapping("key.cpmwheel.edit",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.cpmwheel");

    public static final KeyMapping KEY_HUD = new KeyMapping("key.cpmwheel.hud",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.cpmwheel");

    /** 1.5.8: HUD edit mode (drag the HUD elements); unbound by default */
    public static final KeyMapping KEY_HUD_EDIT = new KeyMapping("key.cpmwheel.hudedit",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.cpmwheel");

    public CpmWheel() {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::keys);
        bus.addListener(this::imc);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        HandAnim.detectConflicts(id -> ModList.get().isLoaded(id));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.RenderTickEvent e) -> { if (e.phase == TickEvent.Phase.START) HandAnim.frameStart(); });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderLevelStageEvent e) -> {
            if (e.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES)
                WorldArm.afterEntities(e.getPoseStack(), e.getCamera(), e.getPartialTick()); });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderHandEvent e) ->
                { if (WorldArm.suppress(e.getHand())) { e.setCanceled(true); return; }
                  if (HandAnim.apply(e.getPoseStack(), e.getHand(), e.getItemStack(), e.getPartialTick(), e.getSwingProgress(), e.getEquipProgress(), e.getMultiBufferSource(), e.getPackedLight())) e.setCanceled(true); });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderGuiEvent.Post e) -> Hud.render(e.getGuiGraphics()));
    }

    private void keys(RegisterKeyMappingsEvent e) {
        e.register(KEY_WHEEL);
        e.register(KEY_EDIT);
        e.register(KEY_HUD);
        e.register(KEY_HUD_EDIT);
    }

    private void imc(InterModEnqueueEvent e) {
        if (ModList.get().isLoaded("cpm")) {
            InterModComms.sendTo("cpm", "api", CpmBridge::createPluginProxy);
        }
    }

    private void tick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.START) { Live2d.tickStart(Minecraft.getInstance()); return; } // 1.7.0 Física Live2D
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Actions.tick(mc);
        while (KEY_WHEEL.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new WheelScreen(true));
        }
        while (KEY_HUD.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                WheelConfig c = WheelConfig.get();
                c.hudEnabled = !c.hudEnabled; c.save(); Hud.invalidate();
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("cpmwheel.hud.toggled", c.hudEnabled ? "ON" : "OFF"), true);
            }
        }
        while (KEY_HUD_EDIT.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new HudEditScreen(null));
        }
        while (KEY_EDIT.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new EditScreen(null));
        }
    }
}
