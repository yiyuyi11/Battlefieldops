package cn.bfops.killicon.client.bridge;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
public final class ClientBridge {
 private static final ClientLoaderBridge LOADER=new ClientLoaderBridge(){
  public Path getConfigDir(){return FMLPaths.CONFIGDIR.get();}
  public boolean isModLoaded(String id){return ModList.get().isLoaded(id);}
  public void registerConfigScreen(){} // Parent mod exposes the original screen through its HUD options.
  public void runOnClient(Runnable action){Minecraft.getInstance().execute(action);}
  public void registerForgeEventBusSubscriber(Object type){} // Lifecycle hooks are owned by parent mod.
 };
 public static ClientLoaderBridge loader(){return LOADER;}
}
