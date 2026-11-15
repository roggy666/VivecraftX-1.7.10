package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.api.IEntityRendererExt;
import lol.gzmc.vivecraftx.compat.AngelicaRenderCompat;
import lol.gzmc.vivecraftx.render.VRCamera;
import lol.gzmc.vivecraftx.render.VRGui;
import lol.gzmc.vivecraftx.render.VRHands;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRAim;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.VRRenderState;
import lol.gzmc.vivecraftx.api.RenderPass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the camera to the headset on eye passes, and sends the interface to its panel.
 *
 * <p>{@code setupCameraTransform} is taken over whole rather than redirecting the perspective call
 * inside it, because Angelica has two {@code @ModifyArg} on that call -- its zoom adjusts the
 * field of view and NotFine adjusts the cloud far plane -- and a redirect would quietly drop both.
 * Cancelling only on eye passes leaves every flat pass, and therefore both of those adjustments,
 * exactly as they were.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer implements IEntityRendererExt {

    @Shadow
    private float farPlaneDistance;

    @Shadow
    private int rendererUpdateCount;

    @Override
    @Unique
    public float vivecraftx$getFarPlaneDistance() {
        return this.farPlaneDistance;
    }

    @Override
    @Unique
    public int vivecraftx$getRendererUpdateCount() {
        return this.rendererUpdateCount;
    }

    /**
     * On an eye pass the projection comes from the runtime and the view from the head pose, so
     * vanilla's perspective and entity-angle camera are skipped entirely.
     *
     * <p>Field of view, view bobbing and the anaglyph offset are deliberately not carried over:
     * the lenses decide the field of view, and bobbing the camera in a headset is what makes
     * people ill.
     */
    @Inject(method = "setupCameraTransform", at = @At("HEAD"), cancellable = true)
    private void vivecraftx$eyeCamera(float partialTicks, int pass, CallbackInfo ci) {
        this.farPlaneDistance = (float) (Minecraft.getMinecraft()
                .gameSettings.renderDistanceChunks * 16);
        if (VRCamera.setup(partialTicks, this.farPlaneDistance, this.rendererUpdateCount)) {
            ci.cancel();
        }
    }

    /**
     * Takes over the first-person hand.
     *
     * <p>Vanilla's version cannot be left to run: the first thing it does is replace the
     * projection with a flat perspective sized to the window, which flattens everything drawn
     * after it. That is what makes a hand look stuck to a screen instead of attached to an arm,
     * and it also undoes the eye camera for anything drawn later in the frame.
     *
     * <p>What replaces it puts the arms and the held item at the controllers, then draws the
     * overlays vanilla drew here -- fire, suffocation, water.
     */
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void vivecraftx$vrHands(float partialTicks, int pass, CallbackInfo ci) {
        if (VRState.running() && VRCamera.isEyePass(VR.currentPass())) {
            ci.cancel();
        }
    }

    /**
     * Draws the hands a moment earlier than vanilla would, while the world is still in the depth
     * buffer.
     *
     * <p>The line vanilla runs between here and its own hand pass wipes the depth buffer, so that
     * the hand is in front of everything whatever it is standing in. On a monitor that is right:
     * an arm disappearing into a wall would read as a glitch. In a headset it is exactly wrong.
     * The hand is a solid object an arm's length away, and one hanging in front of a wall it is
     * pressed against is the clearest possible statement that none of this is really there. It
     * also silently took the crosshair occlusion setting with it, which could be switched on and
     * did nothing at all. Minecrift deleted the same line.
     *
     * <p>Moving the drawing rather than removing the clear, because by the time the clear has
     * run it cannot be undone, and because the clear itself is beyond reach: Angelica rewrites
     * every such call onto its own state tracker, so a hook naming the graphics library would
     * match nothing on a machine that has it installed. The profiler label is Minecraft's own and
     * stays where it is. With vanilla's pass cancelled, nothing is drawn after the clear, so what
     * it clears no longer matters.
     */
    @Inject(method = "renderWorld", at = @At(value = "INVOKE_STRING",
            target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
            args = "ldc=hand", shift = At.Shift.AFTER))
    private void vivecraftx$vrHandsInWorld(float partialTicks, long nanoTime, CallbackInfo ci) {
        if (VRState.running() && VRCamera.isEyePass(VR.currentPass())) {
            VRHands.renderHandsAndOverlays(partialTicks);
        }
    }

    /**
     * Makes both eyes draw the same, current set of chunks.
     *
     * <p>Immediately after the visibility search has been asked for and before anything is drawn
     * with its answer. Without this the first eye draws the previous frame's answer and the
     * second draws this one, and the chunks the two disagree about are precisely the ones at the
     * edge of vision.
     */
    @Inject(method = "renderWorld", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/RenderGlobal;"
                     + "clipRenderersByFrustum(Lnet/minecraft/client/renderer/culling/ICamera;F)V"))
    private void vivecraftx$sameChunksInBothEyes(float partialTicks, long nanoTime,
            CallbackInfo ci) {
        if (VRState.running() && VRCamera.isEyePass(VR.currentPass())) {
            AngelicaRenderCompat.waitForChunkVisibility();
        }
    }

    /**
     * Aims from the hand instead of the body.
     *
     * <p>Vanilla picks along the player entity's facing. In VR that is where the body is pointed,
     * which has nothing to do with where the controller is: the player would mine the block in
     * front of their chest while aiming somewhere else. This is the difference between VR that
     * works and VR that merely renders.
     *
     * <p>Cancelled rather than redirected because what replaces it is a different trace, not a
     * different argument -- the origin, the direction and the reach all change.
     */
    @Inject(method = "getMouseOver", at = @At("HEAD"), cancellable = true)
    private void vivecraftx$aimFromHand(float partialTicks, CallbackInfo ci) {
        if (VRState.running() && Minecraft.getMinecraft().theWorld != null) {
            VRAim.getPointedBlock(partialTicks);
            // Straight after, and before the renderer works out which way sprites should face:
            // that calculation reads the entity's rotation, so it has to be the aimed one by now.
            VRAim.updatePlayerOrientation();
            ci.cancel();
        }
    }

    /**
     * Culls terrain from where the head actually is.
     *
     * <p>Vanilla positions the culling frustum at the player entity. In VR the view comes from the
     * headset, which in room-scale can be metres away from the entity, so chunks were being
     * decided against from the wrong point: stand somewhere with a long view and the ones at the
     * edge of vision drop out. The frustum's shape is already right, since it is snapshotted from
     * the live projection, which is ours -- only where it sits was wrong.
     *
     * <p>The middle of the head rather than the eye being drawn, which is what Vivecraft used, so
     * both eyes cull identically. Three centimetres of parallax is not worth two different answers
     * about which chunks exist. The shape is made to agree between the eyes separately, in
     * {@link #vivecraftx$cullAcrossBothEyes}.
     */
    @Redirect(
            method = "renderWorld",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/culling/Frustrum;"
                              + "setPosition(DDD)V"))
    private void vivecraftx$cullFromHead(Frustrum frustrum, double x, double y, double z) {
        if (VRState.running()) {
            Vec3 head = VRRenderState.getEyeRenderPos(RenderPass.Center);
            if (head != null) {
                frustrum.setPosition(head.xCoord, head.yCoord, head.zCoord);
                return;
            }
        }
        frustrum.setPosition(x, y, z);
    }

    /**
     * Decides what is visible using a shape that covers both eyes rather than just this one.
     *
     * <p>This call is where the game takes its snapshot of the current projection for culling.
     * That projection is one eye's, and the two eyes do not have the same shape -- a headset lens
     * is off-centre, so each eye sees further to its own outer side. Culled against one eye's
     * shape, a chunk at the very edge of vision is kept for the left eye and dropped for the
     * right: one eye shows terrain and the other shows sky in the same place, which is worse to
     * look at than the chunk simply being absent from both.
     *
     * <p>So the snapshot is taken through a shape wide enough to contain both, and put back
     * immediately. Drawing is untouched and still uses each eye's own projection.
     */
    @Redirect(
            method = "renderWorld",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/culling/ClippingHelperImpl;"
                              + "getInstance()Lnet/minecraft/client/renderer/culling/"
                              + "ClippingHelper;"))
    private ClippingHelper vivecraftx$cullAcrossBothEyes() {
        if (!VRState.running() || !VRCamera.isEyePass(VR.currentPass())) {
            return ClippingHelperImpl.getInstance();
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        try {
            VRCamera.applyCullingProjection();
            return ClippingHelperImpl.getInstance();
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    /**
     * Sends everything two dimensional to the interface panel instead of into the eye.
     *
     * <p>Both branches of {@code updateCameraAndRender} stamp {@code renderEndNanoTime} at exactly
     * the point the scene is finished and the interface has yet to be drawn -- the branch with a
     * world just before the overlay, the branch without just after the orthographic setup -- so
     * one injection point with no ordinal covers both, and {@code require = 2} makes a vanilla
     * change that moved either of them a build failure rather than a black screen.
     *
     * <p>The obvious anchors were both worse. The call to {@code setupOverlayRendering} is the
     * method Angelica's interface caching keeps a private copy of, so with caching on it either
     * fires inside Angelica's own framebuffer or does not fire at all. The call to
     * {@code renderGameOverlay} is the instruction Angelica redirects, which means it may no
     * longer be there by the time we are applied. A field store is nobody's hook.
     */
    @Inject(
            method = "updateCameraAndRender",
            at = @At(value = "FIELD",
                     opcode = Opcodes.PUTFIELD,
                     target = "Lnet/minecraft/client/renderer/EntityRenderer;"
                              + "renderEndNanoTime:J",
                     shift = At.Shift.AFTER),
            require = 2)
    private void vivecraftx$guiToPanel(float partialTicks, CallbackInfo ci) {
        VRGui.beginGuiPhase();
    }
}
