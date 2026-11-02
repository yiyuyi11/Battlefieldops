package cn.bfops.voice.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import cn.bfops.voice.client.QuickWheelServerConfig;
import cn.bfops.voice.client.audio.FrontVoiceClientAudioPlayer;
import cn.bfops.voice.server.audio.FrontVoiceCueEmitter;

/**
 * Registers the front-voice payloads and offers the send helpers the emitter, the quick-message HUD, and the
 * login config sync need. Mirrors the parent mod's {@code Network}, but keeps the voice channel self-contained.
 */
public final class VoiceNetwork {
    private VoiceNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2").optional();
        registrar.playToClient(ClientboundPlayAudioPayload.TYPE, ClientboundPlayAudioPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        FrontVoiceClientAudioPlayer.play(payload.request())));
        registrar.playToClient(S2CConfigSyncPayload.TYPE, S2CConfigSyncPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    QuickWheelServerConfig.setAllowCustomMessages(payload.allowCustomMessages());
                    QuickWheelServerConfig.setEnableQuickChat(payload.enableQuickChat());
                }));
        registrar.playToServer(C2SRequestCuePayload.TYPE, C2SRequestCuePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        FrontVoiceCueEmitter.emitCue(player, payload.cue());
                    }
                }));
    }

    public static void sendToPlayer(ServerPlayer player, ClientboundPlayAudioPayload payload) {
        if (!canReceive(player) || !cn.bfops.RoomAudience.member(player) || !hasChannel(player,ClientboundPlayAudioPayload.TYPE.id())) return;
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToPlayer(ServerPlayer player, S2CConfigSyncPayload payload) {
        if (!canReceive(player) || !hasChannel(player,S2CConfigSyncPayload.TYPE.id())) return;
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToTrackingAndSelf(Entity entity, ClientboundPlayAudioPayload payload) {
        if (!(entity instanceof ServerPlayer source) || !cn.bfops.RoomAudience.member(source)) return;
        double radius=payload.request().attenuationDistance();
        if(!Double.isFinite(radius)||radius<=0)radius=32;
        radius=Math.min(radius,256);
        for(var listener:source.serverLevel().players())
            if(cn.bfops.RoomAudience.same(source,listener)&&listener.distanceToSqr(source)<=radius*radius)
                sendToPlayer(listener,payload);
    }

    public static void sendToServer(C2SRequestCuePayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static boolean canReceive(ServerPlayer player) {
        return player != null && player.connection != null && !isBot(player);
    }
    private static boolean hasChannel(ServerPlayer player,net.minecraft.resources.ResourceLocation id){
        return net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel((net.neoforged.neoforge.common.extensions.ICommonPacketListener)(Object)player.connection,id);
    }

    private static boolean isBot(ServerPlayer player) {
        try {
            return cn.bfops.BattlefieldOps.engine != null && cn.bfops.BattlefieldOps.engine.isBot(player);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
