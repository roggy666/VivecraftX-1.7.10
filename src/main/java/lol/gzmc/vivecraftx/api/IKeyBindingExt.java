package lol.gzmc.vivecraftx.api;

/**
 * What Vivecraft's patch added to {@code KeyBinding}, as an interface {@code KeyBinding} is made
 * to implement by a mixin.
 *
 * <p>The interface lives in the main source set and the mixin in its own, because the mixin
 * source set can see main but not the other way round. Callers go through
 * {@link lol.gzmc.vivecraftx.util.KeyBindingUtil} rather than casting themselves.
 */
public interface IKeyBindingExt {

    /** Marks the key down and queues one press, as the patch's {@code pressKey()} did. */
    void vivecraftx$press();

    /** Vanilla's {@code unpressKey()}, which is private there. */
    void vivecraftx$unpress();
}
