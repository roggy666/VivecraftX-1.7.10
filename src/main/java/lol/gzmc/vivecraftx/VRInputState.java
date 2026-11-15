package lol.gzmc.vivecraftx;

/**
 * Input the VR controllers synthesise for screens that only understand mouse and keyboard.
 *
 * <p>Vivecraft's patch kept {@code pressShiftFake} on {@code GuiScreen}, but it is not per screen:
 * it says whether our own input layer is currently holding shift down, which is global.
 */
public final class VRInputState {

    private VRInputState() {}

    /** True while the controller input layer is pretending shift is held. */
    public static boolean pressShiftFake;
}
