package cn.bfops.killicon.network;
import cn.bfops.killicon.network.packet.ScoreboardRequestPacket;
public final class NetworkHandler {
 public static void sendToServer(IPacket packet){if(packet instanceof ScoreboardRequestPacket request)cn.bfops.NativeKillIcons.scoreboard(request);}
}
