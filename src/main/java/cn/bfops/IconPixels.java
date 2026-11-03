package cn.bfops;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Subsample before decoding: never allocate a source-sized native bitmap for a GUI thumbnail. */
final class IconPixels {
    static final int EDGE=256;
    static BufferedImage read(InputStream stream)throws IOException{
        try(var input=new MemoryCacheImageInputStream(stream)){
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("Unsupported icon image");
            var reader=readers.next();
            try{
                reader.setInput(input,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<=0||h<=0||w>8192||h>8192||(long)w*h>16_777_216L)throw new IOException("Icon source is too large");
                int step=Math.max(1,(Math.max(w,h)+EDGE-1)/EDGE);
                var params=reader.getDefaultReadParam();params.setSourceSubsampling(step,step,0,0);
                return reader.read(0,params);
            }finally{reader.dispose();}
        }
    }
    static int rgba(int argb,boolean white){
        int alpha=argb>>>24;if(alpha==0)return 0;
        int gray=((argb>>16&255)*54+(argb>>8&255)*183+(argb&255)*19)>>8;
        int value=white?170+gray*85/255:gray;
        return alpha<<24|value<<16|value<<8|value;
    }
}
