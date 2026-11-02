package cn.bfops.crawl;

/** Clock-injected transition guard; an initial standing observation must never block shooting. */
public final class CrawlWindow {
    private boolean crawling;
    private boolean transitioned;
    private long changed;
    public CrawlWindow(boolean initial,long now){crawling=initial;changed=now;transitioned=initial;}
    public void observe(boolean current,long now){if(current!=crawling){crawling=current;changed=now;transitioned=true;}}
    public boolean blocked(long now,long enter,long exit){return transitioned&&Math.max(0,now-changed)<(crawling?enter:exit);}
}
