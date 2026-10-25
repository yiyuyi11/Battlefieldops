package cn.bfops;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills and refills a Superb Warfare vehicle: ammunition, battery, hull health.
 *
 * <p>Everything here is reflective. Superb Warfare is an optional dependency, so the game must still
 * load and the rest of the mod must still work when it is absent — a compile-time import would make the
 * whole class fail to load instead of just this feature. It also means a future addon version that
 * renames a method degrades to "no resupply" rather than "no server".
 *
 * <p>The API surface being used, verified against Superb Warfare 0.8.9.2:
 * <ul>
 *   <li>{@code VehicleEntity#getInventory()} — the ammo rack, an {@code ItemStackHandler}</li>
 *   <li>{@code VehicleEntity#getEnergy()/setEnergy(int)/getMaxEnergy()/hasEnergyStorage()}</li>
 *   <li>{@code VehicleEntity#getHealth()/setHealth(float)/getMaxHealth()}</li>
 *   <li>{@code VehicleData#from(entity)} → {@code getDefault()} → {@code weapons()}, for the ammo a
 *       hull's guns actually consume</li>
 * </ul>
 *
 * <p>Reflection results are cached per class, because this runs on every deployment and on every supply
 * tick inside a pad: resolving the same {@code Method} objects every second for every vehicle would be
 * wasteful for no benefit.
 */
final class VehicleEquip {
    private static final String VEHICLE_DATA = "com.atsuishio.superbwarfare.data.vehicle.VehicleData";
    private static final String DEFAULT_VEHICLE_DATA =
            "com.atsuishio.superbwarfare.data.vehicle.DefaultVehicleData";
    private static final String VEHICLE_ENTITY =
            "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity";

    /** Cached handles, resolved at most once. {@code null} means "not available". */
    private static Class<?> vehicleDataClass;
    private static java.lang.reflect.Method defaultsFor, defaultWeapons;
    private static boolean resolved;

    private VehicleEquip() { }

    /**
     * Stations and the ammunition each can physically accept, in the vehicle's own weapon order.
     *
     * <p>Read from the same {@code getProcessedAmmoConsumers} table the rest of this class uses, one pass
     * per weapon, so per-station allocation and the flat list can never disagree about what exists.
     */
    static java.util.LinkedHashMap<String, List<ItemStack>> stationStacks(Entity vehicle) {
        java.util.LinkedHashMap<String, List<ItemStack>> out = new java.util.LinkedHashMap<>();
        try {
            Object all = weapons(vehicle);
            if (!(all instanceof Map<?, ?> map)) return out;
            for (var entry : map.entrySet()) {
                String station = String.valueOf(entry.getKey());
                List<ItemStack> stacks = new ArrayList<>();
                try {
                    Object weapon = entry.getValue();
                    for (Object consumer : (Iterable<?>) weapon.getClass()
                            .getMethod("getProcessedAmmoConsumers").invoke(weapon)) {
                        consumer.getClass().getMethod("init").invoke(consumer);
                        ItemStack stack = (ItemStack) consumer.getClass().getMethod("getStack").invoke(consumer);
                        if (!stack.isEmpty() && stacks.stream()
                                .noneMatch(s -> ItemStack.isSameItemSameComponents(s, stack))) {
                            stacks.add(stack.copyWithCount(1));
                        }
                    }
                } catch (ReflectiveOperationException ex) {
                    BattlefieldOps.LOG.warn("Cannot resolve ammunition for station " + station, ex);
                }
                out.put(station, stacks);
            }
        } catch (ReflectiveOperationException ex) {
            BattlefieldOps.LOG.warn("Cannot read vehicle armament", ex);
        }
        return out;
    }

    /**
     * Places ammunition station by station.
     *
     * <p>One top-up call per station, each with that station's own ammunition and its own magazine count.
     * {@link AmmoAllocation#topUp} tops each type up towards a quota and reads the rack first, so
     * successive calls accumulate correctly: a second station sharing a round adds to what the first
     * placed rather than overwriting it.
     *
     * @return rounds actually placed, summed across stations
     */
    private static int allocate(Entity vehicle, VehicleArmament armament) {
        java.util.LinkedHashMap<String, List<ItemStack>> stations = stationStacks(vehicle);
        if (stations.isEmpty()) return 0;
        // Convert to ids for the planning step, which is pure and testable, then back to stacks to place.
        java.util.LinkedHashMap<String, List<String>> offered = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> stacks = new java.util.HashMap<>();
        for (var entry : stations.entrySet()) {
            offered.put(entry.getKey(), entry.getValue().stream()
                    .map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).toList());
            for (ItemStack stack : entry.getValue()) {
                // Stack size is what turns a round count into a number of slots, and therefore the only
                // thing that can decide whether a configuration physically fits the rack.
                stacks.put(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                        Math.max(1, stack.getMaxStackSize()));
            }
        }
        int slots = containerSize(vehicle);
        int placed = 0;
        for (VehicleArmament.Allocation plan : armament.plan(offered, stacks, slots)) {
            if (plan.empty()) continue;
            List<ItemStack> types = stations.getOrDefault(plan.station(), List.of()).stream()
                    .filter(s -> plan.types().contains(
                            BuiltInRegistries.ITEM.getKey(s.getItem()).toString()))
                    .toList();
            if (types.isEmpty()) continue;
            placed += topUp(vehicle, types, plan.rounds());
        }
        return placed;
    }

    /**
     * Fills a newly created vehicle.
     *
     * @return a short human-readable summary for diagnostics, never {@code null}
     */
    static String equip(Entity vehicle, String kind) {
        return equip(vehicle, kind, new VehicleArmament());
    }

    /**
     * Fills a newly created vehicle, honouring the map's armament limits.
     *
     * @param armament the operator's configuration for this vehicle; an empty one means every default
     * @return a short human-readable summary for diagnostics, never {@code null}
     */
    static String equip(Entity vehicle, String kind, VehicleArmament armament) {
        if (vehicle == null) return "无载具";
        if (armament == null) armament = new VehicleArmament();
        List<String> ammo = armament.permitted(ammoTypes(vehicle));
        int weapons = weaponCount(vehicle);
        // The class default, before any per-station override. Used both for the hull-level figures and as
        // the fallback for stations the operator did not configure.
        VehicleSupply.Kit kit = VehicleSupply.Kit.of(kind, weapons, hasEnergy(vehicle));

        // Per station, so a configured count actually lands on the station it was typed into. Loading the
        // hull from one collapsed number is what made the per-station setting look like it did nothing.
        int placed = allocate(vehicle, armament);
        int energy = charge(vehicle, VehicleSupply.energyFor(kit, maxEnergy(vehicle)));
        boolean repaired = repair(vehicle, VehicleSupply.healthFor(kit, maxHealth(vehicle)));

        // Reported in rounds, because that is what the player will count when the rack opens. Silent
        // about a vehicle whose guns take no ammunition: an unarmed hull is not a failure.
        StringBuilder text = new StringBuilder();
        if (placed > 0) text.append("已配弹药 ").append(placed).append(" 件；识别 ").append(ammo.size()).append(" 种弹药定义");
        else if(!ammo.isEmpty())text.append("警告：弹药舱未接收弹药，请检查车型弹药槽限制");
        if (energy > 0) {
            if (text.length() > 0) text.append(" · ");
            text.append("电量 ").append(energy);
        }
        if (repaired) {
            if (text.length() > 0) text.append(" · ");
            text.append("车体完好");
        }
        return text.length() == 0 ? "无需配装" : text.toString();
    }

    /**
     * Refills a vehicle standing in a supply pad.
     *
     * @return true when something was actually restored, so the caller can avoid sending a message
     *         every tick to a vehicle that is already full
     */
    static boolean resupply(Entity vehicle, VehicleSupply.Supply pad) {
        return resupply(vehicle, pad, new VehicleArmament());
    }

    static boolean resupply(Entity vehicle, VehicleSupply.Supply pad, VehicleArmament armament) {
        if (vehicle == null || pad == null) return false;
        if (armament == null) armament = new VehicleArmament();
        boolean changed = false;
        if (pad.ammo()) {
            List<String> ammo = armament.permitted(ammoTypes(vehicle));
            if (!ammo.isEmpty()) {
                // Top up to the same total a fresh deployment would give, so a pad cannot be used to
                // exceed what a deployment hands out. Anything beyond that would make pads a way to
                // stockpile. The vehicle's own type is read back from the catalogue for the same reason
                // the deployment path uses it: the per-class amount depends on it.
                int weapons = weaponCount(vehicle);
                String kind = kindOf(vehicle);
                VehicleSupply.Kit kit = VehicleSupply.Kit.of(kind, weapons, hasEnergy(vehicle));
                // Same per-station allocation as deployment, so a pad refills each station to exactly the
                // amount that station is configured for and cannot top a restricted station past its cap.
                if (allocate(vehicle, armament) > 0) changed = true;
            }
        }
        if (pad.energy() && hasEnergy(vehicle)) {
            int max = maxEnergy(vehicle);
            if (energy(vehicle) < max && charge(vehicle, max) > 0) changed = true;
        }
        if (pad.health() && health(vehicle) < maxHealth(vehicle)) {
            if (repair(vehicle, maxHealth(vehicle))) changed = true;
        }
        return changed;
    }

    /**
     * Rounds currently in the rack.
     *
     * <p>Counts stack sizes, which is how the addon itself counts ammunition in a vehicle container.
     * Reading the ammo data component instead would report the magazine charge of a single stack, not
     * how many rounds the vehicle is carrying.
     */
    /** Heals a player standing in a supply pad. Returns true when health actually changed. */
    static boolean resupplyPlayer(net.minecraft.world.entity.player.Player player, VehicleSupply.Supply pad) {
        if (player == null || pad == null || !pad.health()) return false;
        if (player.getHealth() >= player.getMaxHealth()) return false;
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        return true;
    }

    // ------------------------------------------------------------------ reads

    private static int weaponCount(Entity vehicle) {
        try {
            Object weapons = weapons(vehicle);
            return weapons instanceof Map<?, ?> map ? map.size() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    static Object weapons(Entity vehicle) throws ReflectiveOperationException {
        Object defaults = defaults(vehicle);
        return defaults == null ? null : defaultWeapons.invoke(defaults);
    }

    /** The vehicle's {@code DefaultVehicleData}, or {@code null} when the addon is absent. */
    private static Object defaults(Entity vehicle) throws ReflectiveOperationException {
        resolve();
        if (defaultsFor == null) return null;
        return defaultsFor.invoke(null, vehicle);
    }

    /**
     * Ammo item ids this vehicle's guns consume.
     *
     * <p>Primary source is each weapon's {@code getProcessedAmmoConsumers()}, which is the addon's own
     * resolved view and handles both the single-string and the array/list forms that weapon JSON allows
     * ({@code "AmmoType": "superbwarfare:small_shell_ap"} versus a list of objects with {@code "Ammo"}
     * and per-entry overrides). Reading the raw JSON field instead would mean re-implementing that
     * parsing and drifting from it.
     *
     * <p>Energy weapons legitimately have no consumers at all, so an empty result is normal and simply
     * means the vehicle needs no rounds — it is not an error worth reporting.
     */
    static List<String> ammoTypes(Entity vehicle) {
        return ammoStacks(vehicle).stream().map(s->BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).distinct().toList();
    }
    static List<ItemStack> ammoStacks(Entity vehicle){
        return ammoStacks(vehicle, new VehicleArmament());
    }

    /**
     * The ammunition this hull may be loaded with, after the map's allow-list is applied.
     *
     * <p>Filtering happens on the resolved stacks rather than on ids so that the surviving entries keep
     * their data components — a shell with a custom component is a different item from the plain one, and
     * rebuilding it from an id would silently lose that.
     */
    static List<ItemStack> ammoStacks(Entity vehicle, VehicleArmament armament){
        List<ItemStack> all = rawAmmoStacks(vehicle);
        if (armament == null || armament.stations.isEmpty()) return all;
        List<String> permitted = armament.permitted(
                all.stream().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).toList());
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : all) {
            if (permitted.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) out.add(stack);
        }
        return out;
    }

    private static List<ItemStack> rawAmmoStacks(Entity vehicle){
        List<ItemStack> stacks=new ArrayList<>();
        try{
            Object all=weapons(vehicle);
            if(all instanceof Map<?,?> map)for(Object weapon:map.values()){
                try{
                    for(Object consumer:(Iterable<?>)weapon.getClass().getMethod("getProcessedAmmoConsumers").invoke(weapon)){
                        consumer.getClass().getMethod("init").invoke(consumer);
                        ItemStack stack=(ItemStack)consumer.getClass().getMethod("getStack").invoke(consumer);
                        if(!stack.isEmpty()&&stacks.stream().noneMatch(s->ItemStack.isSameItemSameComponents(s,stack)))stacks.add(stack.copyWithCount(1));
                    }
                }catch(ReflectiveOperationException ex){BattlefieldOps.LOG.warn("Cannot resolve vehicle weapon ammunition",ex);}
            }
        }catch(ReflectiveOperationException ex){BattlefieldOps.LOG.warn("Cannot read vehicle armament",ex);}
        return stacks;
    }
    static int topUp(Entity vehicle,int total){
        return topUp(vehicle, ammoStacks(vehicle), total);
    }
    static int topUp(Entity vehicle,List<ItemStack> ammo,int total){
        try{
            Object raw=vehicle.getClass().getMethod("getInventory").invoke(vehicle);
            if(!(raw instanceof net.neoforged.neoforge.items.IItemHandler inventory))return 0;
            return AmmoAllocation.topUp(inventory,ammo,total);
        }catch(ReflectiveOperationException ex){BattlefieldOps.LOG.warn("Cannot supply vehicle ammunition",ex);return 0;}
    }

    /** Ammo ids with display names, for the editor. */
    static Map<String, String> ammoNames(Entity vehicle) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String id : ammoTypes(vehicle)) out.put(id, Catalog.prettify(id));
        return out;
    }

    /**
     * This vehicle's {@code VehicleType} constant, e.g. {@code TANK}.
     *
     * <p>Read from the same vehicle-data table the ammo comes from, so the loadout decision and the
     * amount are always based on one source. Returns an empty string when the table is unreadable, which
     * {@link VehicleSupply.Kit#of} treats as its uncategorised default rather than failing.
     */
    static String kindOf(Entity vehicle) {
        try {
            Object defaults = defaults(vehicle);
            if (defaults == null) return "";
            Object type = defaults.getClass().getMethod("getType").invoke(defaults);
            return type instanceof Enum<?> value ? value.name() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean hasEnergy(Entity vehicle) {
        try {
            Object value = vehicle.getClass().getMethod("hasEnergyStorage").invoke(vehicle);
            return value instanceof Boolean flag && flag;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int energy(Entity vehicle) {
        try {
            Object value = vehicle.getClass().getMethod("getEnergy").invoke(vehicle);
            return value instanceof Integer number ? number : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int maxEnergy(Entity vehicle) {
        try {
            Object value = vehicle.getClass().getMethod("getMaxEnergy").invoke(vehicle);
            return value instanceof Integer number ? number : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Current hull health, reflective because {@code Entity} has no such method. */
    private static float health(Entity vehicle) {
        try {
            Object value = vehicle.getClass().getMethod("getHealth").invoke(vehicle);
            return value instanceof Number number ? number.floatValue() : 0f;
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    /** Maximum hull health, reflective for the same reason. */
    private static float maxHealth(Entity vehicle) {
        try {
            Object value = vehicle.getClass().getMethod("getMaxHealth").invoke(vehicle);
            return value instanceof Number number ? number.floatValue() : 0f;
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    // ------------------------------------------------------------------ writes

    /**
     * Places ammunition into the rack, up to a total count.
     *
     * <p>Distributes across the ammo types the vehicle actually uses, round-robin over slots, so a
     * two-gun hull with limited space gets a usable share of both rather than all of the first. Slots
     * are filled through the inventory handler's own {@code setStackInSlot}, which keeps the addon's
     * contents-changed bookkeeping intact — writing the backing list directly would desynchronise the
     * client's view of the rack.
     *
     * <p>The count returned is what actually landed in the rack, not what was asked for. A stack is
     * capped by the item's own maximum (a shell holds far fewer than a rifle round), so reusing the
     * requested figure would report a full load while the rack was nearly empty.
     *
     * @return rounds actually placed
     */
    static int containerSize(Entity vehicle) {
        if (vehicle == null) return 0;
        // The rack is reached through the vehicle's inventory handler. Asking the *entity* for getSlots finds
        // nothing, which is how every caller silently got 0 and the capacity check never fired.
        try {
            Object raw = vehicle.getClass().getMethod("getInventory").invoke(vehicle);
            if (raw != null) {
                int slots = slotsOf(raw);
                if (slots > 0) return slots;
            }
        } catch (Throwable ignored) {
            // Fall through to the container-size accessor below.
        }
        try {
            Object value = vehicle.getClass().getMethod("getContainerSize").invoke(vehicle);
            if (value instanceof Integer number && number > 0) return number;
        } catch (Throwable ignored) {
            // Neither source answered; the caller treats 0 as "unknown" rather than "empty".
        }
        return 0;
    }

    /** Slot count of an inventory handler, or 0 when it does not expose one. */
    static int slotsOf(Object inventory) {
        if (inventory == null) return 0;
        try {
            Object value = inventory.getClass().getMethod("getSlots").invoke(inventory);
            return value instanceof Integer number ? number : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int charge(Entity vehicle, int amount) {
        if (amount <= 0) return 0;
        try {
            vehicle.getClass().getMethod("setEnergy", int.class).invoke(vehicle, amount);
            return amount;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean repair(Entity vehicle, float health) {
        if (health <= 0) return false;
        try {
            vehicle.getClass().getMethod("setHealth", float.class).invoke(vehicle, health);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Resolves the vehicle-data chain once.
     *
     * <p>Uses the addon's <b>static</b> {@code getDefault(VehicleEntity)} rather than
     * {@code from(vehicle).getDefault()}, for two reasons found by reading the shipped bytecode:
     *
     * <ul>
     *   <li>{@code from} is declared as {@code from(VehicleEntity)}, not {@code from(Entity)}. Asking for
     *       {@code from(Entity.class)} does not throw loudly — {@code getMethod} raises
     *       {@code NoSuchMethodException}, the surrounding catch treats the whole feature as
     *       unavailable, and a deployed vehicle silently arrives empty with no error anywhere.</li>
     *   <li>{@code getDefault()} is also declared with a covariant bridge, so the no-argument lookup is
     *       ambiguous between two methods with the same signature and different return types.</li>
     * </ul>
     *
     * <p>The static overload is unambiguous and one call shorter. A future version that renames or
     * widens it still works, because the candidate list is tried in order.
     */
    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            vehicleDataClass = Class.forName(VEHICLE_DATA);
            Class<?> vehicleEntity = Class.forName(VEHICLE_ENTITY);
            Class<?> defaults = Class.forName(DEFAULT_VEHICLE_DATA);
            defaultsFor = firstMethod(vehicleDataClass, "getDefault", vehicleEntity, Entity.class);
            if (defaultsFor == null) return;
            defaultWeapons = defaults.getMethod("weapons");
        } catch (Throwable ignored) {
            defaultsFor = null;
        }
    }

    /** First of the candidate parameter types that a static method actually accepts. */
    private static java.lang.reflect.Method firstMethod(Class<?> owner, String name, Class<?>... params) {
        for (Class<?> param : params) {
            try {
                return owner.getMethod(name, param);
            } catch (NoSuchMethodException ignored) {
                // try the next candidate
            }
        }
        return null;
    }

    /** Diagnostic line for the operator: what this mod can and cannot reach. */
    static String diagnose(Entity sample) {
        if (sample == null) return "无载具可用于自检";
        resolve();
        List<String> notes = new ArrayList<>();
        notes.add(defaultsFor == null ? "未读到载具数据表" : "已读到载具数据表");
        notes.add(hasEnergy(sample) ? "有电量系统" : "无电量系统");
        notes.add(ammoTypes(sample).isEmpty()
                ? "未读到弹种" : "弹种 " + String.join("/", ammoTypes(sample)));
        try {
            Object inventory = sample.getClass().getMethod("getInventory").invoke(sample);
            notes.add(inventory == null ? "无弹药舱" : "弹药舱 " + slotsOf(inventory) + " 格");
        } catch (Throwable ignored) {
            notes.add("无法访问弹药舱");
        }
        return String.join(" · ", notes);
    }

    /** Unused but kept explicit: NonNullList is the type the handler exposes internally. */
    @SuppressWarnings("unused")
    private static NonNullList<ItemStack> asList(Object items) {
        return items instanceof NonNullList<?> list
                ? (NonNullList<ItemStack>) list : NonNullList.create();
    }
}
