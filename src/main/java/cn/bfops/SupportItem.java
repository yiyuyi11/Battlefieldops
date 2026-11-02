package cn.bfops;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A registered 656 support item, drawn with the pack's own GeckoLib geometry.
 *
 * <p>The reference mod defines these as {@code GeoItem}s driven by {@code geo/<id>.geo.json},
 * {@code textures/item/<id>.png} and one shared {@code animations/static_item.animation.json}; this mirrors
 * that exactly, so the items look like the original rather than like an extruded sprite.
 */
public class SupportItem extends Item implements GeoItem {
    private final SupportRules.Kind kind;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SupportItem(SupportRules.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public SupportRules.Kind kind() {
        return kind;
    }

    /** The registry path, which is also the geo, texture and icon file name. */
    public String itemId() {
        return kind.id;
    }

    public ResourceLocation geoModelResource() {
        return ResourceLocation.fromNamespaceAndPath("battlefield_ops", "geo/" + itemId() + ".geo.json");
    }

    public ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath("battlefield_ops", "textures/item/" + itemId() + ".png");
    }

    public ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath("battlefield_ops", "animations/static_item.animation.json");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Static props: no animation controllers, matching the reference mod.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
