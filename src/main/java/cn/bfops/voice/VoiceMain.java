package cn.bfops.voice;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Front-voice callout subsystem, ported from {@code gd656frontvoice}.
 *
 * <p>This is <b>not</b> a standalone {@code @Mod}: the audio assets and sound events live under the parent
 * mod's {@code battlefield_ops} namespace, so the subsystem is a library the parent mod boots. The only
 * globally visible surface is {@link #MODID} (always {@code battlefield_ops}) and the logger.
 */
public final class VoiceMain {
    /** The namespace the front-voice assets were merged into. */
    public static final String MODID = "battlefield_ops";

    public static final Logger LOGGER = LogUtils.getLogger();

    private VoiceMain() {
    }
}
