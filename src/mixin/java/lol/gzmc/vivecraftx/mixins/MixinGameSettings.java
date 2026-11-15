package lol.gzmc.vivecraftx.mixins;

import com.mtbs3d.minecrift.provider.MCOpenVR;

import net.minecraft.client.settings.GameSettings;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.settings.KeyBinding;

/**
 * Notes down which key bindings are the game's own.
 *
 * <p>SteamVR groups actions into sets, and bindings that came from other mods belong in a set of
 * their own, so that binding one of them cannot take a button away from the game's own controls.
 * Telling the two apart needs a moment when only the game's own exist, and this is it: the settings
 * are built early in startup, and every mod registers its bindings afterwards.
 *
 * <p>Vivecraft took its snapshot from a patch in the same place but after adding its own bindings
 * to the array. We cannot, since ours are registered later still, so the snapshot here is vanilla
 * alone and our own bindings are excluded by name where it matters.
 */
@Mixin(GameSettings.class)
public abstract class MixinGameSettings {

    @Shadow
    public KeyBinding[] keyBindings;

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V", at = @At("RETURN"))
    private void vivecraftx$snapshotVanillaBindings(CallbackInfo ci) {
        MCOpenVR.setVanillaBindings(this.keyBindings);
    }
}
