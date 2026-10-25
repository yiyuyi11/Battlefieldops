package cn.bfops;

/** Liang-Barsky clipping keeps zoomed-in boundary lines crisp and work proportional to the viewport. */
final class MapGeometry {
    static int[] clip(int x0,int y0,int x1,int y1,int left,int top,int right,int bottom) {
        double dx=(double)x1-x0,dy=(double)y1-y0,lo=0,hi=1;
        double[] p={-dx,dx,-dy,dy},q={x0-(double)left,right-(double)x0,y0-(double)top,bottom-(double)y0};
        for(int i=0;i<4;i++) {
            if(p[i]==0){if(q[i]<0)return null;continue;}
            double r=q[i]/p[i];
            if(p[i]<0)lo=Math.max(lo,r);else hi=Math.min(hi,r);
            if(lo>hi)return null;
        }
        return new int[]{(int)Math.round(x0+lo*dx),(int)Math.round(y0+lo*dy),(int)Math.round(x0+hi*dx),(int)Math.round(y0+hi*dy)};
    }

    /**
     * The map's screen/world projection, as a value.
     *
     * <p>Extracted from the widget so it can be checked without a client level: a map that paints a
     * marker at one pixel and hit-tests a different one is the failure mode this guards against, and
     * that is a pure-maths property. {@code x} scales a square world window onto the panel width,
     * which is what keeps circles round and markers under the cursor.
     */
    record MapProjection(int x, int y, int w, int h, double cx, double cz, double span) {
        double blocksPerPixel() {
            return span / Math.max(1, w);
        }

        int sx(double worldX) {
            return x + w / 2 + (int) Math.round((worldX - cx) / span * w);
        }

        int sz(double worldZ) {
            return y + h / 2 + (int) Math.round((worldZ - cz) / span * w);
        }

        double worldX(double screenX) {
            return cx + (screenX - x - w / 2.0) * span / w;
        }

        double worldZ(double screenY) {
            return cz + (screenY - y - h / 2.0) * span / w;
        }

        boolean inside(double screenX, double screenY) {
            return screenX >= x && screenX < x + w && screenY >= y && screenY < y + h;
        }
    }

    static MapProjection of(Layout.Rect body, double cx, double cz, double span) {
        return new MapProjection(body.x(), body.y(), Math.max(1, body.w()),
                Math.max(1, body.h()), cx, cz, Math.max(8, span));
    }
}
