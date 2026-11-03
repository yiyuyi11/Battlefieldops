package cn.bfops;

import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The BF1 deploy-screen weapon strip: both guns side by side as white side-view silhouettes, with the
 * magazine size on the left and the fitted attachment in a slot on the right.
 *
 * <p>The silhouette is the gun pack's own slot texture, not its 3D model. That is deliberate and matches
 * the reference: on the deploy screen the gun is an inventory glyph that tells you which weapon you are
 * about to spawn with, and a rotating model there would fight the map behind it. The white tint comes from
 * {@link AssetIcons#whiteIcons(boolean)}, which the deploy screen already turns on.
 */
final class DeployWeaponStrip {
    private static final int ROW = 34;

    private DeployWeaponStrip() {}

    static void draw(GuiGraphics g, Font font, Layout.Rect area, String primary, Map<String, String> primaryFitting,
                     String secondary, Map<String, String> secondaryFitting) {
        row(g, font, area.x(), area.y(), area.w(), primary, primaryFitting);
        row(g, font, area.x(), area.y() + ROW + 2, area.w(), secondary, secondaryFitting);
    }

    private static void row(GuiGraphics g, Font font, int x, int y, int w, String gun,
                            Map<String, String> fitting) {
        if (gun == null || gun.isEmpty()) return;
        g.fill(x, y, x + w, y + ROW, 0x33000000);
        g.fill(x, y, x + w, y + 1, 0x28ffffff);

        GunSpec.Spec spec = GunSpec.of(gun, fitting == null ? Map.of() : fitting);
        if (spec.magazine() > 0) {
            String ammo = String.valueOf(spec.magazine());
            int width = font.width(ammo);
            float scale = 1.5f;
            g.pose().pushPose();
            try {
                g.pose().scale(scale, scale, 1);
                g.drawString(font, ammo, (int) ((x + 60 - width * scale / 2f) / scale), (int) ((y + 7) / scale),
                        0xfff2f5f7, false);
            } finally {
                g.pose().popPose();
            }
        }

        // Slot on the right: the first fitted attachment, framed like the reference's selected slot.
        int slot = ROW - 4;
        int slotX = x + w - slot - 2, slotY = y + 2;
        g.fill(slotX, slotY, slotX + slot, slotY + slot, 0x66000000);
        g.fill(slotX, slotY, slotX + slot, slotY + 1, 0x99ffffff);
        String attachment = firstAttachment(fitting);
        if (!attachment.isEmpty()) {
            AssetIcons.draw(g, "attachment", attachment, slotX + 2, slotY + 2, slot - 4, slot - 4);
        }

        // Gun glyph fills whatever is left between the number and the slot.
        // The HUD sheet is 3:1; the box is not. AssetIcons fits by the smaller ratio and centres, so the
        // silhouette keeps its proportions instead of being squeezed into the row.
        int artX = x + 64, artW = slotX - artX - 6, artY = y + 2, artH = ROW - 14;
        if (artW > 8 && artH > 4) AssetIcons.draw(g, "gunhud", gun, artX, artY, artW, artH);

        // Name under the silhouette: the glyph alone cannot tell a Chauchat from an RSC, and the reference
        // screen always prints the weapon name next to its picture.
        String name = GearNames.display("gun", gun, gun);
        if (!name.isEmpty()) {
            float scale = .74f;
            int room = (slotX - 6) - (x + 6);
            String clipped = Ui.clip(font, name, (int) (room / scale));
            g.pose().pushPose();
            try {
                g.pose().scale(scale, scale, 1);
                g.drawString(font, clipped, (int) ((x + 64) / scale), (int) ((y + ROW - 12) / scale),
                        0xffe9eef1, false);
            } finally {
                g.pose().popPose();
            }
        }
    }

    private static String firstAttachment(Map<String, String> fitting) {
        if (fitting == null) return "";
        for (String value : fitting.values()) if (value != null && !value.isEmpty()) return value;
        return "";
    }
}
