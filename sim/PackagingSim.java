package cn.bfops;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonParser;

public final class PackagingSim {
    public static void main(String[] args)throws Exception {
        Path path=Path.of("build/libs/battlefield-ops-0.8.10-neoforge-1.21.1.jar");
        try(var z=new ZipFile(path.toFile())){
            byte[] bytes=z.getInputStream(z.getEntry("META-INF/neoforge.mods.toml")).readAllBytes();
            if(bytes[0]!=(byte)'m')throw new AssertionError("BOM or unexpected metadata prefix");
            var config=new TomlParser().parse(new String(bytes,StandardCharsets.UTF_8));
            if(!"javafml".equals(config.get("modLoader")))throw new AssertionError("invalid loader");
            for(String name:new String[]{"battlefield_ops.mixins.json","battlefield_ops.crawl.mixins.json"}){
                var json=JsonParser.parseString(new String(z.getInputStream(z.getEntry(name)).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                String prefix=json.get("package").getAsString().replace('.','/')+"/";
                for(String side:new String[]{"mixins","client"})if(json.has(side))for(var element:json.getAsJsonArray(side)){
                    if(z.getEntry(prefix+element.getAsString().replace('.','/')+".class")==null)throw new AssertionError("Missing hook "+element);
                }
            }
        }
        // This exact corruption previously passed all pure gameplay tests and crashed the actual loader.
        try{new TomlParser().parse("\ufeffmodLoader=\"javafml\"\n");throw new AssertionError("Parser unexpectedly accepted BOM");}
        catch(com.electronwill.nightconfig.core.io.ParsingException expected){}
        System.out.println("PackagingSim: actual NightConfig parser and both mixin inventories passed");
    }
}
