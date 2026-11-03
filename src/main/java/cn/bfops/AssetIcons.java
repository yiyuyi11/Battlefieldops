package cn.bfops;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Uses the actual installed gun-pack and vehicle-pack UI artwork, never model UV sheets. */
final class AssetIcons {
    private record Icon(ResourceLocation texture, int w, int h, ItemStack stack) {}
    private static final Map<String,Icon> cache=new LinkedHashMap<>(256,.75f,true);
    // Each entry is at most 256x256 RGBA. Native pixel retention is capped at 4 MiB,
    // with a matching GPU allocation; removed entries are actually released.
    private static final IconVariantCache<Icon> variants=new IconVariantCache<>(64,4L*1024*1024,
            icon->Minecraft.getInstance().getTextureManager().release(icon.texture()));
    private static final Set<String> failedVariants=new LinkedHashSet<>();
    private static boolean whiteIcons;
    static boolean whiteIcons(){return whiteIcons;}
    static void whiteIcons(boolean value){whiteIcons=value;}
    static void clear(){variants.clear();cache.clear();failedVariants.clear();whiteIcons=false;GunPreview.clear();GunSpec.clear();}
    private static Icon transformed(GuiGraphics graphics,Icon source,boolean white){
        if(source.texture()==null)return source;
        String key=(white?"white/":"mono/")+source.texture();
        Icon existing=variants.get(key);if(existing!=null)return existing;
        if(failedVariants.contains(key))return new Icon(null,16,16,ItemStack.EMPTY);
        var mc=Minecraft.getInstance();
        NativeImage nativeImage=null;
        net.minecraft.client.renderer.texture.DynamicTexture dynamic=null;
        boolean registered=false;
        try{
            var resource=mc.getResourceManager().getResource(source.texture());
            if(resource.isEmpty())return source;
            java.awt.image.BufferedImage pixels;
            try(var stream=resource.get().open()){pixels=IconPixels.read(stream);}
            int w=pixels.getWidth(),h=pixels.getHeight();
            nativeImage=new NativeImage(w,h,false);
            try{
                for(int y=0;y<h;y++)for(int x=0;x<w;x++)nativeImage.setPixelRGBA(x,y,IconPixels.rgba(pixels.getRGB(x,y),white));
            }finally{pixels.flush();}
            // Flush queued draws before LRU eviction can delete a texture used earlier this frame.
            graphics.flush();
            dynamic=new net.minecraft.client.renderer.texture.DynamicTexture(nativeImage);
            nativeImage=null; // DynamicTexture now owns the native pixels.
            var texture=mc.getTextureManager().register(white?"bfops_white":"bfops_mono",dynamic);
            Icon icon=new Icon(texture,w,h,ItemStack.EMPTY);
            variants.put(key,icon,(long)w*h*4);
            registered=true;
            return icon;
        }catch(Exception ex){
            failedVariants.add(key);
            while(failedVariants.size()>128)failedVariants.remove(failedVariants.iterator().next());
            BattlefieldOps.LOG.debug("Icon conversion unavailable: {}",source.texture(),ex);
            return new Icon(null,16,16,ItemStack.EMPTY);
        }finally{
            if(nativeImage!=null)nativeImage.close();
            if(dynamic!=null&&!registered)dynamic.close();
        }
    }
    private static ResourceLocation superbIcon(String id){
        try{var key=ResourceLocation.tryParse(id);if(key==null)return null;var item=BuiltInRegistries.ITEM.get(key);
            return (ResourceLocation)item.getClass().getMethod("getGunIcon",ItemStack.class).invoke(item,new ItemStack(item));
        }catch(ReflectiveOperationException|RuntimeException|LinkageError e){return null;}
    }
    private static Icon resolve(String kind,String id) {
        if(kind.equals("mono")){
            Icon item=resolve("item",id);
            if(item.texture()!=null||item.stack().isEmpty())return item;
            try{
                var contents=Minecraft.getInstance().getItemRenderer().getModel(item.stack(),Minecraft.getInstance().level,null,0).getParticleIcon().contents();
                var name=contents.name();
                var texture=ResourceLocation.fromNamespaceAndPath(name.getNamespace(),"textures/"+name.getPath()+".png");
                if(Minecraft.getInstance().getResourceManager().getResource(texture).isPresent())return new Icon(texture,contents.width(),contents.height(),ItemStack.EMPTY);
            }catch(RuntimeException|LinkageError ex){BattlefieldOps.LOG.debug("Item thumbnail unavailable: {}",id,ex);}
            return item;
        }
        var mc=Minecraft.getInstance();
        ResourceLocation texture=null;
        ItemStack stack=ItemStack.EMPTY;
        try {
            if(LrtEquipment.explicit(id)) {
                stack=LrtEquipment.create(id,1);
                String category=id.substring(id.indexOf(':')+1).split("/",2)[0];
                String method=switch(category){case "melee"->"getMeleeDisplay";case "throwable"->"getThrowableDisplay";case "consumable"->"getConsumableDisplay";default->"";};
                if(!stack.isEmpty()&&!method.isEmpty()){
                    Object display=((Optional<?>)Class.forName("me.xjqsh.lrtactical.api.LrTacticalAPI").getMethod(method,ItemStack.class).invoke(null,stack)).orElse(null);
                    if(display!=null)texture=(ResourceLocation)display.getClass().getMethod("getSlotTexture").invoke(display);
                }
            } else if(kind.equals("bf1")) {
                texture=ResourceLocation.fromNamespaceAndPath("battlefield_ops","textures/gui/bf1/"+id+".png");
            } else if(kind.equals("attachment")) {
                Class<?> api=Class.forName("com.tacz.guns.api.TimelessAPI");
                var index=((Optional<?>)api.getMethod("getClientAttachmentIndex",ResourceLocation.class)
                        .invoke(null,ResourceLocation.parse(id)));
                if(index.isPresent())texture=(ResourceLocation)index.get().getClass().getMethod("getSlotTexture").invoke(index.get());
            } else if(kind.equals("role")) {
                String role=FixedClasses.stem(id);
                if(role!=null)texture=ResourceLocation.fromNamespaceAndPath("battlefield_ops","textures/gui/class_"+role+".png");
            } else if(kind.equals("elite")) {
                if(EliteRules.Kind.of(id)!=null)texture=ResourceLocation.fromNamespaceAndPath("battlefield_ops","textures/gui/support/herokit_"+id+".png");
            } else if(kind.equals("gunsil")) {
                // Side-view silhouettes, wherever the loader keeps them: TaCZ uses textures/gun/hud,
                // Superb Warfare uses textures/gun_icon/<id>_icon.png.
                ResourceLocation parsed=ResourceLocation.tryParse(id);
                if(parsed!=null){
                    String namespace=parsed.getNamespace(),path=parsed.getPath();
                    for(String candidate:new String[]{
                            "textures/gun/hud/"+path+".png",
                            "textures/gun/hud/"+path+"_hud.png",
                            "textures/gun_icon/"+path+"_icon.png",
                            "textures/gun_icon/"+path+".png"}){
                        ResourceLocation loc=ResourceLocation.fromNamespaceAndPath(namespace,candidate);
                        if(mc.getResourceManager().getResource(loc).isPresent()){texture=loc;break;}
                    }
                    if(texture==null){
                        // Ask the loader for the slot icon, then take its sibling HUD sheet: addon packs often
                        // store the textures under their own namespace, not the one in the gun id.
                        Icon slot=resolve("gun",id);
                        String slotPath=slot.texture()==null?"":slot.texture().getPath();
                        if(slotPath.contains("textures/gun/slot/")){
                            ResourceLocation hud=ResourceLocation.fromNamespaceAndPath(slot.texture().getNamespace(),
                                    slotPath.replace("textures/gun/slot/","textures/gun/hud/"));
                            if(mc.getResourceManager().getResource(hud).isPresent())texture=hud;
                        }
                    }
                }
            } else if(kind.equals("gunhud")) {
                // textures/gun/hud/<path>.png inside the gun's own pack; the wide side-view silhouette.
                ResourceLocation parsed=ResourceLocation.tryParse(id);
                if(parsed!=null){
                    ResourceLocation hud=ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(),
                            "textures/gun/hud/"+parsed.getPath()+".png");
                    if(mc.getResourceManager().getResource(hud).isPresent())texture=hud;
                }
            } else if(kind.equals("gun") && ResourceLocation.tryParse(id)!=null && BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)) !=null && superbIcon(id)!=null) {
                texture=superbIcon(id);
            } else if(kind.equals("gun")) {
                var api=Class.forName("com.tacz.guns.api.TimelessAPI");
                Object index=((Optional<?>)api.getMethod("getClientGunIndex",ResourceLocation.class)
                        .invoke(null,ResourceLocation.parse(id))).orElse(null);
                if(index!=null) {
                    Object display=index.getClass().getMethod("getDefaultDisplay").invoke(index);
                    texture=(ResourceLocation)display.getClass().getMethod("getSlotTexture").invoke(display);
                }
                if(texture==null && mc.level!=null) {
                    Class<?> builder=Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
                    Object instance=builder.getMethod("create").invoke(null);
                    builder.getMethod("setId",ResourceLocation.class).invoke(instance,ResourceLocation.parse(id));
                    stack=(ItemStack)builder.getMethod("build",net.minecraft.core.HolderLookup.Provider.class).invoke(instance,mc.level.registryAccess());
                }
            } else if(kind.equals("vehicle")) {
                Class<?> data=Class.forName("com.atsuishio.superbwarfare.data.vehicle.VehicleData");
                Object defaults=data.getMethod("getDefault",String.class).invoke(null,id);
                if(defaults!=null) texture=(ResourceLocation)defaults.getClass().getMethod("getVehicleIcon").invoke(defaults);
            }
        } catch(ReflectiveOperationException | LinkageError | RuntimeException ex) {
            BattlefieldOps.LOG.debug("No preview for {}",id,ex);
        }
        // A pack that renamed the HUD sheet still gets a usable glyph from its slot icon.
        if(texture==null&&kind.equals("gunhud"))return resolve("gun",id);
        if(texture==null&&kind.equals("gunsil")){
            Icon slot=resolve("gun",id);
            if(slot.texture!=null)return slot;
            return resolve("mono",id);
        }
        if(texture!=null) {
            int w=kind.equals("gun")?128:64,h=64;
            try {
                var resource=mc.getResourceManager().getResource(texture);
                if(resource.isPresent()) try(var stream=resource.get().open()) {
                    byte[] header=stream.readNBytes(24);
                    if(header.length==24&&header[0]==(byte)137&&header[1]==80&&header[2]==78&&header[3]==71){
                        var dimensions=java.nio.ByteBuffer.wrap(header);w=dimensions.getInt(16);h=dimensions.getInt(20);
                        if(w>0&&h>0)return new Icon(texture,w,h,ItemStack.EMPTY);
                    }
                }
            } catch(Exception ex) { BattlefieldOps.LOG.debug("Preview dimensions unavailable: {}",texture); }
        }
        if(kind.equals("attachment")){try{stack=Fittings.attachment(id);}catch(ReflectiveOperationException ex){BattlefieldOps.LOG.debug("Attachment icon unavailable",ex);}}
        if(stack.isEmpty()&&ConquestEquipment.kind(id)!=null)stack=ConquestEquipment.stack(id,1);
        if(stack.isEmpty() && !id.isEmpty()){
            var itemId=ResourceLocation.tryParse(id);
            if(itemId!=null)stack=new ItemStack(BuiltInRegistries.ITEM.get(itemId));
        }
        return new Icon(null,16,16,stack);
    }
    /**
     * Blits the supply marker icon for a supply kind, centred on {@code (cx, cy)}.
     *
     * <p>The 256×256 marker textures were unpacked from the reference pack into this mod's own assets, so
     * the overlay is self-contained; the {@code false} return (and the caller's shape fallback) is kept
     * only for the unlikely case the resource fails to load. {@code size} is the marker half-size.
     */
    static boolean supply(GuiGraphics g, String kind, int cx, int cy, int size) {
        String icon=switch(kind){
            case "AMMO" -> "pickups_ammobag_default_default";
            case "MEDICAL" -> "pickups_medbag_default_default";
            default -> null;
        };
        if(icon==null)return false;
        var mc=Minecraft.getInstance();
        var tex=ResourceLocation.fromNamespaceAndPath("battlefield_ops","textures/gui/bf1/"+icon+".png");
        if(mc==null||mc.getResourceManager().getResource(tex).isEmpty())return false;
        int d=Math.max(1,size*2);Bf1Ui.icon(g,icon,cx-size,cy-size,d);
        return true;
    }


    /** True when the gun loaders know this id; asks the resolver, so it works for any addon namespace. */
    static boolean isGun(String id) {
        if (id == null || id.isEmpty()) return false;
        Icon icon = cache.computeIfAbsent("gun/" + id, key -> resolve("gun", id));
        return icon.texture != null;
    }

    /** Side-view kind for a gun id, or "mono" for anything that is not a gun from one of the gun loaders. */
    static String silhouetteKind(String id) {
        if (id == null || id.isEmpty()) return "mono";
        return isGun(id) ? "gunsil" : "mono";
    }
    static void draw(GuiGraphics g,String kind,String id,int x,int y,int w,int h) {
        if(id.isEmpty()) return;
        if(!kind.equals("mono")&&!kind.equals("bf1")&&!kind.equals("gun")&&!kind.equals("gunhud")&&!kind.equals("gunsil")&&!kind.equals("vehicle")&&!kind.equals("role")&&!kind.equals("elite")&&!kind.equals("attachment")&&!kind.equals("item")) {
            g.drawCenteredString(Minecraft.getInstance().font,"◆",x+w/2,y+h/2-4,0x99b3a0);
            return;
        }
        while(cache.size()>512)cache.remove(cache.keySet().iterator().next());
        // One look for guns everywhere: in a BF1-styled screen a "gun" icon is drawn with the same
        // side-view silhouette the deploy bar uses, at whatever size the caller asked for.
        if(kind.equals("gun")&&whiteIcons){
            Icon silhouette=cache.computeIfAbsent("gunsil/"+id,k->resolve("gunsil",id));
            if(silhouette.texture!=null)kind="gunsil";
        }
        final String iconKind=kind;
        Icon icon=cache.computeIfAbsent(iconKind+"/"+id,k->resolve(iconKind,id));
        if(icon.texture!=null&&(whiteIcons||kind.equals("mono")))icon=transformed(g,icon,whiteIcons&&!kind.equals("mono"));
        if(icon.texture!=null) {
            if(kind.equals("role")){
                // Class art is 24x24 line drawing: draw it at an integer divisor of 24 so no stroke is
                // split across two screen pixels. That is the whole difference between "crisp" and "mush".
                int target=Math.max(4,Math.min(w,h));
                int draw = target>=24?24 : target>=12?12 : target>=8?8 : 6;
                int dx=x+(w-draw)/2, dy=y+(h-draw)/2;
                g.blit(icon.texture,dx,dy,draw,draw,0,0,icon.w,icon.h,icon.w,icon.h);
                return;
            }
            double fit=Math.min(w/(double)icon.w,h/(double)icon.h);
            int dw=Math.max(1,(int)(icon.w*fit)),dh=Math.max(1,(int)(icon.h*fit));
            g.blit(icon.texture,x+(w-dw)/2,y+(h-dh)/2,dw,dh,0,0,icon.w,icon.h,icon.w,icon.h);
        } else if(!icon.stack.isEmpty()) {
            float scale=Math.max(0.5f,Math.min(w,h)/16f);
            g.pose().pushPose();
            g.pose().translate(x+(w-16*scale)/2,y+(h-16*scale)/2,0);
            g.pose().scale(scale,scale,1);
            g.renderItem(icon.stack,0,0);
            g.pose().popPose();
        } else {
            // No texture and no item: this is a gun whose pack failed to load, or an id from a pack that is
            // no longer installed. A neutral BF1 rifle glyph keeps the slot readable instead of printing
            // "暂无图标" over a weapon slot.
            Icon fallback=cache.computeIfAbsent("bf1/kit_rifleman_hud_default",k->resolve("bf1","kit_rifleman_hud_default"));
            if(fallback.texture!=null){
                double fit=Math.min(w/(double)fallback.w,h/(double)fallback.h);
                int dw=Math.max(1,(int)(fallback.w*fit)),dh=Math.max(1,(int)(fallback.h*fit));
                g.blit(fallback.texture,x+(w-dw)/2,y+(h-dh)/2,dw,dh,0,0,fallback.w,fallback.h,fallback.w,fallback.h);
            }else{
                var font=Minecraft.getInstance().font;
                g.drawCenteredString(font,w>=60?"暂无图标":"—",x+w/2,y+h/2-4,0x73867c);
            }
        }
    }
}
