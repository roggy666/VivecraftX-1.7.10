package lol.gzmc.vivecraftx.compat;

import cpw.mods.fml.common.Loader;

/**
 * Optional Angelica (modid {@code angelica}) integration.
 *
 * <p>Angelica is a <em>soft</em> dependency. ViveCraftX renders stereo through vanilla
 * framebuffers and talks to the headset over OpenVR, none of which needs Angelica, so the mod
 * is fully functional on plain Forge. When Angelica is installed we still have to coexist with
 * it, and we can optionally use what it already provides.
 *
 * <p>The GL layer needs no conditional code at all: ordinary {@code GL11.*} calls run as-is
 * without Angelica, and Angelica's {@code AngelicaRedirectorTransformer} rewrites them into its
 * {@code GLStateManager} when it is present. Same bytecode either way.
 *
 * <p>What does need gating is anything that <em>uses</em> Angelica: its Iris shader pipeline and
 * {@code HUDCaching}. Those must live in their own classes, because a class that imports a type
 * Angelica owns fails with {@link NoClassDefFoundError} the moment it is loaded -- not when the
 * method is called. This class deliberately imports nothing from Angelica so it is always safe
 * to touch.
 */
public final class AngelicaCompat {

    private AngelicaCompat() {}

    public static final String MODID = "angelica";

    /** Marker class, present exactly when Angelica is on the classpath. */
    private static final String MARKER = "com.gtnewhorizons.angelica.AngelicaMod";

    private static boolean resolved = false;
    private static boolean present = false;

    /** True when Angelica is installed. Resolved once, then cached. */
    public static boolean isPresent() {
        if (!resolved) {
            resolved = true;
            try {
                present = Loader.isModLoaded(MODID) && Class.forName(MARKER) != null;
            } catch (Throwable ignored) {
                present = false;
            }
        }
        return present;
    }
}
