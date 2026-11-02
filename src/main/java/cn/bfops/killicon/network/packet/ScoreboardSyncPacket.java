package cn.bfops.killicon.network.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import cn.bfops.killicon.client.gui.tabs.ScoreboardTab;
import cn.bfops.killicon.network.IPacket;
import cn.bfops.killicon.network.PacketContext;

public class ScoreboardSyncPacket implements IPacket {
   private final List<ScoreboardSyncPacket.Entry> entries;
   private final int offset;
   private final int totalCount;
   private final long requestId;
   private final int serverLayoutColumns;
   private final String[] serverPanelTeams;

   public ScoreboardSyncPacket(
      List<ScoreboardSyncPacket.Entry> entries, int offset, int totalCount, long requestId, int serverLayoutColumns, String[] serverPanelTeams
   ) {
      this.entries = entries;
      this.offset = Math.max(0, offset);
      this.totalCount = Math.max(0, totalCount);
      this.requestId = requestId;
      this.serverLayoutColumns = serverLayoutColumns;
      this.serverPanelTeams = new String[]{teamAt(serverPanelTeams, 0), teamAt(serverPanelTeams, 1), teamAt(serverPanelTeams, 2), teamAt(serverPanelTeams, 3)};
   }

   public ScoreboardSyncPacket(FriendlyByteBuf buffer) {
      this.offset = Math.max(0, buffer.readInt());
      this.totalCount = Math.max(0, buffer.readInt());
      this.requestId = buffer.readLong();
      this.serverLayoutColumns = buffer.readInt();
      this.serverPanelTeams = new String[]{buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf()};
      int size = buffer.readInt();
      this.entries = new ArrayList<>(size);

      for (int i = 0; i < size; i++) {
         this.entries
            .add(
               new ScoreboardSyncPacket.Entry(
                  buffer.readUUID(),
                  buffer.readUtf(),
                  buffer.readUtf(),
                  buffer.readUtf(),
                  buffer.readUtf(),
                  buffer.readInt(),
                  buffer.readInt(),
                  buffer.readInt(),
                  buffer.readInt(),
                  buffer.readInt(),
                  buffer.readInt(),
                  buffer.readBoolean(),
                  buffer.readBoolean()
               )
            );
      }
   }

   @Override
   public void encode(FriendlyByteBuf buffer) {
      buffer.writeInt(this.offset);
      buffer.writeInt(this.totalCount);
      buffer.writeLong(this.requestId);
      buffer.writeInt(this.serverLayoutColumns);
      buffer.writeUtf(this.serverPanelTeams[0]);
      buffer.writeUtf(this.serverPanelTeams[1]);
      buffer.writeUtf(this.serverPanelTeams[2]);
      buffer.writeUtf(this.serverPanelTeams[3]);
      buffer.writeInt(this.entries.size());

      for (ScoreboardSyncPacket.Entry entry : this.entries) {
         buffer.writeUUID(entry.uuid);
         buffer.writeUtf(entry.name);
         buffer.writeUtf(entry.lastLoginName);
         buffer.writeUtf(entry.teamName);
         buffer.writeUtf(entry.squadLabel);
         buffer.writeInt(entry.score);
         buffer.writeInt(entry.kill);
         buffer.writeInt(entry.death);
         buffer.writeInt(entry.assist);
         buffer.writeInt(entry.revive);
         buffer.writeInt(entry.ping);
         buffer.writeBoolean(entry.online);
         buffer.writeBoolean(entry.spectator);
      }
   }

   @Override
   public void handle(PacketContext context) {
      context.enqueueWork(
         () -> ScoreboardTab.updateData(this.entries, this.offset, this.totalCount, this.requestId, this.serverLayoutColumns, this.serverPanelTeams)
      );
      context.setPacketHandled(true);
   }

   private static String teamAt(String[] teams, int index) {
      if (teams != null && index >= 0 && index < teams.length) {
         String value = teams[index];
         return value == null ? "" : value;
      } else {
         return "";
      }
   }

   public static class Entry {
      public final UUID uuid;
      public final String name;
      public final String lastLoginName;
      public final String teamName;
      public final String squadLabel;
      public final int score;
      public final int kill;
      public final int death;
      public final int assist;
      public final int revive;
      public final int ping;
      public final boolean online;
      public final boolean spectator;

      public Entry(
         UUID uuid,
         String name,
         String lastLoginName,
         String teamName,
         String squadLabel,
         int score,
         int kill,
         int death,
         int assist,
         int revive,
         int ping,
         boolean online,
         boolean spectator
      ) {
         this.uuid = uuid;
         this.name = name;
         this.lastLoginName = lastLoginName;
         this.teamName = teamName == null ? "" : teamName;
         this.squadLabel = squadLabel == null ? "" : squadLabel;
         this.score = score;
         this.kill = kill;
         this.death = death;
         this.assist = assist;
         this.revive = revive;
         this.ping = ping;
         this.online = online;
         this.spectator = spectator;
      }
   }
}
