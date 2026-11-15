package lol.gzmc.vivecraftx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.I18n;

import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A button on the main menu that turns VR on and off.
 *
 * <p>Needed because the two states are not interchangeable from the player's side. With a headset
 * plugged in the mod takes over the screen, and someone who wants to play flat for an evening --
 * or show the game to somebody on the monitor -- has no way to say so. Equally, a player who
 * started the game before putting the headset on should not have to restart.
 *
 * <p>Placed where modern Vivecraft places it: beside the menu's own column, level with the second
 * entry. Not in the options, because that is a screen you have to be able to read to reach, and
 * the whole point of the button is to be usable when the headset is off your head.
 *
 * <p>What it does is only to change the wish. {@link VRState} acts on it at the start of the next
 * frame, which keeps starting and stopping the runtime in one place rather than on an event
 * handler.
 */
@SideOnly(Side.CLIENT)
public class VRToggleButton {

    /** Well clear of vanilla's ids, and of the ones Forge's own menu buttons use. */
    private static final int BUTTON_ID = 0x5652_01;

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiMainMenu)) {
            return;
        }
        if (VR.settings() != null && !VR.settings().vrToggleButtonEnabled) {
            return;
        }
        event.buttonList.add(new GuiButton(BUTTON_ID, event.gui.width / 2 + 104,
                event.gui.height / 4 + 72, 56, 20, label()));
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.button == null || event.button.id != BUTTON_ID) {
            return;
        }
        VRState.toggle();
        // relabelled in place, so the menu does not have to be rebuilt to show the change
        event.button.displayString = label();
    }

    /**
     * Tells the player why nothing is in the headset when VR is on.
     *
     * <p>With hot-switching, VR being on and the game being in the headset are different things:
     * put the headset down and the game comes back to the monitor on purpose. Without a line
     * saying so, that looks exactly like VR having failed.
     */
    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiMainMenu) || !VRState.initialized() || VRState.running()) {
            return;
        }
        if (VR.settings() != null && !VR.settings().vrToggleButtonEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        String hint = I18n.format("vivecraft.toggle.hotswitch");
        event.gui.drawString(mc.fontRenderer, hint,
                event.gui.width / 2 + 104, event.gui.height / 4 + 62, 0xFFAA00);
    }

    /**
     * Says what pressing it will do, and why it cannot.
     *
     * <p>"VR unavailable" rather than "VR off" when the last attempt to start failed, because a
     * button that looks like it should work and does nothing is worse than one that explains
     * itself. Pressing it still tries again -- the usual reason it failed is that SteamVR was not
     * running yet.
     */
    private static String label() {
        if (VRState.enabled()) {
            return I18n.format("vivecraft.toggle.on");
        }
        if (VRState.startFailed() || VRState.unavailable()) {
            return I18n.format("vivecraft.toggle.unavailable");
        }
        return I18n.format("vivecraft.toggle.off");
    }
}
