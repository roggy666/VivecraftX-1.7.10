package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.render.VRKeyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiSleepMP;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes a text field ask for a keyboard when it is typed into.
 *
 * <p>The alternative, and what was here before, is a list of screens known to want typing. That
 * list cannot ever be right: it has to name every mod's screens as well as the game's, and it
 * answers for a whole screen when the thing that wants typing is one field on it. Upstream solves
 * it here instead, where the field itself says so by taking focus, and so does this.
 *
 * <p>Deliberately not the same answer for every field. A field that cannot lose focus is the only
 * thing on its screen -- the chat line is the example, and the only one in the game -- so there
 * is nothing else the player could have meant and a keyboard always appears. An ordinary field
 * sits among buttons and other fields, and having one rise up every time a cursor lands anywhere
 * near one would be its own kind of broken, so that case is a setting and is off by default.
 */
@Mixin(GuiTextField.class)
public abstract class MixinGuiTextField {

    /**
     * Unnamed in these mappings, so it is shadowed under the obfuscation-stable name. It is the
     * field's own font renderer rather than the game's, which matters: a mod is free to hand a
     * field a different one, and the hint has to be measured with whatever draws it.
     */
    @Shadow
    @Final
    private FontRenderer field_146211_a;

    @Shadow
    public int xPosition;

    @Shadow
    public int yPosition;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Shadow
    private String text;

    @Shadow
    private boolean canLoseFocus;

    @Shadow
    private boolean isFocused;

    @Shadow
    private boolean visible;

    @Shadow
    private boolean enableBackgroundDrawing;

    @Shadow
    private int disabledColor;

    @Shadow
    public abstract int getWidth();

    /**
     * The usual way in: something has given this field the keystrokes.
     *
     * <p>At the tail rather than the head, so that the field is already focused when the keyboard
     * is placed -- and because a field that refuses focus should not have summoned one.
     *
     * <p>This is the hook that a mod's own field reaches. One that does its own hit testing and
     * calls {@code setFocused} itself, rather than letting {@code mouseClicked} do it, lands here
     * all the same, which is the whole point of hooking the field instead of the click.
     */
    @Inject(method = "setFocused", at = @At("TAIL"))
    private void vivecraftx$keyboardOnFocus(boolean focused, CallbackInfo ci) {
        if (!focused) {
            return;
        }
        if (vivecraftx$wantsKeyboard()) {
            VRKeyboard.requestShow(true);
        }
    }

    /**
     * Tapping a field that already has focus brings the keyboard back.
     *
     * <p>Without this, dismissing the keyboard with the field still focused leaves no way to ask
     * for it again short of the key binding, because focus never changes and the hook above never
     * fires. Upstream added the same second path.
     */
    @Inject(method = "mouseClicked", at = @At("TAIL"))
    private void vivecraftx$keyboardOnClick(int mouseX, int mouseY, int button, CallbackInfo ci) {
        boolean inside = mouseX >= this.xPosition && mouseX < this.xPosition + this.width
                && mouseY >= this.yPosition && mouseY < this.yPosition + this.height;
        if (inside && this.isFocused && vivecraftx$wantsKeyboard()) {
            VRKeyboard.requestShow(true);
        }
    }

    /**
     * Says what an empty field is for, in a headset where there is no keyboard to reach for.
     *
     * <p>Drawn at the return rather than woven into the middle of the method: the only thing the
     * original draws after this point is the cursor, which on an empty field is a single
     * underscore in the same place, so the order costs nothing and the hook cannot be broken by
     * anything else that touches the method.
     */
    @Inject(method = "drawTextBox", at = @At("RETURN"))
    private void vivecraftx$keyboardHint(CallbackInfo ci) {
        // The font renderer is nullable -- the original guards against it in setSelectionPos --
        // and a field with none is one nothing was ever going to be read out of anyway.
        if (!this.visible || !this.text.isEmpty() || !this.canLoseFocus
                || this.field_146211_a == null) {
            return;
        }
        if (!VRState.running() || VR.settings() == null || VR.settings().seated
                || VRKeyboard.isAnyShowing()) {
            return;
        }
        int x = this.enableBackgroundDrawing ? this.xPosition + 4 : this.xPosition;
        int y = this.enableBackgroundDrawing ? this.yPosition + (this.height - 8) / 2
                                             : this.yPosition;
        // Trimmed, because the hint is longer than most fields are wide and longer again once it
        // has been translated. Upstream lets it run off the end of the box.
        String hint = this.field_146211_a.trimStringToWidth(
                I18n.format("vivecraftx.keyboard.clickToOpen"), this.getWidth());
        this.field_146211_a.drawStringWithShadow(hint, x, y, this.disabledColor);
    }

    /**
     * Whether this field, on this screen, is one a keyboard should appear for.
     */
    @Unique
    private boolean vivecraftx$wantsKeyboard() {
        if (!VRState.running() || VR.settings() == null || !this.visible) {
            return false;
        }
        // Getting into bed opens a chat screen whose field nobody is going to type into.
        if (Minecraft.getMinecraft().currentScreen instanceof GuiSleepMP) {
            return false;
        }
        return !this.canLoseFocus || VR.settings().autoOpenKeyboard;
    }
}
