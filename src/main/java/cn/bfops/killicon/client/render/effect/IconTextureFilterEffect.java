package cn.bfops.killicon.client.render.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import cn.bfops.killicon.client.config.ClientConfigManager;

/**
 * Applies the configured filtering mode to the icon textures.
 *
 * <p><b>Why the mipmap argument stays {@code false}.</b> It is tempting to pass {@code true} here because the
 * ported icon art ships up to 2048x2048 while the HUD draws it on a 64 px quad, and a 32x reduction with no
 * mip chain visibly crawls while the HUD animates. But passing {@code true} does not create a mip chain by
 * itself, and without one it is actively harmful:
 *
 * <ol>
 *   <li>{@code setFilter(blur, true)} sets the minification filter to
 *       {@code GL_LINEAR_MIPMAP_LINEAR}.</li>
 *   <li>{@code DynamicTexture.upload()} allocates its storage through the three-argument
 *       {@code TextureUtil.prepareImage(id, width, height)}, which passes a mip level count of {@code 0} —
 *       so only level 0 exists.</li>
 *   <li>The sampler is then asked to read mip levels that were never defined, which renders the icon black
 *       or garbage rather than merely aliased.</li>
 * </ol>
 *
 * A real mip chain would need a custom upload path that allocates and fills every level (the four-argument
 * {@code prepareImage} plus a per-level {@code NativeImage.upload}). That is a larger change than filtering, and
 * it has not been verified in game, so this stays at the reference's {@code false} until it can be.
 */
public final class IconTextureFilterEffect {
   private IconTextureFilterEffect() {
   }

   /** Applies the configured filtering mode to an icon texture. */
   public static void apply(ResourceLocation textureLocation) {
      apply(textureLocation, false);
   }

   /**
    * Applies the configured filtering mode.
    *
    * @param mipmap whether the texture already owns a mip chain. Only pass {@code true} for a texture whose
    *               upload allocated every level, otherwise the sampler reads undefined levels — see the class
    *               comment.
    */
   public static void apply(ResourceLocation textureLocation, boolean mipmap) {
      if (textureLocation != null) {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft != null && minecraft.getTextureManager() != null) {
            AbstractTexture texture = minecraft.getTextureManager().getTexture(textureLocation);
            if (texture != null) {
               texture.setFilter(ClientConfigManager.isEnableIconAntialiasing(), mipmap);
            }
         }
      }
   }
}
