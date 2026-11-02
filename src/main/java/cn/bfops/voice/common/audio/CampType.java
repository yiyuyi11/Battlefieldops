package cn.bfops.voice.common.audio;

public enum CampType {
    CN,
    US,
    RU,
    COMMON;

    public static CampType fromString(String name) {
        if (name == null) {
            return COMMON;
        }
        return switch (name.toUpperCase(java.util.Locale.ROOT)) {
            case "CN" -> CN;
            case "US" -> US;
            case "RU" -> RU;
            default -> COMMON;
        };
    }
}
