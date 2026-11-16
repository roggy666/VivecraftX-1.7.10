package lol.gzmc.vivecraftx.mixins;

import com.mtbs3d.minecrift.control.VRInputAction;
import com.mtbs3d.minecrift.provider.MCOpenVR;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRFrame;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.VRSwing;
import lol.gzmc.vivecraftx.ViveCraftX;
import lol.gzmc.vivecraftx.compat.AngelicaRenderCompat;
import lol.gzmc.vivecraftx.compat.BackhandCompat;
import lol.gzmc.vivecraftx.render.VRRenderer;
import lol.gzmc.vivecraftx.api.IMinecraftExt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * The four things we need from {@code Minecraft}: the render target, the frame boundary, the
 * stereo render loop, and notice of a screen opening.
 *
 * <p>Kept to one mixin class on purpose. Angelica already has fourteen on {@code Minecraft}, and
 * that overlap is worth spending on real hooks rather than on field storage, which is why the VR
 * state lives in {@code VR} rather than in {@code @Unique} fields here.
 *
 * <p>Raised above the default priority because the stereo redirect below has to displace
 * Angelica's redirect of the same instruction, and a redirect only displaces another when it
 * outranks it: at equal rank the one that arrives second is dropped with nothing but a warning,
 * and which one that is depends on the order the configs happened to be prepared in. Losing that
 * coin toss means the game renders flat. Everything Angelica's redirect did is reproduced in
 * ours, so winning it deliberately costs nothing.
 */
@Mixin(value = Minecraft.class, priority = 1500)
public class MixinMinecraft implements IMinecraftExt {

    /**
     * The render target. Swapped for an eye's scene buffer while a stereo pass is drawn, and for
     * the interface panel's buffer while the interface is drawn, which is how Vivecraft did it and
     * what makes mods that composite the interface themselves land in the right place.
     */
    @Shadow
    private Framebuffer framebufferMc;

    @Shadow
    public GuiScreen currentScreen;

    @Override
    @Unique
    public Framebuffer vivecraftx$getFramebufferMc() {
        return this.framebufferMc;
    }

    @Override
    @Unique
    public void vivecraftx$setFramebufferMc(Framebuffer fb) {
        this.framebufferMc = fb;
    }

    /**
     * Right-click-to-use. MCP 12 leaves this one obfuscated, so it has to be shadowed under its
     * SRG name: there is no readable name to write. Identified from its body, which sets
     * rightClickDelayTimer and reads the held stack, not guessed from a mapping table.
     */
    @Shadow
    private void func_147121_ag() {
        // a shadow of a private method needs a body; mixin replaces it with the real one
        throw new AssertionError("MixinMinecraft was not applied");
    }

    /**
     * Fresh poses once per frame, before anything reads them.
     *
     * <p>An inject at the head rather than a rewrite of the loop: Angelica injects here too, and
     * the body has to stay intact for both of us.
     */
    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void vivecraftx$onFrameStart(CallbackInfo ci) {
        // Whether VR should be running at all is decided here, once, before anything reads the
        // answer: the player may have pressed the button since the last frame, or put the headset
        // down, or started SteamVR.
        VRState.update();
        VRFrame.onFrameStart();
    }

