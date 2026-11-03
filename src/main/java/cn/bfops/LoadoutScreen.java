package cn.bfops;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Card-based loadout editor, laid out from the reference pack's constants (see
 * {@link LoadoutLayout} and 参考端-配装与载具界面-结构提取.md).
 *
 * <p>The reference splits "configure equipment" from "deploy", and this screen is that split: the left
 * column chooses class and weapons, the right panel shows the overview plus the primary and secondary
 * weapon cards, and a card's "改装" button opens the existing {@link FittingScreen}. Weapon and class
 * artwork is read at runtime by {@link AssetIcons} from the installed TaCZ / Superb Warfare packs, never
 * copied into this mod.
 *
 * <p>State is shared with the owning {@link BattleScreen} (the fields are package-private for exactly this
 * cooperation), so a pick made here is the pick the deploy button sends — there is no second copy to drift.
 */
final class LoadoutScreen extends FrontScreen {
    private static final int CARD_BG = 0x4410181F;
    private static final int CARD_BORDER = 0x66FFD78A;
    private static final int TITLE = 0xFFA0D0F0;
    private static final int NAME = 0xFFEDEDED;
    private static final int ENTRY = 0xFF8FB3CF;
    private static final int SUMMARY = 0xFFB8C6D6;
    private static final int OVERVIEW_TITLE = 0xFF7EC8FF;
    private static final int OVERVIEW_NAME = 0xFFD7D7D7;
    private static final int OVERVIEW_HINT = 0xFF9AA6B8;

    /** The six infantry attachment slots, in the reference pack's card order (2 columns × 3 rows). */
    private static final List<String> SLOTS =
            List.of("SCOPE", "MUZZLE", "STOCK", "GRIP", "LASER", "EXTENDED_MAG");

    private final BattleScreen parent;
    private Form leftForm, rightForm, barForm;
    private boolean propertyVisible=true,propertyMain=true;

    LoadoutScreen(BattleScreen parent) {
        super("配装");
        this.parent = parent;
    }

    @Override protected boolean bf1Style(){return true;}
    @Override
    protected String subtitle() {
        return (parent.arena == null ? "未选择地图" : parent.arena.name) + " · 卡片视图";
    }

    private Model.Kit kit() {
        return parent.kit();
    }

    /** The id the card should show: the player's pick, or the first authorised option. */
    private String shown(Model.Kit kit, boolean main) {
        String chosen = main ? parent.primary : parent.secondary;
        if (chosen != null && !chosen.isEmpty()) return chosen;
        return kit == null ? "" : kit.options(main).stream().filter(id -> !id.isEmpty()).findFirst().orElse("");
    }

    @Override
    protected void rebuildContent() {
        int leftScroll = leftForm == null ? 0 : leftForm.scroll();
        int rightScroll = rightForm == null ? 0 : rightForm.scroll();
        clearWidgets();
        slots.clear();

        buildLeft(leftScroll);
        buildRight(rightScroll);
        buildBar();
    }

    // ------------------------------------------------------------------ left: class + weapon slots

