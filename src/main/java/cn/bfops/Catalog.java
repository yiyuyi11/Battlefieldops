package cn.bfops;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Runtime catalogue of guns and vehicles.
 *
 * <p>Nothing here hard-codes an addon. Guns come from TaCZ's own common index, and vehicles are
 * discovered by probing every registered entity type against the Superb Warfare vehicle base
 * class, so any addon that registers a VehicleEntity subclass (ywzj_vehicle, vehicle packs, ...)
 * is picked up automatically. Display names are resolved from the loaded resource pack lang files.
 */
public final class Catalog {
    public record Entry(String kind, String id, String name) {}

    /** Superb Warfare's vehicle base class. Optional dependency, resolved when present. */
    private static final String VEHICLE_BASE = "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity";
    /** Superb Warfare's own classification enum; {@code getType()} on the default data returns one. */
    private static final String VEHICLE_TYPE = "com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType";
    private static final String VEHICLE_DATA = "com.atsuishio.superbwarfare.data.vehicle.VehicleData";
    private static final List<String> LANG_FILES = List.of("en_us.json", "zh_cn.json");

    /**
     * Airborne classes, by their {@code VehicleType} constant names.
     *
     * <p>This is what makes "aircraft get their own launch point" possible: the pack's own enum says
     * which vehicles fly, so no name-matching on registry ids (which would miss {@code ju87} but catch
     * {@code airplane_something}) is needed. {@code DRONE} is included because a drone also needs a
     * runway rather than a road.
     */
    private static final Set<String> AIR_TYPES = Set.of("AIRPLANE", "HELICOPTER", "AIRSHIP", "DRONE");

    public final List<Entry> entries = new ArrayList<>();
    public final Map<String, String> names = new LinkedHashMap<>();
    public final Map<String, String> kinds = new LinkedHashMap<>();
    /** Vehicle id -> its {@code VehicleType} constant name, e.g. {@code TANK} or {@code AIRPLANE}. */
    public final Map<String, String> vehicleTypes = new LinkedHashMap<>();
    public final Set<String> guns = new HashSet<>();
    public final Set<String> vehicles = new HashSet<>();
    /** Vehicles that belong on a runway point instead of a ground spawn. */
    public final Set<String> aircraft = new LinkedHashSet<>();
    public String diagnostics = "";

    private Class<?> vehicleBase;

    public boolean isGun(String id) { return guns.contains(id); }
    public boolean isVehicle(String id) { return vehicles.contains(id); }

    /**
     * True when this vehicle needs an air point rather than a ground spawn.
     *
     * <p>Falls back to "is it not obviously ground" only when the type could not be read, so an
     * addon whose metadata fails to load still lands somewhere sensible instead of silently becoming
     * unavailable at both kinds of point.
     */
    public boolean isAircraft(String id) {
        if (aircraft.contains(id)) return true;
        String type = vehicleTypes.get(id);
        return type != null && AIR_TYPES.contains(type);
    }

    /** {@code 'A'} for aircraft, {@code 'G'} for everything else — the spawn mask character. */
    public char vehicleClass(String id) {
        return isAircraft(id) ? 'A' : 'G';
    }

    /**
     * Registry ids of {@code VehicleEntity} subclasses that are not actually drivable vehicles.
     *
     * <p>Superb Warfare models its vehicle <em>assembling table</em> as a {@code VehicleEntity} — it
     * even declares {@code "Type": "Car"} — so a plain class check offers the workbench as if it were
     * a tank. Fielding it puts an unusable block-entity in a playable match, so it is filtered out of
     * the catalogue entirely rather than left for the operator to notice.
     */
    private static final Set<String> NON_DEPLOYABLE = Set.of(
            "superbwarfare:vehicle_assembling_table");

    /** True when the id is a vehicle that can sensibly be fielded in a match. */
    public boolean deployableVehicle(String id) {
        return vehicles.contains(id) && !NON_DEPLOYABLE.contains(id);
    }

