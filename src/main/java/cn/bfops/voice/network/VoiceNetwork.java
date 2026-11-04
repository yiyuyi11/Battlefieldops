package cn.bfops.voice.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.resources.ResourceLocation;
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

    private static final String VERSION="forge1201-2";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("battlefield_ops","voice"),()->VERSION,v->VERSION.equals(v)||NetworkRegistry.ABSENT.equals(v)||NetworkRegistry.ACCEPTVANILLA.equals(v),v->VERSION.equals(v)||NetworkRegistry.ABSENT.equals(v)||NetworkRegistry.ACCEPTVANILLA.equals(v));
    public static void register() {
        CHANNEL.messageBuilder(ClientboundPlayAudioPayload.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->ClientboundPlayAudioPayload.write(b,p)).decoder(ClientboundPlayAudioPayload::read).consumerMainThread((p,c)->{FrontVoiceClientAudioPlayer.play(p.request());c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(S2CConfigSyncPayload.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->{b.writeBoolean(p.allowCustomMessages());b.writeBoolean(p.enableQuickChat());}).decoder(b->new S2CConfigSyncPayload(b.readBoolean(),b.readBoolean())).consumerMainThread((p,c)->{QuickWheelServerConfig.setAllowCustomMessages(p.allowCustomMessages());QuickWheelServerConfig.setEnableQuickChat(p.enableQuickChat());c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(C2SRequestCuePayload.class,2,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->b.writeEnum(p.cue())).decoder(b->new C2SRequestCuePayload(b.readEnum(cn.bfops.voice.common.audio.VoiceCue.class))).consumerMainThread((p,c)->{var player=c.get().getSender();if(player!=null)FrontVoiceCueEmitter.emitCue(player,p.cue());c.get().setPacketHandled(true);}).add();
    }

    public static void sendToPlayer(ServerPlayer player, ClientboundPlayAudioPayload payload) {
        if (!canReceive(player) || !cn.bfops.RoomAudience.member(player) || !hasChannel(player)) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),payload);
    }

    public static void sendToPlayer(ServerPlayer player, S2CConfigSyncPayload payload) {
        if (!canReceive(player) || !hasChannel(player)) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),payload);
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
        CHANNEL.sendToServer(payload);
    }

    private static boolean canReceive(ServerPlayer player) {
        return player != null && player.connection != null && !isBot(player);
    }
    private static boolean hasChannel(ServerPlayer player) {
        var connection = player.connection.connection;
        return connection.isMemoryConnection() || CHANNEL.isRemotePresent(connection);
    }

    private static boolean isBot(ServerPlayer player) {
        try {
            return cn.bfops.BattlefieldOps.engine != null && cn.bfops.BattlefieldOps.engine.isBot(player);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
