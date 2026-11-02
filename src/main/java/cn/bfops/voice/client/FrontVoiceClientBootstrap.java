package cn.bfops.voice.client;

import cn.bfops.voice.VoiceConfig;
import cn.bfops.voice.VoiceMain;
import cn.bfops.voice.client.hud.QuickMessageHudHandler;
import cn.bfops.voice.common.audio.FrontVoiceAudioManifest;

public final class FrontVoiceClientBootstrap {
    private static boolean initialized;

    private FrontVoiceClientBootstrap() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        FrontVoiceAudioManifest manifestIndex = FrontVoiceAudioManifest.get();
        QuickMessageHudHandler.init();

        if (VoiceConfig.shouldLogManifestSummary()) {
            VoiceMain.LOGGER.info(
                "Imported audio manifest loaded: events={}, sounds={}, skipped={}",
                manifestIndex.getEventCount(),
                manifestIndex.getImportedSoundCount(),
                manifestIndex.getSkippedFileCount()
            );
        }
    }
}
