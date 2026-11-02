package cn.bfops.voice.client;

public final class QuickWheelServerConfig {
    private static volatile boolean allowCustomMessages = true;
    private static volatile boolean enableQuickChat = true;

    private QuickWheelServerConfig() {
    }

    public static void setAllowCustomMessages(boolean allowed) {
        allowCustomMessages = allowed;
    }

    public static boolean isCustomMessagesAllowed() {
        return allowCustomMessages;
    }

    public static void setEnableQuickChat(boolean enabled) {
        enableQuickChat = enabled;
    }

    public static boolean isQuickChatEnabled() {
        return enableQuickChat;
    }
}
