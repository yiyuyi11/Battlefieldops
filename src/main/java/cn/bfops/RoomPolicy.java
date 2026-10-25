package cn.bfops;

/** Pure room visibility and polygon-buffer policy, shared by tests and the server. */
final class RoomPolicy {
    static boolean canView(String membership, int team, String selected, boolean running) {
        return running && team >= 0 && team <= 1 && selected.equals(membership);
    }

    static boolean beyond(Model.Arena map, double x, double y, double z) {
        if (y < map.minY - map.exitMargin || y > map.maxY + map.exitMargin) return true;
        if (Model.contains(map.boundary, x, z)) return false;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < map.boundary.size(); i++) {
            Model.Vec a = map.boundary.get(i), b = map.boundary.get((i + 1) % map.boundary.size());
            double dx = b.x() - a.x(), dz = b.z() - a.z();
            double len = dx * dx + dz * dz;
            double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((x-a.x())*dx + (z-a.z())*dz)/len));
            distance = Math.min(distance, Math.hypot(x-a.x()-t*dx, z-a.z()-t*dz));
        }
        return distance > map.exitMargin;
    }
}
