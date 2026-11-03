package cn.bfops;
public final class MarkerTimingSim {
    static int checks;static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
    public static void main(String[] args){
        check(MarkerTiming.remaining(2000,0)==2000,"initial duration retained");
        check(MarkerTiming.remaining(2000,800)==1200,"duration advances between snapshots");
        check(MarkerTiming.remaining(2000,2100)==0,"expired mark cannot linger");
        check(MarkerTiming.remaining(2000,-10)==2000,"negative elapsed cannot extend lifetime");
        check(MarkerTiming.alpha(2000,0)==1,"full opacity away from expiry");
        check(Math.abs(MarkerTiming.alpha(2000,1400)-.5)<1e-6,"continuous halfway fade");
        check(MarkerTiming.alpha(2000,2000)==0,"fully transparent at expiry");
        float previous=1;for(int ms=0;ms<2500;ms++){float alpha=MarkerTiming.alpha(2000,ms);check(alpha<=previous&&alpha>=0&&alpha<=1,"fade never increases or overshoots");previous=alpha;}
        System.out.println("MarkerTimingSim: "+checks+" checks passed");
    }
}
