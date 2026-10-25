package cn.bfops;

import java.util.List;

/** Conservative GUI-pixel exclusion zones; never draw when no whole card fits. */
final class AwardLayout {
    record Rect(int x, int y, int w, int h) {
        boolean overlaps(Rect b) { return x < b.x+b.w && x+w > b.x && y < b.y+b.h && y+h > b.y; }
        boolean fits(int width,int height) { return x>=8 && y>=8 && x+w<=width-8 && y+h<=height-8; }
    }
    static List<Rect> vehicleZones(int w,int h) {
        return List.of(new Rect(0,0,w,42), new Rect(0,Math.max(0,h-110),w,110),
                new Rect(w/2-156,h/2-90,312,180));
    }
    static Rect place(int w,int h,boolean vehicle,int rows,List<Rect> extra) {
        int width = vehicle ? 148 : Math.min(206,w-16), height = rows*34;
        if (width<140 || rows<1) return null;
        var zones = new java.util.ArrayList<>(extra);
        if (vehicle) zones.addAll(vehicleZones(w,h));
        else {
            zones.add(new Rect(0,h-82,w,82)); // objective / hotbar
            zones.add(new Rect(w/2-32,h/2-24,64,48)); // crosshair
        }
        // Prefer below the reticle on foot, right upper corner in vehicles.
        if (!vehicle) {
            Rect preferred = new Rect((w-width)/2,h/2+32,width,height);
            if (clear(preferred,w,h,zones)) return preferred;
        }
        for (int y=46;y+height<=h-8;y+=4) {
            for (int x : new int[]{w-width-8,8}) {
                Rect candidate = new Rect(x,y,width,height);
                if (clear(candidate,w,h,zones)) return candidate;
            }
        }
        return null;
    }
    static boolean clear(Rect rect,int w,int h,List<Rect> zones) {
        return rect.fits(w,h) && zones.stream().noneMatch(rect::overlaps);
    }
}
