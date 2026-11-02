package cn.bfops.voice.server.command;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class FrontVoiceCommandEntrypoint {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FrontVoiceServerCommands.register(event.getDispatcher());
    }
}
