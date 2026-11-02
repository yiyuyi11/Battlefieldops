package cn.bfops.killicon.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import cn.bfops.killicon.network.*;
public final class ScoreboardRequestPacket implements IPacket {
 public final int offset,limit;public final long requestId;
 public ScoreboardRequestPacket(){this(0,20,0);}
 public ScoreboardRequestPacket(int offset,int limit,long requestId){this.offset=Math.max(0,offset);this.limit=Math.max(1,Math.min(128,limit));this.requestId=requestId;}
 public void encode(FriendlyByteBuf b){b.writeInt(offset);b.writeInt(limit);b.writeLong(requestId);}
 public void handle(PacketContext c){throw new UnsupportedOperationException("Use parent mod's room-scoped snapshots");}
}
