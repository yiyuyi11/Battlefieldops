package cn.bfops.voice.client.hud;

import cn.bfops.Draw;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import cn.bfops.voice.VoiceConfig;
import cn.bfops.voice.client.QuickWheelServerConfig;
import cn.bfops.voice.common.audio.VoiceCue;

public final class QuickMessageHudRenderer {

    /** 基础分段数 */
    static final int BASE_SEGMENT_COUNT = 5;
    /** 最大拓展格数 */
    private static final int MAX_EXTENSION_COUNT = 7;
    /** 最大总分段数 */
    private static final int MAX_SEGMENT_COUNT = BASE_SEGMENT_COUNT + MAX_EXTENSION_COUNT;

    /** 每个基础分段对应的字幕 */
    private static final String[] BASE_LABELS = {"GOGOGO", "需要弹药", "需要维修", "需要医疗", "谢谢"};
    /** 每个基础分段对应的 VoiceCue */
    private static final VoiceCue[] BASE_CUES = {
        VoiceCue.GOGOGO, VoiceCue.REQUEST_AMMO, VoiceCue.REQUEST_REPAIR,
        VoiceCue.DOWNED_HELP, VoiceCue.THANKS
    };

    /** 圆环外半径 */
    private static final float OUTER_RADIUS = 80.0f;
    /** 圆环内半径（中心不渲染区域） */
    private static final float INNER_RADIUS = 32.0f;
    /** 切口角度（度） */
    private static final float GAP_ANGLE = 3.0f;
    /** 中心点击圆的半径比例（相对于内半径） */
    private static final float CENTER_CIRCLE_RATIO = 0.85f;
    /** 字幕文字半径（内外半径的中点） */
    private static final float LABEL_RADIUS = (INNER_RADIUS + OUTER_RADIUS) / 2.0f;

    /** 圆环各部分透明度 */
    private static final float RING_ALPHA = 0.40f;
    /** 中心白色圆透明度 */
    private static final float CENTER_ALPHA = 0.20f;

    /** 悬停时外半径增量 */
    private static final float HOVER_RADIUS_GROWTH = 4.0f;
    /** 悬停动画时长（秒） */
    private static final float ANIM_DURATION = 0.15f;

    /** 倒计时文字距中心的 Y 偏移（像素） */
    private static final float COUNTDOWN_OFFSET_Y = 5.0f;
    /** 倒计时文字入场 / 出场动画时长（秒） */
    private static final float COUNTDOWN_ANIM_DURATION = 0.12f;

    private final float[] prevSegmentHoverAlpha = new float[MAX_SEGMENT_COUNT];
    private final float[] segmentHoverAlpha = new float[MAX_SEGMENT_COUNT];
    private int hoveredSegment = -1;
    private boolean isInCenterCircle;

    /**
     * 获取当前总分段数（基础5格 + 客户端配置拓展格数）。
     */
    public static int getTotalSegmentCount() {
        return BASE_SEGMENT_COUNT + VoiceConfig.getQuickWheelExtensionCount();
    }

    /**
     * 获取指定索引的分段标签。
     */
    public static String getSegmentLabel(int index) {
        if (index < BASE_SEGMENT_COUNT) {
            return BASE_LABELS[index];
        }
        int extIndex = index - BASE_SEGMENT_COUNT;
        return VoiceConfig.getQuickWheelExtensionSlot(extIndex);
    }

    /**
     * 获取指定索引的分段 VoiceCue，拓展格返回 null。
     */
    public static VoiceCue getSegmentCue(int index) {
        if (index < BASE_SEGMENT_COUNT) {
            return BASE_CUES[index];
        }
        return null;
    }

