package lol.gzmc.vivecraftx;

import lol.gzmc.vivecraftx.api.RenderPass;

import com.mtbs3d.minecrift.api.ErrorHelper;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;

/**
 * Where the VR camera is this frame.
 *
 * <p>Vivecraft's patch kept this on {@code EntityRenderer}, but none of it is EntityRenderer's
 * business: the values are produced by the headset and consumed by our own code, and the two
 * methods below read nothing from the vanilla renderer. Keeping them here matters because
 * {@code EntityRenderer} is the class Angelica mixes into most heavily, and that overlap is worth
 * spending on real render hooks rather than on field storage.
 *
 * <p>Client side only, written once per frame from the render loop.
 */
public final class VRRenderState {

    private VRRenderState() {}

    /** The player position the stereo passes are interpolated around. */
    public static Vec3 interpolatedPlayerPos = Vec3.createVectorHelper(0, 0, 0);

    /** Where the play area's origin sits in world space this frame. */
    public static Vec3 interPolatedRoomOrigin = Vec3.createVectorHelper(0, 0, 0);

    /** Guards against interpolating the room origin twice in one frame. */
    public static boolean irpUpdatedThisFrame = false;

    /** Asks the stereo provider to rebuild its framebuffers on the next frame. */
    public static boolean reinitFramebuffers = true;

    /** Seconds a VR error stays on screen. */
    public static final int ERROR_DISPLAY_TIME_SECS = 10;

    /** Collects headset and runtime errors for the options screens to show. */
    public static ErrorHelper errorHelper;

    /** Seconds the last frame took, written by the render loop. */
    public static float frameDelta;

    /** Where getEyeCollisionBlock last found a block, for the caller to reuse. */
    public static Vec3 eyeCollisionBlockPosLoc = Vec3.createVectorHelper(0, 0, 0);

    public static float getFrameDelta() {
        return frameDelta;
    }

    /** The runtime's clock, which is what the headset's prediction is relative to. */
    public static double getCurrentTimeSecs() {
        return VR.stereo() == null ? 0.0D : VR.stereo().getCurrentTimeSecs();
    }

    /**
     * The block the eye, or the off hand, is currently inside, or null when it is in open air.
     * Used to draw the "head in block" overlay.
     */
    public static Block getEyeCollisionBlock(RenderPass pass, boolean offHand) {
        if (pass == RenderPass.Third) {
            return null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || VR.roomScale() == null) {
            return null;
        }
        // The render-time position, which is where the camera this overlay describes actually is.
        // The tick-time one is up to a whole tick behind it, and the overlay would switch on or
        // off a moment before or after the view reached the block -- a visible pop at exactly the
        // moment the player is already disoriented.
        Vec3 exact = offHand ? getControllerRenderPos(1) : getEyeRenderPos(pass);
        if (exact == null) {
            return null;
        }
        int x = (int) Math.floor(exact.xCoord);
        int y = (int) Math.floor(exact.yCoord);
        int z = (int) Math.floor(exact.zCoord);

        Block block = mc.theWorld.getBlock(x, y, z);
        if (block == null) {
            return null;
        }
        float liquidHeight = BlockLiquid.getLiquidHeightPercent(
                mc.theWorld.getBlockMetadata(x, y, z));

        boolean inside = block.isNormalCube()
                || (block.getMaterial() == Material.water
                    && (mc.theWorld.getBlock(x, y + 1, z).getMaterial() == Material.water
                        || exact.yCoord - y < (1 - liquidHeight)));
        if (inside) {
            eyeCollisionBlockPosLoc = Vec3.createVectorHelper(x, y, z);
            return block;
        }
        return null;
    }

    /** World-space position of the eye for a given pass. */
    public static Vec3 getEyeRenderPos(RenderPass pass) {
        if (pass == RenderPass.Center) {
            Vec3 out = VR.roomScale().getHMDPos_Room();
            out.rotateAroundY(VR.player().worldRotationRadians);
            return out.addVector(interPolatedRoomOrigin.xCoord, interPolatedRoomOrigin.yCoord,
                    interPolatedRoomOrigin.zCoord);
        }
        if (pass == RenderPass.Third) {
            float scale = VR.player().worldScale;
            Vec3 out = Vec3.createVectorHelper(
                    VR.settings().vrFixedCamposX * scale,
                    VR.settings().vrFixedCamposY * scale,
                    VR.settings().vrFixedCamposZ * scale);
            out.rotateAroundY(VR.player().worldRotationRadians);
            return interPolatedRoomOrigin.addVector(out.xCoord, out.yCoord, out.zCoord);
        }
        Vec3 out = VR.roomScale().getEyePos_Room(pass);
        out.rotateAroundY(VR.player().worldRotationRadians);
        return out.addVector(interPolatedRoomOrigin.xCoord, interPolatedRoomOrigin.yCoord,
                interPolatedRoomOrigin.zCoord);
    }

    /** World-space position a controller renders at. Seated mode fakes one off the head. */
    public static Vec3 getControllerRenderPos(int controller) {
        Minecraft mc = Minecraft.getMinecraft();
        if (VR.settings().seated) {
            Vec3 dir = VR.roomScale().getHMDDir_World();
            dir.rotateAroundY((float) Math.toRadians(controller == 0 ? -35 : 35));
            dir.yCoord = 0;
            dir = dir.normalize();
            if (mc.renderViewEntity != null && mc.theWorld != null) {
                float scale = VR.player().worldScale;
                return getEyeRenderPos(RenderPass.Center)
                        .addVector(dir.xCoord * 0.3 * scale, -0.4 * scale, dir.zCoord * 0.3 * scale);
            }
            return VR.roomScale().getHMDPos_Room()
                    .addVector(dir.xCoord * 0.3, -0.4, dir.zCoord * 0.3);
        }
        Vec3 out = VR.roomScale().getControllerPos_Room(controller);
        out.rotateAroundY(VR.player().worldRotationRadians);
        return out.addVector(interPolatedRoomOrigin.xCoord, interPolatedRoomOrigin.yCoord,
                interPolatedRoomOrigin.zCoord);
    }
}
