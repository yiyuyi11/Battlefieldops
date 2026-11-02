package cn.bfops.voice.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client sync of the quick-wheel HUD toggles, mirroring the reference's {@code S2CConfigSyncPacket}.
 */
public record S2CConfigSyncPayload(boolean allowCustomMessages, boolean enableQuickChat) implements CustomPacketPayload {
    public static final Type<S2CConfigSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("battlefield_ops", "voice_config"));
    public static final StreamCodec<FriendlyByteBuf, S2CConfigSyncPayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBoolean(payload.allowCustomMessages);
                        buffer.writeBoolean(payload.enableQuickChat);
                    },
                    buffer -> new S2CConfigSyncPayload(buffer.readBoolean(), buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
