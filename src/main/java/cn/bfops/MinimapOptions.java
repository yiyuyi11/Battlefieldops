package cn.bfops;
/** Local display controls cannot expand server-authored enemy information. */
final class MinimapOptions {
    int span=256,size=116,terrainOpacity=90;
    boolean terrain=true,grid=false,compass=true,coordinates=false,smooth=true,squad=true;
    boolean names=false,vehicles=true,support=true,objectives=true,viewCone=true;
    void sanitize(){span=cn.bfops.Java17.clamp(span,64,2048);size=cn.bfops.Java17.clamp(size,64,220);terrainOpacity=cn.bfops.Java17.clamp(terrainOpacity,25,100);}
    MinimapOptions copy(){return BattlefieldOps.GSON.fromJson(BattlefieldOps.GSON.toJson(this),MinimapOptions.class);}
    void zoom(boolean in){sanitize();span=cn.bfops.Java17.clamp(in?Math.max(64,span/2):span*2,64,2048);}
    static int rosterHeight(int count){return count<=0?0:12+Math.min(4,count)*13;}
    static int fitSize(int desired,int width,int height,int roster){return Math.max(32,Math.min(desired,Math.min(Math.max(32,width/2-24),Math.max(32,height-34-roster-42))));}
    static int[] origin(String corner,int width,int height,int size,int roster){int[] at=Briefing.minimapOrigin(corner,width,height,size);int maxY=Math.max(0,height-size-roster-42);if(Briefing.normaliseCorner(corner).startsWith("BOTTOM"))at[1]=maxY;else at[1]=Math.min(at[1],maxY);return at;}
}
