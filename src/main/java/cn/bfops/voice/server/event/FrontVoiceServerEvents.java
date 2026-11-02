package cn.bfops.voice.server.event;

import net.neoforged.neoforge.common.NeoForge;

public final class FrontVoiceServerEvents {
    private FrontVoiceServerEvents() {
    }

    public static void registerAll() {
        FrontVoiceServerRuntime runtime = new FrontVoiceServerRuntime();
        NeoForge.EVENT_BUS.register(new FrontVoicePlayerStateEvents(runtime));
        NeoForge.EVENT_BUS.register(new FrontVoiceProjectileTrackerEvents(runtime));
        NeoForge.EVENT_BUS.register(new FrontVoiceTaczCombatEvents(runtime));
        FrontVoiceTaczCombatEvents.registerTaczListeners(runtime);
    }
}
