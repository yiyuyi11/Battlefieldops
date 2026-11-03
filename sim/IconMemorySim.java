package cn.bfops;
import java.io.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.imageio.ImageIO;

public final class IconMemorySim {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Icon memory check "+checks);}
    public static void main(String[] args)throws Exception{
        Set<Integer> live=new HashSet<>(),released=new HashSet<>();
        var cache=new IconVariantCache<Integer>(64,4L*1024*1024,v->{check(live.remove(v));check(released.add(v));});
        for(int i=0;i<10000;i++){
            live.add(i);cache.put("icon"+i,i,256L*256*4);
            check(cache.bytes()<=4L*1024*1024);check(cache.size()<=16);check(live.size()==cache.size());
        }
        check(cache.get("icon0")==null);check(cache.get("icon9999")==9999);
        cache.clear();check(live.isEmpty()&&released.size()==10000&&cache.bytes()==0);
        cache.clear();check(released.size()==10000);
        List<Integer> drops=new ArrayList<>();var lru=new IconVariantCache<Integer>(2,100,drops::add);
        lru.put("a",1,10);lru.put("b",2,10);lru.get("a");lru.put("c",3,10);
        check(drops.equals(List.of(2))&&lru.get("a")==1);
        lru.put("a",4,20);check(drops.equals(List.of(2,1))&&lru.bytes()==30);
        try{lru.put("huge",5,101);throw new AssertionError("Oversize accepted");}catch(IllegalArgumentException expected){check(lru.bytes()==30);}
        BufferedImage source=new BufferedImage(2048,1024,BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0,0,0x80602010);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(source,"PNG",bytes);source.flush();
        var thumb=IconPixels.read(new ByteArrayInputStream(bytes.toByteArray()));
        check(thumb.getWidth()==256&&thumb.getHeight()==128);
        check((long)thumb.getWidth()*thumb.getHeight()*4==131072);
        check(thumb.getRGB(0,0)==0x80602010);thumb.flush();
        check(IconPixels.rgba(0x00112233,true)==0);
        check(IconPixels.rgba(0x80ffffff,false)==0x80ffffff);
        check(IconPixels.rgba(0xff000000,true)==0xffaaaaaa);
        try{IconPixels.read(new ByteArrayInputStream(new byte[]{0,1,2}));throw new AssertionError("Bad image accepted");}catch(IOException expected){checks++;}
        byte[] huge=bytes.toByteArray();java.nio.ByteBuffer.wrap(huge).putInt(16,10000).putInt(20,10000);
        var crc=new java.util.zip.CRC32();crc.update(huge,12,17);java.nio.ByteBuffer.wrap(huge).putInt(29,(int)crc.getValue());
        try{IconPixels.read(new ByteArrayInputStream(huge));throw new AssertionError("Image bomb accepted");}catch(IOException expected){checks++;}
        System.out.println("IconMemorySim: "+checks+" checks passed; 10000 evictions released; 2048x1024 -> 256x128");
    }
}
