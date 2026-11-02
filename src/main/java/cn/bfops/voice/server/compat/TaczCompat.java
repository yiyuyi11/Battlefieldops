package cn.bfops.voice.server.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * TaCZ interop through reflection only, so the voice subsystem never links TaCZ classes on the classpath.
 * Every entry point fails closed (returns {@code false}/{@code null}/{@code 0L}) when TaCZ is absent or its
 * API differs, exactly like the reference.
 */
public final class TaczCompat {
    private static boolean gunInitialized;
    private static boolean gunInitializationAttempted;
    private static boolean operatorInitialized;
    private static boolean operatorInitializationAttempted;
    private static Method getIGunOrNullMethod;
    private static Method getCurrentAmmoCountMethod;
    private static Method hasBulletInBarrelMethod;
    private static Method getGunIdMethod;
    private static Method timelessGetCommonGunIndexMethod;
    private static Method timelessGetCommonAmmoIndexMethod;
    private static Method ammoItemBuilderCreateMethod;
    private static Method ammoItemBuilderSetIdMethod;
    private static Method ammoItemBuilderSetCountMethod;
    private static Method ammoItemBuilderBuildMethod;
    private static Method gunOperatorFromLivingEntityMethod;
    private static Method gunOperatorGetSynIsAimingMethod;
    private static Method gunOperatorGetSynAimingProgressMethod;

    private TaczCompat() {
    }

