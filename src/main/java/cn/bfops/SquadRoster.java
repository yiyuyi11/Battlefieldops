package cn.bfops;
import java.util.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
final class SquadRoster {
    record Row(String name,boolean self,boolean alive,boolean downed,String kit,boolean leader){}
    static List<Row> rows(JsonObject state){
        if(state==null||!state.has("inMatch")||!state.get("inMatch").getAsBoolean()||!state.has("me"))return List.of();
        var me=state.getAsJsonObject("me");String self=Engine.str(me,"name","");
        var result=new ArrayList<Row>();
        if(state.has("squadRoster"))for(var value:state.getAsJsonArray("squadRoster")){
            var entry=value.getAsJsonObject();String name=Engine.str(entry,"name","");
            if(!name.isEmpty()&&result.stream().noneMatch(r->r.name.equals(name)))result.add(new Row(name,name.equals(self),bool(entry,"alive"),bool(entry,"downed"),Engine.str(entry,"kit",""),bool(entry,"leader")));
        }
        if(!self.isEmpty()&&result.stream().noneMatch(Row::self))result.addFirst(new Row(self,true,bool(me,"alive"),bool(me,"downed"),Engine.str(me,"kit",""),false));
        result.sort(Comparator.comparing(Row::self).reversed().thenComparing(Row::name));return result.stream().limit(SquadRules.CAPACITY).toList();
    }

    /** Battlefield squad row: yellow circle for yourself, green for mates, light-gray id, star for the leader. */
    static void drawAt(GuiGraphics g,Font font,List<Row> rows,int x,int y,int squad){
        if(rows.isEmpty()||squad<=0)return;
        for(var row:rows){
            int cy=y+6;
            // The circle carries the relationship: yellow is you, green is a squadmate.
            if(row.alive()&&!row.downed()){
                dot(g,x+6,cy,6,row.self()?0xf0ffd23f:0xc02f8f57);
                AssetIcons.whiteIcons(true);
                try{AssetIcons.draw(g,"role",row.kit(),x,cy-7,14,14);}
                finally{AssetIcons.whiteIcons(false);}
            }else Bf1Ui.icon(g,"death_hud_default",x,cy-7,14);
            int color=row.alive()&&!row.downed()?0xffe8eef2:0xffb8c2c7;
            String clipped=Ui.clip(font,row.name(),100);
            g.drawString(font,clipped,x+16,y+2,color,true);
            if(row.leader())Bf1Ui.icon(g,"squadleader_hud_default",x+18+font.width(clipped),y+1,10);
            y+=14;
        }
    }

    static void drawBeside(GuiGraphics g,Font font,List<Row> rows,int mapX,int mapY,int mapSize,int squad,boolean circle){
        // No squad, no list: an unassigned player's HUD stays clean.
        if(rows.isEmpty()||squad<=0)return;
        int x=mapX+mapSize+6;
        // Circular map: lower-right, first row just below the centre line. Square map: the whole list
        // hugs the full right edge and stays vertically centred.
        int y=circle?mapY+mapSize/2+2:mapY+mapSize/2-rows.size()*7;
        drawAt(g,font,rows,x,y,squad);
    }

    /** Legacy stacked layout, kept for the deploy screen where there is no circle to hug. */
    static void draw(GuiGraphics g,Font font,List<Row> rows,int x,int y,int width,int squad){
        if(rows.isEmpty())return;int height=MinimapOptions.rosterHeight(rows.size());g.fill(x-2,y,x+width+2,y+height,0xbf080f16);
        g.drawString(font,squad>0?SquadRules.label(squad):"个人",x+2,y+2,TacticalColors.MATE,true);int line=y+13;
        for(var row:rows){int color=row.downed?TacticalColors.ENEMY:row.self?TacticalColors.SELF:row.alive?TacticalColors.MATE:0xff7293a3;
            String status=row.downed?"倒地":row.alive?"":"待部署";String suffix=(row.self?" · 你":"")+(status.isEmpty()?"":" · "+status);
            AssetIcons.draw(g,"role",row.kit,x+2,line-1,12,12);
            g.drawString(font,Ui.clip(font,row.name+suffix,Math.max(8,width-21)),x+17,line,color,true);
            line+=13;
        }
    }

    private static void dot(GuiGraphics g,int cx,int cy,int r,int color){
        for(int dy=-r;dy<=r;dy++){
            int half=(int)Math.sqrt(Math.max(0,r*r-dy*dy));
            g.fill(cx-half,cy+dy,cx+half+1,cy+dy+1,color);
        }
    }

    private static boolean bool(JsonObject o,String key){return o.has(key)&&o.get(key).getAsBoolean();}
}
