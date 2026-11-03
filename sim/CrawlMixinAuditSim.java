package cn.bfops;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import com.google.gson.JsonParser;

/** Checks exact installed TaCZ/MC classfiles without initialising Minecraft or applying mixins. */
public final class CrawlMixinAuditSim {
    static Object value(AnnotationNode a,String key){if(a.values!=null)for(int i=0;i<a.values.size();i+=2)if(key.equals(a.values.get(i)))return a.values.get(i+1);return null;}
    static List<AnnotationNode> annotations(List<AnnotationNode> a,List<AnnotationNode> b){var out=new ArrayList<AnnotationNode>();if(a!=null)out.addAll(a);if(b!=null)out.addAll(b);return out;}
    static ClassNode read(ZipFile jar,String name)throws Exception{var entry=jar.getEntry(name+".class");if(entry==null)throw new AssertionError("Missing target "+name);var node=new ClassNode();new ClassReader(jar.getInputStream(entry)).accept(node,ClassReader.SKIP_CODE);return node;}
    public static void main(String[] args)throws Exception{
        Path mods=Path.of("D:/USTBL/.minecraft/versions/Mechanomania-航空学/mods");
        Path tacz;try(var files=Files.list(mods)){tacz=files.filter(p->p.getFileName().toString().startsWith("tacz-neoforge")&&p.toString().endsWith(".jar")).findFirst().orElseThrow();}
        int checks=0;
        try(var pack=new ZipFile("build/libs/battlefield-ops-0.8.10-neoforge-1.21.1.jar");var guns=new ZipFile(tacz.toFile());var mc=new ZipFile("D:/USTBL/.minecraft/libraries/net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar")){
            var config=JsonParser.parseString(new String(pack.getInputStream(pack.getEntry("battlefield_ops.crawl.mixins.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for(String side:List.of("mixins","client"))for(var entry:config.getAsJsonArray(side)){
                String name=config.get("package").getAsString()+"."+entry.getAsString();ClassNode mixin=read(pack,name.replace('.','/'));String target=null;
                for(var a:annotations(mixin.visibleAnnotations,mixin.invisibleAnnotations))if(a.desc.endsWith("/Mixin;")){
                    Object strings=value(a,"targets"),types=value(a,"value");
                    if(strings instanceof List<?> list&&!list.isEmpty())target=list.getFirst().toString().replace('.','/');
                    if(types instanceof List<?> list&&!list.isEmpty())target=((Type)list.getFirst()).getInternalName();
                }
                if(target==null)throw new AssertionError("No target "+name);
                ClassNode actual=read(target.startsWith("com/tacz/")?guns:mc,target);
                for(var field:mixin.fields)for(var a:annotations(field.visibleAnnotations,field.invisibleAnnotations))if(a.desc.endsWith("/Shadow;")){
                    if(actual.fields.stream().noneMatch(f->f.name.equals(field.name)&&f.desc.equals(field.desc)))throw new AssertionError(name+" shadow mismatch "+field.name);checks++;
                }
                for(var method:mixin.methods)for(var a:annotations(method.visibleAnnotations,method.invisibleAnnotations)){
                    Object selectors=value(a,"method");if(!(selectors instanceof List<?> list))continue;
                    for(Object selector:list){String text=selector.toString();int p=text.indexOf('(');String targetName=p<0?text:text.substring(0,p),descriptor=p<0?null:text.substring(p);
                        var candidates=actual.methods.stream().filter(m->m.name.equals(targetName)&&(descriptor==null||m.desc.equals(descriptor))).toList();
                        if(candidates.isEmpty())throw new AssertionError(name+" method mismatch "+text);
                        if(a.desc.endsWith("/Inject;")){
                            Type[] handler=Type.getArgumentTypes(method.desc);
                            boolean match=candidates.stream().anyMatch(m->{Type[] original=Type.getArgumentTypes(m.desc);return handler.length==1||(handler.length==original.length+1&&Arrays.equals(Arrays.copyOf(handler,handler.length-1),original));});
                            if(!match)throw new AssertionError(name+" callback arguments mismatch "+text);
                        }
                        checks++;
                    }
                }
            }
        }
        System.out.println("CrawlMixinAuditSim: "+checks+" installed-class signatures passed (not a runtime injection test)");
    }
}
