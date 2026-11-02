package cn.bfops;
final class MarkerTiming {
    static long remaining(long snapshotRemaining,long elapsed){return Math.max(0,snapshotRemaining-Math.max(0,elapsed));}
    static float alpha(long snapshotRemaining,long elapsed){return (float)Math.min(1,remaining(snapshotRemaining,elapsed)/1200.0);}
}