    public static long getHeldGunStateSignature(ServerPlayer player) {
        if (player == null || !ensureGunInitialized()) {
            return 0L;
        }
        ItemStack heldWeapon = player.getMainHandItem();
        if (heldWeapon == null || heldWeapon.isEmpty()) {
            return 0L;
        }
        try {
            Object iGun = getIGunOrNullMethod.invoke(null, heldWeapon);
            if (iGun == null) {
                return 0L;
            }
            Object gunIdObject = getGunIdMethod.invoke(iGun, heldWeapon);
            if (!(gunIdObject instanceof ResourceLocation gunId)) {
                return 0L;
            }
            int currentAmmo = ((Number) getCurrentAmmoCountMethod.invoke(iGun, heldWeapon)).intValue();
            boolean bulletInBarrel = (Boolean) hasBulletInBarrelMethod.invoke(iGun, heldWeapon);
            long signature = 17L;
            signature = 31L * signature + gunId.toString().hashCode();
            signature = 31L * signature + currentAmmo;
            signature = 31L * signature + (bulletInBarrel ? 1 : 0);
            signature = 31L * signature + player.getInventory().selected;
            return signature;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static boolean isHeldGunCompletelyEmpty(ServerPlayer player) {
        if (player == null || !ensureGunInitialized()) {
            return false;
        }
        ItemStack heldWeapon = player.getMainHandItem();
        if (heldWeapon == null || heldWeapon.isEmpty()) {
            return false;
        }
        try {
            Object iGun = getIGunOrNullMethod.invoke(null, heldWeapon);
            if (iGun == null) {
                return false;
            }
            int currentAmmo = ((Number) getCurrentAmmoCountMethod.invoke(iGun, heldWeapon)).intValue();
            boolean bulletInBarrel = (Boolean) hasBulletInBarrelMethod.invoke(iGun, heldWeapon);
            if (currentAmmo > 0 || bulletInBarrel) {
                return false;
            }
            TaczAmmoMeta ammoMeta = resolveAmmoMeta(iGun, heldWeapon);
            if (ammoMeta == null) {
                return false;
            }
            return countAmmoInInventory(player, ammoMeta) <= 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isTaczBulletEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        Class<?> currentClass = entity.getClass();
        while (currentClass != null) {
            if ("com.tacz.guns.entity.EntityKineticBullet".equals(currentClass.getName())) {
                return true;
            }
            currentClass = currentClass.getSuperclass();
        }
        return false;
    }

    public static ServerPlayer resolveBulletOwner(Entity entity) {
        if (!isTaczBulletEntity(entity)) {
            return null;
        }
        try {
            Method getOwnerMethod = entity.getClass().getMethod("getOwner");
            Object owner = getOwnerMethod.invoke(entity);
            return owner instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isSniperAiming(ServerPlayer player) {
        return isSniperAimingEntity(player);
    }

    public static boolean isSniperAimingEntity(LivingEntity entity) {
        if (entity == null || !ensureGunInitialized() || !ensureOperatorInitialized()) {
            return false;
        }
        ItemStack heldWeapon = entity.getMainHandItem();
        if (!isSniperGun(heldWeapon)) {
            return false;
        }
        try {
            Object gunOperator = gunOperatorFromLivingEntityMethod.invoke(null, entity);
            if (gunOperator == null) {
                return false;
            }
            boolean isAiming = Boolean.TRUE.equals(gunOperatorGetSynIsAimingMethod.invoke(gunOperator));
            if (isAiming) {
                return true;
            }
            Object progressObject = gunOperatorGetSynAimingProgressMethod.invoke(gunOperator);
            return progressObject instanceof Float progress && progress >= 0.85F;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isSniperGun(ItemStack stack) {
        return isGunType(stack, "sniper");
    }

    public static boolean isRpgGun(ItemStack stack) {
        return isGunType(stack, "rpg");
    }

    public static boolean isMachineGun(ItemStack stack) {
        return isGunType(stack, "mg");
    }

    public static boolean isSniperBulletEntity(Entity entity) {
        return isBulletGunType(entity, "sniper");
    }

    public static boolean isRpgBulletEntity(Entity entity) {
        return isBulletGunType(entity, "rpg");
    }

    public static ResourceLocation resolveGunId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ensureGunInitialized()) {
            return null;
        }
        try {
            Object iGun = getIGunOrNullMethod.invoke(null, stack);
            if (iGun == null) {
                return null;
            }
            Object gunIdObject = getGunIdMethod.invoke(iGun, stack);
            return gunIdObject instanceof ResourceLocation gunId ? gunId : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isGunType(ItemStack stack, String expectedType) {
        if (stack == null || stack.isEmpty() || !ensureGunInitialized()) {
            return false;
        }
        try {
            Object iGun = getIGunOrNullMethod.invoke(null, stack);
            if (iGun == null) {
                return false;
            }
            Object gunIdObject = getGunIdMethod.invoke(iGun, stack);
            return gunIdObject instanceof ResourceLocation gunId && isGunType(gunId, expectedType);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isBulletGunType(Entity entity, String expectedType) {
        ResourceLocation gunId = getBulletGunId(entity);
        if (gunId == null) {
            return false;
        }
        try {
            return isGunType(gunId, expectedType);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static ResourceLocation getBulletGunId(Entity entity) {
        if (!isTaczBulletEntity(entity) || !ensureGunInitialized()) {
            return null;
        }
        try {
            Method bulletGetGunIdMethod = entity.getClass().getMethod("getGunId");
            Object gunIdObject = bulletGetGunIdMethod.invoke(entity);
            return gunIdObject instanceof ResourceLocation gunId ? gunId : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isGunType(ResourceLocation gunId, String expectedType) throws ReflectiveOperationException {
        if (gunId == null || expectedType == null || expectedType.isBlank()) {
            return false;
        }
        Object gunIndexOptionalObject = timelessGetCommonGunIndexMethod.invoke(null, gunId);
        if (!(gunIndexOptionalObject instanceof Optional<?> gunIndexOptional) || gunIndexOptional.isEmpty()) {
            return false;
        }
        Object gunIndex = gunIndexOptional.get();
        Method getTypeMethod = gunIndex.getClass().getMethod("getType");
        Object typeObject = getTypeMethod.invoke(gunIndex);
        return typeObject != null && expectedType.equalsIgnoreCase(String.valueOf(typeObject));
    }

    private static TaczAmmoMeta resolveAmmoMeta(Object iGun, ItemStack gunStack) throws ReflectiveOperationException {
        Object gunIdObject = getGunIdMethod.invoke(iGun, gunStack);
        if (!(gunIdObject instanceof ResourceLocation gunId)) {
            return null;
        }
        Object gunIndexOptionalObject = timelessGetCommonGunIndexMethod.invoke(null, gunId);
        if (!(gunIndexOptionalObject instanceof Optional<?> gunIndexOptional) || gunIndexOptional.isEmpty()) {
            return null;
        }
        Object gunIndex = gunIndexOptional.get();
        Method getGunDataMethod = gunIndex.getClass().getMethod("getGunData");
        Object gunData = getGunDataMethod.invoke(gunIndex);
        if (gunData == null) {
            return null;
        }
        Method getAmmoIdMethod = gunData.getClass().getMethod("getAmmoId");
        Object ammoIdObject = getAmmoIdMethod.invoke(gunData);
        if (!(ammoIdObject instanceof ResourceLocation ammoId)) {
            return null;
        }
        int stackSize = 64;
        Object ammoIndexOptionalObject = timelessGetCommonAmmoIndexMethod.invoke(null, ammoId);
        if (ammoIndexOptionalObject instanceof Optional<?> ammoIndexOptional && ammoIndexOptional.isPresent()) {
            Object ammoIndex = ammoIndexOptional.get();
            Method getStackSizeMethod = ammoIndex.getClass().getMethod("getStackSize");
            Object stackSizeObject = getStackSizeMethod.invoke(ammoIndex);
            if (stackSizeObject instanceof Integer resolvedStackSize && resolvedStackSize > 0) {
                stackSize = resolvedStackSize;
            }
        }
        return new TaczAmmoMeta(ammoId, stackSize);
    }

    private static int countAmmoInInventory(ServerPlayer player, TaczAmmoMeta ammoMeta) throws ReflectiveOperationException {
        ItemStack sampleAmmo = createAmmoStack(ammoMeta.ammoId(), 1);
        if (sampleAmmo.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack != null && !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, sampleAmmo)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static ItemStack createAmmoStack(ResourceLocation ammoId, int count) throws ReflectiveOperationException {
        if (ammoId == null || count <= 0) {
            return ItemStack.EMPTY;
        }
        Object builder = ammoItemBuilderCreateMethod.invoke(null);
        builder = ammoItemBuilderSetIdMethod.invoke(builder, ammoId);
        builder = ammoItemBuilderSetCountMethod.invoke(builder, count);
        Object ammoStackObject = ammoItemBuilderBuildMethod.invoke(builder);
        return ammoStackObject instanceof ItemStack ammoStack ? ammoStack : ItemStack.EMPTY;
    }

    private static boolean ensureGunInitialized() {
        if (gunInitializationAttempted) {
            return gunInitialized;
        }
        gunInitializationAttempted = true;
        try {
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            getIGunOrNullMethod = iGunClass.getMethod("getIGunOrNull", ItemStack.class);
            getCurrentAmmoCountMethod = iGunClass.getMethod("getCurrentAmmoCount", ItemStack.class);
            hasBulletInBarrelMethod = iGunClass.getMethod("hasBulletInBarrel", ItemStack.class);
            getGunIdMethod = iGunClass.getMethod("getGunId", ItemStack.class);

            Class<?> timelessApiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
            timelessGetCommonGunIndexMethod = timelessApiClass.getMethod("getCommonGunIndex", ResourceLocation.class);
            timelessGetCommonAmmoIndexMethod = timelessApiClass.getMethod("getCommonAmmoIndex", ResourceLocation.class);

            Class<?> ammoItemBuilderClass = Class.forName("com.tacz.guns.api.item.builder.AmmoItemBuilder");
            ammoItemBuilderCreateMethod = ammoItemBuilderClass.getMethod("create");
            ammoItemBuilderSetIdMethod = ammoItemBuilderClass.getMethod("setId", ResourceLocation.class);
            ammoItemBuilderSetCountMethod = ammoItemBuilderClass.getMethod("setCount", int.class);
            ammoItemBuilderBuildMethod = ammoItemBuilderClass.getMethod("build");
            gunInitialized = true;
            return true;
        } catch (Throwable ignored) {
            getIGunOrNullMethod = null;
            gunInitialized = false;
            return false;
        }
    }

    private static boolean ensureOperatorInitialized() {
        if (operatorInitializationAttempted) {
            return operatorInitialized;
        }
        operatorInitializationAttempted = true;
        try {
            Class<?> gunOperatorClass = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            Class<?> livingEntityClass = Class.forName("net.minecraft.world.entity.LivingEntity");
            gunOperatorFromLivingEntityMethod = gunOperatorClass.getMethod("fromLivingEntity", livingEntityClass);
            gunOperatorGetSynIsAimingMethod = gunOperatorClass.getMethod("getSynIsAiming");
            gunOperatorGetSynAimingProgressMethod = gunOperatorClass.getMethod("getSynAimingProgress");
            operatorInitialized = true;
            return true;
        } catch (Throwable ignored) {
            gunOperatorFromLivingEntityMethod = null;
            operatorInitialized = false;
            return false;
        }
    }

    private record TaczAmmoMeta(ResourceLocation ammoId, int stackSize) {
    }
}
