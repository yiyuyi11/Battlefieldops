package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Shared shell for every screen in this mod: one opaque window, one layout, one footer.
 *
 * <p><b>Why this class exists.</b> The reported bugs — "the right-hand map looks like it is on a
 * different layer than the left column" and "everything is very blurry" — were not cosmetic. Every
 * screen used to paint its own content and then call {@code super.render(...)}, and vanilla's
 * {@code Screen.render} starts with {@code renderBackground}, which blurs a copy of the world
 * ({@code processBlurEffect}) and then lays a semi-transparent dirt texture over the whole window.
 * That blur landed on top of the hand-drawn map, while the text painted afterwards stayed sharp: the
 * two halves of the same window were literally composited in different passes over a blurred world.
 * The old panels also mixed alpha values ({@code 0xe9…}, {@code 0x80…}, {@code 0x50…}), so the world
 * bled through by a different amount in each region.
 *
 * <p>So the shell owns rendering: it paints an opaque backdrop (nothing of the world shows through),
 * draws the chrome, lets the subclass paint its body, and never calls {@code super.render}. Widgets
 * are rendered exactly once, by the {@link Form} that owns them, in a single pass above the panels —
 * which is what makes both panes read as one window.
 */
abstract class FrontScreen extends Screen {
    /** Message shown in the footer. Blank hides it. */
    protected String message = "";
    /** Colour for {@link #message}; semantic, so callers say "this failed" rather than pick a colour. */
    protected int messageColor = Ui.TEXT_DIM;

    private final String windowTitle;

    FrontScreen(String windowTitle) {
        super(Component.literal(windowTitle));
        this.windowTitle = windowTitle;
    }

    /** Recreated on init and on resize, so no screen ever keeps a stale rectangle. */
    protected Layout layout;

    /** Build widgets for the current tab from current state. */
    protected abstract void rebuild();

    /** Paint the working area. Widgets are drawn by their owning {@link Form}. */
    protected abstract void paint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /** Tab labels, or an empty array for a screen without tabs. */
    protected String[] tabs() {
        return new String[0];
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
        layout = Layout.of(width, height);
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
        super.resize(minecraft, width, height);
    }

    @Override
    public void tick() {
        super.tick();
        poll();
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
        Ui.backdrop(graphics, width, height);
        chrome(graphics, mouseX, mouseY);
        Layout.Rect bar = layout.bar;
        if (bar.h() > 0) {
            graphics.fill(bar.x(),bar.y(),bar.right(),bar.bottom(),Ui.PANEL_SUNKEN);
            Ui.frame(graphics,bar,Ui.FRAME);
        }
        // Same reasoning for painting: a bad frame should cost one frame, not the session. The error is logged
        // once per distinct message so a per-frame failure cannot flood the log.
        try {
            paint(graphics, mouseX, mouseY, partialTick);
        } catch (RuntimeException | LinkageError ex) {
            reportPaintFailure(ex);
        }
        foot(graphics);
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
        Layout.Rect[] cells = tabRects();
        for (int i = 0; i < cells.length; i++) {
            Ui.tab(graphics, font, cells[i], tabs()[i], i == activeTab(), cells[i].inside(mouseX, mouseY));
        }
        // A single accent rule under the whole tab strip, so the strip reads as a band belonging to the
        // window rather than as a row of unrelated buttons. Individual tabs still underline themselves;
        // this is the baseline they sit on.
        if (cells.length > 0) {
            graphics.fill(layout.tabs.x(), layout.tabs.bottom(), layout.tabs.right(),
                    layout.tabs.bottom() + 1, Ui.FRAME);
        }
        // Title strip: window name on the left, context after it, so the operator always knows which
        // map and which tab they are looking at.
        int titleX = layout.title.x() + 7;
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
        if (button == 0) {
            Layout.Rect[] cells = tabRects();
            for (int i = 0; i < cells.length; i++) {
                if (cells[i].inside(mouseX, mouseY)) {
                    if (onTab(i)) return true;
                }
            }
        }
        // Clipping is a rendering hint only, so the panes snap to whole rows: what is drawn and what
        // is clickable stay the same set, and a click can never reach a row scrolled out of sight.
        return super.mouseClicked(mouseX, mouseY, button);
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
        live(control);
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
