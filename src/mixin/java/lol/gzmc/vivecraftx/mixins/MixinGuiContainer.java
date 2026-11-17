package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.VRInputState;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets the controller's Shift binding move a whole stack, the way holding Shift does flat.
 *
 * <p>The binding was wired and handled, and it still could not work. Holding it asked
 * {@code java.awt.Robot} to press Shift on the real keyboard, and only when the game window had
 * focus -- which with a headset on it usually does not, because the window is behind the SteamVR
 * compositor. The one thing that was recorded whether or not the window had focus,
 * {@link VRInputState#pressShiftFake}, was written and never read by anything. So in a headset
 * the stack never moved, which is exactly the complaint.
 *
 * <p>Redirected here rather than through {@code GuiScreen.isShiftKeyDown}, which would look like
 * the one place to do it: these two methods do not call it. They read LWJGL's key state directly,
 * and that cannot be faked from outside -- an event put into LWJGL's queue does not touch the
 * state {@code isKeyDown} reports, and the Windows backend actively undoes a Shift it finds down
 * while the physical key is up, once per poll. The read is therefore the thing to answer, and
 * these are the two reads that decide whether a click moves one item or the whole stack.
 *
 * <p>The key is checked rather than assumed. Both call sites only ever ask about the two Shift
 * keys today, and a redirect that ignored its argument would quietly answer for whatever a future
 * one asked about.
 */
@Mixin(GuiContainer.class)
public abstract class MixinGuiContainer {

    @Redirect(
            method = {"mouseClicked", "mouseMovedOrUp"},
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;isKeyDown(I)Z"))
    private boolean vivecraftx$shiftFromController(int key) {
        if (Keyboard.isKeyDown(key)) {
            return true;
        }
        return VRInputState.pressShiftFake
                && (key == Keyboard.KEY_LSHIFT || key == Keyboard.KEY_RSHIFT);
    }
}
