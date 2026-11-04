package cn.bfops.voice.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Voice mod-bus hooks: key bindings and the client bootstrap.
 *
 * <p>Forge 1.20.1's {@code @EventBusSubscriber} does not cross buses; these two are mod-bus events, so they
 * need their own subscriber. Left in {@link FrontVoiceClientEvents} they registered no keys at all.
 */
@Mod.EventBusSubscriber(modid = "battlefield_ops", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FrontVoiceClientSetup {
    private FrontVoiceClientSetup() {
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
}
