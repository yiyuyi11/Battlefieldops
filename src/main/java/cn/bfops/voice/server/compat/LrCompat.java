package cn.bfops.voice.server.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * LrTactical interop through reflection only. Every entry point fails closed when LR is absent, exactly
 * like the reference. Used to classify throwable items (grenade/molotov/smoke) and their throwable entities.
 */
public final class LrCompat {
    public enum ThrowableCueKind {
        NONE,
        EXPLOSIVE_GRENADE,
        MOLOTOV,
        SMOKE
    }

    private static boolean initialized;
    private static Class<?> throwableItemEntityClass;
    private static Method throwableEntityGetItemMethod;
    private static Method lrGetThrowableIndexMethod;
    private static Method throwableIndexGetDataMethod;

    private LrCompat() {
    }

    public static boolean isExplosiveGrenadeEntity(Entity entity) {
        if (entity == null || !ensureInitialized() || !throwableItemEntityClass.isInstance(entity)) {
            return false;
        }
        try {
            Object itemObject = throwableEntityGetItemMethod.invoke(entity);
            if (!(itemObject instanceof ItemStack stack) || stack.isEmpty()) {
                return false;
            }
            return resolveThrowableCueKind(stack) == ThrowableCueKind.EXPLOSIVE_GRENADE;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static ThrowableCueKind resolveThrowableCueKind(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ensureInitialized()) {
            return ThrowableCueKind.NONE;
        }
        try {
            Object optionalObject = lrGetThrowableIndexMethod.invoke(null, stack);
            if (!(optionalObject instanceof Optional<?> optional) || optional.isEmpty()) {
                return ThrowableCueKind.NONE;
            }
            Object throwableIndex = optional.get();
            Object throwableData = throwableIndexGetDataMethod.invoke(throwableIndex);
            if (throwableData == null) {
                return ThrowableCueKind.NONE;
            }
            String dataClassName = throwableData.getClass().getName();
            if ("me.xjqsh.lrtactical.item.throwable.explode.ExplodeThrowableData".equals(dataClassName)) {
                return ThrowableCueKind.EXPLOSIVE_GRENADE;
            }
            if ("me.xjqsh.lrtactical.item.throwable.area.EffectCloudThrowableData".equals(dataClassName)) {
                return ThrowableCueKind.MOLOTOV;
            }
            if ("me.xjqsh.lrtactical.item.throwable.ThrowableData".equals(dataClassName)) {
                return ThrowableCueKind.SMOKE;
            }
            return ThrowableCueKind.NONE;
        } catch (Throwable ignored) {
            return ThrowableCueKind.NONE;
        }
    }

    public static boolean isRemoteDetonation(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ensureInitialized()) {
            return false;
        }
        try {
            Object optionalObject = lrGetThrowableIndexMethod.invoke(null, stack);
            if (!(optionalObject instanceof Optional<?> optional) || optional.isEmpty()) {
                return false;
            }
            Object throwableIndex = optional.get();
            Object throwableData = throwableIndexGetDataMethod.invoke(throwableIndex);
            if (throwableData == null
                || !"me.xjqsh.lrtactical.item.throwable.explode.ExplodeThrowableData".equals(throwableData.getClass().getName())) {
                return false;
            }
            Method getExplodeMethod = throwableData.getClass().getMethod("getExplode");
            Object explodeData = getExplodeMethod.invoke(throwableData);
            if (explodeData == null) {
                return false;
            }
            Method isRemoteDetonationMethod = explodeData.getClass().getMethod("isRemoteDetonation");
            Object result = isRemoteDetonationMethod.invoke(explodeData);
            return result instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static LivingEntity resolveThrowableOwner(Entity entity) {
        if (entity == null) {
            return null;
        }
        try {
            Method getOwnerMethod = entity.getClass().getMethod("getOwner");
            Object owner = getOwnerMethod.invoke(entity);
            return owner instanceof LivingEntity livingEntity ? livingEntity : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean ensureInitialized() {
        if (initialized) {
            return lrGetThrowableIndexMethod != null && throwableIndexGetDataMethod != null;
        }
        initialized = true;
        try {
            throwableItemEntityClass = Class.forName("me.xjqsh.lrtactical.entity.ThrowableItemEntity");
            throwableEntityGetItemMethod = throwableItemEntityClass.getMethod("getItem");
            Class<?> lrApiClass = Class.forName("me.xjqsh.lrtactical.api.LrTacticalAPI");
            lrGetThrowableIndexMethod = lrApiClass.getMethod("getThrowableIndex", ItemStack.class);

            Class<?> throwableIndexClass = Class.forName("me.xjqsh.lrtactical.item.index.ThrowableIndex");
            throwableIndexGetDataMethod = throwableIndexClass.getMethod("getData");
            return true;
        } catch (Throwable ignored) {
            throwableItemEntityClass = null;
            throwableEntityGetItemMethod = null;
            lrGetThrowableIndexMethod = null;
            throwableIndexGetDataMethod = null;
            return false;
        }
    }
}
