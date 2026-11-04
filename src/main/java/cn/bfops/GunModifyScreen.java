package cn.bfops;

import java.util.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

/**
 * The BF1 "MODIFY" bench: attachment slots down the left, the compatible list beside them, the gun in
 * the middle and the weapon's numbers on the right.
 *
 * <h2>Why a hand-painted screen</h2>
 *
 * <p>The reference layout is four columns over a full-bleed backdrop, with rows that are little more
 * than a highlight behind a glyph — a button widget with its own background, focus ring and narration
 * would have to be told to paint almost nothing, and a list of a hundred rows would still rebuild a
 * widget per row every time the selection moved. This screen therefore owns its own hit-testing against
 * the rectangles it just drew, which also makes it the cheapest screen in the mod: moving the mouse
 * rebuilds nothing, and switching a slot only changes a map entry.
 *
 * <p>The layout is authored at a fixed 1280×709 canvas and scaled to fit the window, exactly like the
 * reference, so the proportions do not drift with GUI scale.
 *
 * <h2>Gun rendering</h2>
 *
 * <p>The model is the pack's own item renderer (the same one TaCZ uses for its refit bench): the stack is
 * built through TaCZ's builder with the selected attachments installed, then drawn through
 * {@link GuiGraphics#renderItem} inside a pose that carries the drag rotation and zoom. {@code renderItem}
 * already applies the GUI display transform and 3D lighting, so the result is the pack's real model from
 * the same angle family as the in-game refit screen rather than a flat slot texture.
 */
final class GunModifyScreen extends Screen {
    private static final int CANVAS_W = 960, CANVAS_H = 532;

    /** Left slot rail. */
    private static final int RAIL_X = 44, RAIL_W = 150, RAIL_TOP = 92, RAIL_ROW = 42, RAIL_GAP = 3;
    /** Compatible attachment list. */
    private static final int LIST_X = 204, LIST_W = 222, LIST_TOP = 92, LIST_ROW = 22, LIST_GAP = 2;
    private static final int LIST_VISIBLE = 9;
    /** Right-hand numbers column. */
    private static final int RIGHT_X = 726, RIGHT_W = 210;
    /** The gun sits between the list and the numbers. */
    private static final int STAGE_X = 446, STAGE_Y = 112, STAGE_W = 270, STAGE_H = 300;

    private static final int WHITE = 0xffffffff, DIM = 0xffc4ccd2, FAINT = 0xff8e989f;
    private static final int INK = 0xff14181b, PANEL = 0x4c0b0e11, HOVER = 0x30ffffff;

    private final Screen parent;
    private final String map, weapon, kit;
    private final Map<String, String> target, working;

    private List<Fittings.Group> groups = List.of();
    private final Map<String, String> locks = new HashMap<>();
    private int level;
    private boolean loaded, requested;
    private long sentAt;
    private String selectedGroup = "";

    private float yaw = 25, pitch = -12, zoom = 6;
    private boolean dragging;
    private double lastMouseX, lastMouseY;

    private String message = "";
    private int messageColor = FAINT;

    private float scale = 1, originX, originY;

    GunModifyScreen(Screen parent, String map, String weapon, Map<String, String> target) {
        this(parent, map, weapon, target, Client.state.has("me")
                ? Engine.str(Client.state.getAsJsonObject("me"), "kit", "") : "");
    }

    GunModifyScreen(Screen parent, String map, String weapon, Map<String, String> target, String kit) {
        super(net.minecraft.network.chat.Component.literal("武器改装"));
        this.parent = parent;
        this.map = map == null ? "" : map;
        this.weapon = weapon == null ? "" : weapon;
        this.kit = kit == null ? "" : kit;
        this.target = target;
        this.working = new LinkedHashMap<>(target);
    }

    // ------------------------------------------------------------------ data