    /**
     * 每 tick 更新鼠标位置判定和动画状态。
     */
    public void update(float mouseX, float mouseY, float centerX, float centerY) {
        float dx = mouseX - centerX;
        float dy = mouseY - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        isInCenterCircle = distance < INNER_RADIUS * CENTER_CIRCLE_RATIO;

        // 鼠标角度：0° = 正上方，顺时针增加
        float angle = (float) Math.toDegrees(Math.atan2(dx, -dy));
        if (angle < 0.0f) {
            angle += 360.0f;
        }

        int totalSegments = getTotalSegmentCount();
        // 判段鼠标落在哪个分段 / 切口
        float segmentSpan = 360.0f / totalSegments;
        float halfGap = GAP_ANGLE / 2.0f;
        int rawSeg = (int) (angle / segmentSpan);
        if (rawSeg >= totalSegments) {
            rawSeg = totalSegments - 1;
        }
        float angleInSegment = angle - rawSeg * segmentSpan;

        if (isInCenterCircle || angleInSegment < halfGap || angleInSegment > segmentSpan - halfGap) {
            hoveredSegment = -1;
        } else {
            hoveredSegment = rawSeg;
        }

        // 每个分段独立驱动悬停动画，先存 prev 再用 partialTick 做帧间平滑插值
        System.arraycopy(segmentHoverAlpha, 0, prevSegmentHoverAlpha, 0, MAX_SEGMENT_COUNT);
        float animStep = 1.0f / (20.0f * ANIM_DURATION);
        for (int i = 0; i < totalSegments; i++) {
            float target = (i == hoveredSegment) ? 1.0f : 0.0f;
            segmentHoverAlpha[i] = Mth.approach(segmentHoverAlpha[i], target, animStep);
        }
    }

    public int getHoveredSegment() {
        return hoveredSegment;
    }

    public boolean isMouseInCenter() {
        return isInCenterCircle;
    }

    /**
     * 渲染整个快速消息圆环 HUD。
     */
    public void render(GuiGraphics guiGraphics, float centerX, float centerY,
            float ringAlpha, float overlayAlpha, float partialTick, int screenWidth, int screenHeight,
            float cooldownRemaining, float cooldownDisplayAlpha) {
        if (ringAlpha <= 0.001f && overlayAlpha <= 0.001f) {
            return;
        }

        // 全屏暗色叠加层
        if (overlayAlpha > 0.001f) {
            int color = ((int) (overlayAlpha * 255.0f) << 24) | 0x000000;
            guiGraphics.fill(0, 0, screenWidth, screenHeight, color);
        }

        if (ringAlpha <= 0.001f) {
            return;
        }

        boolean inCooldown = cooldownRemaining > 0.0f;

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(centerX, centerY, 0.0F);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        try {
            int totalSegments = getTotalSegmentCount();
            float segmentSpan = 360.0f / totalSegments;
            float halfGap = GAP_ANGLE / 2.0f;
            float ringColorAlpha = RING_ALPHA * ringAlpha;

            for (int i = 0; i < totalSegments; i++) {
                float startAngle = i * segmentSpan + halfGap;
                float endAngle = (i + 1) * segmentSpan - halfGap;
                float hover = Mth.lerp(partialTick, prevSegmentHoverAlpha[i], segmentHoverAlpha[i]);
                float outerR = OUTER_RADIUS + HOVER_RADIUS_GROWTH * hover;

                drawArcRing(matrix, startAngle, endAngle, INNER_RADIUS, outerR,
                    0.0f, 0.0f, 0.0f, ringColorAlpha);

                if (hover > 0.001f && !inCooldown) {
                    drawArcRing(matrix, startAngle, endAngle, outerR, outerR + 1.5f,
                        1.0f, 1.0f, 1.0f, hover);
                }
            }

            if (isInCenterCircle) {
                float centerR = INNER_RADIUS * CENTER_CIRCLE_RATIO;
                drawFilledCircle(matrix, centerR, 1.0f, 1.0f, 1.0f, CENTER_ALPHA * ringAlpha);
            }

            // 字幕渲染
            drawSegmentLabels(guiGraphics, ringAlpha, partialTick, inCooldown);

            // 冷却倒计时
            if (inCooldown && cooldownDisplayAlpha > 0.001f) {
                drawCooldownText(guiGraphics, cooldownRemaining, cooldownDisplayAlpha * ringAlpha);
            }
        } finally {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            poseStack.popPose();
        }
    }

