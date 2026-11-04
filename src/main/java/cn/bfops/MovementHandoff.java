package cn.bfops;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Z-key and crawl-pose handoff to the Battlefield Movement addon.
 *
 * <p>That mod owns the stance key and its own prone pose. TaCZ's crawl tick and
 * BF's crawl mixins unconditionally clear the forced pose when their internal
 * crawl flag is false, which fights the addon's prone state. When the addon says
 * it is prone, BF must leave the pose alone.</p>
 */
public final class MovementHandoff {
    public static final String MOD_ID = "battlefield_movement";
    private static Boolean present;
    private static Method clientProne;
    private static Method serverProne;

    public static boolean active() {
        if (present == null) {
            try {
                present = ModList.get().isLoaded(MOD_ID);
            } catch (Throwable ignored) {
                present = false;
            }
        }
        return present;
    }

    /** Reflective to keep BF usable without the addon installed. */
    public static boolean proneOwned(Player player) {
        if (!active() || player == null) {
            return false;
        }
        try {
            boolean client = player.level().isClientSide();
            Method method;
            if (client) {
                if (clientProne == null) {
                    clientProne = Class.forName("cn.bfm.client.ProneClient")
                            .getMethod("isProne", Player.class);
                }
                method = clientProne;
            } else {
                if (serverProne == null) {
                    serverProne = Class.forName("cn.bfm.ProneManager")
                            .getMethod("isActive", Player.class);
                }
                method = serverProne;
            }
            return Boolean.TRUE.equals(method.invoke(null, player));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private MovementHandoff() {
    }
}