    private void fetch() {
        requested = true;
        sentAt = System.currentTimeMillis();
        var request = Client.request("fittings", map);
        request.addProperty("id", weapon);
        request.addProperty("kind", "gun");
        request.addProperty("kit", kit);
        Client.send(request);
        say("正在读取兼容配件…");
    }

    void receive(JsonObject data) {
        if (!map.equals(Engine.str(data, "map", map))) return;
        if (!weapon.equals(Engine.str(data, "id", ""))) return;
        if (!"gun".equals(Engine.str(data, "kind", ""))) return;
        if (!kit.equals(Engine.str(data, "kit", kit))) return;
        locks.clear();
        if (data.has("locks")) {
            data.getAsJsonObject("locks").entrySet().forEach(e -> locks.put(e.getKey(), e.getValue().getAsString()));
        }
        level = Engine.num(data, "level", 0);
        groups = Arrays.asList(BattlefieldOps.GSON.fromJson(data.get("groups"), Fittings.Group[].class));
        loaded = true;
        if (selectedGroup.isEmpty() || groups.stream().noneMatch(g -> g.id().equals(selectedGroup))) {
            selectedGroup = groups.isEmpty() ? "" : groups.get(0).id();
        }
        ok(groups.isEmpty() ? "此武器没有可配置项目" : "选择左侧槽位，再挑一个配件");
    }

    void error(String text) {
        fail(text);
    }

    @Override
    protected void init() {
        computeTransform();
        if (!requested && !loaded) fetch();
    }

