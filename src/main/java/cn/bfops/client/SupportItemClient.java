package cn.bfops.client;

import cn.bfops.SupportItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

/**
 * Client-side wiring for the support items: points every one of them at {@link SupportItemRenderer}.
 *
 * <p>Kept in its own class and only touched from a dist check, so a dedicated server never loads any of it.
 */
public final class SupportItemClient {
    private static final SupportGeoRenderer RENDERER = new SupportGeoRenderer();

    public static void register(IEventBus modBus) {
        modBus.addListener(SupportItemClient::registerExtensions);
    }

    private static void registerExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions extensions = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return RENDERER;
            }
        };
        SupportItems.all().forEach(item -> event.registerItem(extensions, item));
    }

    private SupportItemClient() {
    }
}
