package cn.bfops;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item registration for the six 656 support items.
 *
 * <p>The ids match {@link SupportRules.Kind#id}, so an item id and a kit entry are the same string:
 * {@code battlefield_ops:medic_bag} is both the registry name and what the editor stores.
 */
public final class SupportItems {
    static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(Registries.ITEM,"battlefield_ops");
    private static final Map<SupportRules.Kind, RegistryObject<SupportItem>> BY_KIND = new LinkedHashMap<>();

    /**
     * The deployment terminal handed out on a firing range.
     *
     * <p>Its own item rather than a vanilla one: on a server, a plugin or another mod can claim right-click on
     * common items (a compass in particular), and a registered item with a unique id cannot be intercepted.
     */
    static final RegistryObject<Item> DEPLOY_TERMINAL = REGISTRY.register("deploy_terminal", () -> new Item(new Item.Properties().stacksTo(1)));

    static void registerAll() {
        if (!BY_KIND.isEmpty()) return;
        for (SupportRules.Kind kind : SupportRules.Kind.values()) {
            // Every support item stacks to 64 now: an operator who configures "3 med boxes" should get one
            // stack, not three inventory cells.
            BY_KIND.put(kind, REGISTRY.register(kind.id, () -> new SupportItem(kind, new Item.Properties().stacksTo(64))));
        }
    }

    /** True when the stack is the deployment terminal. Null-safe: registration may not have run yet. */
    static boolean isDeployTerminal(net.minecraft.world.item.ItemStack stack) {
        return stack != null && DEPLOY_TERMINAL.get() != null && stack.is(DEPLOY_TERMINAL.get());
    }


    /** True when the registry id names one of the six support items, independent of item components. */
    public static boolean isSupportId(String id) {
        if (id == null || id.isEmpty()) return false;
        var key = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (key == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)) return false;
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key) instanceof SupportItem;
    }

    /** Every registered support item, in declaration order. */
    public static java.util.List<SupportItem> all() {
        java.util.List<SupportItem> out = new java.util.ArrayList<>();
        for (RegistryObject<SupportItem> holder : BY_KIND.values()) {
            SupportItem item = holder.get();
            if (item != null) out.add(item);
        }
        return out;
    }

    /** The registered item for a kind, or {@code null} before registration has run. */
    static SupportItem item(SupportRules.Kind kind) {
        RegistryObject<SupportItem> holder = BY_KIND.get(kind);
        return holder == null ? null : holder.get();
    }

    private SupportItems() {
    }
}
