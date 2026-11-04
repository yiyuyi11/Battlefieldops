package cn.bfops;
/** The mask is already cropped to visible pixels; the configured size is its on-screen height. */
final class PlayerMarkerSize {
    static int self(float configured){return Math.round(Float.isFinite(configured)?cn.bfops.Java17.clamp(configured,4,20):9);}
    static final int ALLY=10;
}
