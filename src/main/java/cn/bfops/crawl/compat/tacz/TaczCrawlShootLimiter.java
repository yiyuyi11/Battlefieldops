package cn.bfops.crawl.compat.tacz;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import cn.bfops.crawl.CrawlConfig;
import cn.bfops.crawl.CrawlWindow;
public final class TaczCrawlShootLimiter {
 private static final Map<UUID,CrawlWindow> CLIENT=new ConcurrentHashMap<>(),SERVER=new ConcurrentHashMap<>();
 public static double jumpInaccuracyMultiplier(){return CrawlConfig.SPEC.isLoaded()?CrawlConfig.JUMP_INACCURACY_MULTIPLIER.get():100;}
 private static CrawlWindow observe(Map<UUID,CrawlWindow> windows,UUID id,boolean crawl,long now){
  CrawlWindow state=windows.computeIfAbsent(id,k->new CrawlWindow(crawl,now));state.observe(crawl,now);return state;
 }
 public static void updateClientCrawlState(UUID id,boolean crawl,long now){if(id!=null)observe(CLIENT,id,crawl,now);}
 public static void updateServerCrawlState(UUID id,boolean crawl,long now){if(id!=null)observe(SERVER,id,crawl,now);}
 private static boolean blocked(Map<UUID,CrawlWindow> windows,UUID id,boolean crawl,long now){
  if(id==null)return false;
  long enter=CrawlConfig.SPEC.isLoaded()?CrawlConfig.CRAWL_ENTER_BLOCK_MS.get():1000;
  long exit=CrawlConfig.SPEC.isLoaded()?CrawlConfig.CRAWL_EXIT_BLOCK_MS.get():500;
  return observe(windows,id,crawl,now).blocked(now,enter,exit);
 }
 public static boolean shouldBlockClientShoot(UUID id,boolean crawl,long now){return blocked(CLIENT,id,crawl,now);}
 public static boolean shouldBlockServerShoot(UUID id,boolean crawl,long now){return blocked(SERVER,id,crawl,now);}
 public static void clear(UUID id){CLIENT.remove(id);SERVER.remove(id);}
 public static void clearClient(){CLIENT.clear();}
}
