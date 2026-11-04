package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Shared 656-style world-backed screen shell; render widgets once without vanilla blur. */
abstract class FrontScreen extends Screen {
    /** Message shown in the footer. Blank hides it. */
    protected String message = "";
    /** Colour for {@link #message}; semantic, so callers say "this failed" rather than pick a colour. */
    protected int messageColor = Ui.TEXT_DIM;

    private final String windowTitle;
    private DropdownMenu dropdown;
    protected final boolean dropdownOpen(){return dropdown!=null;}
    protected FrontButton dropdown(Form form,String label,String current,java.util.List<ChoiceScreen.Choice> options,Consumer<String> chosen){return dropdown(form,form.take(Layout.ROW),label,current,options,chosen);}
    protected FrontButton dropdown(Form form,Layout.Rect rect,String label,String current,java.util.List<ChoiceScreen.Choice> options,Consumer<String> chosen){
        String value=options.stream().filter(c->c.id().equals(current)).map(ChoiceScreen.Choice::name).findFirst().orElse(current);
        var selectedIcon=options.stream().filter(c->c.id().equals(current)).findFirst().orElse(null);
        boolean hasIcon=selectedIcon!=null&&selectedIcon.kind()!=null&&!selectedIcon.kind().isBlank()&&selectedIcon.icon()!=null&&!selectedIcon.icon().isBlank();
        FrontButton[] holder=new FrontButton[1];
        holder[0]=place(form,rect,label,value+"  ▾",hasIcon?selectedIcon.kind():"",hasIcon?selectedIcon.icon():"",!options.isEmpty(),()->{dropdown=new DropdownMenu(holder[0],options,current,chosen);setFocused(holder[0]);});
        return holder[0];
    }
    protected void openChoice(String title,String current,java.util.List<ChoiceScreen.Choice> options,Consumer<String> chosen){
        if(getFocused() instanceof FrontButton button)dropdown=new DropdownMenu(button,options,current,chosen);
        else minecraft.setScreen(new ChoiceScreen(this,title,options,chosen));
    }
    private void chooseDropdown(String value){DropdownMenu menu=dropdown;dropdown=null;if(value!=null)guard(()->menu.chosen.accept(value));}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(dropdown!=null){
            if(key==256){dropdown=null;return true;}
            if(key==265||key==264){dropdown.move(key==265?-1:1);return true;}
            if(key==268){dropdown.selected=0;return true;}
            if(key==269){dropdown.selected=Math.max(0,dropdown.filtered().size()-1);return true;}
            if(key==257||key==335){chooseDropdown(dropdown.value());return true;}
            if(key==259&&!dropdown.query.isEmpty()){dropdown.query=dropdown.query.substring(0,dropdown.query.offsetByCodePoints(dropdown.query.length(),-1));dropdown.selected=0;dropdown.first=0;}
            return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean charTyped(char chr,int modifiers){if(dropdown!=null){if(!Character.isISOControl(chr)&&dropdown.query.length()<64){dropdown.query+=chr;dropdown.selected=0;dropdown.first=0;}return true;}return super.charTyped(chr,modifiers);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(dropdown!=null){dropdown.move(vertical>0?-1:vertical<0?1:0);return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dropdown!=null)return true;return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(dropdown!=null)return true;return super.mouseReleased(x,y,button);}


    FrontScreen(String windowTitle) {
        super(Component.literal(windowTitle));
        this.windowTitle = windowTitle;
    }

    /** Recreated on init and on resize, so no screen ever keeps a stale rectangle. */
    protected Layout layout;

    /** Build widgets for the current tab from current state. */
    protected abstract void rebuildContent();
    private boolean rebuildPending;
    /** Network updates continue; only widget replacement waits for the modal interaction to finish. */
    protected final void rebuild(){
        if(dropdownOpen()){rebuildPending=true;return;}
        rebuildPending=false;
        long started=System.nanoTime();
        rebuildContent();
        PerfProbe.mark("rebuild:"+windowTitle,System.nanoTime()-started);
    }

    /** Paint the working area. Widgets are drawn by their owning {@link Form}. */
    protected abstract void paint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /** Tab labels, or an empty array for a screen without tabs. */
    protected String[] tabs() {
        return new String[0];
    }

    /** False for a page that paints its own full-screen chrome (the deploy map), so no band is left behind. */
    protected boolean showChrome() {
        return true;
    }

    /** Index of the highlighted tab. */
    protected int activeTab() {
        return 0;
    }

    /** Handle a tab click. Return {@code true} when the screen handled it. */
    protected boolean onTab(int index) {
        return false;
    }

    /** One line of context drawn next to the window title, e.g. the selected map. */
    protected boolean worldBackdrop() { return minecraft != null && minecraft.level != null; }

    /**
     * Draws this page in the BF1 reference style: translucent plates, white glyph icons, black-on-white
     * selection. Item icons become white silhouettes, which is what makes a list of coloured weapon
     * renders read like the reference screen.
     */
    protected boolean bf1Style(){return false;}
    protected boolean actionIcons(){return true;}
    protected boolean horizontalTabs(){return false;}
    private boolean expandedTabs(){return horizontalTabs()&&layout.tabs.w()/Math.max(1,tabs().length)>=56;}
    protected void paintBackdrop(GuiGraphics g) {
        // Pages with their own full-screen content (the deploy map) only get the dimming veil: an image
        // under a map that fills the window is invisible, and any mistake in its alpha hides the map.
        if (!showChrome()) {
            Ui.backdrop(g, width, height);
            return;
        }
        Bf1Ui.screenBackdrop(g, width, height);
    }
    protected Layout layoutFor(int width,int height){return Layout.of(width,height);}
    /** Paints the action-bar widgets. Called after the page, so it cannot be skipped by a page failure. */
    protected void renderBar(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    protected boolean showBottomBar(){return true;}
    protected boolean showFooter(){return true;}

    protected String subtitle() {
        return "";
    }

    // ------------------------------------------------------------------ messages

    protected void ok(String text) {
        message = text == null ? "" : text;
        messageColor = Ui.TEXT_OK;
    }

    protected void warn(String text) {
        message = text == null ? "" : text;
        messageColor = Ui.TEXT_WARN;
    }

    protected void fail(String text) {
        message = text == null ? "" : text;
        messageColor = Ui.TEXT_ERROR;
    }

    protected void say(String text) {
        message = text == null ? "" : text;
        messageColor = Ui.TEXT_DIM;
    }

    /** Runs an action and turns its exception into a footer message instead of a crash. */
    protected void guard(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            fail(ex.getMessage() == null ? "操作无效" : ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        layout = layoutFor(width, height);
        // A failed screen build must not take the game down with it. Whatever the cause, the operator needs to
        // see it in the footer and in the log, and still be able to close the window.
        try {
            rebuild();
        } catch (RuntimeException | LinkageError ex) {
            BattlefieldOps.LOG.error("Cannot build screen {}", windowTitle, ex);
            message = "界面构建失败：" + describe(ex);
            messageColor = Ui.TEXT_ERROR;
        }
    }

    /** One-line description of a failure, preferring the message over the class name. */
    static String describe(Throwable ex) {
        String text = ex.getMessage();
        if (text == null || text.isBlank()) text = ex.getClass().getSimpleName();
        return text.length() > 120 ? text.substring(0, 120) + "…" : text;
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        dropdown=null;
        super.resize(minecraft, width, height);
    }

    @Override
    public void tick() {
        super.tick();
        poll();
        if(rebuildPending&&!dropdownOpen()&&minecraft.screen==this)guard(this::rebuild);
    }

    /** Hook for screens that refresh server state on a timer. */
    protected void poll() {}

    /**
     * Suppresses vanilla's blurred world plus dirt overlay. Without this the whole window is
     * double-exposed and every panel edge turns into a soft smear.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Deliberately empty: {@link #render} paints an opaque backdrop instead.
    }

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        paintBackdrop(graphics);
        chrome(graphics, mouseX, mouseY);
        Layout.Rect bar = layout.bar;
        if (showBottomBar() && bar.h() > 0) {
            graphics.fill(bar.x(),bar.y(),bar.right(),bar.bottom(),Ui.PANEL_SUNKEN);
            Ui.frame(graphics,bar,Ui.FRAME);
        }
        // Same reasoning for painting: a bad frame should cost one frame, not the session. The error is logged
        // once per distinct message so a per-frame failure cannot flood the log.
        long started=System.nanoTime();
        boolean style=bf1Style(),previousStyle=Ui.bf1(),previousWhite=AssetIcons.whiteIcons();
        Ui.bf1(style);AssetIcons.whiteIcons(style);
        try {
            paint(graphics, mouseX, mouseY, partialTick);
        } catch (RuntimeException | LinkageError ex) {
            reportPaintFailure(ex);
        } finally {
            Ui.bf1(previousStyle);AssetIcons.whiteIcons(previousWhite);
            PerfProbe.mark("paint:"+windowTitle,System.nanoTime()-started);
        }
        // The action bar is painted here, outside the page's guard: a page that throws must not be able to
        // hide the save row, which is how "the bar is there but nothing is drawn" used to happen.
        if (showBottomBar() && bar.h() > 0) {
            try {
                renderBar(graphics, mouseX, mouseY, partialTick);
            } catch (RuntimeException | LinkageError ex) {
                reportPaintFailure(ex);
            }
        }
        if(showFooter())foot(graphics);
        // The menu drop-down is drawn AFTER the page content: it is a pop-up, so the panes must not cover it.
        if(tabsOpen)menuOverlay(graphics,mouseX,mouseY);
        if(dropdown!=null){if(!children().contains(dropdown.anchor)||!dropdown.anchor.visible)dropdown=null;else dropdown.draw(graphics,font,mouseX,mouseY,width,height);}
        paintTransition(graphics);
    }

    private long firstPaintAt;
    protected void paintTransition(GuiGraphics graphics) {
        long now=System.nanoTime();
        if(firstPaintAt==0)firstPaintAt=now;
        double remaining=1-Cinematic.ease((now-firstPaintAt)/160_000_000.0);
        if(remaining>0)graphics.fill(0,0,width,height,((int)(remaining*110)<<24)|0x080c11);
    }

    /** Last paint failure we logged, so a repeating error does not fill the log every frame. */
    private String lastPaintFailure;

    private void reportPaintFailure(Throwable ex) {
        String text = describe(ex);
        if (text.equals(lastPaintFailure)) return;
        lastPaintFailure = text;
        BattlefieldOps.LOG.error("Cannot paint screen {}", windowTitle, ex);
        message = "界面绘制失败：" + text;
        messageColor = Ui.TEXT_ERROR;
    }

    // ------------------------------------------------------------------ chrome

    private void chrome(GuiGraphics graphics, int mouseX, int mouseY) {
        // A full-screen page (the deploy map) carries its own chrome; the window title/tab band must not
        // be drawn underneath it, or the leftover band shows through at the top.
        if (!showChrome()) return;
        if (expandedTabs() && tabs().length > 0) {
            Layout.Rect[] cells=tabRects();
            for(int i=0;i<cells.length;i++){
                var r=cells[i]; boolean active=i==activeTab(),hover=r.inside(mouseX,mouseY);
                if(active||hover) graphics.fill(r.x(),r.y(),r.right(),r.bottom(),active?0x30ffffff:0x18ffffff);
                graphics.drawString(font,Ui.clip(font,tabs()[i],r.w()-12),r.x()+6,r.y()+(r.h()-8)/2,active?0xffffffff:Ui.TEXT_DIM,false);
                if(active)graphics.fill(r.x(),r.bottom()-2,r.right(),r.bottom(),0xffffffff);
            }
        } else if (tabs().length > 0) {
            // The tab strip is collapsed into one small menu button; the tabs open as a dropdown under it.
            Layout.Rect toggle = menuButton();
            boolean hovered = toggle.inside(mouseX, mouseY);
            graphics.fill(toggle.x(), toggle.y(), toggle.right(), toggle.bottom(),
                    hovered ? Ui.PANEL_ALT : Ui.mix(Ui.PANEL_ALT, Ui.BACKDROP, 0.55));
            graphics.fill(toggle.x(), toggle.y(), toggle.right(), toggle.y() + 1, Ui.FRAME);
            graphics.drawString(font, "☰ 菜单", toggle.x() + 5, toggle.y() + (toggle.h() - 8) / 2,
                    hovered ? Ui.TEXT : Ui.TEXT_DIM, false);
            graphics.fill(layout.tabs.x(), layout.tabs.bottom(), layout.tabs.right(),
                    layout.tabs.bottom() + 1, Ui.FRAME);
        }
        // Title strip: window name on the left, context after it, so the operator always knows which
        // map and which tab they are looking at.
        if(layout.title.h()<=0)return;
        int titleX = layout.title.x() + 7;
        // A very shallow vertical gradient inside the title band. Two near-identical values, not a
        // decorative fade: the band was a flat rectangle of backdrop and the eye slid off it, so the
        // window had no visual top edge. Banding is avoided by keeping the two colours close.
        Ui.gradient(graphics, layout.title,
                Ui.mix(Ui.PANEL_ALT, Ui.BACKDROP, 0.55),
                Ui.mix(Ui.PANEL_ALT, Ui.BACKDROP, 0.82));
        // Accent bar flush against the window edge: a fixed landmark that makes the title band
        // recognisable at a glance without spending colour on the text itself.
        graphics.fill(layout.title.x() + 2, layout.title.y() + 1, layout.title.x() + 4,
                layout.title.bottom() - 1, Ui.ACCENT);
        graphics.drawString(font, windowTitle, titleX, layout.title.y() + 2, Ui.TEXT, false);
        String sub = subtitle();
        if (!sub.isEmpty()) {
            int used = font.width(windowTitle) + 10;
            graphics.drawString(font, Ui.clip(font, sub, layout.title.w() - used - 4),
                    titleX + used, layout.title.y() + 2, Ui.TEXT_DIM, false);
        }
        graphics.fill(layout.title.x(), layout.title.bottom(), layout.title.right(),
                layout.title.bottom() + 1, Ui.ACCENT_DIM);
    }

    private void foot(GuiGraphics graphics) {
        Layout.Rect footer = layout.footer;
        if (footer.h() > 0 && !message.isEmpty()) {
            // A faint tinted backing only when there is something to say: it lifts the line off the
            // backdrop, and an empty footer stays completely flat instead of showing a dead strip.
            graphics.fill(footer.x(), footer.y(), footer.right(), footer.bottom(),
                    Ui.mix(messageColor, Ui.PANEL_SUNKEN, 0.86));
            graphics.fill(footer.x(), footer.y(), footer.x() + 2, footer.bottom(), messageColor);
            var lines = font.split(Component.literal(message), Math.max(1, footer.w() - 8));
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                graphics.drawString(font, lines.get(i), footer.x() + 5, footer.y() + 2 + i * 10,
                        messageColor, false);
            }
        }
    }

    /** Tab rects, so subclasses hit-test exactly what was painted. */
    protected Layout.Rect[] tabRects() {
        String[] names = tabs();
        return names.length == 0 ? new Layout.Rect[0] : Layout.columns(layout.tabs, names.length, 1);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(dropdown!=null){if(button==0){dropdown.layout(width,height);chooseDropdown(dropdown.click(mouseX,mouseY));}else dropdown=null;return true;}
        if (button == 0 && showChrome() && expandedTabs() && tabs().length > 0) {
            var cells=tabRects();for(int i=0;i<cells.length;i++)if(cells[i].inside(mouseX,mouseY)){if(i!=activeTab())onTab(i);return true;}
        }
        if (button == 0 && showChrome() && !expandedTabs() && tabs().length > 0) {
            Layout.Rect toggle = menuButton();
            if (toggle.inside(mouseX, mouseY)) { tabsOpen = !tabsOpen; return true; }
            if (tabsOpen) {
                for (int i = 0; i < tabs().length; i++) {
                    if (menuItem(i).inside(mouseX, mouseY)) {
                        tabsOpen = false;
                        if (i != activeTab()) onTab(i);
                        return true;
                    }
                }
                tabsOpen = false;
                return true;
            }
        }
        // Clipping is a rendering hint only, so the panes snap to whole rows: what is drawn and what
        // is clickable stay the same set, and a click can never reach a row scrolled out of sight.
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Collapsed tab strip: one small "menu" button in the tab band; the tabs open under it. */
    private boolean tabsOpen;
    private Layout.Rect menuButton() {
        int width = 58, height = Math.max(10, Math.min(14, layout.tabs.h() - 4));
        return new Layout.Rect(layout.tabs.x() + 2, layout.tabs.y() + 2, width, height);
    }

    private Layout.Rect menuItem(int index) {
        int width = Math.max(120, Math.min(200, layout.tabs.w() - 4));
        return new Layout.Rect(layout.tabs.x() + 2, layout.tabs.bottom() + 2 + index * 16, width, 16);
    }

    /** The open menu, painted last so no pane or widget can occlude it. */
    private void menuOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        String[] names = tabs();
        for (int i = 0; i < names.length; i++) {
            Layout.Rect item = menuItem(i);
            boolean on = i == activeTab();
            boolean hovered = item.inside(mouseX, mouseY);
            graphics.fill(item.x(), item.y(), item.right(), item.bottom(),
                    on ? 0xf03a4a58 : hovered ? 0xf0283642 : 0xf0101820);
            graphics.drawString(font, names[i], item.x() + 6, item.y() + (item.h() - 8) / 2,
                    on ? Ui.ACCENT : Ui.TEXT, false);
        }
        graphics.fill(layout.tabs.x() + 2, layout.tabs.bottom() + 2 + names.length * 16,
                layout.tabs.x() + 2 + Math.max(120, Math.min(200, layout.tabs.w() - 4)),
                layout.tabs.bottom() + 3 + names.length * 16, Ui.FRAME);
    }

    // ------------------------------------------------------------------ widget helpers

    /** Creates a pane owned by this screen. */
    protected Form newForm(Layout.Rect area) {
        return new Form(area, font);
    }

    /** Registers a widget for events. Rendering belongs to the {@link Form} that owns it. */
    protected <T extends AbstractWidget> T live(T widget) {
        addRenderableWidget(widget);
        return widget;
    }

    /** A control row in {@code form}: label on the left, value on the right, accent underline. */
    protected FrontButton row(Form form, String label, String value, Runnable action) {
        return place(form, form.take(Layout.ROW), label, value, "", "", true, action);
    }

    protected FrontButton row(Form form, String label, String value, String iconKind, String iconId,
                              Runnable action) {
        return place(form, form.take(Layout.ROW), label, value, iconKind, iconId, true, action);
    }

    /** A row that may be unavailable; it stays visible and dimmed so the state is legible. */
    protected FrontButton row(Form form, String label, String value, boolean enabled, Runnable action) {
        return place(form, form.take(Layout.ROW), label, value, "", "", enabled, action);
    }

    protected FrontButton row(Form form, String label, String value, String iconKind, String iconId,
                              boolean enabled, Runnable action) {
        return place(form, form.take(Layout.ROW), label, value, iconKind, iconId, enabled, action);
    }

    /** Disabled rows stay visible and readable: the player needs to see *what* is unavailable. */
    protected FrontButton row(Form form, Layout.Rect rect, String label, String value,
                              String iconKind, String iconId, boolean enabled, Runnable action) {
        return place(form, rect, label, value, iconKind, iconId, enabled, action);
    }

    protected FrontButton place(Form form, Layout.Rect rect, String label, String value,
                                String iconKind, String iconId, boolean enabled, Runnable action) {
        FrontButton control = new FrontButton(label, value, rect.x(), rect.y(), rect.w(), rect.h(),
                iconKind, iconId, enabled, () -> guard(action));
        control.showActionIcon=actionIcons();live(control);
        form.add(control);
        return control;
    }

    /** A full-width primary action, used for the one thing the page is for. */
    protected FrontButton primary(Form form, String label, boolean enabled, Runnable action) {
        Layout.Rect rect = form.take(Layout.ROW + 4);
        FrontButton control = new FrontButton(label, "", rect.x(), rect.y(), rect.w(), rect.h(),
                "", "", enabled, () -> guard(action));
        control.primary = true;
        live(control);
        form.add(control);
        return control;
    }

    /** Text field with its caption above it. */
    protected void field(Form form, String label, String value, int maxLength, Consumer<String> setter) {
        form.text(label, Ui.TEXT_FAINT);
        EditBox box = edit(form.take(16, 3), value, maxLength, setter);
        form.add(box);
    }

    /** Text field filling a rect the caller already reserved, e.g. one half of a row. */
    protected EditBox edit(Layout.Rect rect, String value, int maxLength, Consumer<String> setter) {
        EditBox box = new EditBox(font, rect.x(), rect.y(), rect.w(), rect.h(), Component.empty());
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setBordered(true);
        box.setTextColor(Ui.TEXT);
        box.setTextColorUneditable(Ui.TEXT_FAINT);
        box.setResponder(text -> guard(() -> {
            setter.accept(text);
            box.setTextColor(Ui.TEXT);
        }));
        return live(box);
    }
}
