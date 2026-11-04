package cn.bfops;
public final class Java17 {
 public static int clamp(int v,int a,int b){return Math.max(a,Math.min(b,v));}
 public static int clamp(long v,int a,int b){return (int)Math.max(a,Math.min(b,v));}
 public static long clamp(long v,long a,long b){return Math.max(a,Math.min(b,v));}
 public static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
 public static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
}
