package cn.bfops;
import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.screens.Screen;import net.minecraft.client.gui.components.Button;import net.minecraft.network.chat.Component;
/** One decision, two buttons; never routed through the searchable equipment picker. */
final class ConfirmActionScreen extends Screen {
 private final Screen parent;private final String text,accept;private final Runnable action;
 ConfirmActionScreen(Screen parent,String title,String text,String accept,Runnable action){super(Component.literal(title));this.parent=parent;this.text=text;this.accept=accept;this.action=action;}
 static Screen base(Screen screen){while(screen instanceof ConfirmActionScreen confirm)screen=confirm.parent;return screen;}
 protected void init(){int w=Math.min(320,width-24),x=(width-w)/2,y=height/2+28;
  addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(x,y,(w-8)/2,20).build());
  addRenderableWidget(Button.builder(Component.literal(accept),b->{minecraft.setScreen(parent);action.run();}).bounds(x+(w+8)/2,y,(w-8)/2,20).build());}
 public void renderBackground(GuiGraphics g,int x,int y,float dt){}
 public void render(GuiGraphics g,int x,int y,float dt){int w=Math.min(340,width-16),left=(width-w)/2,top=height/2-48;g.fill(left,top,left+w,height/2+56,0xf0182631);g.fill(left,top,left+w,top+2,Ui.ACCENT);g.drawString(font,title,left+12,top+12,Ui.TEXT,true);g.drawWordWrap(font,Component.literal(text),left+12,top+32,w-24,Ui.TEXT_DIM);super.render(g,x,y,dt);}
 public void onClose(){minecraft.setScreen(parent);}public boolean isPauseScreen(){return false;}
}
