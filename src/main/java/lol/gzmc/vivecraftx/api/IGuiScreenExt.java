package lol.gzmc.vivecraftx.api;

import net.minecraft.client.gui.FontRenderer;

/**
 * Lets the VR input layer drive a screen as though it were a mouse and keyboard.
 *
 * <p>These are not thin wrappers. Vivecraft's patch versions convert raw display coordinates into
 * GUI coordinates and keep {@code eventButton} and {@code lastMouseEvent} up to date, which is
 * what makes click-and-drag behave; the mixin reproduces that rather than just delegating.
 */
public interface IGuiScreenExt {

    /** Raw display coordinates, as the controller ray reports them. */
    void vivecraftx$mouseDown(int rawX, int rawY, int button);

    void vivecraftx$mouseUp(int rawX, int rawY, int button);

    /** Drag uses the button remembered from the last press. */
    void vivecraftx$mouseDrag(int rawX, int rawY);

    /** Already in GUI coordinates. */
    void vivecraftx$mouseGuiDown(int guiX, int guiY, int button);

    void vivecraftx$keyTyped(char typedChar, int keyCode);

    boolean vivecraftx$isMouseDown();

    FontRenderer vivecraftx$fontRenderer();
}
