package cn.bfops.crawl;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CrawlConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.LongValue CRAWL_ENTER_BLOCK_MS,CRAWL_EXIT_BLOCK_MS;
    public static final ModConfigSpec.DoubleValue PRONE_HEAD_PART_OFFSET,PRONE_FOOT_PART_OFFSET,JUMP_INACCURACY_MULTIPLIER;
    static {
        var b=new ModConfigSpec.Builder();b.push("tacz_crawl");
        CRAWL_ENTER_BLOCK_MS=b.defineInRange("crawl_enter_block_ms",1000L,0L,30000L);
        CRAWL_EXIT_BLOCK_MS=b.defineInRange("crawl_exit_block_ms",500L,0L,30000L);
        PRONE_HEAD_PART_OFFSET=b.defineInRange("prone_head_part_offset",.42,0,4);
        PRONE_FOOT_PART_OFFSET=b.defineInRange("prone_foot_part_offset",.62,0,4);
        JUMP_INACCURACY_MULTIPLIER=b.defineInRange("jump_inaccuracy_multiplier",100.0,0,10000);
        b.pop();SPEC=b.build();
    }
}