    /**
     * 渲染每个分段的字幕标签。
     */
    private void drawSegmentLabels(GuiGraphics guiGraphics, float ringAlpha, float partialTick, boolean inCooldown) {
        Font font = Minecraft.getInstance().font;
        int totalSegments = getTotalSegmentCount();
        float segmentSpan = 360.0f / totalSegments;
        int baseAlpha = Math.max(25, (int) (ringAlpha * 200.0f));
        boolean customAllowed = QuickWheelServerConfig.isCustomMessagesAllowed();
        boolean quickChatEnabled = QuickWheelServerConfig.isQuickChatEnabled();

        int colorGray = (baseAlpha << 24) | 0x00808080;
        int colorWhiteDim = (baseAlpha << 24) | 0x00FFFFFF;
        int colorWhiteBright = (Math.max(25, (int) (ringAlpha * 230.0f)) << 24) | 0x00FFFFFF;
        int colorCyan = (baseAlpha << 24) | 0x0000FFFF;
        int colorCyanBright = (Math.max(25, (int) (ringAlpha * 230.0f)) << 24) | 0x0000FFFF;
        int colorDarkGray = (baseAlpha << 24) | 0x00404040;

        for (int i = 0; i < totalSegments; i++) {
            float midAngle = i * segmentSpan + segmentSpan / 2.0f;
            float rad = (float) Math.toRadians(midAngle);
            float sin = (float) Math.sin(rad);
            float cos = (float) Math.cos(rad);
            String label = getSegmentLabel(i);
            float tx = LABEL_RADIUS * sin;
            float ty = -LABEL_RADIUS * cos;
            int labelX = (int) tx - font.width(label) / 2;
            int labelY = (int) ty - font.lineHeight / 2;

            boolean isCustom = i >= BASE_SEGMENT_COUNT;
            float hover = Mth.lerp(partialTick, prevSegmentHoverAlpha[i], segmentHoverAlpha[i]);

            int labelColor;
            if (!quickChatEnabled) {
                labelColor = colorDarkGray;
            } else if (isCustom && !customAllowed) {
                labelColor = colorDarkGray;
            } else if (inCooldown) {
                labelColor = colorGray;
            } else if (isCustom) {
                labelColor = hover > 0.5f ? colorCyanBright : colorCyan;
            } else {
                labelColor = hover > 0.5f ? colorWhiteBright : colorWhiteDim;
            }

            guiGraphics.drawString(font, label, labelX, labelY, labelColor);
        }
    }

    /**
     * 渲染冷却倒计时文字（屏幕中心下方，红色，x.y 格式）。
     */
    private void drawCooldownText(GuiGraphics guiGraphics, float remainingSeconds, float alpha) {
        Font font = Minecraft.getInstance().font;
        String text = String.format(java.util.Locale.ROOT, "%.1f", remainingSeconds);
        int textWidth = font.width(text);
        int textColor = (Math.max(4, (int) (alpha * 255.0f)) << 24) | 0x00FF4444;
        guiGraphics.drawString(font, text,
            -textWidth / 2,
            Math.round(COUNTDOWN_OFFSET_Y),
            textColor);
    }

    // === 底层绘制工具 ===

    private static void drawArcRing(Matrix4f matrix,
            float startAngle, float endAngle,
            float innerR, float outerR, float r, float g, float b, float a) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = Draw.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        int steps = Math.max(1, (int) ((endAngle - startAngle) / 1.5f));
        for (int i = 0; i < steps; i++) {
            float t0 = (float) i / steps;
            float t1 = (float) (i + 1) / steps;
            float a0 = startAngle + t0 * (endAngle - startAngle);
            float a1 = startAngle + t1 * (endAngle - startAngle);
            float rad0 = (float) Math.toRadians(a0);
            float rad1 = (float) Math.toRadians(a1);
            float sin0 = (float) Math.sin(rad0), cos0 = (float) Math.cos(rad0);
            float sin1 = (float) Math.sin(rad1), cos1 = (float) Math.cos(rad1);

            buffer.vertex(matrix, innerR * sin0, -innerR * cos0, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, outerR * sin0, -outerR * cos0, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, outerR * sin1, -outerR * cos1, 0.0f).color(r, g, b, a);

            buffer.vertex(matrix, innerR * sin0, -innerR * cos0, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, outerR * sin1, -outerR * cos1, 0.0f).color(r, g, b, a);
            buffer.vertex(matrix, innerR * sin1, -innerR * cos1, 0.0f).color(r, g, b, a);
        }

        Draw.end(buffer);
    }

    private static void drawFilledCircle(Matrix4f matrix,
            float radius, float r, float g, float b, float a) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = Draw.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        buffer.vertex(matrix, 0.0f, 0.0f, 0.0f).color(r, g, b, a);
        int steps = 32;
        for (int i = 0; i <= steps; i++) {
            float angle = (float) (2.0 * Math.PI * i / steps);
            float sin = (float) Math.sin(angle);
            float cos = (float) Math.cos(angle);
            buffer.vertex(matrix, radius * sin, -radius * cos, 0.0f).color(r, g, b, a);
        }

        Draw.end(buffer);
    }
}