    /**
     * Runs the frame once per eye instead of once.
     *
     * <p>Vivecraft rewrote the whole loop to do this. A redirect of the single render call is
     * enough and leaves the method body alone, which matters because Angelica injects into
     * runGameLoop as well. On this exact call it has an @Inject rather than a @Redirect, so the
     * point is free, and its handler only pumps display messages once a frame.
     *
     * <p>With VR off, or if the VR frame fails, the original is called exactly once and the
     * behaviour is vanilla.
     *
     * <p>Angelica redirects this same call to guard the frame, and only one redirect of a call can
     * win, so ours outranks it (see the class above) and inherits what it did: posting its own
     * per-frame event,
     * which several of its subsystems listen for, and honouring its frame limiter. The window-size
     * check is reproduced directly rather than reflectively, because skipping a frame while the
     * window is iconified is right for us too -- the eye buffers are not the window, but the
     * compositor has nothing to show while the window is gone.
     */
    @Redirect(
            method = "runGameLoop",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/EntityRenderer;updateCameraAndRender(F)V"))
    private void vivecraftx$renderStereo(EntityRenderer renderer, float partialTicks) {
        AngelicaRenderCompat.onFrameStart();

        Framebuffer target = this.framebufferMc;
        if (target == null || target.framebufferWidth < 16 || target.framebufferHeight < 16
                || AngelicaRenderCompat.shouldSkipFrame()) {
            AngelicaRenderCompat.onFrameSkipped();
            return;
        }

        // where the player and the play area are at this instant between ticks; both eyes are
        // told the same thing, so they cannot disagree about it
        VRFrame.onRenderFrame(partialTicks);

        if (!VRRenderer.renderStereo(new Runnable() {
            @Override
            public void run() {
                renderer.updateCameraAndRender(partialTicks);
            }
        })) {
            renderer.updateCameraAndRender(partialTicks);
        }
    }

    /**
     * Carries out the key releases the input system has queued.
     *
     * <p>A release is queued rather than done the moment the button comes up, because the game
     * samples key state once a tick and a press and release inside one tick would never be seen.
     * Something then has to perform them, once a tick, and without it every release is scheduled
     * and never happens: attack stays held, and a binding can be pressed exactly once ever,
     * because the press is refused while the previous one is still notionally down.
     *
     * <p>At the end of the tick rather than the start, which is where upstream puts it and which
     * matters at headset frame rates. Most frames carry no tick, so a press and the release that
     * follows it a frame or two later often both arrive before the next tick -- and draining at
     * the start of that tick cancelled the press before the body of the tick had looked at it.
     * Any trigger pull shorter than a fiftieth of a second was swallowed whole: no swing, no
     * block placed, no mining begun.
     */
    @Inject(method = "runTick", at = @At("RETURN"))
    private void vivecraftx$drainQueuedReleases(CallbackInfo ci) {
        if (!VRState.running()) {
            return;
        }
        for (VRInputAction action : MCOpenVR.getInputActions()) {
            action.tick();
        }
    }

    /**
     * Tells the stereo provider a screen is about to open, so it can decide where the panel for it
     * should hang: a chest over the block it belongs to, the chat off to one side, the main menu
     * in the middle of the play area.
     *
     * <p>Injected at the store to {@code currentScreen}, which is where Vivecraft patched the call
     * in, and for the same reason: by then Forge's event has had its say and substituted whatever
     * screen is really opening, while the field still holds the one being replaced, so both ends
     * of the change are available.
     */
    @Inject(
            method = "displayGuiScreen",
            at = @At(value = "FIELD",
                     opcode = Opcodes.PUTFIELD,
                     target = "Lnet/minecraft/client/Minecraft;"
                              + "currentScreen:Lnet/minecraft/client/gui/GuiScreen;"))
    private void vivecraftx$onScreenChanging(GuiScreen opening, CallbackInfo ci) {
        if (!VRState.running() || VR.stereo() == null) {
            return;
        }
        try {
            VR.stereo().onGuiScreenChanged(this.currentScreen, opening);
            // And a keyboard, if the screen is one that expects typing.
            lol.gzmc.vivecraftx.render.VRKeyboard.onScreenChanged(opening);
        } catch (Throwable t) {
            ViveCraftX.LOG.error("Placing the VR panel for {} failed",
                    opening == null ? "no screen" : opening.getClass().getName(), t);
        }
    }

    @Override
    @Unique
    public void vivecraftx$rightClickMouse() {
        this.func_147121_ag();
    }

    /**
     * Keeps a gesture-started use going, when no button is being held to keep it going.
     *
     * <p>Vanilla cancels the use of an item the moment the use key is let go, every tick. That is
     * right for a game where using something means holding a button, and wrong for one where it
     * means raising an apple to your mouth: nothing is held down, so the use is cancelled on the
     * very next tick and the apple is never eaten. Drawing a bow has the same shape.
     *
     * <p>So the cancel is skipped while a tracker says the gesture is still going. It still
     * happens for a use the player started with a button and then released, which is what the
     * check is actually for.
     */
    /**
     * Says why the hand is about to swing, so that it can be drawn swinging that way.
     *
     * <p>The game's swing carries no reason with it -- one call, one animation, whether you have
     * just buried an axe in something or opened a door. In a headset the difference is plain, so
     * the reason is taken from where it is still known: these are the three places the game
     * itself decides to swing, and each of them knows which it is.
     *
     * <p>Separate hooks rather than one on the swing itself, because the swing is the one thing
     * that cannot tell them apart. Named by their obfuscated names because this version's
     * mappings never gave these a readable one. Using an item is noted elsewhere, next to the
     * call that does it, because the off-hand mod replaces this class's right-click method
     * whole and nothing can be hooked inside a body another mod has written.
     *
     * <p>Which hand is asked of the off-hand mod rather than assumed, because when it has the
     * off-hand item standing in for the held one it sends the swing to the other arm, and a
     * reason recorded against the wrong hand is worse than none.
     */
    @Redirect(
            method = "func_147116_af",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/entity/EntityClientPlayerMP;swingItem()V"))
    private void vivecraftx$swungToHit(EntityClientPlayerMP player) {
        VRSwing.began(BackhandCompat.actingHand(player), VRSwing.Kind.ATTACK);
        player.swingItem();
    }

    /** The same, for the blow that lands each tick while a block is being broken. */
    @Redirect(
            method = "func_147115_a",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/entity/EntityClientPlayerMP;swingItem()V"))
    private void vivecraftx$swungToMine(EntityClientPlayerMP player) {
        VRSwing.began(BackhandCompat.actingHand(player), VRSwing.Kind.ATTACK);
        player.swingItem();
    }

    @Redirect(
            method = "runTick",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;"
                              + "onStoppedUsingItem(Lnet/minecraft/entity/player/EntityPlayer;)V"))
    private void vivecraftx$keepGestureUseGoing(PlayerControllerMP controller, EntityPlayer player) {
        if (VRState.running() && VR.settings() != null) {
            if (VR.autoFood() != null && VR.autoFood().isEating()) {
                return;
            }
            if (!VR.settings().seated && VR.bowTracker() != null
                    && player instanceof EntityPlayerSP
                    && VR.bowTracker().isActive((EntityPlayerSP) player)) {
                return;
            }
        }
        controller.onStoppedUsingItem(player);
    }
}
