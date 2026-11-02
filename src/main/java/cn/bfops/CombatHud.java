package cn.bfops;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Match-only combat presentation; the full roster belongs to the held F6 overlay. */
final class CombatHud {
    private static double lastWait;
    private static long lastWaitAt;
    /**
     * Smoothed centre for the HUD map.
     *
     * <p>The map is centred on a continuously interpolated point while the player's own marker is drawn
     * at their true position. That split is deliberate: it makes the marker lead the map slightly when
     * moving, which reads as speed, and it costs nothing. Smoothing both would pin the marker dead centre
     * and hide the player's motion entirely.
     */
    private static final MapFollow FOLLOW = new MapFollow();

    /**
     * Cached static layer for the HUD map.
     *
     * <p>Lives as long as the client session rather than being created per frame: the texture registration
     * is the expensive part and the arena does not change between frames. Released on logout — a texture
     * registered per session and never closed is a leak that shows up as a slow grow over hours of play,
     * and a proxy group makes players switch servers constantly.
     */
    private static MapBake mapBake;
    private static final CaptureQueue CAPTURE_QUEUE=new CaptureQueue();

    static int mapSize(int width,int height){return Math.max(64,Math.min(116,Math.min(width/3-12,height/2-16)));}

    /**
     * Releases the baked map texture and drops the follow state.
     *
     * <p>Called on logout. A hard exit does not need a separate hook: a {@link DynamicTexture} is closed
     * when the client's texture manager is torn down with the process, so the only leak worth preventing is
     * the one across sessions — switching servers and coming back, over and over, which is what a proxy
     * group makes players do.
     */
    static void resetCaches() {
        if (mapBake != null) {
            mapBake.close();
            mapBake = null;
        }
        FOLLOW.reset();
        HudMotion.reset();
        CAPTURE_QUEUE.reset();
        MapIcons.clearGhosts();
    }
    static void scoreBar(GuiGraphics g,int x,int y,int width,double ratio,int color,boolean reverse,boolean active){
        g.fill(x-1,y-1,x+width+1,y+7,0x80101820);g.fill(x,y,x+width,y+6,0x50374854);
        if(active){int fill=(int)Math.round(width*Math.clamp(ratio,0,1));g.fill(reverse?x+width-fill:x,y,reverse?x+width:x+fill,y+6,color);}
    }
    static boolean active(Model.Arena arena,Model.Point point,int stage){return !arena.isDeathmatch()&&(!arena.isStaged()||point.sector==stage);}
    static void draw(GuiGraphics g,TerrainPreview terrain,double swayX,double swayY){
        var mc=Minecraft.getInstance();JsonObject state=Client.state,me=state.getAsJsonObject("me");
        EliteHud.draw(g,mc,state);
        Model.Arena arena=BattlefieldOps.GSON.fromJson(state.get("arena"),Model.Arena.class);
        int width=g.guiWidth(),height=g.guiHeight(),bar=Math.min(260,width-16),x=(width-bar)/2;
        int[] tickets={MatchScore.value(arena,state,0),MatchScore.value(arena,state,1)};
        boolean breakthrough=arena.isStaged();
        int friendly=Engine.num(me,"team",0)==1?1:0,enemy=1-friendly;
        long remaining=Math.max(0,arena.roundMinutes*60L-Engine.num(state,"elapsed",0));
        String time=String.format(java.util.Locale.ROOT,"%02d:%02d",remaining/60,remaining%60);
        // A breakthrough defender has no pool to drain, so the readout is an infinity sign rather than a
        // number that never moves.
        String allyText=breakthrough&&friendly==1?"∞":String.valueOf(tickets[friendly]);
        String right=breakthrough&&enemy==1?"∞":String.valueOf(tickets[enemy]);
        int center=width/2,timeHalf=Math.max(28,mc.font.width(time)/2+6),leftEnd=center-timeHalf-4,rightStart=center+timeHalf+4;
        int leftStart=leftEnd-mc.font.width(allyText),rightEnd=rightStart+mc.font.width(right);
        int barWidth=Math.max(8,Math.min(96,Math.min(leftStart-15,width-rightEnd-15)));
        int friendlyColor=Ui.BLUE,enemyColor=Ui.RED;
        // The defender's bar stays full: there is nothing to fill it against.
        double friendlyRatio=breakthrough&&friendly==1?1.0:tickets[friendly]/(double)MatchScore.maximum(arena);
        double enemyRatio=breakthrough&&enemy==1?1.0:tickets[enemy]/(double)MatchScore.maximum(arena);
        scoreBar(g,leftStart-7-barWidth,11,barWidth,friendlyRatio,friendlyColor,true,true);
        scoreBar(g,rightEnd+7,11,barWidth,enemyRatio,enemyColor,false,true);
        g.drawString(mc.font,UiFont.text(allyText),leftStart,9,0xff9bc3ff,true);g.drawString(mc.font,UiFont.text(right),rightStart,9,0xffff9b9b,true);
        g.drawCenteredString(mc.font,UiFont.text(time),center,9,0xffe4edf2);
        // BF1-style team banners: a filled flag beside each side's name, so the bar reads as two sides
        // even before the numbers are legible.
        // Flags and names sit outside the two bars and are mirrored about the timer, so the whole row stays
        // symmetric: [name][flag][bar] timer [bar][flag][name].
        String friendlyName=Board.label(friendly),enemyName=Board.label(enemy);
        int leftBarStart=leftStart-7-barWidth,rightBarEnd=rightEnd+7+barWidth;
        int friendlyFlagX=Math.max(2,leftBarStart-11);
        g.fill(friendlyFlagX,22,friendlyFlagX+7,28,friendlyColor);
        int friendlyLabelX=Math.max(2,friendlyFlagX-3-mc.font.width(friendlyName));
        g.drawString(mc.font,friendlyName,friendlyLabelX,21,0xff82b5ed,true);
        int enemyFlagX=Math.min(width-9,rightBarEnd+4);
        g.fill(enemyFlagX,22,enemyFlagX+7,28,enemyColor);
        int enemyLabelX=Math.min(width-mc.font.width(enemyName)-2,enemyFlagX+10);
        g.drawString(mc.font,enemyName,enemyLabelX,21,0xffe78a8a,true);
        if(arena.isDeathmatch())g.drawCenteredString(mc.font,"团队死斗 · 目标 "+arena.targetKills+" 击杀",width/2,33,0xffd8dde0);
        // Ammunition box progress, under the crosshair: a rocket or grenade arrives one unit per interval.
        double refill=state.has("refill")?state.get("refill").getAsDouble():0;
        double refillWait=state.has("refillWait")?state.get("refillWait").getAsDouble():0;
        boolean refillNeed=!state.has("refillNeed")||state.get("refillNeed").getAsBoolean();
        // The server reports the wait only on state updates, so the HUD counts down locally between them.
        if(refillWait>0.001&&refillWait!=lastWait){lastWait=refillWait;lastWaitAt=System.currentTimeMillis();}
        double shownWait=lastWait-Math.min(lastWait,(System.currentTimeMillis()-lastWaitAt)/1000.0);
        if(refillNeed&&refill<=0.001&&shownWait>0.4){
            String cd="补给冷却 "+((int)Math.ceil(shownWait))+"s";
            g.drawCenteredString(mc.font,cd,width/2,height/2+34,0xffb9c3cf);
        }
        if(refillNeed&&refill>0.001){
            int rw=96,rh=4,rx=width/2-rw/2,ry=height/2+16;
            g.fill(rx-1,ry-1,rx+rw+1,ry+rh+1,0x80000000);
            int filled=(int)Math.round(rw*Math.min(1.0,refill));
            g.fill(rx,ry,rx+filled,ry+rh,0xffd9a566);
        }
        int stage=Engine.num(state,"stage",0);
        int stages=Math.max(1,Engine.num(state,"stages",1));
        // Both staged modes need the same readout: which stage the attack is on, out of how many.
        if(arena.isRush())g.drawCenteredString(mc.font,"突袭 · 阶段 "+(stage+1)+"/"+stages,width/2,33,0xffd8dde0);
        else if(breakthrough)g.drawCenteredString(mc.font,"突破 · 阶段 "+(stage+1)+"/"+stages,width/2,33,0xffd8dde0);
        JsonArray flags=state.has("flags")?state.getAsJsonArray("flags"):new JsonArray();
        var indices=new java.util.ArrayList<Integer>();
        for(int i=0;i<arena.points.size()&&i<flags.size();i++)if(active(arena,arena.points.get(i),stage))indices.add(i);
        for(var slot:CAPTURE_QUEUE.update(arena.id+":"+Engine.num(me,"team",-1),indices,Engine.num(state,"objective",-1),width,bar,System.nanoTime()/1_000_000)){
            int i=slot.point();var f=flags.get(i).getAsJsonObject();int owner=Engine.num(f,"owner",-1);
            float progress=f.has("progress")?f.get("progress").getAsFloat():50;
            DeployIcon.queue(g,mc.font,arena.points.get(i).id,slot,progress,owner,Engine.num(me,"team",-1));
            if(f.has("contested")&&f.get("contested").getAsBoolean())g.drawString(mc.font,"!",Math.round(slot.x()+slot.radius()+3),Math.round(slot.y()-5),0xffdd77,false);
        }
        JsonObject hud=state.has("hud")?state.getAsJsonObject("hud"):new JsonObject();
        boolean allowed=!hud.has("minimap")||hud.get("minimap").getAsBoolean();
        boolean xaero=hud.has("xaero")&&hud.get("xaero").getAsBoolean(),hidden=hud.has("hideXaero")&&hud.get("hideXaero").getAsBoolean();
        // The player's own choice wins over the server default, and the toggle hides it outright. Checked
        // before the size computation so a hidden minimap costs nothing to draw.
        if(NativeXaero.enabled() && !ClientPrefs.minimapHidden()&&!NativeXaero.recent()){
            MinimapOptions preferences=ClientPrefs.minimap();
            var roster=preferences.squad?SquadRoster.rows(state):java.util.List.<SquadRoster.Row>of();
            int extra=MinimapOptions.rosterHeight(roster.size())+(preferences.coordinates?12:0)+5;
            int size=MinimapOptions.fitSize(preferences.size,width,height,extra);
            // Position comes from the operator's setting, defaulting to the top-left. Computed by a pure
            // helper so the "half off screen" and "on top of the ticket bar" cases are covered by tests
            // rather than by whoever happens to be playing at a given window size.
            String corner=ClientPrefs.corner(Engine.str(hud,"minimapCorner","TOP_LEFT"));
            // Superb Warfare draws its vehicle readouts (health/armour and the crew list) along the
            // bottom-left. While riding, keep our minimap on the top edge so it never covers them;
            // on foot the bottom-left Battlefield layout is safe.
            if(mc.player!=null&&mc.player.getRootVehicle()!=mc.player&&corner.startsWith("BOTTOM"))corner="TOP_LEFT";
            int[] origin=MinimapOptions.origin(corner,width,height,size,extra);
            int mapX=origin[0],mapY=origin[1];
            // The in-match minimap is centred on the player, so it is mostly covered by loaded chunks —
            // but the parts of the map it reaches past the view distance still need the bake.
            if(preferences.terrain)Client.wantTerrain(arena.id);
            double[] centre = preferences.smooth?FOLLOW.to(mc.player.getX(), mc.player.getZ(), Smooth.deltaSeconds()):new double[]{mc.player.getX(),mc.player.getZ()};
            if (mapBake == null) mapBake = new MapBake();
            Minimap map=Minimap.local(mapX,mapY,size,centre[0],centre[1],preferences.span).terrain(terrain).bake(mapBake).hudOptions(preferences);
            map.draw(g,mc.font,arena,state,Engine.num(me,"team",-1),mc.player.getX(),mc.player.getZ(),mc.player.getYRot(),true,swayX,swayY);
            int under=mapY+size+4;
            if(preferences.coordinates){String coordinates="X "+mc.player.getBlockX()+"  Z "+mc.player.getBlockZ()+" · "+preferences.span+"m";g.fill(mapX-2,under,mapX+size+2,under+12,0xd4081018);g.drawString(mc.font,mc.font.plainSubstrByWidth(coordinates,size-4),mapX+2,under+2,TacticalColors.FRIEND,true);under+=12;}
            if(preferences.squad){
                // On foot the list sits to the right of the minimap; riding, the vehicle readouts own the
                // bottom-left, so the list stacks under the (top-left) minimap instead of beside it.
                boolean riding=mc.player!=null&&mc.player.getRootVehicle()!=mc.player;
                if(riding)SquadRoster.drawAt(g,mc.font,roster,mapX,mapY+size+6,Engine.num(me,"squad",0));
                else SquadRoster.drawBeside(g,mc.font,roster,mapX,mapY,size,Engine.num(me,"squad",0),false);
            }
        }
        // Downed state: show the bleed-out clock without obscuring crawling or aiming.
        if(me.has("downed")&&me.get("downed").getAsBoolean()){
            int left=Engine.num(state,"downLeft",0);
            g.drawCenteredString(mc.font,"你倒下了",width/2,height-111,0xffff6b6b);
            g.drawCenteredString(mc.font,"出血 "+left+"s · 标记键呼救 · 小队／支援兵空手右键救援",
                    width/2,height-99,0xffffd479);
            String help=arena.allowGiveUp ? "按住空格放弃救援 · " : "";
            g.drawCenteredString(mc.font,help+"部署等待 "+Engine.num(state,"respawnRemaining",0)+"s",width/2,height-124,0xffd8dde0);
            int giveUp=Engine.num(state,"giveUpProgress",0);
            if(giveUp>0)g.fill(width/2-60,height-113,width/2-60+giveUp*6,height-111,0xffe56c72);
            int barW=120, filled=(int)Math.round(barW*Math.max(0,Math.min(1,left/(double)Math.max(1,arena.downSeconds))));
            g.fill(width/2-barW/2,height-88,width/2+barW/2,height-84,0xff000000);
            g.fill(width/2-barW/2,height-88,width/2-barW/2+filled,height-84,0xffff6b6b);
        }
        if (me.has("downed") && me.get("downed").getAsBoolean()) {
            double fraction = Medic.fraction(Engine.num(state,"reviveProgress",0), Medic.reviveTicks(arena.reviveSeconds));
            if (fraction > 0) {
                int px=width/2-60, py=height-81;
                g.fill(px-3,py-3,px+123,py+21,0xe0080d12);
                g.drawCenteredString(mc.font,"正在救援 "+(int)(fraction*100)+"%",width/2,py,0xffd8dde0);
                g.fill(px,py+13,px+(int)(120*fraction),py+17,0xffd8dde0);
            }
        }
        if(arena.isRush()&&me.get("alive").getAsBoolean()){
            String text="";
            if(state.has("rush"))for(var element:state.getAsJsonArray("rush")){var charge=element.getAsJsonObject();int index=Engine.num(charge,"point",-1);if(index<0||index>=arena.points.size())continue;var point=arena.points.get(index);
                if(charge.get("armed").getAsBoolean())text=point.id+" · 爆破剩余 "+Engine.num(charge,"remaining",0)+" 秒";
                if(Engine.num(charge,"progress",0)>0){text=point.id+" · "+(charge.get("armed").getAsBoolean()?"拆除":"安放")+" "+Math.min(100,Engine.num(charge,"progress",0)*100/Math.max(1,Engine.num(charge,"duration",1)))+"%";break;}
            }
            if(text.isEmpty()&&mc.player!=null&&arena.points.stream().anyMatch(p->p.sector==stage&&mc.player.distanceToSqr(p.x,p.y,p.z)<=36))text=Engine.str(state,"rushHint","");
            if(!text.isEmpty()){int lineWidth=Math.min(width-20,mc.font.width(text)+14);g.fill((width-lineWidth)/2,height-130,(width+lineWidth)/2,height-114,0xc8101820);g.drawCenteredString(mc.font,text,width/2,height-126,0xff70deec);}
        }
        int outside=Engine.num(me,"outside",0);
        if(outside>0){
            String warning="返回战区 · "+Math.max(0,arena.outsideSeconds-outside)+" 秒后阵亡";
            int box=Math.max(160,mc.font.width(warning)+20),left=(width-box)/2,top=height-108;
            g.fill(left,top,left+box,top+19,0xc8101419);
            g.fill(left,top+17,left+(int)(box*Math.max(0,1-outside/(double)Math.max(1,arena.outsideSeconds))),top+19,0xffdce6ec);
            g.drawCenteredString(mc.font,warning,width/2,top+4,0xffe1e8ec);
        }
        if(!me.get("alive").getAsBoolean()){int wait=Engine.num(state,"respawnRemaining",0);g.drawCenteredString(mc.font,wait>0?"重生准备 · "+wait+"s":"已可部署 · "+Client.OPEN.getTranslatedKeyMessage().getString()+" 选择目标",width/2,height-94,TacticalColors.FRIEND);}
        String hint=Engine.str(state,"objectiveHint","");
        if(!hint.isEmpty())g.drawCenteredString(mc.font,hint,width/2,height-76,0xffffca77);
        // Only the objective the player is actually standing in gets a capture detail line.
        for(int i:indices){var p=arena.points.get(i);if(!me.get("alive").getAsBoolean()||Engine.num(state,"objective",-1)!=i)continue;
            var f=flags.get(i).getAsJsonObject();int progress=Engine.num(f,"progress",50);if(Engine.num(me,"team",-1)==1)progress=100-progress;int px=width/2-52,py=height-67;
            g.fill(px,py,px+104,py+20,0xc0152027);g.drawCenteredString(mc.font,p.id+" "+(f.has("contested")&&f.get("contested").getAsBoolean()?"争夺中":Engine.num(f,"owner",-1)==Engine.num(me,"team",-1)?"我方控制":"占领中"),width/2,py+3,0xffffff);
            g.fill(px+2,py+15,px+2+progress,py+18,TacticalColors.FRIEND);g.fill(px+2+progress,py+15,px+102,py+18,TacticalColors.ENEMY);break;}
    }
}
