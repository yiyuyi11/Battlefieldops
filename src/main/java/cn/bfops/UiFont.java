package cn.bfops;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The UI font hook.
 *
 * <p>The custom bitmap font was rendering as missing-glyph boxes on some clients, so it is disabled: text
 * falls back to the vanilla font, which always renders. The resource and the style plumbing stay in place
 * so the font can be re-enabled once the sheet is verified in-game.
 */
final class UiFont {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("battlefield_ops", "ui");

    /** Wraps plain text so it can render in the UI font. Currently a plain vanilla-font literal. */
    static Component text(String value) {
        return Component.literal(value == null ? "" : value);
    }

    private UiFont() { }
}
