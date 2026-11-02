package cn.bfops;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Every sound event, unpacked from the reference pack, wired by name.
 *
 * <p><b>All 120 events, not a curated few.</b> The reference pack's battle soundscape is not a set of
 * interchangeable blips: a capture, an incoming heavy shell, a ground-sensor ping and a retreat loop are four
 * different pieces of information, and this mod is reproducing a match's sound <em>language</em>, not decorating
 * a few moments. {@link SoundCatalog} holds the generated name list, taken from the {@code sounds.json} shipped
 * beside the samples, so the registration and the resource cannot disagree.
 *
 * <p><b>Why registration is an explicit call rather than a static initialiser.</b> A {@code DeferredRegister}
 * may only be written to once the game's registries are bootstrapped. Doing it from a static block makes any
 * other reference to this class — including a headless test — fail with "Not bootstrapped", which is exactly
 * what happened when this was first written. {@link #registerAll()} is therefore called once from the mod
 * constructor, in the one place where that precondition is guaranteed.
 *
 * <p><b>Why the names are readable without registering.</b> The offline tests check the whole 120-name
 * catalogue, and they run with no game at all. So {@link #names()} reports what <em>will</em> be registered
 * (the catalogue) while {@link #get} reports what <em>has</em> been, and the two are the same list.
 */
final class Sounds {
    static final DeferredRegister<SoundEvent> REGISTRY =
            DeferredRegister.create(Registries.SOUND_EVENT, "battlefield_ops");

    /** Name to holder, filled by {@link #registerAll()}. */
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> ALL = new LinkedHashMap<>();

    private Sounds() { }

    /**
     * Registers every catalogue name. Called once from the mod constructor, before the registry event.
     *
     * <p>Idempotent: a second call is a no-op rather than a duplicate registration, because the mod
     * constructor and any future caller must not be able to double-register by accident.
     */
    static void registerAll() {
        for (String name : SoundCatalog.ALL) {
            ALL.computeIfAbsent(name, key -> REGISTRY.register(key,
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath("battlefield_ops", key))));
        }
    }

    /** The event for {@code name}, or {@code null} when it is unknown or not yet registered. */
    static SoundEvent get(String name) {
        if (name == null) return null;
        DeferredHolder<SoundEvent, SoundEvent> holder = ALL.get(name);
        return holder == null ? null : holder.get();
    }

    /**
     * Every name this mod registers.
     *
     * <p>Read from the catalogue rather than from the holder map, so it is answerable before registration and
     * in a test. It is the same list {@link #registerAll()} walks.
     */
    static Set<String> names() {
        return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(SoundCatalog.ALL));
    }

    /** How many events are registered; the asset import asserts this against {@code sounds.json}. */
    static int size() {
        return SoundCatalog.ALL.size();
    }

    /** How many have actually been registered so far, which is 0 before the mod constructor runs. */
    static int registered() {
        return ALL.size();
    }
}
