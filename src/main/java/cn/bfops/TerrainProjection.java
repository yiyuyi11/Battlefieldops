package cn.bfops;

/** Reuse a prior terrain pixel while fresh chunks are sampled after pan/zoom. */
final class TerrainProjection {
    static int index(double x,double z,double cx,double cz,double span,double aspect,int width,int height){
        if(!(span>0)||!(aspect>0)||width<=0||height<=0)return -1;
        int px=(int)Math.floor(((x-cx)/span+.5)*width);
        int pz=(int)Math.floor(((z-cz)/(span*aspect)+.5)*height);
        return px<0||pz<0||px>=width||pz>=height?-1:pz*width+px;
    }
}
