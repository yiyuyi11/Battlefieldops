package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import java.lang.reflect.Field;

/** Optional SBW bridge: no SBW classes in descriptors or class initializers. */
final class VehicleUi {
    private static boolean resolved;
    private static Field zoom;
    private static boolean vehicle(Entity entity) {
        if (entity == null) return false;
        for (Class<?> c = entity.getClass(); c != null; c = c.getSuperclass())
            if (c.getName().equals("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity")) return true;
        return false;
    }
    static boolean active() {
        var mc = Minecraft.getInstance();
        return mc.player != null && (vehicle(mc.player.getVehicle()) || vehicle(mc.player.getRootVehicle())
                || vehicle(mc.getCameraEntity()));
    }
    static boolean scoped() {
        if (!resolved) {
            resolved = true;
            try { zoom = Class.forName("com.atsuishio.superbwarfare.event.ClientEventHandler").getField("zoomVehicle"); }
            catch (ReflectiveOperationException | LinkageError ignored) { }
        }
        if (zoom != null) try { return zoom.getBoolean(null); }
        catch (ReflectiveOperationException ignored) { }
        // Unknown optional versions: first-person vehicle view is treated conservatively.
        return Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }
}
