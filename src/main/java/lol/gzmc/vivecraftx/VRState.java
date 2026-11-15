package lol.gzmc.vivecraftx;

import com.mtbs3d.minecrift.control.ControllerType;
import com.mtbs3d.minecrift.control.VRInputAction;
import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.provider.NullStereoRenderer;
import com.mtbs3d.minecrift.provider.OpenVRPlayer;
import com.mtbs3d.minecrift.provider.OpenVRStereoRenderer;
import com.mtbs3d.minecrift.settings.VRSettings;
import com.mtbs3d.minecrift.settings.profile.ProfileManager;

import lol.gzmc.vivecraftx.render.VRGui;
import lol.gzmc.vivecraftx.render.VRRenderer;

import net.minecraft.client.Minecraft;

/**
 * Whether the game is in VR, and the three separate answers that question has.
 *
 * <p>Modern Vivecraft splits it into three flags and this follows it, because the three really do
 * differ and treating them as one is what made VR here an all-or-nothing choice fixed at startup:
 *
 * <ul>
 *   <li><b>enabled</b> -- what the player wants. Saved, so it survives a restart, and changed only
 *       by the player: the button on the main menu, the key, or the setting.
 *   <li><b>initialized</b> -- the runtime is up and the action manifest is loaded. Expensive to
 *       reach and worth keeping: re-declaring the actions makes SteamVR reload the manifest and
 *       forget which bindings are live, which the player sees as their controllers going dead for
 *       a second.
 *   <li><b>running</b> -- frames are being drawn for the headset right now. This is the one the
 *       rest of the mod asks about, and it is false whenever VR is off <i>or</i> the headset is
 *       not being worn.
 * </ul>
 *
 * <p>That last distinction is the point of the whole arrangement. Put the headset down and the
 * game carries on flat on the monitor, keyboard and mouse working; pick it up and it is back in
 * the headset, same world, same session. Upstream calls it hot-switching.
 */
public final class VRState {

    private VRState() {}

    /** Settings and key bindings are up. Everything else waits on this. */
    private static boolean booted;

    private static boolean enabled;
    private static boolean initialized;
    private static boolean running;

    /** True once the runtime has come up, which is what makes a second attempt worth making. */
    private static boolean cameUpOnce;

    /** True when the last attempt to start failed, which is what the button reports. */
    private static boolean startFailed;

    /**
     * True once starting has failed in a way that cannot be retried.
     *
     * <p>A native library that fails to load fails once and for ever: the class that holds it is
     * marked broken by the virtual machine and every later use of it throws without trying
     * again. Offering a button that cannot work is worse than saying so.
     */
    private static boolean unavailable;

    /** What the view mode was before VR forced first person. */
    private static int flatCameraMode;

    /**
     * What the rest of the mod means by "in VR": drawing for the headset at this moment.
     */
    public static boolean running() {
        return running;
    }

    public static boolean initialized() {
        return initialized;
    }

    public static boolean enabled() {
        return enabled;
    }

    /** True once the settings exist, whether or not VR itself ever came up. */
    public static boolean booted() {
        return booted;
    }

    /** True when the runtime has worked at least once this session. */
    public static boolean cameUpOnce() {
        return cameUpOnce;
    }

    /** True when the last attempt to start VR did not get anywhere. */
    public static boolean startFailed() {
        return startFailed;
    }

    /** True when VR cannot be started again without restarting the game. */
    public static boolean unavailable() {
        return unavailable;
    }

    /**
     * Whether a failure has taken the runtime library down with it.
     *
     * <p>The first attempt reports the real problem; every attempt after it reports only that
     * the class is beyond saving, which is the virtual machine remembering, not a new fault.
     */
    private static boolean isUnrecoverable(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnsatisfiedLinkError
                    || cause instanceof NoClassDefFoundError
                    || cause instanceof ExceptionInInitializerError) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads the settings and registers the key bindings, whether or not VR is wanted.
     *
     * <p>Both happen regardless, because both are visible with VR off: the controls screen lists
     * the VR bindings so they can be bound, and the VR settings screen has to have something to
     * show. Only the runtime itself waits for the player to ask for it.
     *
     * <p>Idempotent; called from the first client tick, which is after the display, the resource
     * manager and every other mod's own setup are settled.
     */
    public static void boot() {
        if (booted) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return; // too early; the next tick will do
        }

        // Before the settings, which resolve controller buttons to key bindings by name: the
        // bindings have to exist first.
        try {
            MCOpenVR.registerKeyBindings();
        } catch (Throwable t) {
            ViveCraftX.LOG.warn("Could not register VR key bindings", t);
        }

        // The settings live in a profile file, and the profile store has to be opened before
        // anything reads it -- otherwise every read throws, every setting silently falls back to
        // its compiled-in default, and nothing the player changes is ever written down.
        //
        // Vivecraft did this in VRSettings.initSettings, which we cannot call: it also replaces
        // Minecraft.gameSettings with a fresh instance, which would throw away whatever Forge and
        // Angelica have already put there.
        try {
            ProfileManager.init(mc.mcDataDir);
            VR.setSettings(new VRSettings(mc, mc.mcDataDir));
            // writes the file out on a first run, so the profile exists to be edited
            VR.settings().saveOptions();
        } catch (Throwable t) {
            ViveCraftX.LOG.error("Could not load the VR settings, so VR cannot start", t);
            VR.setStereo(new NullStereoRenderer());
            booted = true;
            return;
        }

