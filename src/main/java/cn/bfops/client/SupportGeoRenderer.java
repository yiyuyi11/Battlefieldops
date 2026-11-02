package cn.bfops.client;

import cn.bfops.SupportItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** The reference mod's renderer, one instance for every support item. */
public class SupportGeoRenderer extends GeoItemRenderer<SupportItem> {
    public SupportGeoRenderer() {
        super(new SupportItemModel());
    }
}
