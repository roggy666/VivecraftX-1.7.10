package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.api.IKeyBindingExt;

import net.minecraft.client.settings.KeyBinding;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Gives {@link KeyBinding} the key-state control Vivecraft's patch added, so the motion trackers
 * can drive keys directly.
 *
 * <p>Field names are the MCP ones; the annotation processor emits the refmap that maps them to
 * the obfuscated names at runtime.
 */
@Mixin(KeyBinding.class)
public abstract class MixinKeyBinding implements IKeyBindingExt {

    @Shadow
    private boolean pressed;

    @Shadow
    private int pressTime;

    @Override
    public void vivecraftx$press() {
        this.pressed = true;
        this.pressTime++;
    }

    @Override
    public void vivecraftx$unpress() {
        this.pressed = false;
        this.pressTime = 0;
    }
}
