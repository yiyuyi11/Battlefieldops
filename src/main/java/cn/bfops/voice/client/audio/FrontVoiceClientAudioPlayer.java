package cn.bfops.voice.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import cn.bfops.voice.VoiceConfig;
import cn.bfops.voice.common.audio.CampType;
import cn.bfops.voice.common.audio.CueRouter;
import cn.bfops.voice.common.audio.FrontVoiceAudioManifest;
import cn.bfops.voice.common.audio.FrontVoicePlayRequest;
import cn.bfops.voice.common.audio.FrontVoicePlaybackMode;
import cn.bfops.voice.common.audio.FrontVoiceSoundIds;
import cn.bfops.voice.common.audio.VoiceCue;

import java.util.List;
import java.util.ArrayList;

public final class FrontVoiceClientAudioPlayer {
    private FrontVoiceClientAudioPlayer() {
    }

    public static void play(FrontVoicePlayRequest request) {
        if (request == null) {
            return;
        }
        if(!cn.bfops.NativeAudioBank.voiceEnabled()&&!medical(request))return;
        if (!VoiceConfig.isClientAudioEnabled()) {
            FrontVoiceClientDebugSink.info("Skipped playback because client audio is disabled");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        if (request.isCue()) {
            playCue(request, minecraft);
            return;
        }

        playRaw(request, minecraft);
    }

    private static boolean medical(FrontVoicePlayRequest request){
        if(request.voiceCue()!=null)return switch(request.voiceCue()){
            case REVIVE_ASSAULT_START,REVIVE_ASSAULT_CHARGE,REVIVE_ASSAULT_FINISH,REVIVE_NORMAL_START,REVIVE_NORMAL_FINISH->true;
            default->false;
        };
        String event=request.soundEventId();return event!=null&&(event.contains("downed.defibrillator")||event.contains("downed.syringe"));
    }
    private static void playCue(FrontVoicePlayRequest request, Minecraft minecraft) {
        Player localPlayer = minecraft.player;
        if (localPlayer == null) return;

        boolean isLocal = request.hasSourceEntity() && request.sourceEntityId() == localPlayer.getId();
        String soundEventId = null;
        FrontVoicePlaybackMode actualMode = request.playbackMode();
        Entity sourceEntity = request.hasSourceEntity() ? minecraft.level.getEntity(request.sourceEntityId()) : null;

        CampType effectiveCamp = request.campType();
        if (VoiceConfig.isClientDetectEnemyByVanillaTeam() || VoiceConfig.isClientIgnoreConquestCamp()) {
            boolean isFriendly = isLocal || (sourceEntity != null && sourceEntity.isAlliedTo(localPlayer));
            effectiveCamp = isFriendly ? VoiceConfig.getClientFriendlyVoicePack() : VoiceConfig.getClientEnemyVoicePack();
        }

        if (isLocal) {
            if (!AudioThrottleManager.shouldPlayFirstPerson(request.voiceCue())) {
                FrontVoiceClientDebugSink.info("Skipped first-person sound due to throttle");
                return;
            }
            List<String> localSoundEventIds = CueRouter.resolveFirstPersonLayers(request.voiceCue(), effectiveCamp);
            if (localSoundEventIds.isEmpty() && VoiceConfig.isClientFallbackThirdPersonForLocal()) {
                String fallbackThirdPerson = CueRouter.resolveThirdPerson(request.voiceCue(), effectiveCamp);
                if (fallbackThirdPerson != null) {
                    localSoundEventIds = List.of(fallbackThirdPerson);
                }
            }
            actualMode = FrontVoicePlaybackMode.LOCAL; // always flat for local player
            if (localSoundEventIds.isEmpty()) {
                FrontVoiceClientDebugSink.warn("Failed to resolve cue " + request.voiceCue() + " for camp " + effectiveCamp + " (isLocal: true)");
                return;
            }
            List<BaseFrontVoiceSound> playedSounds = playCueLayers(localSoundEventIds, actualMode, request, minecraft);
            if (!playedSounds.isEmpty()) {
                if (request.voiceCue() == VoiceCue.BURNING) {
                    AudioThrottleManager.registerFirstPersonGroup(request.voiceCue(), playedSounds);
                } else {
                    AudioThrottleManager.registerFirstPerson(request.voiceCue(), playedSounds.get(0));
                }
            }
            return;
        } else {
            double distanceToLocal = 0.0;
            if (request.position() != null) {
                distanceToLocal = localPlayer.distanceToSqr(request.position().x, request.position().y, request.position().z);
            }
            if (!AudioThrottleManager.shouldPlayThirdPerson(sourceEntity, request.voiceCue(), distanceToLocal)) {
                FrontVoiceClientDebugSink.info("Skipped third-person sound due to throttle");
                return;
            }
            soundEventId = CueRouter.resolveThirdPerson(request.voiceCue(), effectiveCamp);
            // keep actualMode (ENTITY or POSITION)
        }

        if (soundEventId == null) {
            FrontVoiceClientDebugSink.warn("Failed to resolve cue " + request.voiceCue() + " for camp " + effectiveCamp + " (isLocal: " + isLocal + ")");
            return;
        }

        FrontVoicePlayRequest actualRequest = FrontVoicePlayRequest.raw(
            soundEventId, actualMode, request.sourceEntityId(), request.position(), request.volume(), request.pitch(), request.attenuationDistance()
        );
        BaseFrontVoiceSound playedSound = playRaw(actualRequest, minecraft);

        if (playedSound != null) {
            if (isLocal) {
                AudioThrottleManager.registerFirstPerson(request.voiceCue(), playedSound);
            } else {
                double distanceToLocal = 0.0;
                if (request.position() != null) {
                    distanceToLocal = localPlayer.distanceToSqr(request.position().x, request.position().y, request.position().z);
                }
                AudioThrottleManager.registerThirdPerson(sourceEntity, request.voiceCue(), distanceToLocal, playedSound);
            }
        }
    }

    private static List<BaseFrontVoiceSound> playCueLayers(
        List<String> soundEventIds,
        FrontVoicePlaybackMode playbackMode,
        FrontVoicePlayRequest request,
        Minecraft minecraft
    ) {
        List<BaseFrontVoiceSound> playedSounds = new ArrayList<>();
        for (String soundEventId : soundEventIds) {
            BaseFrontVoiceSound playedSound = playRaw(
                FrontVoicePlayRequest.raw(
                    soundEventId,
                    playbackMode,
                    request.sourceEntityId(),
                    request.position(),
                    request.volume(),
                    request.pitch(),
                    request.attenuationDistance()
                ),
                minecraft
            );
            if (playedSound != null) {
                playedSounds.add(playedSound);
            }
        }
        return playedSounds;
    }

    private static BaseFrontVoiceSound playRaw(FrontVoicePlayRequest request, Minecraft minecraft) {
        SoundManager soundManager = minecraft.getSoundManager();
        if (soundManager == null) {
            return null;
        }

        ResourceLocation soundId = FrontVoiceSoundIds.resolve(request.soundEventId());
        if (soundId == null) {
            FrontVoiceClientDebugSink.warn("Invalid sound event id: " + request.soundEventId());
            return null;
        }
        String manifestEventId = FrontVoiceSoundIds.toManifestEventId(request.soundEventId());
        if (manifestEventId == null) {
            FrontVoiceClientDebugSink.warn("Sound event uses unsupported namespace: " + request.soundEventId());
            return null;
        }
        if (!FrontVoiceAudioManifest.get().contains(manifestEventId)&&!cn.bfops.NativeAudioBank.nativeEvent(manifestEventId)) {
            FrontVoiceClientDebugSink.warn("Sound event not present in imported manifest: " + soundId);
        }

        return switch (request.playbackMode()) {
            case LOCAL -> playLocal(soundManager, soundId, request.volume(), request.pitch());
            case POSITION -> playAtPosition(soundManager, soundId, request.position(), request.volume(), request.pitch(), request.attenuationDistance());
            case ENTITY -> playForEntity(soundManager, soundId, request.sourceEntityId(), request.position(), request.volume(), request.pitch(), request.attenuationDistance());
        };
    }

    private static BaseFrontVoiceSound playLocal(SoundManager soundManager, ResourceLocation soundId, float volume, float pitch) {
        float scaledVolume = scaleVolume(volume, VoiceConfig.getClientMasterVolume() * VoiceConfig.getClientFirstPersonVolume());
        FrontVoiceClientDebugSink.info("Play local sound=" + soundId + " volume=" + scaledVolume + " pitch=" + pitch);
        return playResolved(soundManager, new LocalSound(soundId, scaledVolume, pitch));
    }

    private static BaseFrontVoiceSound playAtPosition(
        SoundManager soundManager,
        ResourceLocation soundId,
        Vec3 position,
        float volume,
        float pitch,
        float attenuationDistance
    ) {
        if (position == null) {
            FrontVoiceClientDebugSink.warn("Position playback skipped because position is missing for " + soundId);
            return null;
        }
        float scaledVolume = scaleVolume(volume, VoiceConfig.getClientMasterVolume() * VoiceConfig.getClientThirdPersonVolume());
        float resolvedDistance = resolveDistance(attenuationDistance);
        FrontVoiceClientDebugSink.info(
            "Play positioned sound=" + soundId + " pos=" + position + " volume=" + scaledVolume + " pitch=" + pitch + " dist=" + resolvedDistance
        );
        return playResolved(soundManager, new PositionedSound(soundId, position, scaledVolume, pitch, resolvedDistance));
    }

    private static BaseFrontVoiceSound playForEntity(
        SoundManager soundManager,
        ResourceLocation soundId,
        int sourceEntityId,
        Vec3 fallbackPosition,
        float volume,
        float pitch,
        float attenuationDistance
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return null;
        }
        Entity sourceEntity = sourceEntityId >= 0 ? level.getEntity(sourceEntityId) : null;
        float scaledVolume = scaleVolume(volume, VoiceConfig.getClientMasterVolume() * VoiceConfig.getClientThirdPersonVolume());
        float resolvedDistance = resolveDistance(attenuationDistance);
        if (sourceEntity != null) {
            FrontVoiceClientDebugSink.info(
                "Play entity-tracking sound=" + soundId + " entity=" + sourceEntity.getId() + " volume=" + scaledVolume + " pitch=" + pitch
            );
            return playResolved(soundManager, new EntityTrackingSound(soundId, sourceEntity, scaledVolume, pitch, resolvedDistance));
        }
        if (fallbackPosition != null) {
            FrontVoiceClientDebugSink.warn("Source entity missing, fallback to position playback for " + soundId);
            return playResolved(soundManager, new PositionedSound(soundId, fallbackPosition, scaledVolume, pitch, resolvedDistance));
        }
        FrontVoiceClientDebugSink.warn("Entity playback skipped because source entity and fallback position are both missing for " + soundId);
        return null;
    }