    private void buildLeft(int scroll) {
        leftForm = newForm(Ui.headerBody(layout.left));
        Model.Kit kit = kit();
        if (kit == null) {
            leftForm.paragraph("本图没有可用兵种。", Ui.TEXT_WARN);
        } else {
            leftForm.caption("兵种", Ui.TEXT_FAINT);
            dropdown(leftForm,"兵种",kit.id,parent.arena.kits.stream().map(k->new ChoiceScreen.Choice(k.id,k.id,"role",k.id)).toList(),id->{parent.selectKit(id);rebuild();});
            leftForm.caption("武器槽位", Ui.TEXT_FAINT);
            row(leftForm, "主武器", parent.name(shown(kit, true)), "gun", shown(kit, true),
                    () -> chooseWeapon(kit, true));
            row(leftForm, "副武器", parent.name(shown(kit, false)), "gun", shown(kit, false),
                    () -> chooseWeapon(kit, false));
        }
        if(kit!=null){
            for(boolean main:new boolean[]{true,false}){String weapon=shown(kit,main);row(leftForm,main?"主武器配件":"副武器配件",weapon.isEmpty()?"未选择":parent.fitting("gun",weapon).size()+" 项",!weapon.isEmpty(),()->customize("gun",weapon));}
            row(leftForm,"近身武器",parent.meleeName(parent.melee),ItemCatalog.isGun(parent.melee)?"gun":"item",parent.melee,this::chooseMelee);
            row(leftForm,"投掷武器",parent.throwable.isEmpty()?"无":parent.meleeName(parent.throwable),parent.throwable.isEmpty()?"":"item",parent.throwable,this::chooseThrowable);
row(leftForm,"战术道具 1",parent.tactical1.isEmpty()?"无":parent.meleeName(parent.tactical1),parent.tactical1.isEmpty()?"":"item",parent.tactical1,this::chooseTacticalOne);
        row(leftForm,"战术道具 2",parent.tactical2.isEmpty()?"无":parent.meleeName(parent.tactical2),parent.tactical2.isEmpty()?"":"item",parent.tactical2,this::chooseTacticalTwo);
        parent.ammoRows(leftForm);
        }
        if(kit!=null)for(boolean main:new boolean[]{true,false}){
            String weapon=shown(kit,main);if(!weapon.isEmpty())row(leftForm,main?"主武器属性图表":"副武器属性图表","含当前配件",()->minecraft.setScreen(new EquipmentStatsScreen(this,"gun",weapon,parent.fitting("gun",weapon))));
        }
        leftForm.skip(2);
        primary(leftForm, "返回部署", true, this::onClose);
        leftForm.scrollTo(scroll);
        leftForm.finish();
    }

    // ------------------------------------------------------------------ right: overview + cards

    private void buildRight(int scroll) {
        rightForm = newForm(Ui.headerBody(layout.right));
        if(propertyVisible){
            row(rightForm,"配件卡片","隐藏属性",()->{propertyVisible=false;rebuild();});
            row(rightForm,propertyMain?"主武器属性":"副武器属性","切换主／副武器",()->{propertyMain=!propertyMain;rebuild();});
            String weapon=shown(kit(),propertyMain);
            rightForm.paint(48,(g,r)->{if(!GunPreview.render(g,weapon,parent.fitting("gun",weapon),r.x()+2,r.y(),r.w()-4,30,22,-14))AssetIcons.draw(g,"gun",weapon,r.x()+4,r.y()+2,r.w()-8,30);g.drawString(font,Ui.clip(font,parent.name(weapon),r.w()-8),r.x()+4,r.y()+36,Ui.TEXT,true);});
            var report=EquipmentStatistics.read("gun",weapon,parent.fitting("gun",weapon));
            rightForm.section(report.source().isEmpty()?"装备属性":report.source());
            for(var value:report.rows())rightForm.paint(16,(g,r)->EquipmentStatPanel.diagram(g,font,r,value));
            if(report.rows().isEmpty())rightForm.paragraph(report.warning(),Ui.TEXT_WARN);
            rightForm.paragraph("条形为基础值和当前配件修正；伤害已计入服务端平衡设置。",Ui.TEXT_DIM);
            rightForm.scrollTo(scroll);rightForm.finish();return;
        }
        int overviewTop = rightForm.area().y() + Layout.PAD + 1;
        int primaryTop = overviewTop + LoadoutLayout.INFO_H + LoadoutLayout.CARD_GAP;
        int secondaryTop = primaryTop + LoadoutLayout.CARD_H + LoadoutLayout.CARD_GAP;

        Model.Kit kit = kit();
        rightForm.paint(LoadoutLayout.INFO_H, (g, r) -> drawOverview(g, r, kit));
        rightForm.skip(LoadoutLayout.CARD_GAP);
        rightForm.paint(LoadoutLayout.CARD_H, (g, r) -> drawCard(g, r, "主武器", shown(kit, true)));
        rightForm.skip(LoadoutLayout.CARD_GAP);
        rightForm.paint(LoadoutLayout.CARD_H, (g, r) -> drawCard(g, r, "副武器", shown(kit, false)));

        if (kit != null) {
            addSlotButtons(primaryTop, "gun", shown(kit, true));
            addSlotButtons(secondaryTop, "gun", shown(kit, false));
        }
        row(rightForm,"显示武器属性","原生完整图表",()->{propertyVisible=true;rebuild();});
        if(kit!=null)for(boolean main:new boolean[]{true,false}){
            String weapon=shown(kit,main);if(weapon.isEmpty())continue;
            var report=EquipmentStatistics.read("gun",weapon,parent.fitting("gun",weapon));
            rightForm.section(main?"主武器 · 当前配装属性":"副武器 · 当前配装属性");
            for(var value:report.rows())rightForm.paint(16,(g,rect)->EquipmentStatPanel.diagram(g,font,rect,value));
            if(report.rows().isEmpty())rightForm.paragraph(report.warning(),Ui.TEXT_DIM);
        }
        rightForm.scrollTo(scroll);
        rightForm.finish();
    }

