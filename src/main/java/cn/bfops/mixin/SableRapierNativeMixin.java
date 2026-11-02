package cn.bfops.mixin;

import cn.bfops.BattlefieldOps;
import net.neoforged.fml.loading.FMLPaths;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reuses Sable's already-extracted Rapier native instead of forcing a re-extract.
 *
 * <p>Sable's {@code Rapier3D.loadLibrary} unconditionally deletes
 * {@code .sable/natives/sable_rapier_x86_64_windows.dll}, recreates it, and copies it back out of the jar.
 * On Windows that delete fails whenever the file is still mapped by another JVM or held briefly by an
 * antivirus scan, and the failure is fatal: it happens inside a static initialiser, so the whole world load
 * dies with {@code AccessDeniedException} before anything can report it.
 *
 * <p>This injects at the head of that method: if a usable native is already on disk, load it directly and
 * cancel. The extracted file belongs to the same mod version, so nothing is stale. If the load is refused
 * the injection does nothing, Sable runs its own path, and behaviour is exactly as before.
 */
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.Rapier3D", remap = false)
public class SableRapierNativeMixin {
    private static final String NATIVE = "sable_rapier_x86_64_windows.dll";

    @Inject(method = "loadLibrary", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void battlefield_ops$reuseExtractedNative(CallbackInfo ci) {
        try {
            Path nativeFile = FMLPaths.GAMEDIR.get().resolve(".sable").resolve("natives").resolve(NATIVE);
            if (!Files.isRegularFile(nativeFile) || Files.size(nativeFile) <= 0L) return;
            System.load(nativeFile.toAbsolutePath().toString());
            BattlefieldOps.LOG.info("Reusing the already extracted Sable native at {}", nativeFile);
            ci.cancel();
        } catch (Throwable unavailable) {
            // Fall through: Sable's own extraction still gets its chance, and its own error still surfaces.
            BattlefieldOps.LOG.debug("Could not preload the Sable native; leaving extraction to Sable", unavailable);
        }
    }
}
