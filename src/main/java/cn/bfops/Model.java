package cn.bfops;

import java.util.*;

/** Server-owned configuration. Coordinates are world blocks, not screen pixels. */
public final class Model {
    public record Vec(double x, double z) {}
    public static final class Point {
        public String id="A"; public double x,y=64,z,radius=16,height=12;
        public int sector=0;
    }
    /**
     * The area one team is confined to during a match.
     *
     * <p>An empty or short polygon means "no restriction" — the team may go anywhere inside the map
     * boundary, which is exactly how every map behaved before this existed. That is deliberate: a
     * required polygon would invalidate every saved map, and an operator who has not drawn zones should
     * not get a map that refuses to load.
     *
     * <p>The zone is a <b>hard boundary</b>: leaving it is leaving the battle area, with the same grace
     * period and the same non-death exit as crossing the map edge. It is not a spawn restriction that
     * stops mattering once you are alive.
     */
    public static final class Zone {
        public int team = 0;
        public List<Vec> boundary = new ArrayList<>();

        /** True when this zone actually constrains anything. */
        public boolean active() {
            return boundary != null && boundary.size() >= 3;
        }

        public boolean contains(double x, double z) {
            return active() && Model.contains(boundary, x, z);
        }
    }
    public static final class Spawn {
        /** Spawn kinds, as they appear in the editor and in the deploy list. */
        public static final String BASE = "BASE", POINT = "POINT", AIR = "AIR";
        public String id="基地"; public int team=0,sector=0; public double x,y=64,z; public float yaw;
        /**
         * What kind of site this is: {@link #BASE} (headquarters), {@link #POINT} (forward post) or
         * {@link #AIR} (airfield).
         *
         * <p>Kept separate from {@link #allows}: the mask says which vehicle classes may launch, the
         * kind says what the site <em>is</em>. An airfield is a site whose purpose is aircraft, and
         * labelling it as such lets the editor and the deploy list present it as its own option rather
         * than as a headquarters with an unusual mask. Existing maps deserialise with a blank kind,
         * which {@link #kind()} resolves to {@link #BASE}.
         */
        public String kind = BASE;
        /**
         * Which vehicle classes may spawn together with the player here.
         *
         * <p>Vehicles are no longer created under the player's feet at a hard-coded offset: a spawn
         * point declares what it can launch, and the engine puts the player into the vehicle at the
         * point itself. {@link #PATTERN} repeats one character per class:
         * <ul>
         *   <li>{@code G} - ground vehicles (tanks, cars, trucks, boats, AA, artillery)</li>
         *   <li>{@code A} - aircraft (planes, helicopters, airships, drones)</li>
         *   <li>{@code N} - none: infantry-only spawn</li>
         * </ul>
         * The default {@code "G"} keeps every existing map working: ground vehicles appear exactly
         * where they did before, while aircraft require an explicitly marked runway point.
         */
        public String allows = "G";

        /** The resolved kind, defaulting for maps saved before kinds existed. */
        public String kind() {
            return BASE.equals(kind) || POINT.equals(kind) || AIR.equals(kind) ? kind : BASE;
        }

        public boolean air() {
            return AIR.equals(kind());
        }

        /** True when this spawn may launch a vehicle of the given class character. */
        public boolean allows(char vehicleClass) {
            if (allows == null || allows.isEmpty()) return vehicleClass == 'G';
            return allows.indexOf(Character.toUpperCase(vehicleClass)) >= 0;
        }

        /** True when this point launches nothing but infantry. */
        public boolean infantryOnly() {
            return allows != null && !allows.isEmpty() && allows.indexOf('G') < 0 && allows.indexOf('A') < 0;
        }
    }
    public static final class Sector {
        public int index; public List<Vec> boundary=new ArrayList<>();
    }
    public static final class Kit {
        public String id="突击兵",primary="",secondary="";
        public int ammoStacks=3;
        public List<String> primaryOptions=new ArrayList<>(),secondaryOptions=new ArrayList<>();
        /**
         * Everything the kit issues besides its two weapons: arbitrary items, armour and accessories.
         *
         * <p>Never {@code null} once loaded (see {@link #gear()}), so callers do not need null checks.
         * A map saved before this existed simply has an empty gear block, which means "nothing extra" —
         * exactly the old behaviour.
         */
        public KitGear gear;

        /** The gear block, created on demand so old map JSON keeps loading unchanged. */
        public KitGear gear() {
            if (gear == null) gear = new KitGear();
            return gear;
        }

