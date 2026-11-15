package lol.gzmc.vivecraftx.util;

import lol.gzmc.vivecraftx.api.IGuiScreenExt;
import lol.gzmc.vivecraftx.api.IMinecraftExt;
import lol.gzmc.vivecraftx.api.IModelExt;
import lol.gzmc.vivecraftx.api.IPlayerVRExt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.model.ModelBase;

/**
 * One place for the casts to the interfaces our mixins add to vanilla classes.
 *
 * <p>Call sites read as plain calls, and if a mixin ever fails to apply the
 * {@code ClassCastException} happens here rather than scattered across the codebase.
 */
public final class VanillaExt {

    private VanillaExt() {}

    // --- models -------------------------------------------------------------------------

    public static boolean isVrMode(ModelBase model) {
        return ((IModelExt) model).vivecraftx$isVrMode();
    }

    public static void setVrMode(ModelBase model, boolean value) {
        ((IModelExt) model).vivecraftx$setVrMode(value);
    }

    public static boolean isRenderHead(ModelBase model) {
        return ((IModelExt) model).vivecraftx$isRenderHead();
    }

    public static void setRenderHead(ModelBase model, boolean value) {
        ((IModelExt) model).vivecraftx$setRenderHead(value);
    }

    public static boolean isRenderHeadWear(ModelBase model) {
        return ((IModelExt) model).vivecraftx$isRenderHeadWear();
    }

    public static void setRenderHeadWear(ModelBase model, boolean value) {
        ((IModelExt) model).vivecraftx$setRenderHeadWear(value);
    }

    public static float getVrOffset(ModelBase model) {
        return ((IModelExt) model).vivecraftx$getVrOffset();
    }

    public static void setVrOffset(ModelBase model, float value) {
        ((IModelExt) model).vivecraftx$setVrOffset(value);
    }

    // --- player -------------------------------------------------------------------------

    public static boolean isInitFromServer(EntityPlayerSP player) {
        return player != null && ((IPlayerVRExt) player).vivecraftx$isInitFromServer();
    }

    public static void setInitFromServer(EntityPlayerSP player, boolean value) {
        ((IPlayerVRExt) player).vivecraftx$setInitFromServer(value);
    }

    public static int getMovementTeleportTimer(EntityPlayerSP player) {
        return ((IPlayerVRExt) player).vivecraftx$getMovementTeleportTimer();
    }

    public static void setMovementTeleportTimer(EntityPlayerSP player, int value) {
        ((IPlayerVRExt) player).vivecraftx$setMovementTeleportTimer(value);
    }

    // --- screens ------------------------------------------------------------------------

    public static void mouseDown(GuiScreen screen, int x, int y, int button) {
        ((IGuiScreenExt) screen).vivecraftx$mouseDown(x, y, button);
    }

    public static void mouseUp(GuiScreen screen, int x, int y, int button) {
        ((IGuiScreenExt) screen).vivecraftx$mouseUp(x, y, button);
    }

    /** Raw display coordinates; the drag reuses the button from the last press. */
    public static void mouseDrag(GuiScreen screen, int rawX, int rawY) {
        ((IGuiScreenExt) screen).vivecraftx$mouseDrag(rawX, rawY);
    }

    public static void mouseGuiDown(GuiScreen screen, int guiX, int guiY, int button) {
        ((IGuiScreenExt) screen).vivecraftx$mouseGuiDown(guiX, guiY, button);
    }

    public static boolean isMouseDown(GuiScreen screen) {
        return ((IGuiScreenExt) screen).vivecraftx$isMouseDown();
    }

    public static void keyTyped(GuiScreen screen, char typedChar, int keyCode) {
        ((IGuiScreenExt) screen).vivecraftx$keyTyped(typedChar, keyCode);
    }

    public static FontRenderer fontRenderer(GuiScreen screen) {
        return ((IGuiScreenExt) screen).vivecraftx$fontRenderer();
    }

    // --- Minecraft ----------------------------------------------------------------------

    public static void rightClickMouse(Minecraft mc) {
        ((IMinecraftExt) mc).vivecraftx$rightClickMouse();
    }

    /**
     * Whether the player's own movement packet should be held back for one tick.
     *
     * <p>Set when a teleport has been handed to the server to carry out. Sending our own
     * movement as well would tell the server the same thing twice, the second time in the form
     * it rejects -- a jump of more than ten blocks, which is what its cheat check is for.
     */
    private static boolean suppressMoveUpdate;

    public static void suppressNextMoveUpdate() {
        suppressMoveUpdate = true;
    }

    /** Reads the flag and clears it, so it covers exactly one tick. */
    public static boolean takeSuppressMoveUpdate() {
        boolean was = suppressMoveUpdate;
        suppressMoveUpdate = false;
        return was;
    }
}
