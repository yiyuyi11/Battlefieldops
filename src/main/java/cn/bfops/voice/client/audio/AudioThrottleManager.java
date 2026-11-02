package cn.bfops.voice.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import cn.bfops.voice.VoiceConfig;
import cn.bfops.voice.common.audio.VoiceCue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class AudioThrottleManager {
    private static SoundInstance activeFirstPersonSound = null;
    private static final Map<VoiceCue, List<SoundInstance>> activeFirstPersonByCue = new HashMap<>();
    private static final Map<Integer, SoundInstance> activeThirdPersonFriendly = new HashMap<>();
    private static final Map<Integer, SoundInstance> activeThirdPersonEnemy = new HashMap<>();
    private static final Map<VoiceCue, List<SoundInstance>> activeThirdPersonByType = new HashMap<>();
    private static final Map<Integer, Double> thirdPersonDistances = new HashMap<>();

    private AudioThrottleManager() {
    }

    public static boolean isUnlimitedSfx(VoiceCue cue) {
        if (cue == null) return false;
        return switch (cue) {
            case DEATH, FINAL_DEATH,
                 REVIVE_ASSAULT_START, REVIVE_ASSAULT_CHARGE, REVIVE_ASSAULT_FINISH,
                 REVIVE_NORMAL_START, REVIVE_NORMAL_FINISH -> true;
            default -> false;
        };
    }

    public static boolean shouldPlayFirstPerson(VoiceCue cue) {
        cleanup();
        if (cue == VoiceCue.BURNING) {
            return activeFirstPersonByCue.getOrDefault(cue, Collections.emptyList()).isEmpty();
        }
        if (isUnlimitedSfx(cue)) return true;
        return activeFirstPersonSound == null;
    }

    public static void registerFirstPerson(VoiceCue cue, SoundInstance sound) {
        if (sound == null) return;
        if (cue == VoiceCue.BURNING) {
            activeFirstPersonByCue.computeIfAbsent(cue, key -> new ArrayList<>()).add(sound);
            return;
        }
        if (isUnlimitedSfx(cue)) return;
        activeFirstPersonSound = sound;
    }

    public static void registerFirstPersonGroup(VoiceCue cue, List<? extends SoundInstance> sounds) {
        if (cue == null || sounds == null || sounds.isEmpty()) {
            return;
        }
        List<SoundInstance> activeSounds = activeFirstPersonByCue.computeIfAbsent(cue, key -> new ArrayList<>());
        for (SoundInstance sound : sounds) {
            if (sound != null) {
                activeSounds.add(sound);
            }
        }
    }

    public static boolean shouldPlayThirdPerson(Entity sourceEntity, VoiceCue cue, double distanceToLocalPlayer) {
        if (isUnlimitedSfx(cue)) return true;
        cleanup();

        if (sourceEntity == null) return true;
        int sourceEntityId = sourceEntity.getId();

        // 1. Per-player throttle: if this player already has an active sound, reject.
        if (activeThirdPersonFriendly.containsKey(sourceEntityId) || activeThirdPersonEnemy.containsKey(sourceEntityId)) {
            return false;
        }

        // 2. Global single cue limit (e.g. max 2 RELOADING sounds globally)
        if (cue != null) {
            List<SoundInstance> activeForCue = activeThirdPersonByType.getOrDefault(cue, Collections.emptyList());
            if (activeForCue.size() >= VoiceConfig.getClientMaxSimultaneousSingleTpSound()) {
                return false; // Type limit reached
            }
        }

        // 3. Global player limit (prioritized by distance, split by friendly/enemy)
        Player localPlayer = Minecraft.getInstance().player;
        boolean isFriendly = localPlayer != null && sourceEntity.isAlliedTo(localPlayer);

        Map<Integer, SoundInstance> campMap = isFriendly ? activeThirdPersonFriendly : activeThirdPersonEnemy;
        int maxPlayers = isFriendly
            ? VoiceConfig.getClientMaxSimultaneousTpPlayersFriendly()
            : VoiceConfig.getClientMaxSimultaneousTpPlayersEnemy();

        if (campMap.size() >= maxPlayers) {
            // Find furthest active player in this camp
            int furthestPlayer = -1;
            double maxDistance = -1.0;
            for (int playerId : campMap.keySet()) {
                double dist = thirdPersonDistances.getOrDefault(playerId, 0.0);
                if (dist > maxDistance) {
                    maxDistance = dist;
                    furthestPlayer = playerId;
                }
            }

            // Compare distances
            if (furthestPlayer != -1 && distanceToLocalPlayer < maxDistance) {
                // New player is closer, stop the furthest player's sound to free up a slot
                SoundInstance furthestSound = campMap.remove(furthestPlayer);
                thirdPersonDistances.remove(furthestPlayer);
                if (cue != null) {
                    for (List<SoundInstance> list : activeThirdPersonByType.values()) {
                        list.remove(furthestSound);
                    }
                }
                Minecraft.getInstance().getSoundManager().stop(furthestSound);
            } else {
                // New player is further, reject
                return false;
            }
        }

        return true;
    }

    public static void registerThirdPerson(Entity sourceEntity, VoiceCue cue, double distanceToLocalPlayer, SoundInstance sound) {
        if (isUnlimitedSfx(cue)) return;
        if (sourceEntity == null) return;

        Player localPlayer = Minecraft.getInstance().player;
        boolean isFriendly = localPlayer != null && sourceEntity.isAlliedTo(localPlayer);

        if (isFriendly) {
            activeThirdPersonFriendly.put(sourceEntity.getId(), sound);
        } else {
            activeThirdPersonEnemy.put(sourceEntity.getId(), sound);
        }

        thirdPersonDistances.put(sourceEntity.getId(), distanceToLocalPlayer);
        if (cue != null) {
            activeThirdPersonByType.computeIfAbsent(cue, k -> new ArrayList<>()).add(sound);
        }
    }

    public static void cleanup() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;

        if (activeFirstPersonSound != null && !mc.getSoundManager().isActive(activeFirstPersonSound)) {
            activeFirstPersonSound = null;
        }

        for (Iterator<Map.Entry<VoiceCue, List<SoundInstance>>> iterator = activeFirstPersonByCue.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<VoiceCue, List<SoundInstance>> entry = iterator.next();
            entry.getValue().removeIf(sound -> !mc.getSoundManager().isActive(sound));
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }

        Iterator<Map.Entry<Integer, SoundInstance>> iteratorFriendly = activeThirdPersonFriendly.entrySet().iterator();
        while (iteratorFriendly.hasNext()) {
            Map.Entry<Integer, SoundInstance> entry = iteratorFriendly.next();
            if (!mc.getSoundManager().isActive(entry.getValue())) {
                iteratorFriendly.remove();
                thirdPersonDistances.remove(entry.getKey());
            }
        }

        Iterator<Map.Entry<Integer, SoundInstance>> iteratorEnemy = activeThirdPersonEnemy.entrySet().iterator();
        while (iteratorEnemy.hasNext()) {
            Map.Entry<Integer, SoundInstance> entry = iteratorEnemy.next();
            if (!mc.getSoundManager().isActive(entry.getValue())) {
                iteratorEnemy.remove();
                thirdPersonDistances.remove(entry.getKey());
            }
        }

        for (List<SoundInstance> list : activeThirdPersonByType.values()) {
            list.removeIf(sound -> !mc.getSoundManager().isActive(sound));
        }
    }
}
