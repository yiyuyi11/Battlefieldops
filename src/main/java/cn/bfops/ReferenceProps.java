package cn.bfops;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraftforge.client.event.RenderLivingEvent;
import java.util.*;

/** Local renderer for our tagged vanilla carriers; no custom entity type or mandatory client handshake. */
public final class ReferenceProps {
    private static final Map<SupportRules.Kind,ReferenceGeometry.Mesh> CACHE=new EnumMap<>(SupportRules.Kind.class);
    private static final Set<SupportRules.Kind> FAILED=EnumSet.noneOf(SupportRules.Kind.class);
    static void clear(){CACHE.clear();FAILED.clear();BOUNDS.clear();}
    private record Bounds(float x,float y,float z,float span){}
    private static final Map<SupportRules.Kind,Bounds> BOUNDS=new EnumMap<>(SupportRules.Kind.class);
    private static ReferenceGeometry.Mesh mesh(SupportRules.Kind kind){
        if(FAILED.contains(kind))return null;
        var found=CACHE.get(kind);if(found!=null)return found;
        var mc=Minecraft.getInstance();
        try(var reader=mc.getResourceManager().openAsReader(new ResourceLocation("battlefield_ops","geo/"+kind.id+".geo.json"))){
            if(mc.getResourceManager().getResource(new ResourceLocation("battlefield_ops","textures/item/"+kind.id+".png")).isEmpty())return null;
            found=ReferenceGeometry.bake(JsonParser.parseReader(reader).getAsJsonObject());
            if(found.vertices().isEmpty())throw new IllegalArgumentException("Empty support model");
            float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
            for(var v:found.vertices()){minX=Math.min(minX,v.x());minY=Math.min(minY,v.y());minZ=Math.min(minZ,v.z());maxX=Math.max(maxX,v.x());maxY=Math.max(maxY,v.y());maxZ=Math.max(maxZ,v.z());}
            BOUNDS.put(kind,new Bounds((minX+maxX)/2,(minY+maxY)/2,(minZ+maxZ)/2,Math.max(.01f,Math.max(maxY-minY,Math.max(maxX-minX,maxZ-minZ)))));
            CACHE.put(kind,found);return found;
        }catch(Exception ex){FAILED.add(kind);BattlefieldOps.LOG.error("Cannot load reference equipment "+kind.id,ex);return null;}
    }
    public static boolean renderItem(net.minecraft.world.item.ItemStack stack,net.minecraft.world.item.ItemDisplayContext context,
            PoseStack poses,net.minecraft.client.renderer.MultiBufferSource buffers,int light){
        var kind=ConquestEquipment.kind(stack);if(kind==null)return false;
        var mesh=mesh(kind);if(mesh==null)return false;
        var b=BOUNDS.get(kind);poses.pushPose();
        try{
            boolean gui=context==net.minecraft.world.item.ItemDisplayContext.GUI;
            boolean first=context.firstPerson();
            float scale=(gui?.72f:context==net.minecraft.world.item.ItemDisplayContext.GROUND?.42f:.65f)/b.span;
            if(first)poses.translate(.02,-.07,0);
            poses.mulPose(com.mojang.math.Axis.XP.rotationDegrees(gui?25:first?10:0));
            poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(gui?225:first?210:180));
            poses.scale(scale,scale,scale);poses.translate(-b.x,-b.y,-b.z);
            var vertices=buffers.getBuffer(RenderType.entityCutoutNoCull(new ResourceLocation("battlefield_ops","textures/item/"+kind.id+".png")));
            var pose=poses.last();
            emit(mesh, vertices, pose, light);
        }finally{poses.popPose();}
        return true;
    }
    static boolean render(RenderLivingEvent.Pre<?,?> event){
        if(!(event.getEntity() instanceof ArmorStand stand))return false;
        var kind=ConquestEquipment.kind(stand.getItemBySlot(EquipmentSlot.HEAD));if(kind==null)return false;
        var mc=Minecraft.getInstance();var texture=new ResourceLocation("battlefield_ops","textures/item/"+kind.id+".png");
        if(FAILED.contains(kind)||mc.getResourceManager().getResource(texture).isEmpty())return false;
        ReferenceGeometry.Mesh mesh=mesh(kind);if(mesh==null)return false;
        PoseStack poses=event.getPoseStack();poses.pushPose();
        try{
            poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-net.minecraft.util.Mth.rotLerp(event.getPartialTick(),stand.yRotO,stand.getYRot())));
            float scale=switch(kind){case AMMO_BOX,MED_BOX->.88f;case RADIO_BEACON->1.1f;case AMMO_BAG,MEDIC_BAG->2;default->1;};
            poses.translate(0,kind==SupportRules.Kind.GROUND_SENSOR?.04:.05,0);poses.scale(scale,scale,scale);
            var vertices=event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(texture));var pose=poses.last();
            emit(mesh, vertices, pose, event.getPackedLight());
        }finally{poses.popPose();}
        return true;
    }
    private static void emit(ReferenceGeometry.Mesh mesh, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                             PoseStack.Pose pose, int light) {
        for (var vertex : mesh.vertices()) {
            buffer.vertex(pose.pose(), vertex.x(), vertex.y(), vertex.z())
                    .color(255, 255, 255, 255).uv(vertex.u(), vertex.v())
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(pose.normal(), vertex.nx(), vertex.ny(), vertex.nz()).endVertex();
        }
    }
    private ReferenceProps(){}
}
