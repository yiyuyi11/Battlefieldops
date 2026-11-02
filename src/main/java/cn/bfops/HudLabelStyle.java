package cn.bfops;

/** Screen-space text stays readable without leaving an opaque plate when its marker fades. */
final class HudLabelStyle {
    private HudLabelStyle() {}
    static int backing(int foreground){return ((foreground>>>24)*88/255)<<24|0x08121c;}
    /** World labels stay slightly see-through even when the marker is fully visible. */
    static float focusedOpacity(float markerAlpha){return Math.max(0,Math.min(0.8f,markerAlpha*14));}
}
