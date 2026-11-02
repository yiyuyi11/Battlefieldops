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
    private static final Map<String,Icon> cache=new LinkedHashMap<>();
    static void clear(){cache.clear();}
    private static Icon resolve(String kind,String id) {
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
            } else if(kind.equals("role")) {
                String role=FixedClasses.stem(id);
                if(role!=null)texture=ResourceLocation.fromNamespaceAndPath("battlefield_ops","textures/gui/class_"+role+".png");
            } else if(kind.equals("elite")) {
                if(EliteRules.Kind.of(id)!=null)texture=ResourceLocation.fromNamespaceAndPath("battlefield_ops","textures/gui/support/herokit_"+id+".png");
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
        if(texture!=null) {
            int w=kind.equals("gun")?128:64,h=64;
            try {
                var resource=mc.getResourceManager().getResource(texture);
                if(resource.isPresent()) try(var stream=resource.get().open();var image=NativeImage.read(stream)) {
                    w=image.getWidth();h=image.getHeight();
                    return new Icon(texture,w,h,ItemStack.EMPTY);
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
        ResourceLocation tex = switch (kind) {
            case "AMMO" -> ResourceLocation.fromNamespaceAndPath("battlefield_ops", "textures/gui/marker_ammo_box.png");
            case "MEDICAL" -> ResourceLocation.fromNamespaceAndPath("battlefield_ops", "textures/gui/marker_med_box.png");
            default -> null;
        };
        if (tex == null) return false;
        var mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager().getResource(tex).isEmpty()) return false;
        int d = Math.max(1, size * 2);
        g.blit(tex, cx - size, cy - size, d, d, 0, 0, 256, 256, 256, 256);
        return true;
    }

    static void draw(GuiGraphics g,String kind,String id,int x,int y,int w,int h) {
        if(id.isEmpty()) return;
        if(!kind.equals("gun")&&!kind.equals("vehicle")&&!kind.equals("role")&&!kind.equals("elite")&&!kind.equals("attachment")&&!kind.equals("item")) {
            g.drawCenteredString(Minecraft.getInstance().font,"◆",x+w/2,y+h/2-4,0x99b3a0);
            return;
        }
        if(cache.size()>512) cache.clear();
        Icon icon=cache.computeIfAbsent(kind+"/"+id,k->resolve(kind,id));
        if(icon.texture!=null && Minecraft.getInstance().getResourceManager().getResource(icon.texture).isPresent()) {
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
            var font=Minecraft.getInstance().font;
            g.drawCenteredString(font,w>=60?"暂无图标":"—",x+w/2,y+h/2-4,0x73867c);
        }
    }
}
