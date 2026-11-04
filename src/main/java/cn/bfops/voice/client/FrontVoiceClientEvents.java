package cn.bfops.voice.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderGuiEvent;
import cn.bfops.voice.client.hud.QuickMessageHudHandler;

/**
 * Client-side wiring for the quick-message voice wheel: one key binding, one bootstrap call, and one HUD
 * render pass above everything else.
 */
@EventBusSubscriber(modid = "battlefield_ops", value = Dist.CLIENT)
public final class FrontVoiceClientEvents {
    private FrontVoiceClientEvents() {
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        float partialTick = event.getPartialTick();
        QuickMessageHudHandler.renderHud(event.getGuiGraphics(), partialTick);
    }
}
