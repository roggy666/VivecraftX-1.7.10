package lol.gzmc.vivecraftx.mixins;

import com.mtbs3d.minecrift.gui.GuiMinecriftSettings;

import lol.gzmc.vivecraftx.VR;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A way into the VR settings.
 *
 * <p>The screen itself was ported along with everything else and then had nothing that opened
 * it, which makes every setting on it theoretical. One button on the options screen, where a
 * player would look for it.
 *
 * <p>An id far out of the way of the ones the screen uses for itself, so that a future vanilla
 * or mod button cannot collide with it and quietly do the wrong thing.
 */
@Mixin(GuiOptions.class)
public abstract class MixinGuiOptions extends GuiScreen {

    private static final int VIVECRAFTX_BUTTON = 29170;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void vivecraftx$addSettingsButton(CallbackInfo ci) {
        // Every row above is taken, two buttons wide, so this shares the last row with Done
        // rather than inventing a row under it and pushing the screen past the bottom of a
        // small window. Done moves to the right half and keeps its place in the order.
        GuiButton done = null;
        for (Object entry : this.buttonList) {
            GuiButton button = (GuiButton) entry;
            if (button.id == 200) {
                done = button;
                break;
            }
        }
        int y = done != null ? done.yPosition : this.height / 6 + 168;
        if (done != null) {
            done.xPosition = this.width / 2 + 5;
            done.width = 150;
        }
        this.buttonList.add(new GuiButton(VIVECRAFTX_BUTTON, this.width / 2 - 155, y, 150, 20,
                "VR Settings..."));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void vivecraftx$openSettings(GuiButton button, CallbackInfo ci) {
        if (button.id != VIVECRAFTX_BUTTON) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(new GuiMinecriftSettings(this, VR.settings(), mc.gameSettings));
        ci.cancel();
    }
}
