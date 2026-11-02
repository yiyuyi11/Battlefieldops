package cn.bfops.voice.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class FrontVoiceKeyMappings {
    public static final String CATEGORY = "key.categories.battlefield_ops";

    public static final KeyMapping OPEN_QUICK_MESSAGE_HUD = new KeyMapping(
        "key.battlefield_ops.open_quick_message_hud",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_GRAVE_ACCENT,
        CATEGORY
    );

    public static final KeyMapping TACTICAL_COMMUNICATION = new KeyMapping(
        "key.battlefield_ops.tactical_communication",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_T,CATEGORY);

    private FrontVoiceKeyMappings() {
    }
}
