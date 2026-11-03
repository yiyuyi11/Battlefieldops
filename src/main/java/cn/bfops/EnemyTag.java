package cn.bfops;
import net.minecraft.client.gui.GuiGraphics;
/** Small inverted red triangle. Role glyph is omitted when it cannot be read. No halo or caption. */
final class EnemyTag {
    /**
     * Triangle height. Capped at 6px so an enemy tag is never larger than the 7px place-mark arrow: the two
     * appear side by side, and a callout that outgrows the arrow reads as a different class of marker.
     */
    static int size(double distance){return (int)Math.max(3,Math.round(6.0/(1+Math.max(0,distance)/75)));}
    /** The 6px class glyph is only drawn when the arrow is at least as large, so it stays legible. */
    static boolean roleVisible(int size){return size>=5;}
    static void draw(GuiGraphics g,float x,float y,double distance,String kit,float alpha) {
        int size=size(distance),cx=Math.round(x),top=Math.round(y)-size;
        if(alpha<=.01f)return;
        g.setColor(1,1,1,1);
        int red=0xffa64b50;
        for(int row=0;row<size;row++){
            int half=Math.max(0,(size-row-1)/2);
            g.fill(cx-half,top+row,cx+half+1,top+row+1,red);
        }
        // One pixel narrower than the triangle's widest row, so the glyph sits inside the tag.
        if(roleVisible(size)&&FixedClasses.resolve(kit)!=null)AssetIcons.draw(g,"role",kit,cx-3,top+1,6,6);
    }
}
