package lol.gzmc.vivecraftx.api;

/**
 * The one vanilla {@code Minecraft} method the VR input layer needs that is not public.
 *
 * <p>Everything else Vivecraft's patch put on {@code Minecraft} was state, and lives in the VR
 * holders instead. This is behaviour, so it stays where it is and is only opened up.
 */
public interface IMinecraftExt {

    void vivecraftx$rightClickMouse();

    /**
     * The framebuffer the game renders into. Private in vanilla, and the stereo loop has to swap
     * it per eye.
     */
    net.minecraft.client.shader.Framebuffer vivecraftx$getFramebufferMc();

    void vivecraftx$setFramebufferMc(net.minecraft.client.shader.Framebuffer fb);
}
