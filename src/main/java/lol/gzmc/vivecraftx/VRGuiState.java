package lol.gzmc.vivecraftx;

/**
 * Where the controller ray met the floating GUI quad, and the quad's corners.
 *
 * <p>Vivecraft's patch kept these as sixteen public floats on {@code Minecraft} under a comment
 * reading "vive ui debugging". They are written by the GUI ray-cast each frame and read by the
 * pointer and the debug overlay, so they are ours, not Minecraft's.
 *
 * <p>Client side only, written once per frame.
 */
public final class VRGuiState {

    private VRGuiState() {}

    /** Hit position on the GUI, in texture coordinates. */
    public static float guiU;
    public static float guiV;

    /** Distance from the controller to the GUI plane. */
    public static float intersectDist;

    /** Where the ray met the GUI plane, in world space. */
    public static float pointOnPlaneX;
    public static float pointOnPlaneY;
    public static float pointOnPlaneZ;

    /** The GUI quad's corners, in world space. */
    public static float guiTopLeftX;
    public static float guiTopLeftY;
    public static float guiTopLeftZ;
    public static float guiTopRightX;
    public static float guiTopRightY;
    public static float guiTopRightZ;

    /** The controller the ray was cast from. */
    public static float controllerPosX;
    public static float controllerPosY;
    public static float controllerPosZ;
}