        VR.setStereo(new NullStereoRenderer());
        booted = true;

        VRSettings settings = VR.settings();
        if (settings.vrEnabled && settings.rememberVr) {
            enabled = true;
        } else if (settings.vrEnabled) {
            // the player asked not to have it remembered, so it starts off and is saved off
            settings.vrEnabled = false;
            settings.saveOptions();
        }
        ViveCraftX.LOG.info("VR settings loaded; VR starts {}", enabled ? "on" : "off");
    }

    /**
     * Brings the three states back into agreement. Called once per frame, before anything reads
     * them.
     *
     * <p>A frame rather than a tick because the render loop is what the states gate, and because
     * the game keeps rendering while it is paused -- a player who takes the headset off in a menu
     * should still have the game come back to the monitor.
     */
    public static void update() {
        if (!booted || unavailable) {
            return;
        }
        if (enabled && !initialized) {
            initialize();
        }
        if (!initialized) {
            if (running) {
                setRunning(false);
            }
            return;
        }

        boolean want = enabled && headsetInUse();
        if (want != running) {
            setRunning(want);
        }
        if (!running) {
            // Nothing else is polling, so the runtime's own messages -- SteamVR shutting down,
            // most of all -- would never be read while VR is idle.
            MCOpenVR.pollRuntimeEventsOnly();
        }
    }

    /**
     * Whether the headset is on somebody's head.
     *
     * <p>Asked of the runtime rather than guessed: it reports an activity level per device, and
     * the two levels that mean a person is there are a person interacting and a person who has
     * been still for a while. Standby and idle mean the headset is face down on the desk.
     */
    private static boolean headsetInUse() {
        VRSettings settings = VR.settings();
        if (settings == null || !settings.vrHotswitchingEnabled) {
            return true;
        }
        return MCOpenVR.isHeadsetInUse();
    }

    /** What the player asked for. Saved, because it is a preference rather than a state. */
    public static void setEnabled(boolean want) {
        if (enabled == want || (want && unavailable)) {
            return;
        }
        enabled = want;
        if (want) {
            startFailed = false;
        }
        VRSettings settings = VR.settings();
        if (settings != null) {
            settings.vrEnabled = want;
            settings.saveOptions();
        }
        ViveCraftX.LOG.info("VR switched {} by the player", want ? "on" : "off");
    }

    public static void toggle() {
        setEnabled(!enabled);
    }

    /**
     * Gives up on VR after something broke mid-session.
     *
     * <p>Switched off rather than marked permanently broken: the usual cause is SteamVR being
     * closed or restarted underneath us, and that is a thing the player can undo. The button says
     * "off" afterwards, and pressing it tries again from the top.
     */
    public static void failed(String what, Throwable cause) {
        ViveCraftX.LOG.error("VR stopped: {}", what, cause);
        setEnabled(false);
        setRunning(false);
        // All the way down, not just off. The usual cause is the runtime going away underneath
        // us, and a half-torn-down OpenVR cannot be resumed: switching back on has to mean
        // starting it again from nothing, or the second attempt fails exactly like the first.
        initialized = false;
        cameUpOnce = false;
        try {
            MCOpenVR.destroy();
        } catch (Throwable t) {
            ViveCraftX.LOG.warn("Shutting the VR runtime down after a failure also failed", t);
        }
        teardown();
    }

    /**
     * SteamVR has shut down and the player asked not to be taken with it.
     *
     * <p>Not a failure: nothing went wrong, the runtime is simply gone. Everything is torn down
     * the same way, so that starting SteamVR again and pressing the button works.
     */
    public static void runtimeClosed() {
        setEnabled(false);
        setRunning(false);
        initialized = false;
        cameUpOnce = false;
        try {
            MCOpenVR.destroy();
        } catch (Throwable t) {
            ViveCraftX.LOG.warn("Shutting the VR runtime down failed", t);
        }
        teardown();
    }

    /**
     * Brings the runtime up. The expensive half, and the half that can fail.
     */
    private static void initialize() {
        boolean up;
        try {
            if (!cameUpOnce) {
                new MCOpenVR();
                MCOpenVR.allowRetry();
                up = MCOpenVR.init();
            } else {
                // The runtime is still up from earlier in this session; only the parts we tore
                // down have to come back, and re-declaring the actions would make SteamVR reload
                // the manifest and drop the live bindings for a moment.
                up = true;
            }
        } catch (Throwable t) {
            // With the whole trace, and at a level somebody will actually see. Reporting only
            // the message was a mistake: the one failure that matters here -- the runtime
            // library refusing to load -- says nothing useful in its message and everything
            // useful in the frames above it.
            ViveCraftX.LOG.warn("OpenVR did not start", t);
            if (isUnrecoverable(t)) {
                unavailable = true;
                ViveCraftX.LOG.warn("The VR runtime library could not be loaded. Nothing can "
                        + "undo that without restarting the game, so VR is off for this "
                        + "session. Usually this means SteamVR was not running, or something "
                        + "on this machine is holding the library open -- an antivirus "
                        + "scanner is the usual culprit.");
            }
            giveUpOnStart();
            return;
        }

        if (!up) {
            ViveCraftX.LOG.info("VR not available ({}), staying flat", MCOpenVR.getInitStatus());
            giveUpOnStart();
            return;
        }

        try {
            if (!cameUpOnce) {
                VR.setPlayer(new OpenVRPlayer());
                // Last, because declaring the actions needs both the key bindings and the
                // settings, which decide whether the advanced sets are offered and which hand is
                // which.
                MCOpenVR.initInputAndApplication();
                VR.settings().vrAllowCrawling = false;
            }
            cameUpOnce = true;
            initialized = true;
            startFailed = false;
            ViveCraftX.LOG.info("VR runtime ready");
        } catch (Throwable t) {
            ViveCraftX.LOG.error("OpenVR started but could not be set up, staying flat", t);
            giveUpOnStart();
        }
    }

    /**
     * Turns the wish off after a failed attempt, so the next frame does not try again, and the
     * one after that, for ever.
     */
    private static void giveUpOnStart() {
        enabled = false;
        initialized = false;
        startFailed = true;
        VRSettings settings = VR.settings();
        if (settings != null && settings.vrEnabled) {
            settings.vrEnabled = false;
            settings.saveOptions();
        }
        teardown();
    }

    /**
     * Starts or stops drawing for the headset.
     *
     * <p>Everything here is about the game outside VR: which camera it uses, where its interface
     * is drawn, and whether its own overlays are back. The runtime is untouched either way.
     */
    private static void setRunning(boolean value) {
        if (running == value) {
            return;
        }
        running = value;
        Minecraft mc = Minecraft.getMinecraft();

        if (value) {
            // The eye textures belong to the stereo provider, and this one is new, so the
            // framebuffers wrapping the old ones have to be rebuilt before the first frame.
            VR.setStereo(new OpenVRStereoRenderer());
            VRRenderer.clearFailure();
            VRFrame.reset();

            if (mc != null && mc.gameSettings != null) {
                flatCameraMode = mc.gameSettings.thirdPersonView;
                mc.gameSettings.thirdPersonView = 0;
            }
            if (mc != null && mc.thePlayer != null && VR.player() != null) {
                // otherwise the play area is wherever it was left, which is wherever the player
                // happened to be standing when they last took the headset off
                VR.player().snapRoomOriginToPlayerEntity(mc.thePlayer, true, false, 0);
            }
            // The screen on display was opened while there was nothing to tell, so its panel was
            // never placed; deliver the notification late.
            if (mc != null && VR.stereo() != null) {
                try {
                    VR.stereo().onGuiScreenChanged(null, mc.currentScreen);
                } catch (Throwable t) {
                    ViveCraftX.LOG.warn("Could not place the panel for the screen already open", t);
                }
            }
            ViveCraftX.LOG.info("Drawing for the headset");
        } else {
            // Tell everyone nearby before we stop talking, so their copy of us puts its arms
            // down instead of holding the last pose until it goes stale.
            try {
                com.mtbs3d.minecrift.api.NetworkHelper.sendLeftVR();
            } catch (Throwable t) {
                ViveCraftX.LOG.debug("Could not say that VR is off", t);
            }
            teardown();
            if (mc != null && mc.gameSettings != null) {
                mc.gameSettings.thirdPersonView = flatCameraMode;
            }
            ViveCraftX.LOG.info("Back to the monitor");
        }
    }

    /**
     * Puts the game back the way it renders and reads input without us.
     *
     * <p>Called whenever VR stops, however it stopped. The interface has to let go of the game's
     * framebuffer -- left borrowed, the first flat frame draws into a buffer that no longer
     * exists -- and anything the controllers were holding down has to be let go, or the player
     * finds themselves walking into a wall with no way to stop.
     */
    private static void teardown() {
        try {
            VRGui.shutdown();
        } catch (Throwable t) {
            ViveCraftX.LOG.warn("Tearing down the VR interface failed", t);
        }
        try {
            VRGui.restoreFlatOverlays();
        } catch (Throwable t) {
            ViveCraftX.LOG.warn("Restoring the flat interface failed", t);
        }
        try {
            for (VRInputAction action : MCOpenVR.getInputActions()) {
                action.unpressBindingImmediately(null);
                action.unpressBindingImmediately(ControllerType.LEFT);
                action.unpressBindingImmediately(ControllerType.RIGHT);
            }
        } catch (Throwable t) {
            ViveCraftX.LOG.warn("Releasing the controller bindings failed", t);
        }
        VR.setStereo(new NullStereoRenderer());
        VRFrame.reset();
    }
}