    private static BaseFrontVoiceSound playResolved(SoundManager soundManager, BaseFrontVoiceSound soundInstance) {
        logResolution(soundManager, soundInstance);
        soundManager.play(soundInstance);
        return soundInstance;
    }

    private static float scaleVolume(float requestedVolume, float multiplier) {
        return Math.max(0.0F, requestedVolume) * Math.max(0.0F, multiplier);
    }

    private static float resolveDistance(float requestedDistance) {
        float boundedRequestedDistance = requestedDistance <= 0.0F ? VoiceConfig.getClientMaxThirdPersonDistance() : requestedDistance;
        return Math.min(VoiceConfig.getClientMaxThirdPersonDistance(), boundedRequestedDistance);
    }

    private static float computeDistanceAttenuatedVolume(float baseVolume, float x, float y, float z, float attenuationDistance) {
        Minecraft minecraft = Minecraft.getInstance();
        Player localPlayer = minecraft.player;
        if (localPlayer == null) {
            return baseVolume;
        }
        double dx = localPlayer.getX() - x;
        double dy = localPlayer.getEyeY() - y;
        double dz = localPlayer.getZ() - z;
        double distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        if (distance >= attenuationDistance) {
            return 0.0F;
        }
        double ratio = Math.max(0.0D, 1.0D - (distance / attenuationDistance));
        return (float) (baseVolume * ratio);
    }

