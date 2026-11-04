package cn.bfops.voice.server.event;

import net.minecraftforge.common.MinecraftForge;

public final class FrontVoiceServerEvents {
    private FrontVoiceServerEvents() {
    }

    public static void registerAll() {
        FrontVoiceServerRuntime runtime = new FrontVoiceServerRuntime();
        MinecraftForge.EVENT_BUS.register(new FrontVoicePlayerStateEvents(runtime));
        MinecraftForge.EVENT_BUS.register(new FrontVoiceProjectileTrackerEvents(runtime));
        MinecraftForge.EVENT_BUS.register(new FrontVoiceTaczCombatEvents(runtime));
        FrontVoiceTaczCombatEvents.registerTaczListeners(runtime);
    }
}
