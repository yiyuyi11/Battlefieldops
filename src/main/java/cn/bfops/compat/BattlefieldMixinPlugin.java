package cn.bfops.compat;
import java.util.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
/** Guard optional hooks by inspecting bytecode without initializing client dependencies. */
public final class BattlefieldMixinPlugin implements IMixinConfigPlugin {
    private static Boolean xaero;
    public static boolean xaeroCompatible(){
        if(xaero!=null)return xaero;xaero=false;
        try{
            var over=read("xaero.hud.minimap.element.render.over.MinimapElementOverMapRendererHandler");if(over==null)return false;
            Map<String,String> fields=new HashMap<>();for(var f:over.fields)fields.put(f.name,f.desc);
            for(String f:List.of("ps","pc","zoom"))if(!"D".equals(fields.get(f)))return false;
            for(String f:List.of("halfViewW","halfViewH"))if(!"I".equals(fields.get(f)))return false;
            if(!"Z".equals(fields.get("circle"))||!"F".equals(fields.get("optionalScale")))return false;
            if(over.methods.stream().noneMatch(m->m.name.equals("afterRender")&&m.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;Lxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V")))return false;
            var handler=read("xaero.hud.minimap.element.render.MinimapElementRendererHandler");var processor=read("xaero.common.minimap.MinimapProcessor");
            if(handler==null||handler.methods.stream().noneMatch(m->m.name.equals("renderForRenderer")&&m.desc.endsWith(")I"))||processor==null||processor.methods.stream().noneMatch(m->m.name.equals("onRender")))return false;
            xaero=true;
        }catch(Exception|LinkageError ignored){}return xaero;
    }
    private static ClassNode read(String name)throws java.io.IOException{
        try(var stream=BattlefieldMixinPlugin.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")){
            if(stream==null)return null;var node=new ClassNode();new ClassReader(stream).accept(node,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return node;
        }
    }
    public static boolean worldMapCompatible(){
        try{var node=read("xaero.map.gui.GuiMap");if(node==null)return false;
            var fields=new HashMap<String,String>();for(var f:node.fields)fields.put(f.name,f.desc);
            for(String name:List.of("cameraX","cameraZ","scale","screenScale"))if(!"D".equals(fields.get(name)))return false;
            return fields.containsKey("lastViewedDimensionId")&&node.methods.stream().anyMatch(m->m.name.equals("render")&&m.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"));
        }catch(Exception|LinkageError e){return false;}
    }
    public void onLoad(String mixinPackage){}
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){return mixin.endsWith("XaeroWorldMapMixin")?worldMapCompatible():!mixin.substring(mixin.lastIndexOf('.')+1).startsWith("Xaero")||xaeroCompatible();}
    public void acceptTargets(Set<String> own,Set<String> others){}
    public List<String> getMixins(){return null;}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
