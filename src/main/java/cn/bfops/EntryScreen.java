package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Matches the source's reveal, brief hold, dark transition and one-second post fade. */
final class EntryScreen extends Screen {
    private int polls;
    private FrontButton leave;
    EntryScreen(){super(Component.literal("战场简报"));}
    @Override protected void init(){
        leave=addRenderableWidget(new FrontButton("离开房间",12,height-25,84,18,()->{
            var req=Client.request("lobbyReturn",Client.currentMap());Client.send(req);
        }));
    }
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override public void onClose(){}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){
        if(++polls%20==0&&EntryScene.active())Client.send(Client.request("state",Client.currentMap()));
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){}
    private static float smooth(float t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        if(!EntryScene.active())return;
        var data=EntryScene.copy();float t=EntryScene.elapsed()+dt,d=EntryScene.duration();
        float reveal=Math.min(40,d*.4f),darken=Math.min(10,d*.2f),dark;
        if(t>d)dark=1-smooth((t-d)/20);
        else if(t<reveal)dark=1-.8f*smooth(t/reveal);
        else if(t>d-darken)dark=.2f+.8f*smooth((t-(d-darken))/darken);
        else dark=.2f;
        g.fill(0,0,width,height,Math.round(Math.clamp(dark,0,1)*255)<<24);
        // FPS (刷新率) pinned to the top-right with a metrics glyph, on a solid backing so it stays
        // readable over the fading world behind it.
        String fpsText="20 Hz";
        int fpsW=font.width(fpsText);
        g.fill(width-fpsW-34,8,width-8,27,0xe0101820);
        UiIcons.draw(g,UiIcons.CHART,width-fpsW-28,11,16);
        g.drawString(font,fpsText,width-fpsW-8,12,Ui.TEXT,true);
        var layout=data.has("layout")&&data.get("layout").isJsonObject()?data.getAsJsonObject("layout"):new JsonObject();
        int textX=Engine.num(layout,"textX",12),textY=Engine.num(layout,"textY",12);
        float titleScale=Math.max(.5f,Math.min(4f,Engine.num(layout,"titleScale",25)/10f));
        int descWidth=Math.max(90,Engine.num(layout,"descWidth",220)),descHeight=Math.max(40,Engine.num(layout,"descHeight",85)),descBottom=Math.max(20,Engine.num(layout,"descBottom",31));
        int waitX=Engine.num(layout,"waitX",104);
        if(t<d){
            int y=textY;
            y=title(g,Engine.str(data,"room",""),textX,y,titleScale)+2;
            String map=Engine.str(data,"map","");
            if(!map.equals(Engine.str(data,"room","")))y=title(g,map,textX,y,Math.max(.75f,titleScale*.6f))+5;
            g.drawString(font,Engine.str(data,"dimension",""),textX,y,Ui.TEXT_DIM,true);y+=16;
            if(data.has("tips"))for(var value:data.getAsJsonArray("tips")){
                String line=value.getAsString();if(line.isBlank())continue;
                for(var text:font.split(Component.literal("▶ "+line),Math.max(30,width-24))){
                    if(y>height/2)break;g.drawString(font,text,textX,y,Ui.TEXT,true);y+=11;
                }
            }
            int w=Math.min(descWidth,Math.max(90,width/3)),top=Math.max(y+12,height-descBottom-descHeight);
            if(top<height-descBottom){
                g.fill(width-w-12,top-4,width-8,height-descBottom,0x60081016);
                int lineY=top;for(var line:font.split(Component.literal(Engine.str(data,"description","")),w-8)){
                    if(lineY+9>height-descBottom)break;g.drawString(font,line,width-w-8,lineY,Ui.TEXT,true);lineY+=11;
                }
            }
        }
        String wait=t<d?"战场简报 · "+Math.max(0,(int)Math.ceil((d-t)/20))+"s":"正在进入部署…";
        g.drawString(font,wait,waitX,height-20,Ui.ACCENT,true);
        if(leave!=null)leave.render(g,mx,my,dt);
    }
    private int title(GuiGraphics g,String value,int x,int y,float preferred){
        float scale=Math.max(.75f,Math.min(preferred,(width-24f)/Math.max(1,font.width(value))));
        int w=Math.min(width-24,Math.round(font.width(value)*scale)+8),h=Math.round(9*scale)+6;
        g.fill(x,y,x+w,y+h,0x4d000000);
        g.pose().pushPose();g.pose().translate(x+3,y+3,0);g.pose().scale(scale,scale,1);g.drawString(font,value,0,0,Ui.TEXT,true);g.pose().popPose();return y+h;
    }
}
