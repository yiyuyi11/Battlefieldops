package cn.bfops.voice.common.audio;

import cn.bfops.voice.VoiceMain;

import org.jetbrains.annotations.Nullable;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CueRouter {
    private static final Map<VoiceCue, Map<CampType, String>> fpCues = new EnumMap<>(VoiceCue.class);
    private static final Map<VoiceCue, Map<CampType, String>> tpCues = new EnumMap<>(VoiceCue.class);
    private static boolean initialized = false;

    private CueRouter() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }

        FrontVoiceAudioManifest manifest = FrontVoiceAudioManifest.get();
        for (String eventId : manifest.getEventIds()) {
            String dir = manifest.getPrimaryOriginalDirectory(eventId);
            if (dir == null || dir.isBlank()) {
                continue;
            }

            CampType camp = CampType.COMMON;
            if (dir.contains("/CN/")) camp = CampType.CN;
            else if (dir.contains("/US/")) camp = CampType.US;
            else if (dir.contains("/RU/")) camp = CampType.RU;

            boolean isFp = dir.contains("第一人称");
            boolean isTp = dir.contains("第三人称");

            String displayName = manifest.getDisplayName(eventId);

            // Special cases where they are not in standard fp/tp folders
            if (!isFp && !isTp) {
                if (dir.contains("被救起来的第一人称")) {
                    isFp = true;
                } else if (dir.contains("放弃or最终死亡")) {
                    isFp = true; // It's a flat sound according to requirements
                } else if (dir.contains("倒地呼救")) {
                    isTp = true;
                } else if (dir.contains("電擊器") || dir.contains("醫療用針筒") || displayName != null && displayName.toLowerCase(java.util.Locale.ROOT).contains("defibrillator")) {
                    isTp = true; // Medical revive sounds are third person
                }
            }

            VoiceCue cue = matchCue(eventId, dir, displayName);
            if (cue != null) {
                if (isFp) {
                    fpCues.computeIfAbsent(cue, k -> new EnumMap<>(CampType.class)).putIfAbsent(camp, eventId);
                }
                if (isTp) {
                    tpCues.computeIfAbsent(cue, k -> new EnumMap<>(CampType.class)).putIfAbsent(camp, eventId);
                }
            }
        }

        initialized = true;
        VoiceMain.LOGGER.info("CueRouter initialized. FP cues: {}, TP cues: {}", fpCues.size(), tpCues.size());
    }

    @Nullable
    private static VoiceCue matchCue(String eventId, String dir, String displayName) {
        String lowerEventId = eventId == null ? "" : eventId.toLowerCase(java.util.Locale.ROOT);
        if (lowerEventId.contains("downed.defibrillator.defibrillator_fire_wave")) return VoiceCue.REVIVE_ASSAULT_START;
        if (lowerEventId.contains("downed.defibrillator.defibrillator_charged_wave")) return VoiceCue.REVIVE_ASSAULT_CHARGE;
        if (lowerEventId.contains("downed.defibrillator.defibrillator_deploy_wave")) return VoiceCue.REVIVE_ASSAULT_FINISH;
        if (lowerEventId.contains("downed.defibrillator.defibrillator_rub_wave")) return VoiceCue.REVIVING;
        if (lowerEventId.contains("downed.syringe.gadget_syringe_inject")) return VoiceCue.REVIVE_NORMAL_START;
        if (lowerEventId.contains("downed.syringe.gadget_syringe_fill")) return VoiceCue.REVIVE_NORMAL_FINISH;
        if (displayName != null) {
            String lowerDisplay = displayName.toLowerCase(java.util.Locale.ROOT);
            if (lowerDisplay.contains("defibrillator_fire_wave")) return VoiceCue.REVIVE_ASSAULT_START;
            if (lowerDisplay.contains("defibrillator_charged_wave")) return VoiceCue.REVIVE_ASSAULT_CHARGE;
            if (lowerDisplay.contains("defibrillator_deploy_wave")) return VoiceCue.REVIVE_ASSAULT_FINISH;
            if (lowerDisplay.contains("gadget_syringe_inject")) return VoiceCue.REVIVE_NORMAL_START;
            if (lowerDisplay.contains("gadget_syringe_fill")) return VoiceCue.REVIVE_NORMAL_FINISH;
        }

        if (dir.contains("放弃or最终死亡")) return VoiceCue.FINAL_DEATH;
        if (dir.contains("丢医疗") || dir.contains("医疗包")) return VoiceCue.THROW_MEDKIT;
        if (dir.contains("丢弹药") || dir.contains("给你弹药")) return VoiceCue.THROW_AMMO;
        if (dir.contains("丢手雷") || dir.contains("我要丢手雷") || dir.contains("要丢手雷")) return VoiceCue.THROW_GRENADE;
        if (dir.contains("丢烟") || dir.contains("烟雾弹")) return VoiceCue.THROW_SMOKE;
        if (dir.contains("需要医疗") || dir.contains("需要急救")) return VoiceCue.DOWNED_HELP;
        if (dir.contains("需要弹药")) return VoiceCue.REQUEST_AMMO;
        if (dir.contains("需要维修")) return VoiceCue.REQUEST_REPAIR;
        if (dir.contains("放c4") || dir.contains("放置c4") || dir.contains("C4")) return VoiceCue.PLACE_C4;
        if (dir.contains("索敌敌军机枪手") || (dir.contains("敌军机枪手") && !dir.contains("索敌"))) return VoiceCue.SPOTTED_SUPPORT;
        if (dir.contains("反坦克")) return VoiceCue.SPOTTED_ENGINEER;
        if (dir.contains("敌方狙击手")) return VoiceCue.SPOTTED_RECON;
        if (dir.contains("发现敌兵")) return VoiceCue.SPOTTED_ENEMY;
        if (dir.contains("被救起来")) return VoiceCue.REVIVED;
        if (dir.contains("受不了了")) return VoiceCue.LOW_HEALTH_PANIC;
        if (dir.contains("换子弹")) return VoiceCue.RELOADING;
        if (dir.contains("急救队友")) return VoiceCue.REVIVING;
        if (dir.contains("压制住")) return VoiceCue.SUPPRESSED;
        if (dir.contains("持续压制")) return VoiceCue.SUPPRESSING_HOSTILE;
        if (dir.contains("冲冲冲")) return VoiceCue.GOGOGO;
        if (dir.contains("打得好")) return VoiceCue.NICE_SHOT;
        if (dir.contains("击杀")) return VoiceCue.KILL_ENEMY;
        if (dir.contains("火箭弹")) return VoiceCue.RPG_ALERT;
        if (dir.contains("敌军狙击")) return VoiceCue.SNIPER_ALERT;
        if (dir.contains("队友倒下")) return VoiceCue.TEAMMATE_DOWNED;
        if (dir.contains("差点炸到")) return VoiceCue.EXPLOSION_CLOSE;
        if (dir.contains("手雷在我旁边") || dir.contains("手雷")) return VoiceCue.GRENADE_ALERT;
        if (dir.contains("受伤")) return VoiceCue.HEAVY_HURT;
        if (dir.contains("战况激烈")) return VoiceCue.SUPPRESSING_HOSTILE;
        if (dir.contains("友军")) return VoiceCue.FRIENDLY_FIRE;
        if (dir.contains("死亡") || dir.contains("惨叫") || dir.contains("倒下")) return VoiceCue.DEATH;
        if (dir.contains("着火") || dir.contains("被火焰点燃")) return VoiceCue.BURNING;
        if (dir.contains("谢谢")) return VoiceCue.THANKS;

        return null;
    }

    @Nullable
    public static String resolveFirstPerson(VoiceCue cue, CampType camp) {
        List<String> eventIds = resolveFirstPersonLayers(cue, camp);
        return eventIds.isEmpty() ? null : eventIds.get(0);
    }

    @Nullable
    public static String resolveThirdPerson(VoiceCue cue, CampType camp) {
        if (!initialized) init();
        String explicitEventId = resolveExplicitEventId("tp", cue, camp);
        if (explicitEventId != null) {
            return explicitEventId;
        }
        Map<CampType, String> camps = tpCues.get(cue);
        if (camps == null) return null;
        String eventId = camps.get(camp);
        if (eventId == null) eventId = camps.get(CampType.COMMON);
        return eventId;
    }

    public static List<String> resolveFirstPersonLayers(VoiceCue cue, CampType camp) {
        if (!initialized) init();
        if (cue == null) {
            return List.of();
        }
        Set<String> resolved = new LinkedHashSet<>();
        switch (cue) {
            case DEATH -> {
                addIfPresent(resolved, "fp.death.death");
                addIfPresent(resolved, "fp.death.sfx");
            }
            case BURNING -> {
                addIfPresent(resolved, "fp.burning.scream");
                addIfPresent(resolved, "fp.burning.flame");
                addIfPresent(resolved, "fp.burning.spark");
            }
            case THROW_AMMO -> addIfPresent(resolved, buildCampedEventId("fp", camp, "throw_ammo"));
            case THROW_MEDKIT -> addIfPresent(resolved, buildCampedEventId("fp", camp, "throw_medkit"));
            case REVIVE_NORMAL_START -> addFirstPresent(resolved, "downed.syringe.gadget_syringe_inject", "downed.syringe");
            case REVIVE_NORMAL_FINISH -> addFirstPresent(resolved, "downed.syringe.gadget_syringe_fill", "downed.syringe");
            case REVIVE_ASSAULT_START -> addFirstPresent(resolved, "downed.defibrillator.defibrillator_fire_wave", "downed.defibrillator");
            case REVIVE_ASSAULT_CHARGE -> addFirstPresent(resolved, "downed.defibrillator.defibrillator_charged_wave", "downed.defibrillator");
            case REVIVE_ASSAULT_FINISH -> addFirstPresent(resolved, "downed.defibrillator.defibrillator_deploy_wave", "downed.defibrillator");
            default -> {
                String eventId = resolveMappedEvent(fpCues, cue, camp);
                if (eventId != null) {
                    resolved.add(eventId);
                }
            }
        }
        return List.copyOf(resolved);
    }

    @Nullable
    private static String resolveExplicitEventId(String category, VoiceCue cue, CampType camp) {
        if (cue == null) {
            return null;
        }
        String eventId = switch (cue) {
            case THROW_AMMO -> buildCampedEventId(category, camp, "throw_ammo");
            case THROW_MEDKIT -> buildCampedEventId(category, camp, "throw_medkit");
            default -> null;
        };
        if (eventId != null && FrontVoiceAudioManifest.get().contains(eventId)) {
            return eventId;
        }
        return null;
    }

    @Nullable
    private static String buildCampedEventId(String category, CampType camp, String key) {
        if (category == null || key == null || key.isBlank()) {
            return null;
        }
        CampType resolvedCamp = camp == null ? CampType.COMMON : camp;
        return switch (resolvedCamp) {
            case CN -> category + ".cn." + key;
            case US -> category + ".us." + key;
            case RU -> category + ".ru." + key;
            case COMMON -> null;
        };
    }

    @Nullable
    private static String resolveMappedEvent(Map<VoiceCue, Map<CampType, String>> cueMap, VoiceCue cue, CampType camp) {
        Map<CampType, String> camps = cueMap.get(cue);
        if (camps == null) {
            return null;
        }
        String eventId = camps.get(camp);
        if (eventId == null) {
            eventId = camps.get(CampType.COMMON);
        }
        return eventId;
    }

    private static void addIfPresent(Set<String> resolved, String eventId) {
        if (FrontVoiceAudioManifest.get().contains(eventId)) {
            resolved.add(eventId);
        }
    }

    private static void addFirstPresent(Set<String> resolved, String... eventIds) {
        for (String eventId : eventIds) {
            if (FrontVoiceAudioManifest.get().contains(eventId)) {
                resolved.add(eventId);
                return;
            }
        }
    }
}
