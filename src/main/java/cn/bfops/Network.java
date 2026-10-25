package cn.bfops;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.function.Consumer;

public final class Network {
    public static Consumer<String> receiver=s->{};
    public record Request(String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("battlefield_ops","request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,20000),b->new Request(b.readUtf(20000)));
        public Type<Request> type(){return TYPE;}
    }
    public record State(String json) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("battlefield_ops","state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,200000),b->new State(b.readUtf(200000)));
        public Type<State> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("2").optional();r.playToServer(Request.TYPE,Request.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer sp&&BattlefieldOps.engine!=null)BattlefieldOps.engine.request(sp,p.json);}));r.playToClient(State.TYPE,State.CODEC,(p,c)->c.enqueueWork(()->receiver.accept(p.json)));}
    public static boolean supported(ServerPlayer p){
        // Bots (fake players) have no client: treat them as unsupported so state/award packets never
        // reach their dropped connection.
        if(BattlefieldOps.engine!=null && BattlefieldOps.engine.isBot(p))return false;
        return net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel((net.neoforged.neoforge.common.extensions.ICommonPacketListener)(Object)p.connection,State.TYPE.id()) && net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel((net.neoforged.neoforge.common.extensions.ICommonPacketListener)(Object)p.connection,Request.TYPE.id());
    }
    public static void send(ServerPlayer p,Object state){if(!supported(p))return;String s=BattlefieldOps.GSON.toJson(state);if(s.length()>190000)throw new IllegalStateException("响应过大");PacketDistributor.sendToPlayer(p,new State(s));}
}