    private static void logResolution(SoundManager soundManager, AbstractSoundInstance soundInstance) {
        WeighedSoundEvents weighedSoundEvents = soundInstance.resolve(soundManager);
        if (weighedSoundEvents == null || weighedSoundEvents.getWeight() <= 0) {
            FrontVoiceClientDebugSink.warn("Sound resolve failed for " + soundInstance.getLocation() + " source=" + soundInstance.getSource());
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float categoryVolume = minecraft.options.getSoundSourceVolume(soundInstance.getSource());
        FrontVoiceClientDebugSink.info(
            "Resolved sound " + soundInstance.getLocation()
                + " source=" + soundInstance.getSource()
                + " weight=" + weighedSoundEvents.getWeight()
                + " categoryVolume=" + categoryVolume
        );
    }

    private abstract static class BaseFrontVoiceSound extends AbstractSoundInstance implements TickableSoundInstance {
        protected final float baseVolume;
        protected final float attenuationDistance;
        private boolean stopped;

        protected BaseFrontVoiceSound(ResourceLocation soundId, SoundSource soundSource, float volume, float pitch, float attenuationDistance) {
            super(soundId, soundSource, RandomSource.create());
            this.baseVolume = Math.max(0.0F, volume);
            this.volume = this.baseVolume;
            this.pitch = Math.max(0.01F, pitch);
            this.looping = false;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.attenuationDistance = Math.max(1.0F, attenuationDistance);
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        protected final void stopSound() {
            this.stopped = true;
        }

        @Override
        public float getVolume() {
            return computeDistanceAttenuatedVolume(baseVolume, (float) this.x, (float) this.y, (float) this.z, attenuationDistance);
        }
    }

    private static final class LocalSound extends BaseFrontVoiceSound {
        private LocalSound(ResourceLocation soundId, float volume, float pitch) {
            super(soundId, SoundSource.PLAYERS, volume, pitch, 1.0F);
            this.relative = true;
        }

        @Override
        public void tick() {
        }

        @Override
        public float getVolume() {
            return baseVolume;
        }
    }

    private static final class PositionedSound extends BaseFrontVoiceSound {
        private PositionedSound(ResourceLocation soundId, Vec3 position, float volume, float pitch, float attenuationDistance) {
            super(soundId, SoundSource.PLAYERS, volume, pitch, attenuationDistance);
            this.relative = false;
            this.x = position.x;
            this.y = position.y;
            this.z = position.z;
        }

        @Override
        public void tick() {
        }
    }

    private static final class EntityTrackingSound extends BaseFrontVoiceSound {
        private final int sourceEntityId;

        private EntityTrackingSound(ResourceLocation soundId, Entity sourceEntity, float volume, float pitch, float attenuationDistance) {
            super(soundId, SoundSource.PLAYERS, volume, pitch, attenuationDistance);
            this.relative = false;
            this.sourceEntityId = sourceEntity.getId();
            this.x = sourceEntity.getX();
            this.y = sourceEntity.getEyeY();
            this.z = sourceEntity.getZ();
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft.level;
            if (level == null) {
                stopSound();
                return;
            }
            Entity sourceEntity = level.getEntity(sourceEntityId);
            if (sourceEntity == null || sourceEntity.isRemoved()) {
                stopSound();
                return;
            }
            this.x = sourceEntity.getX();
            this.y = sourceEntity.getEyeY();
            this.z = sourceEntity.getZ();
        }
    }
}
