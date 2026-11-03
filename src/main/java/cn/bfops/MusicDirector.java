package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

/**
 * Chooses and plays the match's background music bed.
 *
 * <p><b>Why this is a director and not a jukebox.</b> The reference pack ships eleven loops for a reason: the
 * music is supposed to <em>change with the match</em> — a deploy screen, a retreat, an attack, a desperate
 * defence, a final segment won or lost. Playing one track on loop would use eleven files to say one thing and
 * would ignore the data the server already sends.
 *
 * <p><b>What decides the bed.</b> Three inputs, all from the snapshot: whether the player is deploying, their
 * ticket share, and which state the match is in. {@link Mood} does the arithmetic; this class only notices when
 * the answer changes and stops the old track before starting the new one. Restarting a track on every snapshot
 * would produce a stutter every second, which is why the "only on change" rule is the whole point of the class.
 *
 * <p><b>Why stopping matters.</b> These are streamed files, not one-shots. Starting a second bed without
 * stopping the first leaves both playing forever, and the reference's own config separates "music" from
 * "loops" precisely because a stuck loop is the failure mode people notice first. So every transition is
 * stop-then-start, and {@link #silence} is called from the places that end a match.
 */
final class MusicDirector {
    private static String current="",room="";
    private static boolean firstDeploy,roundStarted,wasAlive;
    private static int lobbyTrack;
    private static String pendingMood="";private static long moodAt;
    private static int stage=-1,age;
    private static final java.util.Map<String,Track> channels=new java.util.HashMap<>();
    private static final java.util.List<Track> owned=new java.util.ArrayList<>();
    private static Track cue;
    static final long MIN_DWELL_MS=3000;
    private MusicDirector(){}
    private static final class Track extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        float target,rate;final float full;int started;
        Track(net.minecraft.sounds.SoundEvent event,float volume,boolean loop,int fade){
            super(event,net.minecraft.sounds.SoundSource.MUSIC,net.minecraft.util.RandomSource.create());
            full=volume;target=volume;this.volume=fade>0?.001f:volume;rate=volume/Math.max(1,fade);
            looping=loop;delay=0;relative=true;attenuation=net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE;
            started=age;
        }
        void tune(float pitch){this.pitch=pitch;}
        void fade(int ticks){target=0;rate=Math.max(.001f,volume/Math.max(1,ticks));}
        public void tick(){if(volume<target)volume=Math.min(target,volume+rate);else if(volume>target)volume=Math.max(target,volume-rate);if(target==0&&volume<=.001f)stop();}
    }
    private static Track play(String name,float volume,boolean loop,int fade){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return null;
        var event=Sounds.get(name);if(event==null)return null;
        if(owned.isEmpty())mc.getMusicManager().stopPlaying();
        Track track=new Track(event,volume,loop,fade);owned.add(track);mc.getSoundManager().play(track);return track;
    }
    static void oneShot(String name,float volume,float pitch){
        if(!ClientPrefs.musicEnabled())return;
        if(cue!=null)cue.fade(5);
        String nativeName=switch(name){case "music.death"->{
            // The single-player death sting is the reference's own "you died" cue; the man-down music bed
            // stays as the fallback for packs that ship without it.
            String sting=NativeAudioBank.music("deathstinger");
            yield sting!=null?sting:NativeAudioBank.music("mandown");
        }case "music.first_deploy"->NativeAudioBank.music("spawnin");case "music.stage_begin"->NativeAudioBank.music("attackersadvance");case "music.victory","music.defeat"->NativeAudioBank.music("eor");default->null;};
        cue=play(nativeName==null?name:nativeName,volume,false,0);if(cue!=null)cue.tune(pitch);
    }
    private static void channel(String key,String bed,int fade){
        Track old=channels.get(key);String event=bed.isEmpty()?"":eventFor(bed);
        if(old!=null&&old.getLocation().getPath().equals(event)&&!old.isStopped()){
            if(age-old.started<40||Minecraft.getInstance().getSoundManager().isActive(old))return;
        }
        if(old!=null){old.fade(fade);channels.remove(key);}
        if(event==null||event.isEmpty())return;
        Track next=play(event,Mood.volume(bed),!bed.equals("loop_lobby"),fade);if(bed.equals("loop_lobby"))lobbyTrack++;if(next!=null)channels.put(key,next);
    }
    static void tick(JsonObject state,long now){
        age++;var mc=Minecraft.getInstance();
        owned.removeIf(t->t.isStopped()||age-t.started>40&&!mc.getSoundManager().isActive(t));
        if(mc.level==null||!ClientPrefs.musicEnabled()){silence();return;}
        String nextRoom=state.has("me")?Engine.str(state.getAsJsonObject("me"),"map",""):"";
        if(!nextRoom.equals(room)){for(var t:channels.values())t.fade(20);channels.clear();room=nextRoom;firstDeploy=false;roundStarted=false;wasAlive=false;stage=-1;}
        boolean inMatch=state.has("inMatch")&&state.get("inMatch").getAsBoolean();
        var me=state.has("me")?state.getAsJsonObject("me"):new JsonObject();
        boolean alive=me.has("alive")&&me.get("alive").getAsBoolean();
        int nextStage=Engine.num(state,"stage",-1);
        if(inMatch&&stage>=0&&nextStage!=stage){oneShot("music.stage_begin",.85f,1);}
        stage=nextStage;
        boolean running=state.has("roomRunning")&&state.get("roomRunning").getAsBoolean();
        if(running&&!roundStarted){roundStarted=true;String event=NativeAudioBank.music("roundstart");if(event!=null)oneShot(event,.85f,1);}

        if(inMatch&&alive&&!wasAlive){oneShot("music.first_deploy",.85f,1);firstDeploy=true;}
        wasAlive=inMatch&&alive;
        String desiredFinale=inMatch?Mood.finale(state):"";
        if(!desiredFinale.equals(pendingMood)){pendingMood=desiredFinale;moodAt=now;}
        Track previous=channels.get("finale");
        String finale=now-moodAt<MIN_DWELL_MS&&previous!=null?cueFor(previous.getLocation().getPath()):desiredFinale;
        String deploy=inMatch&&!alive&&finale.isEmpty()?"loop_deploy":"";
        String objective=inMatch&&alive&&deploy.isEmpty()&&finale.isEmpty()&&Mood.inObjective(state)?"loop_in_objective":"";
        // No continuous battle loop: the strategic channel only carries the room's queue bed. Mid-match music
        // is event-driven (stage begin, first deploy, death, objective, finale), never a bed that plays on.
        String strategic="";
        if(!inMatch&&!nextRoom.isEmpty())strategic="loop_queue";
        else if(!inMatch&&mc.screen instanceof LobbyScreen)strategic="loop_lobby";
        channel("deploy",deploy,60);channel("strategic",strategic,20);channel("objective",objective,20);channel("finale",finale,20);
        current=!finale.isEmpty()?finale:!deploy.isEmpty()?deploy:!objective.isEmpty()?objective:strategic;
    }
    static void captured(){String event=NativeAudioBank.music("flagcaptured");if(event!=null)oneShot(event,.6f,1);}
    private static String eventFor(String cue){
        String key=switch(cue){
            case "loop_deploy"->"menu_"+String.format(java.util.Locale.ROOT,"%02d",Math.floorMod(room.hashCode(),5)+1);
            case "loop_queue"->"briefing";
            case "loop_desperate","loop_final_defeat","loop_last_objective"->"preeor_03";
            case "loop_final_victory","loop_last_objective_victory"->"preeor_01";
            default->null;
        };
        if(cue.equals("loop_lobby")){Track old=channels.get("strategic");if(old!=null&&!old.isStopped()&&(age-old.started<40||Minecraft.getInstance().getSoundManager().isActive(old)))return old.getLocation().getPath();String event=NativeAudioBank.frontend(lobbyTrack);if(event!=null)return event;}
        String event=key==null?null:NativeAudioBank.music(key);return event!=null?event:SoundBank.pick(cue,0);
    }
    private static String cueFor(String path){for(String cue:new String[]{"loop_last_objective","loop_last_objective_victory","loop_final_victory","loop_final_defeat","loop_desperate"})if(path.equals(eventFor(cue)))return cue;return "";}
    /** Low percussion layer under the death sting; stopped when the deploy map opens or the player respawns. */
    private static Track deathBed;

    static void death(){
        if(deathBed!=null)deathBed.fade(4);
        channel("objective","",20);
        oneShot("music.death",.85f,1);
        String drum=NativeAudioBank.music("deathdrum");
        deathBed=drum!=null&&ClientPrefs.musicEnabled()?play(drum,.32f,false,6):null;
    }
    static void result(String outcome){for(var t:channels.values())t.fade(20);channels.clear();current="";if(outcome.equals("胜利"))oneShot("music.victory",1,1);else if(outcome.equals("失败"))oneShot("music.defeat",1,1);}
    static void silence(){var mc=Minecraft.getInstance();for(var t:owned)mc.getSoundManager().stop(t);owned.clear();channels.clear();cue=null;current="";room="";firstDeploy=false;roundStarted=false;wasAlive=false;stage=-1;}
    static boolean active(){return !owned.isEmpty()&&ClientPrefs.musicEnabled();}
    static String current(){return current;}
    static String wanted(JsonObject state){return Mood.bed(state);}

    static final class Mood {
        private Mood() { }

        /**
         * The bed for a snapshot.
         *
         * @return a {@link SoundBank} cue name, or an empty string when no bed applies
         */
        static String bed(JsonObject state) {
            if(state==null)return "";
            boolean inMatch=state.has("inMatch")&&state.get("inMatch").getAsBoolean();
            var me=state.has("me")?state.getAsJsonObject("me"):new JsonObject();
            if(!inMatch)return Engine.str(me,"map","").isEmpty()?"":"loop_queue";
            // A decided result outranks every other bed: the victory/defeat sting is the last word.
            String result=Engine.str(state,"result","");
            if(!result.isEmpty()){
                Boolean won=resultWon(state,me);
                if(won!=null)return won?"music_victory":"music_defeat";
            }
            String finale=finale(state);if(!finale.isEmpty())return finale;
            if(!me.has("alive")||!me.get("alive").getAsBoolean())return "loop_deploy";
            if(inObjective(state))return "loop_in_objective";
            // Normal mid-match play: silence. The music is event-driven — stage begin, first deploy, death,
            // objective and the finale near the end — rather than a continuous battle bed.
            return "";
        }

        /**
         * Whether the viewer won, lost, or the match has no decided result.
         *
         * <p>The snapshot's {@code result} field names <em>who</em> won — {@code 机械轴心胜利}, {@code 格雷同盟胜利},
         * {@code 平局} — not whether the viewer did. Reading it as "contains 胜利 so we won" would play the
         * victory music over the losing team, which is the kind of failure that looks like a joke rather than a
         * bug and so survives a long time. Hence the team comparison, and {@code null} for "no result yet" so
         * an in-progress match is not mistaken for a loss.
         *
         * @return TRUE for a win, FALSE for a loss, {@code null} when undecided or unreadable
         */
        static Boolean resultWon(JsonObject state, JsonObject me) {
            String result = Engine.str(state, "result", "");
            if (result.isEmpty()) return null;
            if (result.contains("平局")) return null;
            int team = Engine.num(me, "team", -1);
            // Spectators get neither: celebration or commiseration for a side they are not on would be wrong.
            if (team < 0) return null;
            boolean blueWon = result.contains("机械轴心");
            boolean redWon = result.contains("格雷同盟");
            if (!blueWon && !redWon) return null;
            int winner = blueWon ? 0 : 1;
            return winner == team;
        }

        /**
         * The player's own side's remaining ticket share, or -1 when it cannot be determined.
         *
         * <p>Read from the {@code tickets} array the snapshot already sends (blue at index 0, red at index 1),
         * not from a percentage the server could precompute: the ratio is what the mood depends on, and a
         * rounded percent would flip the bed at the wrong moment.
         *
         * <p>Returns -1 rather than 1 when unknown, so an unrecognised snapshot plays the neutral attack bed
         * instead of being read as "at full strength" or "about to lose".
         */
        static double ticketShare(JsonObject state, JsonObject me) {
            if (!state.has("tickets") || !state.get("tickets").isJsonArray()) return -1;
            com.google.gson.JsonArray tickets = state.getAsJsonArray("tickets");
            if (tickets.size() < 2) return -1;
            try {
                int team = Engine.num(me, "team", -1);
                double blue = tickets.get(0).getAsDouble();
                double red = tickets.get(1).getAsDouble();
                double total = state.has("arena")?Engine.num(state.getAsJsonObject("arena"),"tickets",0):0;
                if (total <= 0) return -1;
                // Team 0 is blue, team 1 is red; a spectator gets the blue share, which is the same information
                // as far as the music is concerned.
                return (team == 1 ? red : blue) / total;
            } catch (RuntimeException ex) {
                // A malformed array must not throw inside a HUD tick; an unknown share is a benign answer.
                return -1;
            }
        }

        /**
         * Whether the player is standing inside a live objective.
         *
         * <p>Read from the snapshot's existing top-level {@code objective} index, which the server already
         * computes with the full {@code ObjectiveZone} test (radius <em>and</em> height). Re-deriving it from
         * the player's map position here would disagree with the capture logic the moment the height band
         * mattered, and the two would then be visibly inconsistent: the point would show as contested while
         * the music said otherwise.
         */
        static boolean inObjective(JsonObject state) {
            int index=Engine.num(state,"objective",-1);
            if(index<0||!state.has("flags")||!state.has("me")||index>=state.getAsJsonArray("flags").size())return false;
            var flag=state.getAsJsonArray("flags").get(index).getAsJsonObject();int team=Engine.num(state.getAsJsonObject("me"),"team",-1);
            return team>=0&&Engine.num(flag,"driving",-1)==team&&Engine.num(flag,"owner",-1)!=team;
        }
        static String finale(JsonObject state){
            if(!state.has("me"))return "";var me=state.getAsJsonObject("me");int team=Engine.num(me,"team",-1);if(team<0)return "";
            // Use remaining troops relative to the configured initial cap, rather than the other side's pool.
            if(!state.has("tickets")||!state.has("arena"))return "";
            var a=state.getAsJsonObject("arena");var tickets=state.getAsJsonArray("tickets");if(tickets.size()<2)return "";
            double cap=Math.max(1,Engine.num(a,"tickets",1));String mode=Engine.str(a,"mode","");
            double own=tickets.get(team).getAsDouble(),other=tickets.get(1-team).getAsDouble();
            boolean winning=own>other;
            // Desperate: our own pool is nearly gone, whatever the mode. This is the one bed that may loop
            // before the very end, because the situation itself is the event.
            if(own/cap<=.2)return "loop_desperate";
            if(mode.equals("BREAKTHROUGH")||mode.equals("RUSH")){
                int stage=Engine.num(state,"stage",0),stages=Math.max(1,Engine.num(state,"stages",1));
                if(stage>=stages-1)return winning?"loop_final_victory":"loop_final_defeat";
                return "";
            }
            if(mode.equals("DEATHMATCH")&&state.has("teamScores")){
                var scores=state.getAsJsonArray("teamScores");double goal=Math.max(1,Engine.num(a,"targetKills",100));
                if(scores.size()<2)return "";
                double ownScore=scores.get(team).getAsDouble(),otherScore=scores.get(1-team).getAsDouble();
                boolean winningScore=ownScore>otherScore;
                if(otherScore/goal>=.8&&otherScore>ownScore)return "loop_desperate";
                if(Math.max(ownScore,otherScore)/goal>=.8)return winningScore?"loop_last_objective_victory":"loop_last_objective";
                return "";
            }
            if(Math.min(own,other)/cap<=.2)return winning?"loop_last_objective_victory":"loop_last_objective";
            return "";
        }
        /**
         * Volume for a bed.
         *
         * <p>Loops sit lower than stingers: a bed that competes with gunfire is a bed the player turns off in
         * the first minute. One-shots ({@code music_*}) are allowed to be louder because they are over quickly
         * and are carrying news.
         */
        static float volume(String bed) {
            if (bed == null || bed.isEmpty()) return 0f;
            // A win or a loss is the loudest thing the mod says, and it only happens once.
            if ("music_victory".equals(bed) || "music_defeat".equals(bed)) return 1.0f;
            if (bed.startsWith("music_")) return 0.85f;
            // The desperate bed is deliberately a touch louder than the other loops: it is the one that has
            // to communicate "you are losing" over the sound of you losing.
            if ("loop_desperate".equals(bed)) return 0.7f;
            return 0.55f;
        }
    }
}
