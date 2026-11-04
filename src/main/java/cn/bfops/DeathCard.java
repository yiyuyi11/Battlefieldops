package cn.bfops;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The death overlay: BF1's "you are dead" card, drawn over the world while the death camera is running.
 *
 * <p>The camera half of the reference's death flow already lives in {@link Cinematic} (look up, drift,
 * hold, recover — the reference's own tick constants). This is the other half: the screen treatment that
 * tells the player what just happened before the deploy map takes over. KillCardInfo supplies the
 * identity/weapon split; NearbyMedicsContainer stays anchored at the lower-right over the battlefield.
 *
 * <p>The skull is BF1's own {@code death_hud_default}, the killer's class and weapon are the same white
 * glyphs the rest of the BF1-styled screens use, and the whole block fades and drifts up over the first
 * third of a second so it reads as an event rather than a pop-up.
 */
final class DeathCard {
    /** Long enough for the deploy key to be pressed, short enough not to sit there if the player waits. */
    private static final long HOLD_MS = 4500L;
    private static final long SKIP_FADE_MS = 200L;
    private static final float RISE_PIXELS = 6f;

    private static long shownAt;
    /** Set when the player asked to skip the death view (space); clears on the next death. */
    private static boolean dead;
    private static long releasedAt;
    private static String killer = "", kit = "", weapon = "";
    private static int distance;
    private static float killerHealth = -1, killerMaxHealth = -1;

    private DeathCard() {}

    static void show(JsonObject payload) {
        boolean downed = "downedCard".equals(Engine.str(payload, "type", ""));
        begin(!downed);
        killer = Engine.str(payload, "killer", "");
        kit = Engine.str(payload, "kit", "");
        weapon = Engine.str(payload, "weapon", "");
        distance = Engine.num(payload, "distance", 0);
        killerHealth = payload.has("killerHealth") ? payload.get("killerHealth").getAsFloat() : -1;
        killerMaxHealth = payload.has("killerMaxHealth") ? payload.get("killerMaxHealth").getAsFloat() : -1;
    }

    static void begin(boolean finalDeath) {
        if (shownAt > 0 && dead == finalDeath) return;
        shownAt = System.currentTimeMillis();
        releasedAt = 0;
        dead = finalDeath;
    }

    /**
     * True while the card is still owed to the player: the deploy map must not open underneath it.
     *
     * <p>This is the authority rather than a bare timestamp, because the server keeps offering the deploy
     * screen every snapshot and the two used to race.
     */
    static boolean blockingDeploy() {
        return dead && shownAt > 0 && System.currentTimeMillis() < endAt();
    }

    private static long endAt() {
        return releasedAt == 0 ? shownAt + HOLD_MS : Math.min(shownAt + HOLD_MS, releasedAt + SKIP_FADE_MS);
    }

    static void release() {
        if (dead && releasedAt == 0) releasedAt = System.currentTimeMillis();
    }

    static void reset() {
        shownAt = 0;
        dead = false;
        releasedAt = 0;
        medicState = null;
        medicRows = java.util.List.of();
        killer = "";
        kit = "";
        weapon = "";
        distance = 0;
        killerHealth = killerMaxHealth = -1;
    }

    private static JsonObject medicState;
    private static long medicAt;
    private static java.util.List<DeathContacts.Row> medicRows = java.util.List.of();
    private static java.util.List<DeathContacts.Row> nearbyMedics(JsonObject me) {
        long now = System.currentTimeMillis();
        if (medicState != Client.state || now-medicAt >= 250) {
            medicState = Client.state; medicAt = now;
            medicRows = DeathContacts.medics(Client.state,me);
        }
        return medicRows;
    }

