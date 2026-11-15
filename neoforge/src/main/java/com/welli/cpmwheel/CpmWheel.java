package com.welli.cpmwheel;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
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

    public CpmWheel(IEventBus bus, ModContainer container, Dist dist) {
        if (dist != Dist.CLIENT) return;
        bus.addListener(this::keys);
        bus.addListener(this::imc);
        NeoForge.EVENT_BUS.addListener(this::tick);
        HandAnim.detectConflicts(id -> ModList.get().isLoaded(id));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderFrameEvent.Pre e) -> HandAnim.frameStart());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderLevelStageEvent e) -> {
            if (e.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES)
                WorldArm.afterEntities(e.getPoseStack(), e.getCamera(), e.getPartialTick().getGameTimeDeltaPartialTick(true)); });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderHandEvent e) ->
                { if (WorldArm.suppress(e.getHand())) { e.setCanceled(true); return; }
                  if (HandAnim.apply(e.getPoseStack(), e.getHand(), e.getItemStack(), e.getPartialTick(), e.getSwingProgress(), e.getEquipProgress(), e.getMultiBufferSource(), e.getPackedLight())) e.setCanceled(true); });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiEvent.Post e) -> Hud.render(e.getGuiGraphics()));
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

    private void tick(ClientTickEvent.Post e) {
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