    /** Human-readable Chinese label for a {@code VehicleType} constant. */
    public static String vehicleTypeLabel(String type) {
        if (type == null) return "未分类";
        return switch (type) {
            case "TANK" -> "坦克";
            case "APC" -> "装甲车";
            case "AA" -> "防空";
            case "AIRPLANE" -> "固定翼";
            case "HELICOPTER" -> "直升机";
            case "CAR" -> "车辆";
            case "ARTILLERY" -> "火炮";
            case "DEFENSE" -> "防御设施";
            case "BOAT" -> "船艇";
            case "DRONE" -> "无人机";
            case "AIRSHIP" -> "飞艇";
            case "SPECIAL" -> "特殊";
            default -> "未分类";
        };
    }

    public boolean isVehicleEntity(Entity entity) {
        return entity != null && vehicleBase != null && vehicleBase.isInstance(entity);
    }

    public static boolean hasTacz() { return ModList.get().isLoaded("tacz"); }
    public static boolean hasSuperbWarfare() { return ModList.get().isLoaded("superbwarfare"); }

    public void scan(MinecraftServer server) {
        entries.clear();
        names.clear();
        kinds.clear();
        vehicleTypes.clear();
        guns.clear();
        vehicles.clear();
        aircraft.clear();
        diagnostics = "";
        Map<String, String> lang = lang(server);
        scanGuns(lang);
        scanVehicles(server, lang);
        entries.sort(Comparator.comparing(Entry::kind).thenComparing(Entry::id));
        if (!aircraft.isEmpty()) {
            diagnostics += aircraft.size() + " 种为航空器（需飞机点位）；";
        }
    }