        public void validateOptions() {
            for(List<String> values:Arrays.asList(primaryOptions,secondaryOptions)){
                require(values!=null&&values.size()<=64,"每个武器栏最多 64 个选项");
                for(String value:values)require(value!=null&&value.length()<160&&value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"),"可选武器 ID 无效");
            }
            if (gear != null) gear.validate();
        }
        public List<String> options(boolean main) {
            LinkedHashSet<String> values=new LinkedHashSet<>();
            String fixed=main?primary:secondary;
            if(fixed!=null&&!fixed.isEmpty())values.add(fixed);
            List<String> configured=main?primaryOptions:secondaryOptions;
            if(configured!=null)values.addAll(configured);
            if(values.isEmpty())values.add("");
            return List.copyOf(values);
        }
        public String select(boolean main,String requested) {
            String chosen=requested==null?(main?primary:secondary):requested;
            require(options(main).contains(chosen),"武器未获当前兵种授权");
            return chosen;
        }
    }
    public static final class Arena {
        public String id="map",name="新地图",dimension="minecraft:overworld",mode="CONQUEST";
        public List<Vec> boundary=new ArrayList<>();
        public List<Point> points=new ArrayList<>();
        public List<Spawn> spawns=new ArrayList<>();
        /**
         * Per-team activity zones. Empty means both teams may use the whole map, which is how every
         * map worked before this existed.
         *
         * <p>Only meaningful in CONQUEST: in BREAKTHROUGH the stage boundary already advances and
         * confines play, and stacking a second hard boundary on top would make a stage unplayable the
         * moment the operator moved one but not the other. The editor says so and validation does not
         * require them.
         */
        public List<Zone> zones=new ArrayList<>();
        /**
         * Supply pads: places that refill vehicle ammunition, battery and player health.
         *
         * <p>Separate from spawns and capture points because a resupply point is not a place you appear
         * at or fight over — it is logistics. Attaching it to a spawn would force every base to also be
         * a workshop, and attaching it to a capture point would make supply a reward for holding ground
         * rather than a decision the operator makes about the map.
         */
        public List<VehicleSupply.Supply> supplies=new ArrayList<>();
        public List<Sector> sectors=new ArrayList<>();
        public List<Kit> kits=new ArrayList<>();
        public List<String> vehicles=new ArrayList<>();
        /**
         * Per-vehicle armament limits, keyed by vehicle registry id.
         *
         * <p>Keyed by id rather than by index so that reordering {@link #vehicles} — or removing an entry
         * and adding it back — cannot silently move one vehicle's limits onto another. A vehicle with no
         * entry here gets every default, which is what every map saved before this existed relies on.
         */
        public java.util.Map<String, VehicleArmament> armaments=new java.util.LinkedHashMap<>();
        public int tickets=300,killScore=100,captureScore=200,deathCost=1,bleed=1,captureSeconds=20;
        public int respawnSeconds=8,outsideSeconds=10,roundMinutes=30,sectorBonus=50,vehicleCooldown=60;
        /**
         * Retired: vehicles are rationed by {@link #vehicleCooldown} and {@link #vehicleLimit}, not by
         * personal score.
         *
         * <p>Kept as a field so existing map JSON still loads without a validation error — an unknown
         * key, or a removed one, would make every previously saved map fail to open. Nothing reads it.
         */
        public int vehicleCost=0;
        public int exitMargin=32;
        public int minY=-64,maxY=320,vehicleLimit=6;
        public int blueVehicleLimit=-1,redVehicleLimit=-1;
        public int vehicleLimitFor(int team){return team==0?(blueVehicleLimit<0?vehicleLimit:blueVehicleLimit):team==1?(redVehicleLimit<0?vehicleLimit:redVehicleLimit):0;}
        public boolean friendlyFire=false;
        /** Hide enemy nametags entirely; friendlies keep theirs. Team colours apply either way. */
        public boolean hideEnemyNametags=false;
        /** Teammates glow through walls as squad markers. */
        public boolean glowTeammates=true;
        /**
         * How long a manual enemy mark stays on the map, in seconds.
         *
         * <p>Per map because "long enough to act on" is a balance decision: a slow-moving map wants marks
         * that persist, a knife-fight map wants them to vanish before they go stale. Five seconds is the
         * default because it is long enough to call out and short enough that the marker is still true.
         */
        public int markSeconds=5;
        /** Score for successfully marking an enemy. */
        public int markScore=10;
        /** Score for assisting a kill: damage dealt shortly before someone else finished it. */
        public int assistScore=50;
        /** Extra score for a headshot kill. */
        public int headshotBonus=50;
        /** Extra score for each kill after the first inside the streak window. */
        public int comboBonus=50;
        /** Extra score for killing the player who killed you last. */
        public int revengeBonus=50;
        /** How long a kill keeps a streak alive, in seconds. */
        public int comboSeconds=5;
        /** How long damage still counts towards an assist, in seconds. */
        public int assistSeconds=10;
        /**
         * Whether a lethal hit downs a player instead of killing them.
         *
         * <p>Off restores the plain kill-and-respawn flow, which some maps want: a revive system in a
         * one-life mode is just a slower death.
         */
        public boolean downEnabled=true;
        /** Seconds a downed player has before bleeding out. */
        public int downSeconds=30;
        /** Seconds a teammate must stay close to bring someone back. */
        public int reviveSeconds=4;
        /** Health restored on a successful revive; 0 means 30% of maximum. */
        public int reviveHealth=12;
        /** Enemies glow once someone on the other side has line of sight on them. */
        public boolean glowEnemies=true;
        /**
         * Whether the map spawns AI soldiers to fill the teams. Off by default: bots are a map-level
         * choice, because a one-life or vehicle-heavy map wants very different bot behaviour.
         */
        public boolean botMode=false;
        /** How many AI soldiers each team gets while {@link #botMode} is on. */
        public int botsPerTeam=8;
        public void validate() {
            require(id!=null&&id.matches("[a-z0-9_-]{1,32}"),"地图 ID 仅允许小写英文、数字、下划线，最多 32 字符");
            require(name!=null&&!name.isBlank()&&name.length()<=48,"地图名称长度 1～48");
            require(dimension!=null&&dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"),"维度 ID 无效");
            require(mode.equals("CONQUEST")||mode.equals("BREAKTHROUGH"),"模式无效");
            polygon(boundary); require(points!=null&&points.size()>=1&&points.size()<=24,"需要 1～24 个据点");
            require(spawns!=null&&spawns.size()<=32,"出生点最多 32 个");
            require(kits!=null&&kits.size()>=1&&kits.size()<=16,"需要 1～16 个配装");
            for(Kit kit:kits){require(kit!=null,"配装不能为空");kit.validateOptions();}
            require(vehicles!=null&&vehicles.size()<=128,"载具白名单最多 128 个");
            require(sectors!=null&&sectors.size()<=16,"阶段最多 16 个");
            Set<String> ids=new HashSet<>(); Set<Integer> stages=new TreeSet<>();
            for(Point p:points){ require(p!=null&&validName(p.id)&&ids.add(p.id),"据点名称无效或重复");pos(p.x,p.y,p.z);require(contains(boundary,p.x,p.z),"据点必须在地图边界内");require(p.y>=minY&&p.y<=maxY,"据点 "+p.id+" 的 Y 超出战区高度，请在据点页修正");require(p.radius>=2&&p.radius<=128&&p.height>=2&&p.height<=128,"据点半径/高度超限");require(p.sector>=0&&p.sector<16,"阶段编号为 0～15");stages.add(p.sector); }
            ids.clear(); for(Spawn s:spawns){require(s!=null&&validName(s.id)&&ids.add(s.id),"出生点名称无效或重复");pos(s.x,s.y,s.z);require(s.team==0||s.team==1,"阵营为 0 或 1");require(s.sector>=0&&s.sector<16,"阶段无效");require(contains(boundary,s.x,s.z),"出生点必须在地图边界内");require(s.y>=minY&&s.y<=maxY,"出生点 "+s.id+" 的 Y 超出战区高度");require(validAllows(s.allows),"出生点载具类型只能是 G（地面）/ A（空中）/ N（无）的组合");}
            ids.clear(); for(Kit k:kits){require(k!=null&&validName(k.id)&&ids.add(k.id),"配装名称无效或重复");require(k.primary!=null&&k.secondary!=null&&k.primary.length()<160&&k.secondary.length()<160,"武器 ID 无效");range(k.ammoStacks,0,12,"弹药组数");}
            for(String v:vehicles)require(v!=null&&v.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")&&v.length()<160,"载具 ID 无效");
            Set<Integer> zoneIds=new HashSet<>();for(Sector s:sectors){require(s!=null&&zoneIds.add(s.index)&&s.index>=0&&s.index<16,"阶段边界编号为 0～15");if(s.boundary==null||s.boundary.size()<3)continue;polygon(s.boundary);for(Vec v:s.boundary)require(contains(boundary,v.x,v.z),"阶段边界必须在地图内");}
            if(mode.equals("CONQUEST")){for(int team=0;team<2;team++){final int t=team;require(spawns.stream().anyMatch(s->s.team==t),"双方都需要出生点");}}
            else {int expected=0;for(int stage:stages){require(stage==expected++,"突破阶段须从 0 开始连续编号");for(int team=0;team<2;team++){final int t=team;require(spawns.stream().anyMatch(s->s.team==t&&s.sector==stage),"每个突破阶段双方都需要出生点");}for(Point p:points)if(p.sector==stage)require(inSector(stage,p.x,p.z),"据点在所属阶段边界外");for(Spawn s:spawns)if(s.sector==stage)require(inSector(stage,s.x,s.z),"出生点在所属阶段边界外");}}
            validateZones();
            validateSupplies();
            validateArmaments();
            range(exitMargin,0,512,"退出边界距离");range(tickets,1,100000,"兵力");range(killScore,0,10000,"击杀分");range(captureScore,0,10000,"占领分");range(deathCost,0,100,"阵亡扣票");range(bleed,0,100,"每秒扣票");range(captureSeconds,1,600,"占领秒数");range(respawnSeconds,0,120,"重生秒数");range(outsideSeconds,1,120,"越界秒数");range(roundMinutes,1,240,"局时");range(sectorBonus,0,10000,"阶段兵力奖励");range(markSeconds,1,60,"标记持续秒数");range(markScore,0,1000,"标记得分");range(assistScore,0,1000,"助攻得分");range(headshotBonus,0,1000,"爆头加成");range(comboBonus,0,1000,"连杀加成");range(revengeBonus,0,1000,"复仇加成");range(comboSeconds,1,30,"连杀窗口秒数");range(assistSeconds,1,60,"助攻窗口秒数");range(downSeconds,3,120,"倒地出血秒数");range(reviveSeconds,1,30,"救援所需秒数");range(reviveHealth,0,1000,"救援后生命值");range(botsPerTeam,1,32,"每队人机数量");range(vehicleCooldown,0,600,"载具冷却");range(vehicleLimit,1,32,"载具上限");range(blueVehicleLimit,-1,32,"蓝方载具名额");range(redVehicleLimit,-1,32,"红方载具名额");require(minY>=-2048&&maxY<=2048&&minY<maxY,"高度范围无效");
        }
        public boolean inSector(int stage,double x,double z){return sectors.stream().filter(s->s.index==stage).findFirst().map(s->contains(s.boundary,x,z)).orElse(true);}

        /**
         * Checks supply pads.
         *
         * <p>Deliberately permissive: a supply point restores something, has a sane radius and sits on
         * the map. It is <em>not</em> required to be inside a team's activity zone, because a neutral
         * pad both sides can reach is a legitimate and interesting map feature.
         *
         * <p>The one thing rejected outright is a pad that restores nothing — that is always a mistake,
         * and it would otherwise sit in the map invisibly doing no work.
         */
        private void validateSupplies() {
            if (supplies == null) return;
            require(supplies.size() <= 32, "补给点最多 32 个");
            Set<String> ids = new HashSet<>();
            for (VehicleSupply.Supply supply : supplies) {
                require(supply != null && validName(supply.id()), "补给点名称无效或重复");
                require(ids.add(supply.id()), "补给点名称无效或重复");
                require(supply.team() >= -1 && supply.team() <= 1, "补给点阵营为 -1（双方）/0/1");
                pos(supply.x(), supply.y(), supply.z());
                require(supply.y() >= minY && supply.y() <= maxY,
                        "补给点 " + supply.id() + " 的 Y 超出战区高度");
                require(contains(boundary, supply.x(), supply.z()),
                        "补给点 " + supply.id() + " 必须在地图边界内");
                require(supply.radius() >= 2 && supply.radius() <= 128,
                        "补给点半径 2～128");
                require(supply.restoresAnything(),
                        "补给点 " + supply.id() + " 没有开启任何补给项");
            }
        }

        /** Live supply pads, or an empty list for maps that predate them. */
        public List<VehicleSupply.Supply> supplyPoints() {
            return supplies == null ? List.of() : supplies;
        }

        /**
         * Checks the per-vehicle armament limits.
         *
         * <p>An entry for a vehicle that is not on the whitelist is rejected rather than ignored: it is
         * always a mistake (a typo, or a vehicle the operator removed but whose limits stayed behind),
         * and silently dropping it would leave the operator editing numbers that have no effect.
         */
        private void validateArmaments() {
            if (armaments == null) return;
            Model.require(armaments.size() <= 128, "载具配置最多 128 项");
            for (var entry : armaments.entrySet()) {
                String id = entry.getKey();
                Model.require(id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") && id.length() < 160,
                        "载具配置的载具 ID 无效：" + id);
                Model.require(vehicles.contains(id),
                        "载具「" + id + "」不在白名单内，其武器配置无效");
                Model.require(entry.getValue() != null, "载具配置不能为空：" + id);
                entry.getValue().validate();
            }
        }

        /** Armament limits for a vehicle, or an empty configuration when none was set. */
        public VehicleArmament armament(String vehicle) {
            if (armaments == null || vehicle == null) return new VehicleArmament();
            VehicleArmament found = armaments.get(vehicle);
            return found == null ? new VehicleArmament() : found;
        }

        /** The pad servicing this position for this team, or {@code null}. */
        public VehicleSupply.Supply supplyAt(int team, double x, double y, double z) {
            return VehicleSupply.supplyAt(supplyPoints(), team, x, y, z);
        }

        /** The activity zone for a team, or {@code null} when that team is unconstrained. */
        public Zone zone(int team) {
            if (zones == null) return null;
            for (Zone zone : zones) {
                if (zone != null && zone.team == team && zone.active()) return zone;
            }
            return null;
        }

        /**
         * The polygon the deploy camera should frame for a team: its activity zone when it has one,
         * otherwise the whole map boundary.
         *
         * <p>Framing the zone rather than the map is the point of having zones: a team confined to a
         * third of the map wants that third filling the view, not a distant corner of it.
         */
        public List<Vec> viewArea(int team) {
            Zone zone = zone(team);
            return zone == null ? boundary : zone.boundary;
        }

        /** Centre and span of a polygon, as {@code {centreX, centreZ, span}}; span is the larger extent. */
        public double[] extent(List<Vec> polygon) {
            if (polygon == null || polygon.isEmpty()) return new double[]{0, 0, 0};
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (Vec v : polygon) {
                if (v == null) continue;
                minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
                minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
            }
            if (minX > maxX) return new double[]{0, 0, 0};
            return new double[]{(minX + maxX) / 2, (minZ + maxZ) / 2, Math.max(maxX - minX, maxZ - minZ)};
        }

        /**
         * Whether a team may be at this position under its activity zone.
         *
         * <p>An unconstrained team returns true everywhere, so this is safe to call for maps that
         * predate zones. Height is deliberately not part of the test: the zone is a floor plan, and a
         * player in an aircraft above their zone is still in it.
         */
        public boolean inZone(int team, double x, double z) {
            Zone zone = zone(team);
            return zone == null || zone.contains(x, z);
        }

        /**
         * Checks the two activity zones against the rest of the map.
         *
         * <p>The rules that matter are the ones that would otherwise produce an unplayable match rather
         * than a merely odd one:
         * <ul>
         *   <li>A zone must sit inside the map boundary. Part of it hanging outside would create a
         *       strip that is both "in your zone" and "outside the map", and the two boundary checks
         *       would fight over which one fires.</li>
         *   <li>Every spawn of that team must be inside its own zone, or the team spawns outside its
         *       allowed area and is immediately treated as having left the battle.</li>
         *   <li>Every capture point must be inside <b>at least one</b> zone. A point in neither zone is
         *       unreachable by anyone: it can never be captured, so in conquest it is dead weight and
         *       the bleed calculation is decided by the remaining points before the match starts.</li>
         * </ul>
         *
         * <p>Deliberately <em>not</em> required: that both teams can reach every point. A point inside
         * only one zone is that team's home objective — a legitimate design — so it is reported by the
         * editor rather than rejected here. Requiring it would also force the two zones to overlap
         * everywhere the flags are, which defeats the point of drawing separate zones at all.
         *
         * <p>Known approximation: zone vertices are checked against the map boundary, not the whole
         * polygon against it, so a concave map boundary could in principle allow a zone edge to cross
         * outside between two vertices. Rejecting that properly needs polygon clipping; the vertex test
         * catches the mistake operators actually make (a zone drawn past the edge).
         */
        private void validateZones() {
            if (zones == null || zones.isEmpty()) return;
            require(mode.equals("CONQUEST"),
                    "活动区域只用于征服模式；突破模式由阶段边界推进，两者同时生效会互相矛盾");
            java.util.Set<Integer> seen = new HashSet<>();
            boolean anyActive = false;
            for (Zone zone : zones) {
                require(zone != null && (zone.team == 0 || zone.team == 1), "活动区域阵营为 0 或 1");
                require(seen.add(zone.team), "每个阵营最多一个活动区域");
                if (!zone.active()) continue; // an empty zone means "unrestricted"
                anyActive = true;
                polygon(zone.boundary);
                for (Vec v : zone.boundary) {
                    require(contains(boundary, v.x, v.z), "活动区域必须完全位于地图边界内");
                }
                for (Spawn spawn : spawns) {
                    if (spawn.team != zone.team) continue;
                    require(zone.contains(spawn.x, spawn.z),
                            "出生点 " + spawn.id + " 不在本方活动区域内");
                }
            }
            if (!anyActive) return;
            for (Point point : points) {
                boolean reachable = false;
                for (Zone zone : zones) {
                    if (zone.active() && zone.contains(point.x, point.z)) reachable = true;
                }
                require(reachable, "据点 " + point.id + " 不在任何一方的活动区域内，任何一方都无法占领它");
            }
        }
    }
    public static void require(boolean yes,String message){if(!yes)throw new IllegalArgumentException(message);}
    /**
     * A spawn's launch mask: only the characters G, A and N, at most one of each, non-empty.
     *
     * <p>{@code null} is accepted on purpose. Maps saved before this field existed deserialise with
     * {@code allows == null}, and rejecting them here would make every existing map fail to load
     * after an update. {@link Spawn#allows(char)} already treats a blank mask as "ground vehicles",
     * which is exactly what those maps meant when they were authored.
     */
    static boolean validAllows(String allows){
        if(allows==null)return true;
        if(allows.isEmpty()||allows.length()>3)return false;
        Set<Character> seen=new HashSet<>();
        for(char c:allows.toCharArray()){
            char up=Character.toUpperCase(c);
            if(up!='G'&&up!='A'&&up!='N')return false;
            if(!seen.add(up))return false;
        }
        // "N" means infantry-only and cannot be combined with anything.
        return !(seen.contains('N')&&seen.size()>1);
    }
    static boolean validName(String s){return s!=null&&!s.isBlank()&&s.length()<=32;}
    static void range(int n,int lo,int hi,String s){require(n>=lo&&n<=hi,s+"范围 "+lo+"～"+hi);}
    static void pos(double x,double y,double z){require(Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<29900000&&Math.abs(z)<29900000&&y>=-2048&&y<=2048,"坐标无效");}
    static double cross(Vec a,Vec b,Vec c){return (b.x-a.x)*(c.z-a.z)-(b.z-a.z)*(c.x-a.x);}
    static boolean on(Vec a,Vec b,Vec p){return Math.abs(cross(a,b,p))<1e-7&&p.x>=Math.min(a.x,b.x)-1e-7&&p.x<=Math.max(a.x,b.x)+1e-7&&p.z>=Math.min(a.z,b.z)-1e-7&&p.z<=Math.max(a.z,b.z)+1e-7;}
    static boolean intersect(Vec a,Vec b,Vec c,Vec d){double u=cross(a,b,c),v=cross(a,b,d),w=cross(c,d,a),t=cross(c,d,b);return u*v<0&&w*t<0||on(a,b,c)||on(a,b,d)||on(c,d,a)||on(c,d,b);}
    public static void polygon(List<Vec> p){require(p!=null&&p.size()>=3&&p.size()<=64,"边界需要 3～64 个顶点");double area=0;for(int i=0;i<p.size();i++){Vec a=p.get(i),b=p.get((i+1)%p.size());require(a!=null,"边界顶点无效");pos(a.x,0,a.z);require(!a.equals(b),"边界顶点重复");area+=a.x*b.z-b.x*a.z;for(int j=i+2;j<p.size();j++){if(i==0&&j==p.size()-1)continue;require(!intersect(a,b,p.get(j),p.get((j+1)%p.size())),"边界不能自交");}}require(Math.abs(area)>16,"地图面积过小");}
    public static boolean contains(List<Vec> p,double x,double z){if(p==null||p.size()<3)return false;boolean in=false;Vec q=new Vec(x,z);for(int i=0,j=p.size()-1;i<p.size();j=i++){Vec a=p.get(i),b=p.get(j);if(on(a,b,q))return true;if((a.z>z)!=(b.z>z)&&x<(b.x-a.x)*(z-a.z)/(b.z-a.z)+a.x)in=!in;}return in;}
}
