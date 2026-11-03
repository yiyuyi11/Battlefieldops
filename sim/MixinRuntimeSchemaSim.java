package cn.bfops;

import java.util.*;
import java.util.zip.ZipFile;
import org.objectweb.asm.tree.*;
import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.wrapper.factory.FactoryRedirectWrapperMixinTransformer;

/** Real runtime extension prepass, not a complete Minecraft Mixin application test. */
public final class MixinRuntimeSchemaSim {
    public static void main(String[] args)throws Exception {
        var transformer=new FactoryRedirectWrapperMixinTransformer();
        int checks=0; boolean regression=false;
        try(var jar=new ZipFile("build/libs/battlefield-ops-0.8.10-neoforge-1.21.1.jar")) {
            for(String file:List.of("battlefield_ops.mixins.json","battlefield_ops.crawl.mixins.json")) {
                var config=JsonParser.parseString(new String(jar.getInputStream(jar.getEntry(file)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for(String side:List.of("mixins","client")) {
                    if(!config.has(side))continue;
                    for(var entry:config.getAsJsonArray(side)) {
                        String name=config.get("package").getAsString()+"."+entry.getAsString();
                        ClassNode node=CrawlMixinAuditSim.read(jar,name.replace('.','/'));
                        for(var method:node.methods)for(var annotation:CrawlMixinAuditSim.annotations(method.visibleAnnotations,method.invisibleAnnotations)) {
                            if(!annotation.desc.endsWith("/Redirect;")&&!annotation.desc.endsWith("/ModifyVariable;"))continue;
                            Object at=CrawlMixinAuditSim.value(annotation,"at");
                            if(!(at instanceof AnnotationNode))throw new AssertionError(name+": runtime requires single @At, got "+at);
                            checks++;
                        }
                        transformer.transform(null,node);
                        if(name.endsWith("TaczMouseHandlerMixin")) {
                            // Recreate 0.17.4's annotation array encoding: the shipped runtime must reject it.
                            for(var method:node.methods)for(var annotation:CrawlMixinAuditSim.annotations(method.visibleAnnotations,method.invisibleAnnotations)) {
                                if(!annotation.desc.endsWith("/Redirect;"))continue;
                                for(int i=0;i<annotation.values.size();i+=2)if("at".equals(annotation.values.get(i))) {
                                    annotation.values.set(i+1,new ArrayList<>(List.of(annotation.values.get(i+1))));
                                }
                            }
                            try {transformer.transform(null,node);throw new AssertionError("Crash regression was not reproduced");}
                            catch(ClassCastException expected) {regression=true;}
                        }
                    }
                }
            }
        }
        if(checks<2||!regression)throw new AssertionError("Missing annotation regression coverage");
        System.out.println("MixinRuntimeSchemaSim: "+checks+" schemas and runtime 0.5.3 prepass passed; old array crash reproduced");
    }
}
