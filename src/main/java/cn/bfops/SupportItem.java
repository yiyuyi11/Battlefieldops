package cn.bfops;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
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

    /**
     * 1.20.1 attaches client extensions by asking the item, not through NeoForge's registration event.
     *
     * <p>The extension itself is a client class, so it is only named after a dist check: on a dedicated
     * server this branch is never taken and the client package is never resolved.
     */
    @Override
    public void initializeClient(
            java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            cn.bfops.client.SupportItemClient.extend(consumer);
        }
    }

    public SupportRules.Kind kind() {
        return kind;
    }

    /** The registry path, which is also the geo, texture and icon file name. */
    public String itemId() {
        return kind.id;
    }

    public ResourceLocation geoModelResource() {
        return new ResourceLocation("battlefield_ops", "geo/" + itemId() + ".geo.json");
    }

    public ResourceLocation textureResource() {
        return new ResourceLocation("battlefield_ops", "textures/item/" + itemId() + ".png");
    }

    public ResourceLocation animationResource() {
        return new ResourceLocation("battlefield_ops", "animations/static_item.animation.json");
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
