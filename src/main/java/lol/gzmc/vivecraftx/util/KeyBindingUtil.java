package lol.gzmc.vivecraftx.util;

import lol.gzmc.vivecraftx.api.IKeyBindingExt;

import net.minecraft.client.settings.KeyBinding;

/**
 * Drives key state directly, the way the motion trackers need.
 *
 * <p>Vivecraft's patch added {@code KeyBinding.pressKey()} and made {@code unpressKey()} public.
 * Here that behaviour comes from a mixin, and the cast stays in this one class rather than at
 * every call site.
 */
public final class KeyBindingUtil {

    private KeyBindingUtil() {}

    /** Equivalent to the patch's {@code pressKey()}. */
    public static void press(KeyBinding key) {
        if (key != null) {
            ((IKeyBindingExt) key).vivecraftx$press();
        }
    }

    /** Equivalent to vanilla's private {@code unpressKey()}. */
    public static void unpress(KeyBinding key) {
        if (key != null) {
            ((IKeyBindingExt) key).vivecraftx$unpress();
        }
    }

    /** Matches the patch's {@code setKeyBindState}. */
    public static void setState(KeyBinding key, boolean pressed) {
        if (pressed) {
            press(key);
        } else {
            unpress(key);
        }
    }
}
