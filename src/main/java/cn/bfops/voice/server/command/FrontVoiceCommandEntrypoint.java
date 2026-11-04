package cn.bfops.voice.server.command;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class FrontVoiceCommandEntrypoint {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FrontVoiceServerCommands.register(event.getDispatcher());
    }
}
