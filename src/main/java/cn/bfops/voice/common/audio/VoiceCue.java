package cn.bfops.voice.common.audio;

public enum VoiceCue {
    DOWNED_HELP,
    REQUEST_AMMO,
    HEAVY_HURT,
    EXPLOSION_CLOSE,
    GRENADE_ALERT,
    TEAMMATE_DOWNED,
    THROW_GRENADE,
    SUPPRESSED,
    SNIPER_ALERT,
    RPG_ALERT,
    THROW_MEDKIT,
    THROW_AMMO,
    SPOTTED_ENEMY,
    REVIVED,
    SUPPRESSING_HOSTILE,
    GOGOGO,
    NICE_SHOT,
    KILL_ENEMY,
    MACHINE_GUN_ALERT,
    THROW_SMOKE,
    SPOTTED_ENGINEER,
    SPOTTED_RECON,
    SPOTTED_SUPPORT,
    LOW_HEALTH_PANIC,
    RELOADING,
    REVIVING,
    REVIVE_ASSAULT_START,
    REVIVE_ASSAULT_CHARGE,
    REVIVE_ASSAULT_FINISH,
    REVIVE_NORMAL_START,
    REVIVE_NORMAL_FINISH,
    FINAL_DEATH,
    PLACE_C4,
    FRIENDLY_FIRE,
    DEATH,
    BURNING,
    THANKS,
    REQUEST_REPAIR;

    public static VoiceCue fromString(String name) {
        if (name == null) {
            return null;
        }
        try {
            String normalized = name.toUpperCase(java.util.Locale.ROOT);
            if (normalized.startsWith("PLAYER_")) {
                normalized = normalized.substring(7);
            }
            return VoiceCue.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
