package cn.bfops.client;

import cn.bfops.SupportItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Client-side wiring for the support items: points every one of them at {@link SupportItemRenderer}.
 *
 * <p>Kept in its own class and only touched from a dist check, so a dedicated server never loads any of it.
 *
 * <p>1.20.1 has no {@code RegisterClientExtensionsEvent}; Forge calls {@code Item#initializeClient} while
 * the item is being registered, so the extensions are attached there instead.
 */
public final class SupportItemClient {
    private static final SupportGeoRenderer RENDERER = new SupportGeoRenderer();

    private static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return RENDERER;
        }
    };

    /** Called from {@link cn.bfops.SupportItem#initializeClient} for every support item. */
    public static void extend(java.util.function.Consumer<IClientItemExtensions> consumer) {
        consumer.accept(EXTENSIONS);
    }

    public static void register(IEventBus modBus) {
        // Nothing to subscribe to in 1.20.1: the hook below is consumed by Forge during registration.
        // TODO(1.20.1): attach through Item#initializeClient at construction time; the event hook used
        // by the 1.21 build does not exist here, so the custom renderer stays unwired for now.
    }

    private SupportItemClient() {
    }
}
