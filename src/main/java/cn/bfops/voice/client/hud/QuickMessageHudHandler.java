package cn.bfops.voice.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
import cn.bfops.voice.VoiceConfig;
import cn.bfops.voice.client.FrontVoiceKeyMappings;
import cn.bfops.voice.client.QuickWheelServerConfig;
import cn.bfops.voice.common.audio.VoiceCue;
import cn.bfops.voice.network.C2SRequestCuePayload;
import cn.bfops.voice.network.VoiceNetwork;

public final class QuickMessageHudHandler {
    private static final QuickMessageHudHandler INSTANCE = new QuickMessageHudHandler();

    private static final float TARGET_ALPHA = 0.15f;
    private static final float ALPHA_STEP = 0.04f;
    private static final float COOLDOWN_ALPHA_STEP = 1.0f / (20.0f * 0.12f); // matches COUNTDOWN_ANIM_DURATION

    private final QuickMessageHudRenderer renderer = new QuickMessageHudRenderer();

    private boolean keyWasDown;
    private boolean gestureHeld,voiceGesture,gestureConsumed;
    private long gestureAt;
    private static final float VOICE_DRAG_THRESHOLD=38;
    /** Game ticks a hold must persist before the wheel takes over the mouse; below this it is a tap (a mark). */
    private static final long HOLD_TICKS=3;
    private void freeMouse(Minecraft mc){
        if(mouseFreedByUs)return;
        mc.mouseHandler.releaseMouse();mouseFreedByUs=true;
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),mc.getWindow().getWidth()/2.0,mc.getWindow().getHeight()/2.0);
    }
    private boolean physicalDown(net.minecraft.client.KeyMapping mapping,Minecraft mc){
        var key=com.mojang.blaze3d.platform.InputConstants.getKey(mapping.saveString());
        if(key.getType()==com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM&&key.getValue()>=0)return GLFW.glfwGetKey(mc.getWindow().getWindow(),key.getValue())==GLFW.GLFW_PRESS;
        if(key.getType()==com.mojang.blaze3d.platform.InputConstants.Type.MOUSE)return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),key.getValue())==GLFW.GLFW_PRESS;
        return mapping.isDown();
    }
    private void restoreMouse(Minecraft mc){
        if(!mouseFreedByUs)return;
        if(mc.player==null||mc.level==null){mouseFreedByUs=false;return;}
        if(mc.screen!=null||mc.getOverlay()!=null||!mc.isWindowActive())return;
        // Other handlers can change GLFW mode while vanilla still thinks the mouse is grabbed.
        // Reset both states together, then let vanilla re-establish captured mode.
        mc.mouseHandler.releaseMouse();mc.mouseHandler.grabMouse();
        mouseFreedByUs=!mc.mouseHandler.isMouseGrabbed();
    }
    private void updatePointer(Minecraft mc){
        float x=(float)(mc.mouseHandler.xpos()*mc.getWindow().getGuiScaledWidth()/mc.getWindow().getWidth());
        float y=(float)(mc.mouseHandler.ypos()*mc.getWindow().getGuiScaledHeight()/mc.getWindow().getHeight());
        renderer.update(x,y,mc.getWindow().getGuiScaledWidth()/2f,mc.getWindow().getGuiScaledHeight()/2f);
    }
    private boolean commitVoice(){
        int segment=renderer.getHoveredSegment();
        if(segment<0||segment>=QuickMessageHudRenderer.getTotalSegmentCount()||getCooldownRemaining()>0
                ||!QuickWheelServerConfig.isQuickChatEnabled()
                ||segment>=QuickMessageHudRenderer.BASE_SEGMENT_COUNT&&!QuickWheelServerConfig.isCustomMessagesAllowed())return false;
        triggerQuickMessage(segment);return true;
    }
    private float currentAlpha;
    private float prevAlpha;
    private boolean mouseFreedByUs;
    private boolean closeRequested;

    private long cooldownEndTimeMs;
    private float cooldownDisplayAlpha;
    private float prevCooldownDisplayAlpha;

    private QuickMessageHudHandler() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.register(INSTANCE);
    }

    @SubscribeEvent
    public void onClientTick(final ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if(!cn.bfops.BattleServerMode.clientActive||!cn.bfops.Client.inBattleRoom()||mc.player==null||mc.level==null||mc.screen!=null||mc.getOverlay()!=null||!mc.isWindowActive()){
            while(FrontVoiceKeyMappings.TACTICAL_COMMUNICATION.consumeClick()){}
            gestureHeld=false;voiceGesture=false;gestureConsumed=false;keyWasDown=false;closeRequested=false;
            currentAlpha=prevAlpha=0;
            restoreMouse(mc);
            return;
        }
        if(mouseFreedByUs&&!gestureHeld&&!keyWasDown)restoreMouse(mc);
        boolean legacy=physicalDown(FrontVoiceKeyMappings.OPEN_QUICK_MESSAGE_HUD,mc)
                &&!FrontVoiceKeyMappings.OPEN_QUICK_MESSAGE_HUD.same(FrontVoiceKeyMappings.TACTICAL_COMMUNICATION);
        boolean tactical=physicalDown(FrontVoiceKeyMappings.TACTICAL_COMMUNICATION,mc)&&cn.bfops.Client.tacticalVoiceAllowed()&&!legacy;
        boolean clicked=false;
        while(FrontVoiceKeyMappings.TACTICAL_COMMUNICATION.consumeClick())clicked=true;
        // A tap shorter than one game tick still arrives in the key mapping's click queue.
        // A tap of the wheel key no longer marks: marking has its own key, and the wheel only
        // responds to a deliberate hold.
        if(tactical&&!gestureHeld){
            gestureHeld=true;voiceGesture=false;gestureConsumed=false;closeRequested=false;
            gestureAt=mc.level.getGameTime();
        }
        // A quick tap is a mark and must not disturb the current aim; only a deliberate hold takes the mouse over.
        boolean gestureEngaged=tactical&&gestureHeld&&!gestureConsumed&&mc.level.getGameTime()>=gestureAt+HOLD_TICKS;
        if(gestureEngaged&&!mouseFreedByUs){
            freeMouse(mc);
            GLFW.glfwSetInputMode(mc.getWindow().getWindow(),GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_HIDDEN);
        }
        if(gestureHeld&&!gestureConsumed&&mc.level.getGameTime()>gestureAt){
            float dx=(float)(mc.mouseHandler.xpos()*mc.getWindow().getGuiScaledWidth()/mc.getWindow().getWidth())-mc.getWindow().getGuiScaledWidth()/2f;
            float dy=(float)(mc.mouseHandler.ypos()*mc.getWindow().getGuiScaledHeight()/mc.getWindow().getHeight())-mc.getWindow().getGuiScaledHeight()/2f;
            if(dx*dx+dy*dy>=VOICE_DRAG_THRESHOLD*VOICE_DRAG_THRESHOLD)voiceGesture=true;
            updatePointer(mc);
            if(voiceGesture&&mouseFreedByUs)GLFW.glfwSetInputMode(mc.getWindow().getWindow(),GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_NORMAL);
        }
        if(!tactical&&gestureHeld){
            if(!gestureConsumed&&!closeRequested&&cn.bfops.Client.tacticalVoiceAllowed()){
                if(voiceGesture)commitVoice();else cn.bfops.Client.tacticalMark();
            }
            gestureHeld=false;voiceGesture=false;gestureConsumed=false;
        }
        boolean keyDown=legacy||tactical&&voiceGesture;
        if(closeRequested){keyDown=false;if(!legacy&&!tactical)closeRequested=false;}
        boolean wantsMouse=(legacy||tactical&&gestureEngaged)&&!closeRequested;
        if(wantsMouse)freeMouse(mc);
        else restoreMouse(mc);
        if(mouseFreedByUs){mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);}
        prevAlpha=currentAlpha;
        currentAlpha=Mth.approach(currentAlpha,keyDown?TARGET_ALPHA:0,ALPHA_STEP);
        keyWasDown=keyDown;
        prevCooldownDisplayAlpha=cooldownDisplayAlpha;
        cooldownDisplayAlpha=Mth.approach(cooldownDisplayAlpha,getCooldownRemaining()>0?1:0,COOLDOWN_ALPHA_STEP);
        if(keyDown)updatePointer(mc);
    }

    /**
     * 由 {@code RenderGuiEvent.Post} 调用，每帧精确执行一次（在所有 HUD 元素之上渲染）。
     */
    public static void renderHud(GuiGraphics guiGraphics, float partialTick) {
        if(!cn.bfops.Client.inBattleRoom())return;
        INSTANCE.doRenderHud(guiGraphics, partialTick);
    }

    private void doRenderHud(GuiGraphics guiGraphics, float partialTick) {
        float alpha = Mth.lerp(partialTick, prevAlpha, currentAlpha);
        if (alpha < 0.001f) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        float ringAlpha = alpha / TARGET_ALPHA;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;
        float cooldownRemaining = Math.max(0.0f, getCooldownRemaining());
        float cdAlpha = Mth.lerp(partialTick, prevCooldownDisplayAlpha, cooldownDisplayAlpha);
        renderer.render(guiGraphics, centerX, centerY, ringAlpha, alpha, partialTick, screenWidth, screenHeight,
            cooldownRemaining, cdAlpha);
    }

    @SubscribeEvent
    public void onMouseScroll(final InputEvent.MouseScrollingEvent event) {
        if (keyWasDown || gestureHeld) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMousePress(final InputEvent.MouseButton.Pre event) {
        if (!keyWasDown && !gestureHeld) return;
        event.setCanceled(true);
        if(event.getAction()!=GLFW.GLFW_PRESS||closeRequested)return;
        if(event.getButton()==GLFW.GLFW_MOUSE_BUTTON_RIGHT){gestureConsumed=true;closeRequested=true;return;}
        if(!keyWasDown)return;
        updatePointer(Minecraft.getInstance());
        if(event.getButton()==GLFW.GLFW_MOUSE_BUTTON_LEFT&&(renderer.isMouseInCenter()||commitVoice())){
            gestureConsumed=true;closeRequested=true;
        }
    }

    private void triggerQuickMessage(int segmentIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        String text = QuickMessageHudRenderer.getSegmentLabel(segmentIndex);
        mc.player.connection.sendChat(text);

        VoiceCue cue = QuickMessageHudRenderer.getSegmentCue(segmentIndex);
        if (cue != null) {
            VoiceNetwork.sendToServer(new C2SRequestCuePayload(cue));
        }

        double cooldownSec = VoiceConfig.getChatCooldownSeconds();
        if (cooldownSec > 0.0) {
            cooldownEndTimeMs = System.currentTimeMillis() + (long) (cooldownSec * 1000.0);
        }
    }

    private float getCooldownRemaining() {
        if (cooldownEndTimeMs == 0) {
            return 0.0f;
        }
        long remainingMs = cooldownEndTimeMs - System.currentTimeMillis();
        if (remainingMs <= 0) {
            cooldownEndTimeMs = 0;
            return 0.0f;
        }
        return remainingMs / 1000.0f;
    }
}
