package lol.gzmc.vivecraftx;

import lol.gzmc.vivecraftx.api.RenderPass;

import com.mtbs3d.minecrift.api.IRoomscaleAdapter;
import com.mtbs3d.minecrift.gameplay.BackpackTracker;
import com.mtbs3d.minecrift.gameplay.BowTracker;
import com.mtbs3d.minecrift.gameplay.ClimbTracker;
import com.mtbs3d.minecrift.gameplay.EatingTracker;
import com.mtbs3d.minecrift.gameplay.HapticTracker;
import com.mtbs3d.minecrift.gameplay.HorseTracker;
import com.mtbs3d.minecrift.gameplay.RowTracker;
import com.mtbs3d.minecrift.gameplay.JumpTracker;
import com.mtbs3d.minecrift.gameplay.InteractTracker;
import com.mtbs3d.minecrift.gameplay.RunTracker;
import com.mtbs3d.minecrift.gameplay.SwingTracker;
import com.mtbs3d.minecrift.gameplay.SneakTracker;
import com.mtbs3d.minecrift.gameplay.SwimTracker;
import com.mtbs3d.minecrift.api.IStereoProvider;
import com.mtbs3d.minecrift.provider.OpenVRPlayer;
import com.mtbs3d.minecrift.settings.VRSettings;

/**
 * The client's VR state: settings, the tracked player, and the stereo provider.
 *
 * <p>Vivecraft kept these as fields on {@code Minecraft}, which its patches could add. A mod
 * cannot, and the usual workaround -- a mixin holding {@code @Unique} fields plus an accessor
 * interface -- would be worse here for three reasons. {@code Minecraft} is itself a singleton in
 * 1.7.10, so nothing is gained by tying process-wide state to its instance. Every one of the ~430
 * read sites would need a cast and a prefixed accessor name instead of a plain call. And
 * {@code Minecraft} is the class Angelica mixes into most heavily, so spending that overlap on
 * field storage rather than on an actual hook is a poor trade.
 *
 * <p>The cost is that initialisation is no longer implicit in Minecraft's constructor. That is
 * handled explicitly by the setters below and {@link #stop()}, and is arguably clearer for being visible.
 *
 * <p>Client side only, and only ever touched from the client thread.
 */
public final class VR {

    private VR() {}

    private static VRSettings settings;
    private static OpenVRPlayer player;
    private static IStereoProvider stereo;

    /** Null until the VR subsystem has been initialised. */
    public static VRSettings settings() {
        return settings;
    }

    /**
     * The tracked player. Vivecraft exposed the same object twice, as {@code mc.vrPlayer} and
     * {@code mc.roomScale}, typed as {@code OpenVRPlayer} and {@code IRoomscaleAdapter}
     * respectively. Both accessors are kept, with the same types, so the inherited call sites
     * compile unchanged.
     */
    public static OpenVRPlayer player() {
        return player;
    }

    public static IRoomscaleAdapter roomScale() {
        return player;
    }

    public static IStereoProvider stereo() {
        return stereo;
    }

    // The motion trackers. Vivecraft created these eagerly as fields on Minecraft, one instance
    // for the whole session, so they are plain finals here.
    private static final BackpackTracker BACKPACK = new BackpackTracker();
    private static final BowTracker BOW = new BowTracker();
    private static final SwimTracker SWIM = new SwimTracker();
    private static final EatingTracker EATING = new EatingTracker();
    private static final HapticTracker HAPTICS = new HapticTracker();
    private static final RowTracker ROWING = new RowTracker();
    private static final HorseTracker HORSE = new HorseTracker();
    private static final JumpTracker JUMP = new JumpTracker();
    private static final SneakTracker SNEAK = new SneakTracker();
    private static final ClimbTracker CLIMB = new ClimbTracker();
    private static final RunTracker RUN = new RunTracker();
    private static final SwingTracker SWING = new SwingTracker();
    private static final InteractTracker INTERACT = new InteractTracker();

    public static BackpackTracker backpackTracker() {
        return BACKPACK;
    }

    public static BowTracker bowTracker() {
        return BOW;
    }

    public static SwimTracker swimTracker() {
        return SWIM;
    }

    /** Vivecraft called this one {@code mc.autoFood}. */
    public static EatingTracker autoFood() {
        return EATING;
    }

    /** What the world does to you, felt in the hands. */
    public static HapticTracker haptics() {
        return HAPTICS;
    }

    /** Rowing a boat with your arms. */
    public static RowTracker rowing() {
        return ROWING;
    }

    /** Reins and jump charge, for riding a horse by hand. */
    public static HorseTracker horse() {
        return HORSE;
    }

    public static JumpTracker jumpTracker() {
        return JUMP;
    }

    public static SneakTracker sneakTracker() {
        return SNEAK;
    }

    public static ClimbTracker climbTracker() {
        return CLIMB;
    }

    public static RunTracker runTracker() {
        return RUN;
    }

    /** Hitting and mining things by swinging at them. */
    public static SwingTracker swingTracker() {
        return SWING;
    }

    /** Putting a hand on something and pressing, rather than pointing at it. */
    public static InteractTracker interactTracker() {
        return INTERACT;
    }

    /** Which eye the renderer is currently drawing. Vivecraft kept it as mc.currentPass. */
    private static RenderPass currentPass = RenderPass.Center;

    public static RenderPass currentPass() {
        return currentPass;
    }

    public static void setCurrentPass(RenderPass pass) {
        currentPass = pass;
    }

    public static boolean isReady() {
        return settings != null && player != null && stereo != null;
    }

    public static void setSettings(VRSettings value) {
        settings = value;
    }

    public static void setPlayer(OpenVRPlayer value) {
        player = value;
    }

    public static void setStereo(IStereoProvider value) {
        stereo = value;
    }

    /** Releases everything, so a failed init cannot leave half-built state behind. */
    public static void stop() {
        settings = null;
        player = null;
        stereo = null;
    }
}
