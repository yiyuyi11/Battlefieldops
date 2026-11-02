package cn.bfops;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

/** Modal popup owned by the existing screen; opening it never closes the editor or its terrain. */
final class DropdownMenu {
    final FrontButton anchor;
    final List<ChoiceScreen.Choice> options;
    final Consumer<String> chosen;
    String query="";
    int selected, first;
    Layout.Rect box;
    int rows;
    DropdownMenu(FrontButton anchor,List<ChoiceScreen.Choice> options,String current,Consumer<String> chosen){
        this.anchor=anchor;this.options=List.copyOf(options);this.chosen=chosen;
        for(int i=0;i<options.size();i++)if(options.get(i).id().equals(current)){selected=i;break;}
    }
    List<ChoiceScreen.Choice> filtered(){return options.stream().filter(c->LibrarySearch.matches(query.toLowerCase(Locale.ROOT),c.id(),c.name(),LibrarySource.searchable(c.icon(),c.kind()))).toList();}
    void layout(int width,int height){
        int below=height-anchor.getY()-anchor.getHeight()-4,above=anchor.getY()-4;
        boolean down=below>=Math.min(196,Math.max(36,above));
        rows=Math.max(1,Math.min(9,(Math.max(36,down?below:above)-18)/18));
        int count=Math.min(rows,Math.max(1,filtered().size())),h=18+count*18;
        int w=Math.min(Math.max(180,anchor.getWidth()),Math.max(40,width-8));
        box=new Layout.Rect(Math.clamp(anchor.getX(),4,Math.max(4,width-w-4)),Math.clamp(down?anchor.getY()+anchor.getHeight()+1:anchor.getY()-h-1,2,Math.max(2,height-h-2)),w,h);
        selected=Math.clamp(selected,0,Math.max(0,filtered().size()-1));
        first=Math.clamp(first,Math.max(0,selected-rows+1),selected);
        first=Math.min(first,Math.max(0,filtered().size()-rows));
    }
    void draw(GuiGraphics g,Font font,int mx,int my,int width,int height){
        layout(width,height);var list=filtered();
        g.pose().pushPose();g.pose().translate(0,0,500);
        g.fill(box.x()-1,box.y()-1,box.right()+1,box.bottom()+1,0xff526875);
        g.fill(box.x(),box.y(),box.right(),box.bottom(),0xfa131d24);
        g.drawString(font,Ui.clip(font,query.isEmpty()?"输入筛选 · ↑↓ 选择 · Enter 确认":"> "+query,box.w()-10),box.x()+5,box.y()+5,Ui.TEXT_DIM,false);
        for(int i=first;i<Math.min(list.size(),first+rows);i++){
            var c=list.get(i);int y=box.y()+18+(i-first)*18;
            boolean hover=mx>=box.x()&&mx<box.right()&&my>=y&&my<y+18;
            if(i==selected||hover)g.fill(box.x()+1,y,box.right()-1,y+18,hover?0xff345064:0xff253b49);
            int x=box.x()+6;
            if(!c.kind().isEmpty()&&!c.icon().isEmpty()){AssetIcons.draw(g,c.kind(),c.icon(),x,y+2,14,14);x+=18;}
            g.drawString(font,Ui.clip(font,c.name(),box.right()-x-9),x,y+5,Ui.TEXT,false);
        }
        if(list.isEmpty())g.drawString(font,"无匹配选项",box.x()+6,box.y()+23,Ui.TEXT_DIM,false);
        if(list.size()>rows){int track=box.h()-18,thumb=Math.max(8,track*rows/list.size()),y=box.y()+18+(track-thumb)*first/Math.max(1,list.size()-rows);g.fill(box.right()-3,y,box.right()-1,y+thumb,Ui.ACCENT);}
        g.pose().popPose();
    }
    String click(double x,double y){if(box==null||!box.inside(x,y)||y<box.y()+18)return null;int index=first+(int)(y-box.y()-18)/18;var list=filtered();return index>=0&&index<list.size()?list.get(index).id():null;}
    String value(){var list=filtered();return list.isEmpty()?null:list.get(Math.clamp(selected,0,list.size()-1)).id();}
    void move(int delta){selected=Math.clamp(selected+delta,0,Math.max(0,filtered().size()-1));}
}
