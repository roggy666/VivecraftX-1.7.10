package lol.gzmc.vivecraftx.api;

/**
 * Which view the renderer is currently producing.
 *
 * <p>Vivecraft declared this as {@code Minecraft.renderPass}, because its patches could add types
 * to vanilla classes. A mod cannot, and should not want to: the enum describes our own stereo
 * pipeline, not Minecraft's, so it lives here.
 *
 * <p>{@code Left} and {@code Right} are the eye passes submitted to the headset, {@code Center}
 * is the undistorted view used for the desktop mirror, and {@code Third} is the third-person
 * "mixed reality" camera.
 */
public enum RenderPass {
    Left,
    Right,
    Center,
    Third;

    public int value() {
        return ordinal();
    }
}
