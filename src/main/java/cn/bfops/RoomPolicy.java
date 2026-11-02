package cn.bfops;

/** Pure room visibility and polygon-buffer policy, shared by tests and the server. */
final class RoomPolicy {
    static boolean canManage(boolean admin,java.util.UUID player,java.util.UUID owner){return admin||owner!=null&&owner.equals(player);}
    static boolean sameRoom(String membership,String requested){return membership!=null&&!membership.isEmpty()&&membership.equals(requested);}
    static boolean canView(String membership, int team, String selected, boolean running) {
        return running && team >= 0 && team <= 1 && selected.equals(membership);
    }

    /** Reserve whole chunks including the non-death exit buffer, not just the visible polygon. */
    static boolean overlaps(Model.Arena a,Model.Arena b){
        if(!a.dimension.equals(b.dimension))return false;
        long[] x=bounds(a),y=bounds(b);
        return x[0]<=y[1]&&x[1]>=y[0]&&x[2]<=y[3]&&x[3]>=y[2];
    }
    static long[] bounds(Model.Arena a){
        double loX=a.boundary.stream().mapToDouble(Model.Vec::x).min().orElse(0)-a.exitMargin;
        double hiX=a.boundary.stream().mapToDouble(Model.Vec::x).max().orElse(0)+a.exitMargin;
        double loZ=a.boundary.stream().mapToDouble(Model.Vec::z).min().orElse(0)-a.exitMargin;
        double hiZ=a.boundary.stream().mapToDouble(Model.Vec::z).max().orElse(0)+a.exitMargin;
        return new long[]{(long)Math.floor(loX/16),(long)Math.floor(hiX/16),(long)Math.floor(loZ/16),(long)Math.floor(hiZ/16)};
    }
    static boolean hubConflict(Model.Arena a,String dimension,double x,double z){
        if(!a.dimension.equals(dimension))return false;
        long[] b=bounds(a);long cx=(long)Math.floor(x/16),cz=(long)Math.floor(z/16);
        return cx>=b[0]-1&&cx<=b[1]+1&&cz>=b[2]-1&&cz<=b[3]+1;
    }
    static String joinBlocker(boolean member,boolean room,long players,int capacity){
        return member?"请先退出当前房间":!room?"房间已关闭，请刷新":players>=capacity?"房间人数已满":"";
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
