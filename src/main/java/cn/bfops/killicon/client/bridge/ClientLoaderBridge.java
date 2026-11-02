package cn.bfops.killicon.client.bridge;

import java.nio.file.Path;

public interface ClientLoaderBridge {
   Path getConfigDir();

   boolean isModLoaded(String var1);

   void registerConfigScreen();

   void runOnClient(Runnable var1);

   void registerForgeEventBusSubscriber(Object var1);
}