    private void scanGuns(Map<String, String> lang) {
        if (!hasTacz()) {
            diagnostics += "未安装 TaCZ；";
            return;
        }
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            for (Object item : (Set<?>) api.getMethod("getAllCommonGunIndex").invoke(null)) {
                Map.Entry<?, ?> indexed = (Map.Entry<?, ?>) item;
                String id = indexed.getKey().toString();
                guns.add(id);
                String name = gunName(indexed.getValue(), id, lang);
                names.put(id, name);
                kinds.put(id, "gun");
                entries.add(new Entry("gun", id, name));
            }
        } catch (ClassNotFoundException ex) {
            diagnostics += "未安装 TaCZ；";
        } catch (ReflectiveOperationException ex) {
            diagnostics += "TaCZ API 不匹配：" + ex.getClass().getSimpleName() + "；";
            BattlefieldOps.LOG.error("TaCZ scan failed", ex);
        }
    }

    private static String gunName(Object index, String id, Map<String, String> lang) {
        try {
            Object pojo = index.getClass().getMethod("getPojo").invoke(index);
            Object raw = pojo.getClass().getMethod("getName").invoke(pojo);
            if (raw instanceof String key && !key.isBlank()) {
                String localized = lang.get(key);
                if (localized != null && !localized.isBlank()) return localized;
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through to the prettified id
        }
        return prettify(id);
    }

    private void scanVehicles(MinecraftServer server, Map<String, String> lang) {
        if (!hasSuperbWarfare()) {
            diagnostics += "未安装卓越前线；";
            vehicleBase = null;
            return;
        }
        try {
            vehicleBase = Class.forName(VEHICLE_BASE);
        } catch (ClassNotFoundException ex) {
            diagnostics += "未找到卓越前线载具基类；";
            vehicleBase = null;
            return;
        }
        ServerLevel probeLevel = server.overworld();
        String lastError = "";
        int unreadable = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null) continue;
            String key = id.toString();
            // Filter non-vehicles modelled as vehicles (the assembling table) before probing, so the
            // workbench never appears in the library or the whitelist.
            if (NON_DEPLOYABLE.contains(key)) continue;
            // Probe by instantiating. This deliberately does NOT use EntityType#getBaseClass(): in the
            // SRG-mapped client jar that method is stubbed to return Entity.class, so an
            // assignability test against it silently matched nothing and the scan reported
            // "no vehicle registrations found" even with Superb Warfare installed.
            Entity probe = null;
            try {
                probe = type.create(probeLevel);
                if (probe == null || vehicleBase == null || !vehicleBase.isInstance(probe)) continue;
            } catch (Throwable ex) {
                // A type whose factory throws is not a vehicle we can use; record and move on rather
                // than letting one broken addon abort the scan.
                lastError = ex.getClass().getSimpleName();
                continue;
            } finally {
                if (probe != null) probe.discard();
            }
            String name = lang.getOrDefault(type.getDescriptionId(), prettify(key));
            vehicles.add(key);
            names.put(key, name);
            kinds.put(key, "vehicle");
            entries.add(new Entry("vehicle", key, name));

            String typeName = null;
            try {
                typeName = vehicleType(type);
            } catch (Throwable ex) {
                unreadable++;
                lastError = ex.getClass().getSimpleName();
            }
            if (typeName != null) {
                vehicleTypes.put(key, typeName);
                if (AIR_TYPES.contains(typeName)) aircraft.add(key);
            }
        }
        if (vehicles.isEmpty()) {
            diagnostics += "已载入卓越前线但未探测到载具注册项" + (lastError.isEmpty() ? "；" : "（" + lastError + "）；");
        } else if (unreadable > 0) {
            diagnostics += unreadable + " 种载具未读到分类，按地面载具处理（" + lastError + "）；";
        }
    }

    /**
     * Reads a vehicle type's {@code VehicleType} constant name from its default data.
     *
     * <p>Uses the {@code EntityType} overload of {@code getDefault}, which reads the metadata table
     * without instantiating the entity. A failed read is not fatal: the vehicle stays catalogued as a
     * ground vehicle, which is the safe default — a tank launched from a runway still works, a plane
     * launched from a road does not.
     */
    private String vehicleType(EntityType<?> type) throws ReflectiveOperationException {
        Class<?> data = Class.forName(VEHICLE_DATA);
        Object defaults = data.getMethod("getDefault", EntityType.class).invoke(null, type);
        if (defaults == null) return null;
        Object value;
        try {
            value = defaults.getClass().getMethod("getType").invoke(defaults);
        } catch (NoSuchMethodException missing) {
            // Kotlin generates the accessor on the concrete class; find it at the declaring type if
            // the concrete class does not expose it directly.
            value = data.getMethod("getType").invoke(defaults);
        }
        return value == null ? null : ((Enum<?>) value).name();
    }

    /** Merges every loaded pack's en_us/zh_cn lang table so names work without client-side lookups. */
    private static Map<String, String> lang(MinecraftServer server) {
        Map<String, String> out = new HashMap<>();
        for (String file : LANG_FILES) {
            try {
                Map<ResourceLocation, Resource> found =
                        server.getResourceManager().listResources("lang", r -> r.getPath().equals("lang/" + file));
                for (Resource resource : found.values()) {
                    try (var in = resource.open()) {
                        JsonObject object = JsonParser.parseReader(
                                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                            if (entry.getValue().isJsonPrimitive()) {
                                out.put(entry.getKey(), entry.getValue().getAsString());
                            }
                        }
                    } catch (Exception ignored) {
                        // A broken lang file from any pack is skipped instead of failing the scan.
                    }
                }
            } catch (Exception ignored) {
                // No lang resources available at all: names fall back to prettified ids.
            }
        }
        return out;
    }

    /** "superbwarfare:t_90a" -> "T 90a", used when no translation is present. */
    static String prettify(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String[] parts = path.split("[_./]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.isEmpty() ? id : sb.toString();
    }

    public String name(String id) { return names.getOrDefault(id, id); }

    /** Rejects a map whose kits or vehicle whitelist reference something that is not registered. */
    public void check(Model.Arena map) {
        for (Model.Kit kit : map.kits) {
            for (boolean main : new boolean[]{true,false}) {
                Model.require(kit.options(main).size() <= 64, "每个兵种每个武器栏最多 64 把枪");
                for(String id : kit.options(main)) if(!id.isEmpty()) Model.require(guns.contains(id), "未扫描到可选武器：" + id);
            }
            if (!kit.primary.isEmpty()) Model.require(guns.contains(kit.primary), "未扫描到主武器：" + kit.primary);
            if (!kit.secondary.isEmpty()) Model.require(guns.contains(kit.secondary), "未扫描到副武器：" + kit.secondary);
        }
        for (String v : map.vehicles) Model.require(vehicles.contains(v), "未扫描到载具：" + v);
    }

    /**
     * Reports vehicles a map authorises that can never launch, because no spawn point of the right
     * class exists.
     *
     * <p>This is the "vehicle cannot be selected in the designer" symptom: the whitelist is fine, but
     * the map has no air point, so every aircraft is unlaunchable and the deploy screen has nothing to
     * offer. Reported as a warning with the fix spelled out, not as a hard rejection — an operator may
     * be midway through building the runway point.
     */
    public List<String> launchWarnings(Model.Arena map) {
        List<String> out = new ArrayList<>();
        boolean ground = map.spawns.stream().anyMatch(s -> s.allows('G'));
        boolean air = map.spawns.stream().anyMatch(s -> s.allows('A'));
        if (!ground && map.spawns.stream().anyMatch(s -> s.infantryOnly())) {
            out.add("所有出生点都是纯步兵点，载具无处刷新");
        }
        for (String id : map.vehicles) {
            char need = vehicleClass(id);
            if (need == 'A' && !air) {
                out.add("航空器「" + name(id) + "」需要至少一个飞机点位（出生点勾选「空中」）");
            } else if (need == 'G' && !ground) {
                out.add("载具「" + name(id) + "」需要至少一个地面出生点");
            }
        }
        return out;
    }

    public List<ItemStack> gun(ServerPlayer player, String id, int stacks) throws ReflectiveOperationException {
        if (id.isEmpty()) return List.of();
        Model.require(guns.contains(id), "该武器不可用");
        ResourceLocation key = ResourceLocation.parse(id);
        Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
        Object index = ((Optional<?>) api.getMethod("getCommonGunIndex", ResourceLocation.class).invoke(null, key))
                .orElseThrow(() -> new IllegalArgumentException("TaCZ 未加载该武器：" + id));
        Object data = index.getClass().getMethod("getGunData").invoke(index);
        Class<?> builderClass = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
        Object builder = builderClass.getMethod("create").invoke(null);
        builderClass.getMethod("setId", ResourceLocation.class).invoke(builder, key);
        int amount = (int) data.getClass().getMethod("getAmmoAmount").invoke(data);
        builderClass.getMethod("setAmmoCount", int.class).invoke(builder, amount);
        builderClass.getMethod("setAmmoInBarrel", boolean.class).invoke(builder, true);
        List<?> modes = (List<?>) data.getClass().getMethod("getFireModeSet").invoke(data);
        if (!modes.isEmpty()) {
            builderClass.getMethod("setFireMode", Class.forName("com.tacz.guns.api.item.gun.FireMode"))
                    .invoke(builder, modes.getFirst());
        }
        ItemStack gun = (ItemStack) builderClass
                .getMethod("build", net.minecraft.core.HolderLookup.Provider.class)
                .invoke(builder, player.registryAccess());
        Model.require(!gun.isEmpty(), "TaCZ 构建武器失败");

        List<ItemStack> result = new ArrayList<>();
        result.add(gun);
        if (stacks > 0) {
            ResourceLocation ammo = (ResourceLocation) data.getClass().getMethod("getAmmoId").invoke(data);
            Class<?> ammoBuilder = Class.forName("com.tacz.guns.api.item.builder.AmmoItemBuilder");
            for (int i = 0; i < stacks; i++) {
                Object ammoObj = ammoBuilder.getMethod("create").invoke(null);
                ammoBuilder.getMethod("setId", ResourceLocation.class).invoke(ammoObj, ammo);
                ItemStack stack = (ItemStack) ammoBuilder.getMethod("build").invoke(ammoObj);
                Model.require(!stack.isEmpty(), "弹药构建失败：" + ammo);
                stack.setCount(stack.getMaxStackSize());
                result.add(stack);
            }
        }
        return result;
    }
}