    static void draw(GuiGraphics g, Minecraft mc) {drawView(g,mc,Client.state,false,false);}
    static void drawLocal(GuiGraphics g,Minecraft mc,JsonObject snapshot){drawView(g,mc,snapshot,true,false);}
    static void drawPreview(GuiGraphics g,Minecraft mc,JsonObject snapshot){drawView(g,mc,snapshot,true,true);}
    private static void drawView(GuiGraphics g,Minecraft mc,JsonObject state,boolean local,boolean preview){
        if (mc.screen != null && !local) return;
        if (shownAt <= 0) return;
        if (state == null || !state.has("me")) {
            return;
        }
        JsonObject me = state.getAsJsonObject("me");
        boolean downed = me.has("downed") && me.get("downed").getAsBoolean();
        if (downed && !ClientPrefs.deathOverDowned() && !preview) {
            return;
        }
        if (!downed && (!me.has("alive") || me.get("alive").getAsBoolean())) {
            return;
        }
        long now = System.currentTimeMillis();
        float age = (now - shownAt) / 1000f;
        if(preview)age=Math.min(age,1f);
        if (!preview && !downed && now >= endAt()) return;

        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        // In: fast. Out: slow, so the card is still readable when the deploy map opens over it.
        float in = Math.min(1f, age / 0.35f);
        float out = (preview && releasedAt==0) || downed ? 1f : Math.max(0f, Math.min(1f,
                (endAt() - now) / (releasedAt == 0 ? 500f : SKIP_FADE_MS)));
        float alpha = in * out;
        int rise = Math.round((1f - in) * RISE_PIXELS);

        // Vignette: a dark veil plus a heavier band at the top and bottom, which is what makes the live
        // world behind read as "not yours any more" without hiding it.
        g.fill(0, 0, w, h, argb(0x5c000000, alpha));
        Ui.gradient(g, new Layout.Rect(0, 0, w, Math.max(1, h / 3)), argb(0x8c000000, alpha), argb(0x00000000, alpha));
        Ui.gradient(g, new Layout.Rect(0, h - Math.max(1, h / 3), w, Math.max(1, h / 3)),
                argb(0x00000000, alpha), argb(0x99000000, alpha));

        int cx = w / 2;
        int base = h - (downed ? 152 : 132) + rise;

        // Original KillCardInfo: panelLeft killer identity, vertical separator, right weapon artwork.
        int panelW = Math.min(w-24,420), panelLeft = cx-panelW/2, right = cx+panelW/2;
        int panelY = base+18;
        g.fill(panelLeft,panelY,right,panelY+43,argb(0x78000000,alpha));
        g.fill(cx,panelY+5,cx+1,panelY+38,argb(0x44ffffff,alpha));
        g.setColor(1,1,1,alpha);
        try{Bf1Ui.icon(g,"death_hud_default",cx-8,base-28,16);}
        finally{g.setColor(1,1,1,1);}
        title(g,font,cx,base-6,downed?"倒地":"阵亡",alpha);
        boolean named = !killer.isEmpty();
        int identityX = panelLeft+8;
        String line = named ? Ui.clip(font,killer,panelW/2-36) : downed ? "等待救援" : "你阵亡了";
        AssetIcons.whiteIcons(true);
        try{
            if(named && FixedClasses.stem(kit)!=null){
                AssetIcons.draw(g,"role",kit,identityX,panelY+7,16,16);
                identityX+=21;
            }
            text(g,font,identityX,panelY+10,line,argb(0xfff0f0f0,alpha),false);
            if(named && killerMaxHealth>0 && killerHealth>=0){
                float health = Math.clamp(killerHealth/killerMaxHealth,0f,1f);
                int length = Math.max(16,panelW/2-60);
                g.fill(panelLeft+8,panelY+31,panelLeft+8+length,panelY+33,argb(0x55ffffff,alpha));
                g.fill(panelLeft+8,panelY+31,panelLeft+8+Math.round(length*health),panelY+33,argb(0xffeeeeee,alpha));
                text(g,font,panelLeft+12+length,panelY+27,Math.round(health*100)+"%",argb(0xffeeeeee,alpha),false);
            }
            if(!weapon.isEmpty())AssetIcons.draw(g,"gun",weapon,cx+8,panelY+2,Math.max(24,panelW/2-46),27);
            else if(preview)Bf1Ui.icon(g,"kit_rifleman_hud_default",cx+panelW/4-12,panelY+2,27);
            String detail = distance>0 ? distance+" m" : "";
            text(g,font,right-8-font.width(detail),panelY+29,detail,argb(0xffc0c5c8,alpha),false);
        }finally{AssetIcons.whiteIcons(false);g.setColor(1,1,1,1);}

        String hint;
        if (downed) {
            boolean revivable = !(me.has("noRevive") && me.get("noRevive").getAsBoolean());
            long left = Engine.num(state, "downLeft", 0);
            hint = revivable
                    ? "等待医疗兵救援" + (left > 0 ? " · " + left + " 秒" : "") + (FactionArt.arena() != null && FactionArt.arena().allowGiveUp ? " · 按住 空格 放弃" : "")
                    : "近身武器击杀 · 无法救援 · 按 空格 放弃";
        } else {
            String key=mc.options.keyJump.getTranslatedKeyMessage().getString();
            hint = local ? "按 "+key+" 查看复活选项" : "按 "+key+" 进入部署";
        }
        if(preview)hint="界面预览 · ESC 返回 · R 重播镜头";
        int hintW = font.width(hint);
        text(g, font, cx - hintW / 2, base + 62, hint, argb(0x8c9aa4ab, alpha), false);

        if (downed) {
            var arena = FactionArt.arena();
            int left = Engine.num(state,"downLeft",0);
            double bleed = Math.clamp(left/(double)Math.max(1,arena == null ? 30 : arena.downSeconds),0,1);
            int barY = base + 73;
            g.fill(cx-60,barY,cx+60,barY+2,argb(0x55ffffff,alpha));
            g.fill(cx-60,barY,cx-60+(int)(120*bleed),barY+2,argb(0xffe7e9e9,alpha));
            double rescue = Medic.fraction(Engine.num(state,"reviveProgress",0),Medic.reviveTicks(arena == null ? 3 : arena.reviveSeconds));
            int giveUp = Math.clamp(Engine.num(state,"giveUpProgress",0),0,20);
            if(rescue>0 || giveUp>0){
                String progress = rescue>0 ? "正在救援 " + (int)(rescue*100) + "%" : "正在放弃救援";
                g.drawCenteredString(font,progress,cx,base-49,argb(rescue>0?0xff8fd7a8:0xffefaaaa,alpha));
                g.fill(cx-60,base-37,cx-60+(int)(120*(rescue>0?rescue:giveUp/20.0)),base-35,argb(rescue>0?0xff8fd7a8:0xffefaaaa,alpha));
            }
            boolean noRevive = me.has("noRevive") && me.get("noRevive").getAsBoolean();
            var medics = noRevive ? java.util.List.<DeathContacts.Row>of() : preview ? DeathContacts.medics(state,me) : nearbyMedics(me);
            // KillScreen.NearbyMedicsContainer is anchored to the lower-right in the original EBX.
            int rowY = Math.max(8,h-238);
            String header = noRevive ? "无法救援" : medics.isEmpty() ? "附近没有可救援的医疗兵" : "附近医疗兵";
            int headW = font.width(header);
            text(g, font, w-18-headW, rowY, header,
                    argb(medics.isEmpty() ? 0xffd9a566 : 0xff8fd7a8, alpha), false);
            for (int i = 0; i < Math.min(3, medics.size()); i++) {
                String row = Ui.clip(font,medics.get(i).name(),110) + " · " + medics.get(i).metres() + " 米";
                int rowW = font.width(row);
                text(g, font, w-18-rowW, rowY + 12 + i * 11, row, argb(0xffdfe6ea, alpha), false);
            }
        }
    }

    /** Letter-spaced title, matching the MODIFY screen's typography. */
    private static void title(GuiGraphics g, Font font, int cx, int y, String text, float alpha) {
        float scale = 1.55f;
        float spacing = 6f;
        float total = 0;
        for (char c : text.toCharArray()) total += (font.width(String.valueOf(c)) + spacing) * scale;
        float x = cx - total / 2f;
        for (char c : text.toCharArray()) {
            String glyph = String.valueOf(c);
            g.pose().pushPose();
            try {
                g.pose().scale(scale, scale, 1);
                g.drawString(font, glyph, (int) (x / scale), (int) (y / scale), argb(0xfff4f7f9, alpha), false);
            } finally {
                g.pose().popPose();
            }
            x += (font.width(glyph) + spacing) * scale;
        }
    }

    private static void text(GuiGraphics g, Font font, int x, int y, String value, int colour, boolean shadow) {
        if (value.isEmpty()) return;
        g.drawString(font, value, x, y, colour, shadow);
    }

    private static int argb(int colour, float alpha) {
        int a = Math.round(((colour >>> 24) & 0xff) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (colour & 0xffffff);
    }
}
