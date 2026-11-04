package cn.bfops.voice;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Registers the 149 front-voice sound events, kept separate from the parent mod's {@code Sounds}
 * registry so the two subsystems' catalogues stay independent (the parent's offline tests assert its
 * own 206-event sound bank). Mirrors the parent's explicit-call pattern: a {@code DeferredRegister}
 * may only be written to once the registries are bootstrapped.
 */
public final class FrontVoiceSounds {
    public static final DeferredRegister<SoundEvent> REGISTRY =
            DeferredRegister.create(Registries.SOUND_EVENT, VoiceMain.MODID);

    private static boolean registered;

    private FrontVoiceSounds() {
    }

    public static void registerAll() {
        if (registered) {
            return;
        }
        registered = true;
        for (String name : FrontVoiceSoundCatalog.ALL) {
            REGISTRY.register(name,
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(VoiceMain.MODID, name)));
        }
    }
}
