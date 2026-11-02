package cn.bfops.client;

import cn.bfops.SupportItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Delegates the three resources to the item, exactly like the reference mod's item model. */
public class SupportItemModel extends GeoModel<SupportItem> {
    @Override
    public ResourceLocation getModelResource(SupportItem item) {
        return item.geoModelResource();
    }

    @Override
    public ResourceLocation getTextureResource(SupportItem item) {
        return item.textureResource();
    }

    @Override
    public ResourceLocation getAnimationResource(SupportItem item) {
        return item.animationResource();
    }
}
