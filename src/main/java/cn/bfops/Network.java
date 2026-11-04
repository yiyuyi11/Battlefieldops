package cn.bfops;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Consumer;
public final class Network {
 private static final String VERSION="forge1201-3";
 private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("battlefield_ops","main"),()->VERSION,v->VERSION.equals(v)||NetworkRegistry.ABSENT.equals(v)||NetworkRegistry.ACCEPTVANILLA.equals(v),v->VERSION.equals(v)||NetworkRegistry.ABSENT.equals(v)||NetworkRegistry.ACCEPTVANILLA.equals(v));
 private static final com.google.gson.Gson WIRE_JSON=new com.google.gson.Gson();
 public static Consumer<String> receiver=s->{};
 public static Consumer<Boolean> modeReceiver=value->{};
 public record Mode(boolean enabled) {}
 public record Request(String json) {}
 public record State(String json) {}
 public record MapSave(byte[] data) {}
 public static void register(){
  CHANNEL.messageBuilder(Mode.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->b.writeBoolean(p.enabled)).decoder(b->new Mode(b.readBoolean())).consumerMainThread((p,c)->{modeReceiver.accept(p.enabled);c.get().setPacketHandled(true);}).add();
  CHANNEL.messageBuilder(Request.class,1,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->b.writeUtf(p.json,20000)).decoder(b->new Request(b.readUtf(20000))).consumerMainThread((p,c)->{ServerPlayer sp=c.get().getSender();if(sp!=null&&BattlefieldOps.engine!=null)BattlefieldOps.engine.request(sp,p.json);c.get().setPacketHandled(true);}).add();
  CHANNEL.messageBuilder(State.class,2,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->b.writeUtf(p.json,800000)).decoder(b->new State(b.readUtf(800000))).consumerMainThread((p,c)->{receiver.accept(p.json);c.get().setPacketHandled(true);}).add();
  CHANNEL.messageBuilder(MapSave.class,3,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->b.writeByteArray(p.data)).decoder(b->new MapSave(b.readByteArray(30000))).consumerMainThread((p,c)->{ServerPlayer sp=c.get().getSender();if(sp!=null)MapTransfer.receive(sp,p.data);c.get().setPacketHandled(true);}).add();
 }
 public static boolean supported(ServerPlayer player) {
  if (player == null || player.connection == null
      || (BattlefieldOps.engine != null && BattlefieldOps.engine.isBot(player))) return false;
  var connection = player.connection.connection;
  return connection.isMemoryConnection() || CHANNEL.isRemotePresent(connection);
 }
 public static void mode(ServerPlayer p,boolean enabled){
  if(!supported(p))return;
  CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Mode(enabled));
 }
 public static void sendToServer(Object packet){CHANNEL.sendToServer(packet);}
 public static void send(ServerPlayer p,Object state){if(!supported(p))return;String s=WIRE_JSON.toJson(state);if(s.length()>780000||s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>900000)throw new IllegalStateException("response too large");CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new State(s));}
}
