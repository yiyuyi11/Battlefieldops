package cn.bfops.voice.common.audio;

public enum CampType {
    CN,
    US,
    RU,
    COMMON,
    UK, DE, FR, IT, TK, NONE;

    public static CampType fromString(String name) {
        if (name == null) {
            return COMMON;
        }
        return switch (name.toUpperCase(java.util.Locale.ROOT)) {
            case "CN" -> CN;
            case "US" -> US;
            case "RU" -> RU;
            case "UK" -> UK;
            case "DE" -> DE;
            case "FR" -> FR;
            case "IT" -> IT;
            case "TK" -> TK;
            case "NONE" -> NONE;
            default -> COMMON;
        };
    }
}
