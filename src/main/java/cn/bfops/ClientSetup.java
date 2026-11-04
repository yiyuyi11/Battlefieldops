package cn.bfops;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The client's <b>mod bus</b> entry points.
 *
 * <p>Kept apart from {@link Client} on purpose: that class is a FORGE-bus subscriber, and Forge 1.20.1's
 * {@code @EventBusSubscriber} does not cross buses. Key bindings, the reload listener and the client setup
 * hook are all mod-bus events, so leaving them there silently registered nothing — no keys, and no packet
 * receivers, which read in game as "the deploy key does nothing" and "this server has battlefield disabled".
 */
@Mod.EventBusSubscriber(modid = "battlefield_ops", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(Client.OPEN);event.register(Client.MINIMAP_SETTINGS);event.register(Client.MINIMAP_ZOOM_IN);event.register(Client.MINIMAP_ZOOM_OUT);
        event.register(Client.ELITE_PICKUP);
        event.register(Client.SCORES);
        event.register(Client.MINIMAP_CORNER);
        event.register(Client.MINIMAP_TOGGLE);
        event.register(Client.MARK);
        event.register(Client.MAP_ROTATION);
        event.register(Client.KILL_ICON_STYLE);
        event.register(Client.KILL_ICON_PICKER);
        event.register(Client.AUDIO);
        // The 656 leaderboard's own key. It lived in KeyBindings but was never registered with the
        // game, so pressing its bound key did nothing at all; registering it here is what makes
        // "open the standings with a key, from any screen or no screen" true.
        for (var mapping : cn.bfops.killicon.client.KeyBindings.all()) event.register(mapping);
    }

    @SubscribeEvent
    public static void referenceReload(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event){
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
                Minecraft.getInstance().execute(() -> {
                    FactionArt.clear(); AssetIcons.clear(); ReferenceProps.clear();
                    GearNames.clear(); EquipmentStatistics.clear();
                    BoundaryEffect.reset(); NearMissEffect.reset();
                    NativeAudioBank.clear(); NativeBattleAudio.reset(); MusicDirector.silence();
                }));
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        // One capability report per launch: it answers "which optional mods were actually found" without
        // anybody having to guess from the behaviour of individual screens.
        for (String mod : new String[]{"tacz", "superbwarfare", "lrtactical", "geckolib", "playeranimator", "xaero"}) {
            var container = net.minecraftforge.fml.ModList.get().getModContainerById(mod);
            BattlefieldOps.LOG.info("[bfops] capability report: {} = {}", mod,
                    container.map(c -> c.getModInfo().getVersion().toString()).orElse("absent"));
        }
        event.enqueueWork(() -> {Network.receiver = Client::receive;Network.modeReceiver=Client::mode;LoadoutProfiles.load();LobbyAccess.clientOpen=()->{if(Client.serverSupported())LobbyScreen.open();};NativeKillIcons.init();});
    }

}
