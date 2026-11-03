package cn.bfops;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import com.google.gson.*;
import javax.imageio.ImageIO;

/** Audit all statically named local PNGs, including the non-textures icon directory missed before. */
public final class UiResourceSim {
    public static void main(String[] args)throws Exception{
        int checks=0;Set<String> resources=new TreeSet<>();
        Pattern calls=Pattern.compile("fromNamespaceAndPath\\(\"battlefield_ops\",\\s*\"([^\"]+\\.png)\"");
        Pattern literals=Pattern.compile("\"battlefield_ops:([^\"]+\\.png)\"");
        try(var paths=Files.walk(Path.of("src/main/java"))){for(var p:paths.filter(p->p.toString().endsWith(".java")).toList()){
            String text=Files.readString(p);for(Pattern pattern:List.of(calls,literals)){var m=pattern.matcher(text);while(m.find())resources.add(m.group(1));}
        }}
        try(var jar=new ZipFile(args.length>0?args[0]:"build/libs/battlefield-ops-0.8.30-neoforge-1.21.1.jar")){
            for(String name:resources){var entry=jar.getEntry("assets/battlefield_ops/"+name);if(entry==null)throw new AssertionError("Missing static UI resource "+name);
                try(var stream=jar.getInputStream(entry)){if(ImageIO.read(stream)==null)throw new AssertionError("Invalid PNG "+name);}checks++;
            }
            for(String lang:List.of("en_us","zh_cn","zh_tw","lzh")){
                var entry=jar.getEntry("assets/battlefield_ops/lang/"+lang+".json");try(var stream=jar.getInputStream(entry)){
                    var data=JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    if(!data.get("key.categories.battlefield_ops").getAsString().equals("battlefieldops"))throw new AssertionError("Wrong keybinding category "+lang);checks++;
                }
            }
            try(var in=jar.getInputStream(jar.getEntry("assets/battlefield_ops/textures/gui/empty_preview.png"))){
                var image=ImageIO.read(in);if((image.getRGB(0,0)>>>24)!=0)throw new AssertionError("Fallback must be transparent");checks++;
            }
        }
        if(resources.size()<20)throw new AssertionError("Resource audit accidentally skipped the source tree");
        System.out.println("UiResourceSim: "+checks+" packaged PNG/category checks passed");
    }
}
