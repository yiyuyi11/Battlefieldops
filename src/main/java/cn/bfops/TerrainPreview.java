package cn.bfops;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/** Screen-resolution, nearest-filtered surface texture with bounded work per frame. */
final class TerrainPreview implements AutoCloseable {
    /**
     * Registration name counter.
     *
     * <p>Every instance needs its own texture name. Sharing one name meant that the HUD minimap, the
     * editor canvas and the in-match map — which all exist at the same time — registered over each
     * other's texture, so one of them would suddenly show another's terrain. A unique suffix per
     * instance is what makes them independent.
     */
    private static int counter;

    private final String name = "bfops_terrain_" + (++counter);
    private DynamicTexture texture;
    private ResourceLocation location;
    private int tw,th,cursor;
    private int[] heights;
    private int[] colors;
    private double centerX=Double.NaN,centerZ,scale,aspect;
    private String dimension="";
    private long nextRefresh;
    void draw(GuiGraphics g,int left,int top,int width,int height,double cx,double cz,double span,String dim) {
        var mc=Minecraft.getInstance();var level=mc.level;
        if(level==null || width<=0 || height<=0)return;
        // A map saved before the dimension field existed, or a screen that has no arena at all, passes
        // an empty dimension. Treating that as "not this world" would leave the canvas showing the
        // placeholder checkerboard forever; assuming the current dimension is what the operator means.
        String target=dim==null || dim.isEmpty() ? level.dimension().location().toString() : dim;
        int nw=Math.min(1024,width),nh=Math.min(1024,height);
        boolean resized=texture==null || nw!=tw || nh!=th;
        int[] oldColors=colors;int oldW=tw,oldH=th;
        double oldX=centerX,oldZ=centerZ,oldScale=scale,oldAspect=aspect;
        String oldDimension=dimension;
        if(resized){
            close();tw=nw;th=nh;heights=new int[tw*th];
            texture=new DynamicTexture(tw,th,false);texture.setFilter(false,false);
            location=mc.getTextureManager().register(name,texture);
        }
        double ratio=height/(double)width;
        if(resized || cx!=centerX || cz!=centerZ || span!=scale || ratio!=aspect || !target.equals(dimension)){
            centerX=cx;centerZ=cz;scale=span;aspect=ratio;dimension=target;cursor=0;nextRefresh=0;
            colors=new int[tw*th];
            for(int z=0;z<th;z++)for(int x=0;x<tw;x++){
                int color=unknown(x,z);
                if(oldColors!=null&&target.equals(oldDimension)){
                    double wx=cx+((x+.5)/tw-.5)*span,wz=cz+((z+.5)/th-.5)*span*ratio;
                    int oldIndex=TerrainProjection.index(wx,wz,oldX,oldZ,oldScale,oldAspect,oldW,oldH);
                    if(oldIndex>=0)color=oldColors[oldIndex];
                }
                colors[z*tw+x]=color;texture.getPixels().setPixelRGBA(x,z,color);
            }
            texture.upload();
        }
        long now=System.currentTimeMillis();
        if(cursor==tw*th && now>=nextRefresh)cursor=0;
        if(target.equals(level.dimension().location().toString()) && cursor<tw*th){
            int end=Math.min(tw*th,cursor+4096);long deadline=System.nanoTime()+3_000_000;
            BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
            for(;cursor<end;cursor++){
                if((cursor&63)==0 && System.nanoTime()>deadline)break;
                int sx=cursor%tw,sz=cursor/tw;
                int x=(int)Math.floor(cx+((sx+0.5)/tw-0.5)*span);
                int z=(int)Math.floor(cz+((sz+0.5)/th-0.5)*span*ratio);
                var chunk=level.getChunkSource().getChunk(x>>4,z>>4,ChunkStatus.FULL,false);
                int color=unknown(sx,sz);
                if(chunk!=null){
                    int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15);pos.set(x,y,z);
                    int rgb=chunk.getBlockState(pos).getMapColor(level,pos).col;
                    // Slope is measured against the pixel to the left on the same scanline. Reading
                    // heights[cursor-1] across a row boundary compares unrelated parts of the map, so
                    // the leftmost column of every row would be lit from the wrong neighbour.
                    int previous = sx > 0 ? heights[cursor - 1] : y;
                    double slope=(y-previous)/Math.max(1,span/tw);heights[cursor]=y;
                    double light=Math.max(.6,Math.min(1.2,.94+slope*.08));
                    int r=Math.min(255,(int)(((rgb>>16)&255)*light)),green=Math.min(255,(int)(((rgb>>8)&255)*light)),b=Math.min(255,(int)((rgb&255)*light));
                    color=0xff000000|b<<16|green<<8|r;
                } else {
                    // No chunk here: the client only holds what the server has sent, so anything past the
                    // view distance is genuinely absent. Fall back to the server's one-time bake instead
                    // of the placeholder pattern — this is what makes the whole map visible.
                    TerrainMap baked = Client.baked;
                    if (baked != null) {
                        int sampled = baked.sample(x, z);
                        if (sampled != 0) color = sampled;
                    }
                }
                colors[cursor]=color;
                texture.getPixels().setPixelRGBA(sx,sz,color);
            }
            texture.upload();if(cursor==tw*th)nextRefresh=now+1000;
        }
        g.blit(location,left,top,width,height,0,0,tw,th,tw,th);
    }
    private static int unknown(int x,int z){return ((x/16+z/16)&1)==0?0xff3c3226:0xff4c4236;}
    @Override public void close(){
        if(location!=null)Minecraft.getInstance().getTextureManager().release(location);
        else if(texture!=null)texture.close();location=null;texture=null;colors=null;
    }
}
