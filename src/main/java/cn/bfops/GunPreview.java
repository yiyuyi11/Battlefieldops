package cn.bfops;

import java.util.*;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Display-only gun stacks, assembled once per (weapon, attachment set).
 *
 * <p>Building the stack is cheap, but installing an attachment goes through TaCZ's reflective API and
 * validating the set enumerates every attachment in the pack — doing that per frame is what made the
 * bench and the statistics preview feel heavy. The assembled stack is read-only for rendering, so it is
 * cached and handed out as is.
 */
final class GunPreview {
    private static final Map<String, ItemStack> CACHE = new LinkedHashMap<>(64, .75f, true);
    private static Method builderCreate, builderSetId, builderBuild;

    private GunPreview() {}

    /** The bare gun stack for {@code id}, or an empty stack when the pack cannot build it. */
    static ItemStack stack(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        return assembled(id, Map.of());
    }

    /** The gun with {@code choices} installed, or the bare gun when a part cannot be applied. */
    static ItemStack assembled(String id, Map<String, String> choices) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        String key = id + "|" + new TreeMap<>(choices == null ? Map.of() : choices);
        ItemStack cached = CACHE.get(key);
        if (cached != null) return cached;
        ItemStack stack = build(id);
        if (!stack.isEmpty() && choices != null && !choices.isEmpty()) install(stack, choices);
        CACHE.put(key, stack);
        while (CACHE.size() > 64) CACHE.remove(CACHE.keySet().iterator().next());
        return stack;
    }

    static void clear() {
        CACHE.clear();
    }


    /**
     * Draws the gun model into a GUI box, centred, with the pack's own renderer.
     *
     * <p>Every caller that used to blit a 16-pixel slot texture instead can now show the model; the
     * texture remains the fallback when the pack declines to build the stack. Returns false when the
     * caller should draw its own art.
     */
    static boolean render(net.minecraft.client.gui.GuiGraphics g, String id, java.util.Map<String, String> choices,
                          int x, int y, int w, int h, float yaw, float pitch) {
        ItemStack stack = assembled(id, choices);
        if (stack.isEmpty()) return false;
        float modelScale = Math.min(w, h) * 1.35f;
        GuiClip.push(g,x,y,w,h);
        g.pose().pushPose();
        try {
            g.pose().translate(x + w / 2f, y + h / 2f, 220);
            g.pose().scale(modelScale / 16f, -modelScale / 16f, modelScale / 16f);
            g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
            g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            Minecraft.getInstance().getItemRenderer().renderStatic(stack,
                    net.minecraft.world.item.ItemDisplayContext.FIXED, 0xF000F0,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, g.pose(), buffers,
                    Minecraft.getInstance().level, 0);
            buffers.endBatch();
            return true;
        } catch (RuntimeException | LinkageError ex) {
            BattlefieldOps.LOG.debug("Gun model render unavailable: {}", id, ex);
            return false;
        } finally {
            g.pose().popPose();
            GuiClip.pop(g);
        }
    }
    private static ItemStack build(String id) {
        try {
            HolderLookup.Provider registry = registry();
            if (registry == null) return ItemStack.EMPTY;
            if (builderCreate == null) {
                Class<?> builder = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
                builderCreate = builder.getMethod("create");
                builderSetId = builder.getMethod("setId", ResourceLocation.class);
                builderBuild = builder.getMethod("build", HolderLookup.Provider.class);
            }
            Object value = builderCreate.invoke(null);
            builderSetId.invoke(value, ResourceLocation.parse(id));
            return (ItemStack) builderBuild.invoke(value, registry);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            BattlefieldOps.LOG.debug("Gun preview unavailable: {}", id, ex);
            return ItemStack.EMPTY;
        }
    }

    /**
     * Installs the chosen attachments without the full compatibility sweep.
     *
     * <p>{@link Fittings#applyGun} validates the whole set first, which is the right thing on the
     * confirm path and the wrong thing once per frame; here an incompatible part is simply skipped and
     * the preview shows the rest.
     */
    private static void install(ItemStack stack, Map<String, String> choices) {
        try {
            Class<?> igun = Class.forName("com.tacz.guns.api.item.IGun");
            Object item = igun.getMethod("getIGunOrNull", ItemStack.class).invoke(null, stack);
            if (item == null) return;
            Method allow = igun.getMethod("allowAttachment", ItemStack.class, ItemStack.class);
            Method install = igun.getMethod("installAttachment", HolderLookup.Provider.class,
                    ItemStack.class, ItemStack.class);
            HolderLookup.Provider registry = registry();
            if (registry == null) return;
            for (String attachment : choices.values()) {
                if (attachment == null || attachment.isEmpty()) continue;
                ItemStack part = Fittings.attachment(attachment);
                if ((boolean) allow.invoke(item, stack, part)) install.invoke(item, registry, stack, part);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            BattlefieldOps.LOG.debug("Gun preview attachment skipped", ex);
        }
    }

    private static HolderLookup.Provider registry() {
        var mc = Minecraft.getInstance();
        if (mc.level != null) return mc.level.registryAccess();
        return mc.player != null ? mc.player.registryAccess() : null;
    }
}
