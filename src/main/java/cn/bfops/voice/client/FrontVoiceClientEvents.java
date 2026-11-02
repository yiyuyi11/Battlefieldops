package cn.bfops.voice.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
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
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(FrontVoiceKeyMappings.OPEN_QUICK_MESSAGE_HUD);
        event.register(FrontVoiceKeyMappings.TACTICAL_COMMUNICATION);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(FrontVoiceClientBootstrap::init);
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        QuickMessageHudHandler.renderHud(event.getGuiGraphics(), partialTick);
    }
}
