package cn.bfops;
/** The mask is already cropped to visible pixels; the configured size is its on-screen height. */
final class PlayerMarkerSize {
    static int self(float configured){return Math.round(Float.isFinite(configured)?Math.clamp(configured,4,20):9);}
    static final int ALLY=10;
}