    /**
     * The six attachment-slot buttons on a card, tiled 2×3 exactly as the reference does. Each button
     * names the slot and whether it is configured, and opens the attachment editor for the weapon.
     *
     * <p>Hovering one shows the property panel from {@link LoadoutHover}. The panel is read-only: clicking
     * is what opens the editor, so browsing a slot can never leave an uncommitted change on the gun. Slot
     * hit-rects are recorded rather than hit-tested a second time in the hover path, so what the pointer is
     * over and what the panel describes cannot disagree.
     */
    private void addSlotButtons(int cardTop, String kind, String id) {
        int inner = Math.max(40, rightForm.width() - 16);
        int btnW = Math.max(74, (inner - LoadoutLayout.CARD_BUTTON_GAP) / 2);
        Map<String, String> fitting = id.isEmpty() ? Map.of() : parent.fitting(kind, id);
        for (int i = 0; i < SLOTS.size(); i++) {
            String slot = SLOTS.get(i);
            int col = i % 2, row = i / 2;
            int bx = rightForm.x() + 8 + col * (btnW + LoadoutLayout.CARD_BUTTON_GAP);
            int by = cardTop + 42 + row * 24;
            String part=fitting.getOrDefault(slot,"");
            String value=part.isEmpty()?"默认":GearNames.display("attachment",part,part);
            place(rightForm, new Layout.Rect(bx, by, btnW, LoadoutLayout.CARD_BUTTON_H),
                    Fittings.slotName(slot), value, "", "", !id.isEmpty(), () -> customize(kind, id));
            if (!id.isEmpty()) {
                slots.add(new Slot(slot, id, new Layout.Rect(bx, by, btnW, LoadoutLayout.CARD_BUTTON_H)));
            }
        }
    }

    /** One hoverable attachment slot: enough to build the panel without re-deriving anything. */
    private record Slot(String slot, String weapon, Layout.Rect rect) { }

    /** Slot rects from the last rebuild, in paint order, for the hover panel. */
    private final List<Slot> slots = new ArrayList<>();

