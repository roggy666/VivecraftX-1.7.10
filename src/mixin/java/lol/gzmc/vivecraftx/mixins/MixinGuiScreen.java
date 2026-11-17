package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.VRInputState;
import lol.gzmc.vivecraftx.api.IGuiScreenExt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Public entry points into GuiScreen's protected input handling, for synthesised controller input.
 *
 * <p>The coordinate conversion and the eventButton / lastMouseEvent bookkeeping are copied from
 * Vivecraft's patch, not invented: without them a drag has no button and no elapsed time, and
 * sliders stop working.
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen implements IGuiScreenExt {

    @Shadow
    protected FontRenderer fontRendererObj;

    @Shadow
    protected Minecraft mc;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Shadow
    private int eventButton;

    @Shadow
    private long lastMouseEvent;

    @Shadow
    protected abstract void mouseClicked(int x, int y, int button);

    @Shadow
    protected abstract void mouseMovedOrUp(int x, int y, int button);

    @Shadow
    protected abstract void mouseClickMove(int x, int y, int button, long heldMillis);

    @Shadow
    protected abstract void keyTyped(char typedChar, int keyCode);

    @Unique
    private boolean vivecraftx$mouseDown;

    @Unique
    private int vivecraftx$guiX(int rawX) {
        return rawX * this.width / this.mc.displayWidth;
    }

    @Unique
    private int vivecraftx$guiY(int rawY) {
        return this.height - rawY * this.height / this.mc.displayHeight - 1;
    }

    @Override
    @Unique
    public void vivecraftx$mouseDown(int rawX, int rawY, int button) {
        this.eventButton = button;
        this.lastMouseEvent = Minecraft.getSystemTime();
        this.mouseClicked(vivecraftx$guiX(rawX), vivecraftx$guiY(rawY), button);
        this.vivecraftx$mouseDown = true;
    }

    @Override
    @Unique
    public void vivecraftx$mouseUp(int rawX, int rawY, int button) {
        this.vivecraftx$mouseDown = false;
        this.mouseMovedOrUp(vivecraftx$guiX(rawX), vivecraftx$guiY(rawY), button);
    }

    @Override
    @Unique
    public void vivecraftx$mouseDrag(int rawX, int rawY) {
        long held = Minecraft.getSystemTime() - this.lastMouseEvent;
        this.mouseClickMove(vivecraftx$guiX(rawX), vivecraftx$guiY(rawY), this.eventButton, held);
    }

    @Override
    @Unique
    public void vivecraftx$mouseGuiDown(int guiX, int guiY, int button) {
        this.eventButton = button;
        this.lastMouseEvent = Minecraft.getSystemTime();
        this.mouseClicked(guiX, guiY, button);
        this.vivecraftx$mouseDown = true;
    }

    @Override
    @Unique
    public void vivecraftx$keyTyped(char typedChar, int keyCode) {
        this.keyTyped(typedChar, keyCode);
    }

    @Override
    @Unique
    public boolean vivecraftx$isMouseDown() {
        return this.vivecraftx$mouseDown;
    }

    @Override
    @Unique
    public FontRenderer vivecraftx$fontRenderer() {
        return this.fontRendererObj;
    }

    /**
     * Leaves out the dark wash a screen paints over the world behind it.
     *
     * <p>Flat, that wash dims the game so the menu reads against it, and it costs nothing because
     * the screen fills the window. In VR the screen is a panel standing in the world, and the wash
     * covers the whole panel -- so instead of a menu floating in front of you, you get a dark
     * rectangle with a menu on it, and the game showing faintly through the rectangle. That is the
     * whole of "the little screen renders, not just the menu".
     *
     * <p>Vivecraft's own setting for this already existed and had never been connected to
     * anything. It is off by default, which is why menus should be drawn plain.
     *
     * <p>Only the in-world case. With no world the method paints the main menu's background
     * instead, and that one is meant to be there.
     */
    @Inject(method = "drawWorldBackground", at = @At("HEAD"), cancellable = true)
    private void vivecraftx$plainMenuBackground(int tint, CallbackInfo ci) {
        if (!VRState.running() || VR.settings() == null || VR.settings().menuBackground) {
            return;
        }
        if (this.mc != null && this.mc.theWorld != null) {
            ci.cancel();
        }
    }

    /**
     * The controller's Shift binding counts as Shift wherever the game asks the question.
     *
     * <p>The two reads that decide whether a click moves a whole stack are not here -- they go
     * straight to LWJGL and are answered in MixinGuiContainer -- but every other screen asks
     * through this method, and so does any mod. A binding that moved stacks and did nothing else
     * Shift does would be a worse answer than none.
     *
     * <p>Only ever forced true. A real Shift held on a real keyboard is still a Shift.
     */
    @Inject(method = "isShiftKeyDown", at = @At("HEAD"), cancellable = true)
    private static void vivecraftx$shiftFromController(CallbackInfoReturnable<Boolean> cir) {
        if (VRInputState.pressShiftFake) {
            cir.setReturnValue(true);
        }
    }
}
