package cn.bfops.voice.common.audio;

import net.minecraft.resources.ResourceLocation;
import cn.bfops.voice.VoiceMain;

public final class FrontVoiceSoundIds {
    private FrontVoiceSoundIds() {
    }

    public static ResourceLocation resolve(String rawSoundEventId) {
        if (rawSoundEventId == null) {
            return null;
        }
        String trimmed = rawSoundEventId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.indexOf(':') >= 0) {
            return ResourceLocation.tryParse(trimmed);
        }
        return ResourceLocation.tryParse(VoiceMain.MODID + ":" + trimmed);
    }

    public static String toManifestEventId(String rawSoundEventId) {
        ResourceLocation resolved = resolve(rawSoundEventId);
        if (resolved == null) {
            return null;
        }
        if (!VoiceMain.MODID.equals(resolved.getNamespace())) {
            return null;
        }
        return resolved.getPath();
    }
}
