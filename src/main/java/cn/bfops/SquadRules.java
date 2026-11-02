package cn.bfops;

final class SquadRules {
    static final int COMBAT_TICKS=140;
    static boolean inCombat(long until,long now){return until>now;}
    static final int COUNT=8, CAPACITY=4;
    static final long BEACON_LIFE=20*600, BEACON_COOLDOWN=20*60;
    static boolean same(String mapA,int teamA,int squadA,String mapB,int teamB,int squadB) {
        return mapA!=null && mapA.equals(mapB) && teamA>=0 && teamA==teamB && squadA>0 && squadA==squadB;
    }
    static boolean canJoin(int squad,int occupants) {return squad>=1&&squad<=COUNT&&occupants<CAPACITY;}
    static String label(int squad) {return squad<=0?"未加入小队":"小队 "+squad;}
    static String teammateBlocker(boolean memberAlive,boolean downed,boolean playerAlive,boolean spectator,boolean creative,boolean passenger){
        if(!memberAlive||!playerAlive||spectator)return "队友当前不可部署";
        if(downed)return "队友倒地";
        if(creative)return "队友处于创造模式";
        if(passenger)return "队友在载具中，请选择现有载具登车部署";
        return "";
    }
    static boolean dynamic(String kind) {return "TEAMMATE".equals(kind)||"VEHICLE".equals(kind)||"BEACON".equals(kind);}

    /**
     * Why a placed beacon should be removed this pass, or {@code null} to keep it.
     *
     * <p>Extracted from the engine's cleanup loop so the one decision that is easy to get wrong can be
     * verified headless. The subtlety is that an entity being absent has two completely different causes:
     *
     * <ul>
     *   <li>the beacon was destroyed or never spawned, so it is gone;</li>
     *   <li>its chunk is not loaded, because nobody from the squad has been near it — which is precisely the
     *       situation a squad beacon exists to serve.</li>
     * </ul>
     *
     * <p>Testing "entity is null" alone conflates them and deletes a working beacon as soon as the squad
     * walks away. So {@code chunkLoaded} is consulted first, and while the chunk is unloaded only the facts
     * that do not need the entity can end a beacon: expiry, the owner leaving, the match ending, or the
     * squad membership changing.
     *
     * @return a short reason for the log, or {@code null} when the beacon stays
     */
    static String beaconFate(boolean ownerPresent, boolean matchRunning, boolean expired,
                             boolean chunkLoaded, boolean entityPresent, boolean entityAlive,
                             boolean sameSquad) {
        if (ownerPresent && matchRunning && sameSquad && !expired) {
            // The entity can only be judged when its chunk is loaded; otherwise the absence is inconclusive.
            if (!chunkLoaded || (entityPresent && entityAlive)) return null;
            // Loaded chunk, no live entity: it was destroyed or failed to spawn.
            return "destroyed";
        }
        if (!ownerPresent) return "owner left";
        if (!matchRunning) return "match ended";
        if (expired) return "expired";
        return "squad changed";
    }
}
