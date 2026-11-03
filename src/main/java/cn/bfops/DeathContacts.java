package cn.bfops;

import com.google.gson.JsonObject;
import java.util.*;

/** Nearby medics are selected exclusively from the server's authorized contact snapshot. */
final class DeathContacts {
    record Row(String name, int metres) {}
    static List<Row> medics(JsonObject state, JsonObject me) {
        var out = new ArrayList<Row>();
        if (!state.has("mates") || !me.has("x") || !me.has("z")) return List.of();
        for (var element : state.getAsJsonArray("mates")) {
            if (!element.isJsonObject()) continue;
            var mate = element.getAsJsonObject();
            if (!mate.has("x") || !mate.has("z") || flag(mate,"self") || flag(mate,"hostile")
                    || !flag(mate,"alive") || flag(mate,"downed")) continue;
            String name = string(mate,"name");
            if (name.isEmpty() || name.equals(string(me,"name"))) continue;
            if (!"support".equals(FixedClasses.stem(string(mate,"kit")))) continue;
            if (mate.has("team") && me.has("team") && !mate.get("team").equals(me.get("team"))) continue;
            double dx = coordinate(mate,"x")-coordinate(me,"x");
            double dz = coordinate(mate,"z")-coordinate(me,"z");
            double dy = mate.has("y") && me.has("y") ? coordinate(mate,"y")-coordinate(me,"y") : 0;
            double distance = Math.sqrt(dx*dx+dy*dy+dz*dz);
            if (Double.isFinite(distance) && distance <= 40) out.add(new Row(name,(int)Math.round(distance)));
        }
        out.sort(Comparator.comparingInt(Row::metres).thenComparing(Row::name));
        return out.stream().limit(3).toList();
    }
    private static double coordinate(JsonObject value,String key){return value.get(key).getAsDouble();}
    private static String string(JsonObject value,String key){return value.has(key)?value.get(key).getAsString():"";}
    private static boolean flag(JsonObject value,String key){return value.has(key)&&value.get(key).getAsBoolean();}
}
