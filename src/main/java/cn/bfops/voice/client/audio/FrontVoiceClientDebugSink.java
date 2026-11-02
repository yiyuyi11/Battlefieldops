package cn.bfops.voice.client.audio;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import cn.bfops.voice.VoiceConfig;
import cn.bfops.voice.VoiceMain;

public final class FrontVoiceClientDebugSink {
    private FrontVoiceClientDebugSink() {
    }

    public static void info(String message) {
        log("[FrontVoice] " + message, ChatFormatting.GOLD);
    }

    public static void warn(String message) {
        log("[FrontVoice][WARN] " + message, ChatFormatting.RED);
    }

    private static void log(String message, ChatFormatting color) {
        if (VoiceConfig.isClientDebugLoggingEnabled()) {
            VoiceMain.LOGGER.info(message);
        }
        if (!VoiceConfig.isClientDebugChatEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        minecraft.player.sendSystemMessage(Component.literal(message).withStyle(color));
    }
}
