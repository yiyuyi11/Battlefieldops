package cn.bfops;

import cn.bfops.conquest.client.render.ClientCrosshairFadeSupport;

/** Snapshot translation and reference presentation constants, independent of the GPU. */
final class MarkerVisualRules {
    private MarkerVisualRules(){}
    static String teamToken(int owner,int self){return owner<0||owner>1||self<0||self>1?"NEUTRAL":owner==self?"ALLY":"ENEMY";}
    // Our bar is 100=blue; 656's renderer is 0=viewer ally, 100=viewer enemy.
    static float displayProgress(float raw,int self){float p=clamp(raw);return self==0?100-p:self==1?p:50;}
    private static float clamp(float raw){return Float.isFinite(raw)?Math.max(0,Math.min(100,raw)):50;}
    static float alpha(int width,int height,double x,double y,boolean cameraOverride){
        return cameraOverride?.4f:ClientCrosshairFadeSupport.resolveWeightedAlpha(width,height,x,y,.05f,.4f,300,1.2f);
    }
    static float approach(float current,float target,float dt){return Math.abs(current-target)<=.001f?target:current+(target-current)*Math.max(0,Math.min(1,dt/.12f));}
    static float entry(float ageTicks,int remainingTicks){return ease(Math.min(Math.max(0,Math.min(1,ageTicks/14f)),remainingTicks==Integer.MAX_VALUE?1:Math.max(0,Math.min(1,remainingTicks/14f))));}
    static float ease(float value){float x=Math.max(0,Math.min(1,value));return 1-(float)Math.pow(1-x,3);}
    static float entryOffset(float scale){return (1-scale)*(1-scale)*10;}
    static float friendScale(double distance){float t=(float)(Math.max(3,Math.min(30,distance))-3)/27;return 1.1f+(.72f-1.1f)*t;}
    static double crosshairDistance(int width,int height,double x,double y){return Math.hypot(x-width*.5,y-height*.5);}
}