    @Override
    public void tick() {
        super.tick();
        if (!loaded && requested && System.currentTimeMillis() - sentAt > 8000) {
            requested = false;
            warn("未收到兼容列表，点击右侧「重试」；现有配装未改变");
        }
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float x = canvasX(mouseX), y = canvasY(mouseY);
        if (inStage(x, y)) {
            if (button == 2) {
                resetView();
                return true;
            }
            dragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        Fittings.Group group = groupAt(x, y);
        if (button == 0 && group != null) {
            selectGroup(group);
            return true;
        }
        if (button == 0 && paging(x, y)) return true;
        if (button == 0) {
            int row = listRowAt(x, y);
            if (row >= 0) {
                choose(row);
                return true;
            }
        }
        if (button == 0) {
            String action = barActionAt(x, y);
            if (action != null) {
                switch (action) {
                    case "back" -> onClose();
                    case "retry" -> {
                        if (!loaded) fetch();
                    }
                    case "reset" -> {
                        working.clear();
                        say("已恢复默认；点击「应用配装」后生效");
                    }
                    case "view" -> resetView();
                    case "apply" -> apply();
                    default -> { }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            float factor = scale <= 0 ? 1 : 1 / scale;
            yaw += (float) ((mouseX - lastMouseX) * 0.55 * factor);
            pitch = (float) Math.max(-80, Math.min(80, pitch + (mouseY - lastMouseY) * 0.4 * factor));
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (vertical == 0) return false;
        float x = canvasX(mouseX), y = canvasY(mouseY);
        if (inStage(x, y)) {
            zoom = (float) Math.max(3, Math.min(12, zoom * (vertical > 0 ? 1.12 : 1 / 1.12)));
            return true;
        }
        if (x >= LIST_X && x <= LIST_X + LIST_W && y >= LIST_TOP) {
            int overflow = Math.max(0, listSize() - LIST_VISIBLE);
            listScroll = Math.max(0, Math.min(overflow, listScroll + (vertical > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void apply() {
        for (String id : working.values()) {
            if (locks.containsKey(id)) {
                fail(locks.get(id) + "；请移除该配件");
                return;
            }
        }
        try {
            Fittings.validate(groups, working);
        } catch (RuntimeException ex) {
            fail(FrontScreen.describe(ex));
            return;
        }
        target.clear();
        target.putAll(working);
        LoadoutProfiles.save();
        minecraft.setScreen(parent);
    }

    private void selectGroup(Fittings.Group group) {
        selectedGroup = group.id();
        listScroll = 0;
        cameraPreset(group.id());
    }

    private void choose(int row) {
        Fittings.Group group = selected();
        if (group == null) return;
        String id = row == 0 ? "" : group.options().get(row - 1).id();
        if (locks.containsKey(id)) {
            fail(locks.get(id));
            return;
        }
        if (id.isEmpty()) working.remove(group.id());
        else working.put(group.id(), id);
        say(row == 0 ? "已移除该槽位配件" : "已装上 " + group.options().get(row - 1).name());
    }

    /** TaCZ's bench turns the gun to the slot being edited; these are the same idea, per slot. */
    private void cameraPreset(String group) {
        switch (group) {
            case "SCOPE" -> { yaw = 2; pitch = -8; zoom = 7.5f; }
            case "MUZZLE" -> { yaw = -26; pitch = -10; zoom = 8f; }
            case "STOCK" -> { yaw = 34; pitch = -14; zoom = 7f; }
            case "GRIP" -> { yaw = -16; pitch = -26; zoom = 8f; }
            case "LASER" -> { yaw = 12; pitch = -30; zoom = 8f; }
            case "EXTENDED_MAG" -> { yaw = 6; pitch = 18; zoom = 7.5f; }
            default -> resetView();
        }
    }

    private void resetView() {
        yaw = 25;
        pitch = -12;
        zoom = 6;
    }

    // ------------------------------------------------------------------ painting

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean previousStyle = Ui.bf1();
        boolean previousWhite = AssetIcons.whiteIcons();
        Ui.bf1(true);
        AssetIcons.whiteIcons(true);
        try {
            renderBench(g, mouseX, mouseY, partialTick);
        } finally {
            Ui.bf1(previousStyle);
            AssetIcons.whiteIcons(previousWhite);
        }
    }

    private void renderBench(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        computeTransform();
        blitBackdrop(g);
        float x = canvasX(mouseX), y = canvasY(mouseY);
        g.pose().pushPose();
        try {
            g.pose().translate(originX, originY, 0);
            g.pose().scale(scale, scale, 1);
            drawTitle(g);
            drawRail(g, x, y);
            drawList(g, x, y);
            drawStage(g, x, y);
            drawStats(g);
            drawBar(g, x, y);
            if (!message.isEmpty()) {
                g.drawCenteredString(font, message, CANVAS_W / 2, CANVAS_H - 46, messageColor);
            }
        } finally {
            g.pose().popPose();
        }
    }

    private void blitBackdrop(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xff05070a);
        var texture = new net.minecraft.resources.ResourceLocation(
                "battlefield_ops", "textures/gui/bf1/modify_background.png");
        double k = Math.max(width / 1600.0, height / 900.0);
        int w = (int) Math.ceil(1600 * k), h = (int) Math.ceil(900 * k);
        g.blit(texture, (width - w) / 2, (height - h) / 2, w, h, 0, 0, 1600, 900, 1600, 900);
        g.fill(0, 0, width, height, 0x40000000);
    }

    private void drawTitle(GuiGraphics g) {
        String text = "改装";
        float s = 2.35f;
        float total = 0;
        for (char c : text.toCharArray()) total += (font.width(String.valueOf(c)) + 5) * s;
        float px = (CANVAS_W - total) / 2f, py = 20;
        for (char c : text.toCharArray()) {
            String glyph = String.valueOf(c);
            float w = font.width(glyph);
            text(g, s, px, py, glyph, 0xf2ffffff, false);
            px += (w + 5) * s;
        }
    }

    private void drawRail(GuiGraphics g, float mx, float my) {
        for (int i = 0; i < groups.size(); i++) {
            Fittings.Group group = groups.get(i);
            int top = RAIL_TOP + i * (RAIL_ROW + RAIL_GAP);
            boolean selected = group.id().equals(selectedGroup);
            boolean hover = inside(mx, my, RAIL_X, top, RAIL_W, RAIL_ROW);
            g.fill(RAIL_X, top, RAIL_X + RAIL_W, top + RAIL_ROW,
                    selected ? 0xf2f4f5f6 : hover ? HOVER : PANEL);
            if (selected) g.fill(RAIL_X, top, RAIL_X + RAIL_W, top + 1, WHITE);
            String label = label(group);
            text(g, 1.12f, RAIL_X + 14, top + RAIL_ROW / 2f - 6, label, selected ? INK : 0xffeef2f4, false);
            String current = working.get(group.id());
            String value = current == null ? "" : nameOf(group, current);
            if (!value.isEmpty()) {
                text(g, .82f, RAIL_X + 14, top + RAIL_ROW / 2f + 6,
                        Ui.clip(font, value, RAIL_W - 70), selected ? 0xff3d444a : 0xffa8b2b8, false);
            }
            drawSlotIcon(g, group, RAIL_X + RAIL_W - 44, top + RAIL_ROW / 2 - 14, 28, selected);
        }
        // Rail header, matching the reference's small caption above the slots.
        text(g, .85f, RAIL_X + 2, RAIL_TOP - 26, "配件槽位  " + working.size() + "/" + Math.max(groups.size(), 1),
                0xffc9d2d8, false);
    }

    private void drawSlotIcon(GuiGraphics g, Fittings.Group group, int x, int y, int size, boolean selected) {
        if (group.options().isEmpty()) return;
        var first = group.options().get(0);
        float tint = selected ? .12f : .9f;
        g.setColor(tint, tint, tint, 1f);
        try {
            AssetIcons.draw(g, first.kind(), first.icon(), x, y, size, size);
        } finally {
            g.setColor(1f, 1f, 1f, 1f);
        }
    }

    private void drawList(GuiGraphics g, float mx, float my) {
        Fittings.Group group = selected();
        if (group == null) {
            text(g, .95f, LIST_X, LIST_TOP + 6, loaded ? "此武器没有可配置项目" : "正在读取兼容配件…", DIM, false);
            return;
        }
        List<Fittings.Option> options = group.options();
        String current = working.getOrDefault(group.id(), "");
        int overflow = Math.max(0, listSize() - LIST_VISIBLE);
        if (overflow > 0) {
            int top = LIST_TOP - 26;
            boolean hoverUp = inside(mx, my, LIST_X + LIST_W - 58, top, 26, 18);
            boolean hoverDown = inside(mx, my, LIST_X + LIST_W - 30, top, 26, 18);
            g.fill(LIST_X + LIST_W - 58, top, LIST_X + LIST_W - 32, top + 18, hoverUp ? HOVER : PANEL);
            g.fill(LIST_X + LIST_W - 30, top, LIST_X + LIST_W - 4, top + 18, hoverDown ? HOVER : PANEL);
            g.drawCenteredString(font, "<", LIST_X + LIST_W - 45, top + 5, WHITE);
            g.drawCenteredString(font, ">", LIST_X + LIST_W - 17, top + 5, WHITE);
        }
        for (int i = 0; i < LIST_VISIBLE && listScroll + i < listSize(); i++) {
            int index = listScroll + i;
            int top = LIST_TOP + i * (LIST_ROW + LIST_GAP);
            boolean selected = index == 0 ? current.isEmpty() : options.get(index - 1).id().equals(current);
            boolean hover = inside(mx, my, LIST_X, top, LIST_W, LIST_ROW);
            g.fill(LIST_X, top, LIST_X + LIST_W, top + LIST_ROW, selected ? 0xf2f4f5f6 : hover ? HOVER : PANEL);
            if (index == 0) {
                if (!selected) g.fill(LIST_X + 6, top + LIST_ROW / 2 - 1, LIST_X + 22, top + LIST_ROW / 2 + 1,
                        selected ? INK : 0xff9aa6ad);
                text(g, .92f, LIST_X + 30, top + 9, "默认 / 不加装", selected ? INK : 0xffdfe6ea, false);
                continue;
            }
            var option = options.get(index - 1);
            if (!option.icon().isEmpty()) {
                float tint = selected ? .12f : .9f;
                g.setColor(tint, tint, tint, 1f);
                try {
                    AssetIcons.draw(g, option.kind(), option.icon(), LIST_X + 8, top + 3, 16, 16);
                } finally {
                    g.setColor(1f, 1f, 1f, 1f);
                }
            }
            String label = Ui.clip(font, clean(option), LIST_W - 60);
            text(g, .92f, LIST_X + 30, top + 9, label, selected ? INK : 0xffe9eef1, false);
            if (locks.containsKey(option.id())) {
                text(g, .8f, LIST_X + LIST_W - 30, top + 10, "锁", 0xffd9a566, false);
            }
        }
        if (listScroll > overflow) listScroll = Math.max(0, overflow);
    }

    private void drawStage(GuiGraphics g, float mx, float my) {
        int cx = STAGE_X + STAGE_W / 2, cy = STAGE_Y + STAGE_H / 2 - 10;
        boolean hover = inStage(mx, my);
        int shadowY = STAGE_Y + STAGE_H - 46;
        for (int i = 0; i < 6; i++) {
            int half = 92 - i * 8, height = 8 - i;
            if (height <= 0) continue;
            g.fill(cx - half, shadowY - height, cx + half, shadowY + height, (0x0c - i) << 24 | 0x000000);
        }

        ItemStack stack = displayGun();
        if (stack.isEmpty()) {
            // Only when the pack has no model at all does the slot texture stand in for it.
            g.setColor(1f, 1f, 1f, .85f);
            try {
                AssetIcons.draw(g, "gun", weapon, STAGE_X + 24, cy - 44, STAGE_W - 48, 88);
            } finally {
                g.setColor(1f, 1f, 1f, 1f);
            }
            text(g, .95f, STAGE_X + 10, STAGE_Y + STAGE_H - 26, "枪包没有提供可渲染的枪模（显示槽位贴图）", DIM, false);
            return;
        }
        GuiClip.push(g,STAGE_X,STAGE_Y,STAGE_W,STAGE_H-26);
        try {
            g.pose().pushPose();
            g.pose().translate(cx, cy, 240);
            // The model is authored in block units, so the pose scale is "pixels per block": 150 fills
            // most of the stage for a rifle and keeps the muzzle inside the panel.
            float modelScale = 72f * Math.max(.4f, zoom / 6f);
            g.pose().scale(modelScale, -modelScale, modelScale);
            g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
            g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            Minecraft.getInstance().getItemRenderer().renderStatic(stack,
                    net.minecraft.world.item.ItemDisplayContext.FIXED, 0xF000F0,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, g.pose(), buffers,
                    Minecraft.getInstance().level, 0);
            buffers.endBatch();
        } catch (RuntimeException | LinkageError ex) {
            BattlefieldOps.LOG.debug("Gun model render failed for {}", weapon, ex);
        } finally {
            g.pose().popPose();
            GuiClip.pop(g);
        }
        if (hover) {
            text(g, .72f, STAGE_X + 6, STAGE_Y + STAGE_H - 14, "拖动旋转 · 滚轮缩放 · 中键复位", 0x99c6d0d6, false);
        }
    }

    private void drawStats(GuiGraphics g) {
        GunSpec.Spec spec = GunSpec.of(weapon, working);
        int y = 112;
        AssetIcons.draw(g, "gun", weapon, RIGHT_X + 8, y, RIGHT_W - 16, 46);
        y += 66;
        text(g, 1.2f, RIGHT_X, y, Ui.clip(font, spec.name().isEmpty() ? GearNames.display("gun", weapon, weapon) : spec.name(), (int)(RIGHT_W/1.2f)),
                0xfff4f7f9, false);
        y += 20;
        String role = FixedClasses.resolve(kit) == null ? kit : FixedClasses.resolve(kit).name;
        text(g, .92f, RIGHT_X, y, (role.isEmpty() ? "步兵" : role) + "  /  主武器", 0xffc2ccd2, false);
        y += 16;
        text(g, .92f, RIGHT_X, y, "已装配 " + working.size() + " 项配件", 0xff9fabb2, false);
        y += 22;
        divider(g, RIGHT_X, y, RIGHT_W);
        y += 14;

        String[] labels = {"伤害", "精度", "腰射", "后坐"};
        float[] values = {spec.bars().damage(), spec.bars().accuracy(), spec.bars().hipFire(), spec.bars().control()};
        for (int i = 0; i < labels.length; i++) {
            text(g, .82f, RIGHT_X, y + 2, labels[i], 0xffcfd7dc, false);
            segments(g, RIGHT_X + 52, y, RIGHT_W - 52, values[i]);
            y += 18;
        }
        y += 6;
        divider(g, RIGHT_X, y, RIGHT_W);
        y += 12;

        y = fact(g, y, "射速", spec.rpm() > 0 ? spec.rpm() + "" : "—");
        y = fact(g, y, "射击模式", spec.fireModes().isEmpty() ? "—" : spec.fireModes());
        y = fact(g, y, "弹匣容量", spec.magazine() > 0 ? spec.magazine() + "" : "—");
        y += 8;
        divider(g, RIGHT_X, y, RIGHT_W);
        y += 10;
        text(g, .82f, RIGHT_X, y, "伤害衰减（米）", 0xffcfd7dc, false);
        y += 14;
        damageDrop(g, RIGHT_X, y, RIGHT_W, 66, spec);
    }

    private int fact(GuiGraphics g, int y, String label, String value) {
        text(g, .82f, RIGHT_X, y + 2, label, 0xffc6ced4, false);
        int w = (int) (font.width(value) * .92f);
        text(g, .92f, RIGHT_X + RIGHT_W - w, y, value, 0xffeef2f4, false);
        return y + 17;
    }

    private void damageDrop(GuiGraphics g, int x, int y, int w, int h, GunSpec.Spec spec) {
        GunBallistics.Curve curve = GunBallistics.of(weapon);
        if (!curve.falloff()) {
            float range = curve.range() > 1 ? curve.range() : spec.effectiveRange();
            text(g, .9f, x, y + h / 2f - 5, range > 1 ? "有效射程 " + Math.round(range) + " 米" : "枪包未提供射程数据",
                    0xffcfd7dc, false);
            return;
        }

        float maxDistance = 1f, maxDamage = 1f;
        for (float[] point : curve.points()) {
            maxDistance = Math.max(maxDistance, point[0]);
            maxDamage = Math.max(maxDamage, point[1]);
        }
        float top = maxDamage * 1.08f;

        // Gutters: 26 px on the left for the damage labels, 14 px underneath for the distance labels and one
        // row for the caption. Everything inside the frame is plot; no text shares a row with the line.
        int left = x + 26, right = x + w - 2;
        int topPx = y + 3, bottom = y + h - 24;
        int plotW = Math.max(8, right - left), plotH = Math.max(8, bottom - topPx);

        g.fill(left, bottom, right, bottom + 1, 0x66ffffff);
        g.fill(left, topPx, left + 1, bottom + 1, 0x66ffffff);
        for (int i = 1; i <= 3; i++) {
            int py = bottom - plotH * i / 4;
            g.fill(left, py, right, py + 1, 0x14ffffff);
        }
        for (int i = 1; i <= 4; i++) {
            int px = left + plotW * i / 4;
            g.fill(px, bottom, px + 1, bottom + 2, 0x40ffffff);
            String tick = String.valueOf(Math.round(maxDistance * i / 4f));
            int tw = (int) (font.width(tick) * .68f);
            text(g, .68f, px - tw / 2f, bottom + 3, tick, 0xff8e989f, false);
        }
        // Damage labels live in the left gutter, right-aligned against the axis, so they never cross the plot.
        String topLabel = String.valueOf(Math.round(top));
        text(g, .68f, left - 3 - (int) (font.width(topLabel) * .68f), topPx - 1, topLabel, 0xff7f8b93, false);
        text(g, .68f, left - 3 - (int) (font.width("0") * .68f), bottom - 8, "0", 0xff7f8b93, false);

        int prevX = -1, prevY = -1;
        for (float[] point : curve.points()) {
            int px = left + Math.round(Math.min(1f, point[0] / maxDistance) * plotW);
            int py = bottom - Math.round(Math.max(0f, Math.min(1f, point[1] / top)) * plotH);
            py = Math.max(topPx, Math.min(bottom, py));
            if (prevX >= 0) line(g, prevX, prevY, px, py, 0xffe9eef2);
            g.fill(Math.max(left, px - 1), Math.max(topPx, py - 1), Math.min(right, px + 2), Math.min(bottom, py + 2),
                    0xffffffff);
            prevX = px;
            prevY = py;
        }

        float[] last = curve.points().get(curve.points().size() - 1);
        String caption = Math.round(last[1]) + " 伤害 @ " + Math.round(last[0]) + " 米";
        text(g, .68f, right - (int) (font.width(caption) * .68f), bottom + 14, caption, 0xff9aa6ad, false);
    }

    private void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            double f = steps == 0 ? 0 : i / (double) steps;
            int px = (int) Math.round(x0 + (x1 - x0) * f), py = (int) Math.round(y0 + (y1 - y0) * f);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private void segments(GuiGraphics g, int x, int y, int width, float value) {
        int count = 12, gap = 3;
        int cell = Math.max(3, (width - gap * (count - 1)) / count);
        int filled = Math.round(Math.max(0, Math.min(1, value)) * count);
        for (int i = 0; i < count; i++) {
            int px = x + i * (cell + gap);
            g.fill(px, y + 2, px + cell, y + 5, i < filled ? 0xffe9eef2 : 0x40ffffff);
        }
    }

    private void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, 0x38ffffff);
    }

    private void drawBar(GuiGraphics g, float mx, float my) {
        int top = CANVAS_H - 33;
        g.fill(0, top, CANVAS_W, CANVAS_H, 0xcc07090b);
        g.fill(0, top, CANVAS_W, top + 1, 0x50ffffff);
        barItem(g, mx, my, "返回", 40, 74, "back", false);
        barItem(g, mx, my, loaded ? "恢复默认" : "重试", 146, 96, loaded ? "reset" : "retry", false);
        barItem(g, mx, my, "复位视角", 272, 96, "view", false);
        barItem(g, mx, my, "应用配装", CANVAS_W - 148, 118, "apply", true);
        text(g, .85f, 420, top + 12, "拖动旋转 · 滚轮缩放 · 中键 复位", 0x88aab4ba, false);
        String progress = loaded ? (working.size() + " / " + groups.size()) : "…";
        text(g, .85f, CANVAS_W - 210, top + 12, progress, 0x88aab4ba, false);
    }

    private void barItem(GuiGraphics g, float mx, float my, String label, int x, int w, String id, boolean primary) {
        int top = CANVAS_H - 33;
        boolean hover = inside(mx, my, x, top + 3, w, 27);
        if (hover) g.fill(x, top + 4, x + w, top + 29, primary ? 0x40ffffff : HOVER);
        text(g, .95f, x + 8, top + 12, label, primary ? 0xffffffff : 0xffd7dee2, false);
    }

    // ------------------------------------------------------------------ helpers

    private float canvasX(double mouseX) {
        return (float) ((mouseX - originX) / (scale <= 0 ? 1 : scale));
    }

    private float canvasY(double mouseY) {
        return (float) ((mouseY - originY) / (scale <= 0 ? 1 : scale));
    }

    private void computeTransform() {
        scale = Math.max(.05f, Math.min(width / (float) CANVAS_W, height / (float) CANVAS_H));
        originX = (width - CANVAS_W * scale) / 2f;
        originY = (height - CANVAS_H * scale) / 2f;
    }

    private void text(GuiGraphics g, float size, float x, float y, String value, int color, boolean shadow) {
        if (value == null || value.isEmpty()) return;
        g.pose().pushPose();
        try {
            g.pose().scale(size, size, 1);
            g.drawString(font, value, (int) (x / size), (int) (y / size), color, shadow);
        } finally {
            g.pose().popPose();
        }
    }

    private static boolean inside(float mx, float my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean inStage(float mx, float my) {
        return inside(mx, my, STAGE_X, STAGE_Y, STAGE_W, STAGE_H);
    }

    private Fittings.Group selected() {
        for (Fittings.Group group : groups) if (group.id().equals(selectedGroup)) return group;
        return groups.isEmpty() ? null : groups.get(0);
    }

    private Fittings.Group groupAt(float mx, float my) {
        // X matters: without it every list row at a rail row's height re-selected that rail entry.
        if (mx < RAIL_X || mx > RAIL_X + RAIL_W) return null;
        for (int i = 0; i < groups.size(); i++) {
            int top = RAIL_TOP + i * (RAIL_ROW + RAIL_GAP);
            if (my >= top && my < top + RAIL_ROW) return groups.get(i);
        }
        return null;
    }

    private boolean paging(float mx, float my) {
        int overflow = listSize() - LIST_VISIBLE;
        if (overflow <= 0) return false;
        if (mx < LIST_X + LIST_W - 58 || mx > LIST_X + LIST_W - 4) return false;
        return my >= LIST_TOP - 26 && my < LIST_TOP - 8;
    }

    private int listRowAt(float mx, float my) {
        if (mx < LIST_X || mx > LIST_X + LIST_W) return -1;
        if (my < LIST_TOP) return -1;
        int row = (int) ((my - LIST_TOP) / (LIST_ROW + LIST_GAP));
        if (row < 0 || row >= LIST_VISIBLE) return -1;
        if (listScroll + row >= listSize()) return -1;
        return listScroll + row;
    }

    private String barActionAt(float mx, float my) {
        int top = CANVAS_H - 33;
        if (my < top || my > CANVAS_H) return null;
        if (inside(mx, my, 40, top + 3, 74, 27)) return "back";
        if (inside(mx, my, 146, top + 3, 96, 27)) return loaded ? "reset" : "retry";
        if (inside(mx, my, 272, top + 3, 96, 27)) return "view";
        if (inside(mx, my, CANVAS_W - 148, top + 3, 118, 27)) return "apply";
        return null;
    }

    private int listSize() {
        Fittings.Group group = selected();
        return group == null ? 1 : group.options().size() + 1;
    }

    private int listScroll;

    private static String label(Fittings.Group group) {
        String name = group.name();
        return name == null || name.isBlank() ? group.id() : name;
    }

    private static String nameOf(Fittings.Group group, String id) {
        for (var option : group.options()) if (option.id().equals(id)) return clean(option);
        return "";
    }

    /** A name that still looks like a translation key is not a name; ask the local catalogue instead. */
    private static String clean(Fittings.Option option) {
        String name = option.name() == null ? "" : option.name();
        if (!name.isEmpty() && !name.contains(".") && !name.startsWith("item.")) return name;
        String local = GearNames.display(option.kind(), option.icon(), option.id());
        return local == null || local.isEmpty() ? option.id() : local;
    }

    private ItemStack displayGun() {
        return GunPreview.assembled(weapon, working);
    }

    private void ok(String text) {
        message = text;
        messageColor = 0xff8fd7a8;
    }

    private void warn(String text) {
        message = text;
        messageColor = 0xffd9a566;
    }

    private void fail(String text) {
        message = text;
        messageColor = 0xffe08a86;
    }

    private void say(String text) {
        message = text;
        messageColor = FAINT;
    }
}