    private void drawOverview(GuiGraphics g, Layout.Rect r, Model.Kit kit) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), CARD_BG);
        g.fill(r.x(), r.y(), r.right(), r.y() + 1, 0x667EC8FF);
        g.drawString(font, "当前兵种或装备", r.x() + 8, r.y() + 6, OVERVIEW_TITLE, false);
        String player = minecraft.player == null ? "-" : minecraft.player.getName().getString();
        g.drawString(font, clip(player + " · " + (kit == null ? "无" : kit.id), r.w() - 16),
                r.x() + 8, r.y() + 20, OVERVIEW_NAME, false);
        g.drawString(font, "左侧更换兵种与武器，卡片内「改装」编辑配件", r.x() + 8, r.y() + 46,
                OVERVIEW_HINT, false);
    }

    private void drawCard(GuiGraphics g, Layout.Rect r, String title, String id) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), CARD_BG);
        g.fill(r.x(), r.y(), r.right(), r.y() + 1, CARD_BORDER);
        g.drawString(font, title, r.x() + 8, r.y() + 6, TITLE, false);
        g.drawString(font, clip(parent.name(id), r.w() - Math.min(64,Math.max(24,r.w()/5)) - 24), r.x() + 8, r.y() + 20, NAME, false);
        g.drawString(font, clip(id.isEmpty() ? "未选择" : "@" + LibrarySource.name(id) + " · " + id, r.w() - Math.min(64,Math.max(24,r.w()/5)) - 24),
                r.x() + 8, r.y() + 32, ENTRY, false);
        int count = id.isEmpty() ? 0 : parent.fitting("gun", id).size();
        String stats = GunStats.summary(id);
        var report=EquipmentStatistics.read("gun",id,parent.fitting("gun",id));
        if(!report.rows().isEmpty()){var first=report.rows().getFirst();stats="当前 "+first.title()+" "+first.after();}
        String summary = stats.isEmpty()
                ? (count == 0 ? "无改装" : ("已配置 " + count + " 项改装"))
                : stats + (count > 0 ? " · 已装 " + count + " 项" : "");
        g.drawString(font, clip(summary, r.w() - 16), r.x() + 8, r.y() + 118, SUMMARY, false);
        if (!id.isEmpty()) {
            // The attachment grid owns y=42..108. Keep weapon art in the header above it.
            int artW = Math.min(64, Math.max(24,r.w()/5));
            GuiClip.push(g,r.right()-artW-8,r.y()+4,artW,32);
            try{AssetIcons.draw(g,"gun",id,r.right()-artW-8,r.y()+4,artW,32);}
            finally{GuiClip.pop(g);}
        }
    }

    private String clip(String text, int width) {
        return Ui.clip(font, text, Math.max(1, width));
    }

    // ------------------------------------------------------------------ actions

    private void pick(String title, List<ChoiceScreen.Choice> choices, Consumer<String> result) {
        minecraft.setScreen(new ChoiceScreen(this, title, choices, result));
    }

    private void chooseMelee(){
        Model.Kit kit=kit();if(kit==null)return;
        var choices=new ArrayList<ChoiceScreen.Choice>();choices.add(new ChoiceScreen.Choice("","无 / 不装备","item"));
        for(String id:kit.meleeOptions())choices.add(new ChoiceScreen.Choice(id,parent.meleeName(id),ItemCatalog.isGun(id)?"gun":"item"));
        pick("地图授权近身武器",choices,id->{parent.melee=id;parent.rememberLoadout();rebuild();});
    }
    private void chooseTacticalOne(){chooseTactical(false);}
    private void chooseTacticalTwo(){chooseTactical(true);}
    private void chooseTactical(boolean second){
        Model.Kit kit=kit();if(kit==null)return;
        List<ChoiceScreen.Choice> choices=new ArrayList<>();
        choices.add(new ChoiceScreen.Choice("","无 / 不装备","item"));
        for(String id:kit.tacticalChoices())choices.add(new ChoiceScreen.Choice(id,parent.meleeName(id),"item"));
        pick(second?"战术道具 2":"战术道具 1",choices,id->{
            if(second)parent.tactical2=id;else parent.tactical1=id;
            parent.rememberLoadout();rebuild();
        });
    }

    private void chooseThrowable(){
        Model.Kit kit=kit();if(kit==null)return;
        var choices=new ArrayList<ChoiceScreen.Choice>();choices.add(new ChoiceScreen.Choice("","无 / 不装备","item"));
        for(String id:kit.throwableOptions())choices.add(new ChoiceScreen.Choice(id,parent.meleeName(id),"item"));
        pick("地图授权投掷武器",choices,id->{parent.throwable=id;parent.rememberLoadout();rebuild();});
    }
    private void chooseKit() {
        if (parent.arena == null || parent.arena.kits.isEmpty()) return;
        pick("选择兵种 / 配装",
                parent.arena.kits.stream().map(k -> new ChoiceScreen.Choice(k.id, k.id, "role")).toList(),
                id -> { parent.selectKit(id); rebuild(); });
    }

    private void chooseWeapon(Model.Kit kit, boolean main) {
        List<ChoiceScreen.Choice> choices = kit.options(main).stream()
                .filter(id -> !id.isEmpty())
                .map(id -> new ChoiceScreen.Choice(id, parent.name(id), "gun"))
                .toList();
        if (choices.isEmpty()) {
            warn("该兵种没有授权" + (main ? "主" : "副") + "武器");
            return;
        }
        pick(main ? "主武器库" : "副武器库", choices, id -> {
            if (main) parent.primary = id; else parent.secondary = id;
            propertyMain=main;propertyVisible=true;
            parent.rememberLoadout();
            rebuild();
        });
    }

    private void customize(String kind, String id) {
        if (id == null || id.isEmpty()) {
            warn("请先选择装备");
            return;
        }
        minecraft.setScreen(FittingScreen.open(this, parent.selected, id, kind, parent.fitting(kind, id),kit()==null?"":kit().id));
    }

    private void buildBar() {
        barForm = newForm(layout.bar.inset(2));
        Layout.Rect[] cells = Layout.columns(layout.bar.inset(2), 1, 3);
        place(barForm, cells[0], "返回部署", "", "", "", true, this::onClose);
        barForm.finish();
    }

    @Override
    protected void paint(GuiGraphics g, int mx, int my, float dt) {
        Ui.header(g, font, layout.left, "兵种与武器");
        leftForm.render(g, mx, my, dt);
        Ui.header(g, font, layout.right, "配装总览");
        rightForm.render(g, mx, my, dt);
        barForm.render(g, mx, my, dt);
        // Last, so it sits over every panel. A tooltip under a card is a tooltip nobody can read.
        drawHover(g, mx, my);
    }

    /**
     * The attachment property panel, for whichever slot the pointer is over.
     *
     * <p>Runs after the panes and the bar for the same reason the panel exists at all: it has to be readable,
     * which means being on top. Placement goes through {@link LoadoutHover#place}, which flips the panel to
     * the other side of the pointer rather than clamping it against the window edge — a clamped panel lands
     * under the cursor that is still resting on the slot it describes.
     */
    private void drawHover(GuiGraphics g, int mx, int my) {
        for (Slot slot : slots) {
            if (!slot.rect().inside(mx, my)) continue;
            Map<String, String> fitting = parent.fitting("gun", slot.weapon());
            String installed = fitting.getOrDefault(slot.slot(),"");if(!installed.isEmpty())installed=GearNames.display("attachment",installed,installed);
            LoadoutHover.Panel panel = LoadoutHover.build(Fittings.slotName(slot.slot()), installed,
                    compatibleCount(slot.weapon(), slot.slot()), GunStats.summary(slot.weapon()));
            var report=EquipmentStatistics.read("gun",slot.weapon(),fitting);var lines=new ArrayList<>(panel.lines());
            for(int i=0;i<Math.min(3,report.rows().size());i++){var row=report.rows().get(i);lines.add(row.title()+"："+row.before()+" → "+row.after());}
            panel=new LoadoutHover.Panel(panel.title(),List.copyOf(lines),0,0);
            Layout.Rect size = LoadoutHover.size(font, panel);
            Layout.Rect rect = LoadoutHover.place(mx, my, size.w(), size.h(), width, height);
            LoadoutHover.draw(g, font, rect, panel);
            return;
        }
    }

    /**
     * How many parts this weapon accepts in this slot, from the server's start-up scan.
     *
     * <p>Read from {@code fittingCounts} rather than by opening a "fittings" request: a hover happens on
     * every mouse move, and a round trip per move would be both slow and a way to accidentally hammer the
     * server. A weapon the scan could not read reports 0, and {@link LoadoutHover#build} words that as
     * "兼容配件 0 种" — which is true of what we know, rather than a claim about the gun.
     */
    private int compatibleCount(String weapon, String slot) {
        if (Client.state == null || !Client.state.has("fittingCounts")
                || !Client.state.get("fittingCounts").isJsonObject()) return 0;
        var counts = Client.state.getAsJsonObject("fittingCounts");
        if (!counts.has(weapon) || !counts.get(weapon).isJsonObject()) return 0;
        var bySlot = counts.getAsJsonObject(weapon);
        return bySlot.has(slot) && bySlot.get(slot).isJsonPrimitive() ? bySlot.get(slot).getAsInt() : 0;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {if(dropdownOpen())return super.mouseScrolled(x,y,h,v);
        if (rightForm != null && rightForm.wheel(x, y, v)) return true;
        if (leftForm != null && leftForm.wheel(x, y, v)) return true;
        return super.mouseScrolled(x, y, h, v);
    }

    @Override
    public void onClose() {
        parent.rememberLoadout();minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
