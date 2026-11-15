package lol.gzmc.vivecraftx;

import cpw.mods.fml.common.gameevent.TickEvent;

import lol.gzmc.vivecraftx.render.VRCursor;

import net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side hooks: bringing the VR runtime up, and the start-of-tick bookkeeping.
 */
@SideOnly(Side.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (!VRState.booted()) {
            VRState.boot();
            return;
        }
        // Forget anybody who has stopped telling us where their hands are, and move everyone
        // else's history on, before anything asks. Once a tick, which is as often as a pose
        // changes.
        lol.gzmc.vivecraftx.net.VRPlayers.expire();
        lol.gzmc.vivecraftx.net.VRPlayers.tick();
        if (VRState.running() && VR.player() != null) {
            // Remembers where the play area was, so the render frames between this tick and the
            // next have something to interpolate from. Vivecraft called this from partway through
            // runTick; the start-of-tick event is the same point in the loop and before anything
            // has moved, which is all it needs.
            VR.player().preTick();
        }
    }

    /**
     * Draws the pointer once the screen under it has been drawn.
     *
     * <p>Vivecraft patched this into the renderer right after the screen; Forge posts an event at
     * the same point, so no mixin is needed. It also means mods that draw their own overlays on a
     * screen have already done so, and the pointer ends up on top of them rather than under.
     */
    @SubscribeEvent
    public void onDrawScreen(DrawScreenEvent.Post event) {
        VRCursor.draw();
    }

    /** How far through the current tick this frame is. */
    private static volatile float partialTicks;

    /**
     * What to blend other people's poses by.
     *
     * <p>Recorded here rather than read where it is wanted, because the place it is wanted --
     * posing a model, deep inside the entity renderer -- is not handed it, and the field the
     * game keeps it in is private.
     */
    public static float partialTicks() {
        return partialTicks;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            partialTicks = event.renderTickTime;
        }
    }

    /**
     * Nobody is remembered from the last world.
     *
     * <p>Poses are kept against entity ids, and a new world hands the same ids to different
     * people.
     */
    @SubscribeEvent
    public void onWorldUnload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if (event.world != null && event.world.isRemote) {
            lol.gzmc.vivecraftx.net.VRPlayers.clear();
            lol.gzmc.vivecraftx.net.VRServerRules.clear();
        }
    }

    /**
     * A nudge in the hands when somebody says something.
     *
     * <p>A headset covers your whole field of view with the world, so a line of chat appearing on
     * a panel at your wrist is easy to miss entirely -- there is no corner of a monitor for it to
     * catch your eye in. The buzz is deliberately slight: it is a message, not an emergency.
     */
    @SubscribeEvent
    public void onChat(net.minecraftforge.client.event.ClientChatReceivedEvent event) {
        if (!VRState.running() || VR.settings() == null || !VR.settings().vrChatHaptics) {
            return;
        }
        com.mtbs3d.minecrift.provider.MCOpenVR.triggerHapticPulse(
                com.mtbs3d.minecrift.control.ControllerType.LEFT, 0.04F, 160F, 0.4F);
    }
}
