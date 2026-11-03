package cn.bfops;
public final class TeamCleanupSim {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var board=new net.minecraft.world.scores.Scoreboard();
        var blue=board.addPlayerTeam(Board.teamName("arena_one",0));var red=board.addPlayerTeam(Board.teamName("arena_two",1));
        blue.setPlayerPrefix(net.minecraft.network.chat.Component.literal("[Blue] "));red.setPlayerPrefix(net.minecraft.network.chat.Component.literal("[Red] "));
        board.addPlayerToTeam("leaving",blue);board.addPlayerToTeam("staying",blue);board.addPlayerToTeam("other_room",red);
        Board.clearOwned(board,"leaving");check(board.getPlayersTeam("leaving")==null,"stale membership cleared even without an in-memory joined ledger");
        check(board.getPlayersTeam("staying")==blue,"teammate remains in active room");check(board.getPlayersTeam("other_room")==red,"other map untouched");
        check(blue.getPlayers().size()==1,"only departing player removed");
        Board.clearOwned(board,"staying");check(board.getPlayerTeam(blue.getName())==null,"empty owned team is removed");
        var foreign=board.addPlayerTeam("TAB_admin");board.addPlayerToTeam("foreign_player",foreign);Board.clearOwned(board,"foreign_player");
        check(board.getPlayersTeam("foreign_player")==foreign,"foreign plugin membership preserved");
        var similarlyNamed=board.addPlayerTeam("bfops_external_role");board.addPlayerToTeam("foreign_prefix",similarlyNamed);Board.clearOwned(board,"foreign_prefix");
        check(board.getPlayersTeam("foreign_prefix")==similarlyNamed,"unrelated prefix is insufficient to claim a team");
        Board.clearOwned(board,"never_joined");check(board.getPlayersTeam("never_joined")==null,"idempotent no-op for player with no team");
        for(String map:new String[]{"map","arena-one","a_b","0123"})for(int team=0;team<2;team++)check(Board.owned(Board.teamName(map,team)),"recognise exact generated team");
        System.out.println("TeamCleanupSim: "+checks+" actual scoreboard checks passed");
    }
}
