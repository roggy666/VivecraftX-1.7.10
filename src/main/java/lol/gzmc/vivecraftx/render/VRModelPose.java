package lol.gzmc.vivecraftx.render;

import com.mtbs3d.minecrift.api.IRoomscaleAdapter;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.net.VRPlayers;
import lol.gzmc.vivecraftx.net.VRPose;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Standing a player model the way a person in a headset is actually standing.
 *
 * <p>Used for two things that look unrelated and are the same problem: the figure in your own
 * inventory, and other people in the world. Both are an ordinary biped drawn by the ordinary
 * renderer, and in both cases the parts a headset knows about -- the head and the two hands --
 * are being drawn in a default pose that has nothing to do with the person.
 *
 * <p>Posing the model rather than drawing free limbs beside it is how Vivecraft has always done
 * it, and the reason is everything that comes along for the ride: armour follows the limb it is
 * on, a held item stays in the hand that holds it, the whole figure takes the light at its own
 * position, goes invisible with an invisibility potion, lies down in a bed, sits in a boat, and
 * carries its own name tag. Drawn separately, every one of those is a thing you have to
 * reimplement and will get subtly wrong.
 *
 * <p>Head and arms only. Those are what a headset tracks; the legs are guessed from how the
 * player is moving either way, and guessing them differently here would not be showing anything
 * that is true.
 */
public final class VRModelPose {

    private VRModelPose() {}

    /** Set while the inventory is drawing the figure, so the local player is posed only there. */
    private static boolean inventory;

    /** The two-piece arms, one set per model that needs them. */
    private static final Map<ModelBiped, VRArmBones> BONES =
            new WeakHashMap<ModelBiped, VRArmBones>();

    /** Which model is the body itself rather than a layer of armour over it. */
    private static ModelBiped bodyModel;

    public static VRArmBones bonesFor(ModelBiped model) {
        return BONES.get(model);
    }

    /**
     * Where the model's origin sits above the player's feet.
     *
     * <p>A biped model is built around the base of the neck, and the game places that 1.501
     * blocks up. The third decimal is Minecraft's, not ours.
     *
     * <p>Above the <em>feet</em>, and on this version of the game that is not where a player's
     * position is. An entity here sits 1.62 above its own feet, so measuring from the position
     * put the whole figure a metre and a half out: the hands came out somewhere around the
     * knees, and raising them did not help because they were never going to reach.
     */
    private static final double MODEL_ORIGIN_HEIGHT = 1.501D;

    /** Model units per block. */
    private static final float UNITS_PER_BLOCK = 16F;

    /** How long an arm is, in model units, measured from the shoulder. */
    private static final float ARM_LENGTH = 10F;

    /**
     * How far the head may be drawn from the body before it stops following.
     *
     * <p>Somebody leaning out from behind a wall is worth showing -- it is half of what people do
     * with room-scale cover. Somebody whose play area has drifted, or who has walked away from
     * their tracking origin, is not: past about an arm's length the figure stops reading as a
     * person leaning and starts reading as a bug.
     */
    private static final float MAX_HEAD_LEAN = 0.6F;

    /** Whether this is the pass that draws your own body in front of your own eyes. */
    public static boolean inWorld() {
        return VRState.running() && VR.settings() != null && VR.settings().renderOwnBody
                && VRCamera.isEyePass(VR.currentPass())
                && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0;
    }

    /**
     * Which way the chest faces, for the body we are about to draw.
     *
     * <p>This exists so that one number can be handed to two places at once. The renderer turns
     * the whole model by the player's own yaw before the model is ever asked to pose itself; the
     * poser then places the arms in a frame turned by the yaw the headset implies. If those two
     * disagree -- and in a headset they always do, because the player's yaw is written from the
     * controller -- the arms swing round the body instead of hanging off it. So the renderer is
     * told to use this one, and the poser uses it too, and there is nothing left to disagree
     * about.
     */
    public static Float selfBodyYaw() {
        IRoomscaleAdapter room = VR.roomScale();
        if (room == null || !room.isHMDTracking()) {
            return null;
        }
        Vec3 look = room.getHMDDir_World();
        if (look == null) {
            return null;
        }
        if (VR.settings() != null && VR.settings().seated) {
            return yawOf(look);
        }
        Vec3 main = room.getControllerPos_World(0);
        Vec3 off = room.getControllerPos_World(1);
        if (main == null || off == null) {
            return yawOf(look);
        }
        boolean reversed = VR.settings() != null && VR.settings().vrReverseHands;
        return bodyYaw(reversed ? main : off, reversed ? off : main, look);
    }

    /** Whether this model is the body the renderer draws, rather than a layer over it. */
    private static boolean isBodyOf(Entity entity, ModelBiped model) {
        Object renderer = net.minecraft.client.renderer.entity.RenderManager.instance
                .getEntityRenderObject(entity);
        return renderer instanceof net.minecraft.client.renderer.entity.RenderPlayer
                && ((net.minecraft.client.renderer.entity.RenderPlayer) renderer)
                        .modelBipedMain == model;
    }

    private static void useBones(ModelBiped model, boolean wanted) {
        VRArmBones bones = BONES.get(model);
        if (wanted && bones == null) {
            bones = new VRArmBones(model);
            BONES.put(model, bones);
        }
        if (bones != null) {
            bones.active = wanted;
        }
        if (!wanted) {
            if (model.bipedRightArm != null) {
                model.bipedRightArm.isHidden = false;
            }
            if (model.bipedLeftArm != null) {
                model.bipedLeftArm.isHidden = false;
            }
        }
    }

    private static void hideHead(ModelBiped model, boolean hidden) {
        if (model.bipedHead != null) {
            model.bipedHead.isHidden = hidden;
        }
        if (model.bipedHeadwear != null) {
            model.bipedHeadwear.isHidden = hidden;
        }
    }

    public static void beginInventory() {
        inventory = true;
    }

    public static void endInventory() {
        inventory = false;
    }

    /**
     * Poses a biped, if this is somebody whose pose we know.
     *
     * <p>Called for the armour models as well as the body, which is what makes a helmet turn with
     * the head rather than hanging in the air where the head used to be.
     */
    public static void pose(ModelBiped model, Entity entity) {
        if (!(entity instanceof EntityPlayer)) {
            putBack(model);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (entity == mc.thePlayer) {
            boolean own = inventory || inWorld();
            if (own && VRState.running()) {
                remember(model);
                putBack(model);
                // Looking down at yourself, the head is between your eyes and everything else.
                // Hiding it is what every version of Vivecraft does, and the alternative -- a
                // head somewhere you can see it -- would mean your own face in the way.
                hideHead(model, inWorld());
                // Two-piece arms only on the body itself. A suit of armour is its own model of
                // the same kind, and it follows the shoulder rather than growing an elbow.
                useBones(model, inWorld() && isBodyOf(entity, model));
                poseFromLive(model, entity);
            } else {
                putBack(model);
                hideHead(model, false);
                useBones(model, false);
            }
            return;
        }
        useBones(model, false);
        hideHead(model, false);
        VRPose pose = VRPlayers.get(entity.getEntityId(),
                lol.gzmc.vivecraftx.ClientEvents.partialTicks());
        if (pose == null) {
            putBack(model);
            return;
        }
        remember(model);
        putBack(model);
        poseFromNetwork(model, entity, pose);
    }

    /**
     * Where each part of a model sits when nobody has moved it.
     *
     * <p>One model object is shared by every player on screen, and the game only ever sets the
     * angles of its parts, never their positions -- those are decided once when the model is
     * built. So moving a shoulder for a player in VR moves it for the next player drawn with the
     * same model too, and for every mob that borrows it, until the game is restarted. The
     * original positions are kept here the first time a model is touched and put back before
     * each use.
     *
     * <p>Weakly, so a model belonging to a resource pack that has been reloaded away is not held
     * alive by this.
     */
    private static final Map<ModelBiped, float[]> ORIGINALS =
            new WeakHashMap<ModelBiped, float[]>();

    private static final ModelRenderer[] MOVED = new ModelRenderer[5];

    private static ModelRenderer[] movedParts(ModelBiped model) {
        MOVED[0] = model.bipedHead;
        MOVED[1] = model.bipedHeadwear;
        MOVED[2] = model.bipedBody;
        MOVED[3] = model.bipedRightArm;
        MOVED[4] = model.bipedLeftArm;
        return MOVED;
    }

    private static void remember(ModelBiped model) {
        if (ORIGINALS.containsKey(model)) {
            return;
        }
        ModelRenderer[] parts = movedParts(model);
        float[] saved = new float[parts.length * 3];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null) {
                continue;
            }
            saved[i * 3] = parts[i].rotationPointX;
            saved[i * 3 + 1] = parts[i].rotationPointY;
            saved[i * 3 + 2] = parts[i].rotationPointZ;
        }
        ORIGINALS.put(model, saved);
    }

    private static void putBack(ModelBiped model) {
        float[] saved = ORIGINALS.get(model);
        if (saved == null) {
            return;
        }
        ModelRenderer[] parts = movedParts(model);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null) {
                continue;
            }
            parts[i].rotationPointX = saved[i * 3];
            parts[i].rotationPointY = saved[i * 3 + 1];
            parts[i].rotationPointZ = saved[i * 3 + 2];
        }
    }

    /** The local player, straight from the runtime, with no round trip and nothing to smooth. */
    private static void poseFromLive(ModelBiped model, Entity entity) {
        IRoomscaleAdapter room = VR.roomScale();
        if (room == null || !room.isHMDTracking()) {
            return;
        }
        Vec3 origin = Vec3.createVectorHelper(entity.posX,
                feetOf(entity) + MODEL_ORIGIN_HEIGHT, entity.posZ);
        Vec3 head = room.getHMDPos_World();
        Vec3 main = room.getControllerPos_World(0);
        Vec3 off = room.getControllerPos_World(1);
        if (head == null || main == null || off == null) {
            return;
        }
        boolean reversed = VR.settings() != null && VR.settings().vrReverseHands;
        // Our own figure is posed even when we are seated: it is the one case where the
        // controllers being on the desk is still worth showing, because it is us looking at it.
        apply(model, origin, head, room.getHMDDir_World(), main, off, 1F, reversed, false);
    }

    /**
     * Somebody else, from what their client last told us.
     *
     * <p>Their offsets arrive measured from their own feet, which is the one thing both ends can
     * agree on: the body position itself is interpolated differently at each end and is always a
     * little stale, so an absolute head position would swim about as the body caught up with it.
     */
    private static void poseFromNetwork(ModelBiped model, Entity entity, VRPose pose) {
        Vec3 origin = Vec3.createVectorHelper(0, MODEL_ORIGIN_HEIGHT, 0);
        Vec3 head = pose.positions[0];
        Vec3 main = pose.positions[1];
        Vec3 off = pose.positions[2];
        if (head == null || main == null || off == null) {
            return;
        }
        Vec3 look = forwardOf(pose.rotations[0]);
        float scale = pose.worldScale <= 0F ? 1F : pose.worldScale;
        apply(model, origin, head, look, main, off, scale, pose.reversed, pose.seated);
    }

    /**
     * The posing itself, in whichever space the caller has handed us.
     *
     * <p>The caller gives an origin and three points in the same frame as that origin, so the one
     * routine serves both the live poses, which are in world coordinates, and the received ones,
     * which are offsets from somebody's feet.
     */
    private static void apply(ModelBiped model, Vec3 origin, Vec3 head, Vec3 look, Vec3 main,
            Vec3 off, float worldScale, boolean reversed, boolean seated) {
        // Which hand is which. The input layer swaps the two for a left-handed player at the
        // source, so that everything on their own machine can stay ignorant of the setting --
        // but the swap happened there, and here we are drawing a body with a definite left and
        // right, so it has to be undone.
        Vec3 right = reversed ? off : main;
        Vec3 left = reversed ? main : off;
        // Seated, the hands say nothing about which way the chest faces, so the head has to.
        float bodyYaw = seated ? yawOf(look) : bodyYaw(left, right, look);

        // The head turns relative to the body, so what matters is the difference between where
        // the body faces and where the headset points -- not either on its own. The figure itself
        // keeps whatever yaw the game gave it, which for the inventory is the mouse and in the
        // world is the direction of travel.
        float headYaw = (float) Math.toRadians(
                MathHelper.wrapAngleTo180_float(yawOf(look) - bodyYaw));
        float headPitch = (float) Math.toRadians(-pitchOf(look));
        setAngles(model.bipedHead, headPitch, headYaw);
        setAngles(model.bipedHeadwear, headPitch, headYaw);

        // Leaning. The head goes where the headset is and the chest follows it sideways, so a
        // player peering round a corner looks like one rather than like a head on a stick.
        if (seated) {
            // Nothing below applies: a seated player has not moved, their hands are on a desk,
            // and the figure should stand as the game would stand it.
            return;
        }

        float[] headModel = toModelSpace(head, origin, bodyYaw, worldScale);
        float leanX = clampLean(headModel[0]);
        float leanZ = clampLean(headModel[2]);
        lean(model.bipedHead, leanX, leanZ);
        lean(model.bipedHeadwear, leanX, leanZ);
        lean(model.bipedBody, leanX * 0.5F, leanZ * 0.5F);

        VRArmBones bones = BONES.get(model);
        VRArmBones use = bones != null && bones.active ? bones : null;
        poseArm(model.bipedRightArm, -5F, right, origin, bodyYaw, worldScale, true, use);
        poseArm(model.bipedLeftArm, 5F, left, origin, bodyYaw, worldScale, false, use);
    }

    private static void lean(ModelRenderer part, float x, float z) {
        if (part != null) {
            part.rotationPointX += x;
            part.rotationPointZ += z;
        }
    }

    private static float clampLean(float value) {
        float limit = MAX_HEAD_LEAN * UNITS_PER_BLOCK;
        return value < -limit ? -limit : value > limit ? limit : value;
    }

    /**
     * Where this entity is standing, as opposed to where it is.
     *
     * <p>The bounding box, because it is the one that is true: an entity's own position is a
     * way up its body on this version, and the amount varies with what is standing where.
     */
    private static double feetOf(Entity entity) {
        return entity.boundingBox != null ? entity.boundingBox.minY : entity.posY;
    }

    private static void setAngles(ModelRenderer part, float x, float y) {
        if (part == null) {
            return;
        }
        part.rotateAngleX = x;
        part.rotateAngleY = y;
        part.rotateAngleZ = 0F;
    }

    /**
     * Which way the body is facing, as the shoulders rather than the head.
     *
     * <p>Taken from the line between the two controllers, turned a quarter circle: you can look
     * over your shoulder without turning your body, and a figure whose chest snapped round every
     * time its owner glanced sideways would be wrong in a way that is immediately obvious. Falls
     * back to the head when the hands are together, which is the best guess available.
     */
    private static float bodyYaw(Vec3 left, Vec3 right, Vec3 look) {
        double dx = left.xCoord - right.xCoord;
        double dz = left.zCoord - right.zCoord;
        if (dx * dx + dz * dz < 1.0E-4D) {
            return yawOf(look);
        }
        // A quarter turn from the shoulder line is the direction the chest faces. Turning the
        // vector from the right hand to the left one that way, in the game's own convention
        // where a yaw of zero faces positive Z, comes out as this.
        return (float) Math.toDegrees(Math.atan2(dz, dx));
    }

    private static float yawOf(Vec3 dir) {
        return (float) Math.toDegrees(Math.atan2(-dir.xCoord, dir.zCoord));
    }

    private static float pitchOf(Vec3 dir) {
        double length = dir.lengthVector();
        return length < 1.0E-6D ? 0F
                : (float) Math.toDegrees(Math.asin(dir.yCoord / length));
    }

    /**
     * Which way a sent rotation is facing.
     *
     * <p>The quaternion is stored w first, and the runtime's idea of forward is negative Z, so
     * this is that axis turned by it. Written out rather than built into a matrix because one
     * vector is all anybody here wants.
     */
    private static Vec3 forwardOf(float[] q) {
        float w = q[0];
        float x = q[1];
        float y = q[2];
        float z = q[3];
        return Vec3.createVectorHelper(
                -2.0D * (x * z + w * y),
                -2.0D * (y * z - w * x),
                -(1.0D - 2.0D * (x * x + y * y)));
    }

    /**
     * A point near the player, in the units and axes a model part is positioned in.
     *
     * <p>Model space counts sixteen to the block, points its Y downwards and its Z backwards, and
     * is turned to face wherever the body is facing. The signs here are what does all three at
     * once.
     */
    private static float[] toModelSpace(Vec3 point, Vec3 origin, float bodyYaw, float worldScale) {
        double a = Math.toRadians(bodyYaw);
        double cos = Math.cos(a);
        double sin = Math.sin(a);
        double dx = point.xCoord - origin.xCoord;
        double dy = point.yCoord - origin.yCoord;
        double dz = point.zCoord - origin.zCoord;
        // Whoever sent this had the world at their own scale, so their metre is not ours.
        double units = UNITS_PER_BLOCK / (worldScale <= 0F ? 1F : worldScale);
        return new float[] {
                (float) ((dx * cos + dz * sin) * units),
                (float) (-dy * units),
                (float) (-(dz * cos - dx * sin) * units)
        };
    }

    /**
     * Points one arm at the controller it belongs to.
     *
     * <p>A 1.7.10 arm is a single rigid box with no elbow, so it cannot both start at the
     * shoulder and end at the hand unless it stretches. Modern Vivecraft stretches it -- slides
     * the shoulder along towards the hand once the hand is further away than an arm is long --
     * and so does this, because a hand that is not where the hand is defeats the point.
     *
     * @param shoulderX the model X of this arm, which is also which side of the body it is on
     */
    private static void poseArm(ModelRenderer arm, float shoulderX, Vec3 controller, Vec3 origin,
            float bodyYaw, float worldScale, boolean right, VRArmBones use) {
        if (arm == null || controller == null) {
            return;
        }
        float[] hand = toModelSpace(controller, origin, bodyYaw, worldScale);

        // Where this arm hangs from on this model, which is not the same on every model.
        shoulderX = arm.rotationPointX;
        float shoulderY = arm.rotationPointY;
        float toX = hand[0] - shoulderX;
        float toY = hand[1] - shoulderY;
        float toZ = hand[2];
        float distance = MathHelper.sqrt_float(toX * toX + toY * toY + toZ * toZ);
        if (distance < 1.0E-4F) {
            return;
        }
        float nx = toX / distance;
        float ny = toY / distance;
        float nz = toZ / distance;

        if (use == null) {
            // One straight piece and nothing to bend: the best it can do is point at the hand
            // and, past its own length, go and meet it.
            if (distance > ARM_LENGTH) {
                float reach = distance - ARM_LENGTH;
                arm.rotationPointX += nx * reach;
                arm.rotationPointY += ny * reach;
                arm.rotationPointZ += nz * reach;
            }
            aim(arm, nx, ny, nz);
            return;
        }

        // Where the elbow goes. Halfway between shoulder and hand, pushed out sideways by
        // however much slack the two bones have -- which is what a bent arm is. Pushed
        // downwards, because that is where an elbow hangs; the exact direction is the part of
        // "down" that is square to the arm, so the elbow never tries to bend through it.
        float ex = (shoulderX + hand[0]) * 0.5F;
        float ey = (shoulderY + hand[1]) * 0.5F;
        float ez = hand[2] * 0.5F;
        if (distance < ARM_LENGTH) {
            float slack = MathHelper.sqrt_float((ARM_LENGTH * ARM_LENGTH - distance * distance)
                    * 0.25F);
            // "down" in model space is +Y, squared off against the arm's own direction
            float dot = ny;
            float hx = -dot * nx;
            float hy = 1F - dot * ny;
            float hz = -dot * nz;
            float len = MathHelper.sqrt_float(hx * hx + hy * hy + hz * hz);
            if (len > 1.0E-4F) {
                ex += hx / len * slack;
                ey += hy / len * slack;
                ez += hz / len * slack;
            }
        }

        ModelRenderer upper = right ? use.rightUpper : use.leftUpper;
        ModelRenderer lower = right ? use.rightLower : use.leftLower;

        upper.rotationPointX = arm.rotationPointX;
        upper.rotationPointY = arm.rotationPointY;
        upper.rotationPointZ = arm.rotationPointZ;
        aimAt(upper, ex - shoulderX, ey - shoulderY, ez);

        lower.rotationPointX = ex;
        lower.rotationPointY = ey;
        lower.rotationPointZ = ez;
        aimAt(lower, hand[0] - ex, hand[1] - ey, hand[2] - ez);

        // The vanilla arm is hidden but still aimed, along the upper piece: the armour is its
        // own model copying these angles, and a sleeve on an arm nobody aimed would hang where
        // the arm used to be.
        aim(arm, nx, ny, nz);
        arm.isHidden = true;
    }

    private static void aimAt(ModelRenderer part, float dx, float dy, float dz) {
        float len = MathHelper.sqrt_float(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4F) {
            return;
        }
        aim(part, dx / len, dy / len, dz / len);
    }

    /**
     * Points a part along a direction.
     *
     * <p>A limb hangs along the part's own +Y, and the game turns a part by Z, then Y, then X.
     * Leaving Y at zero and using X for the forward swing and Z for the sideways one is both the
     * shape vanilla animates in and the decomposition that twists the limb least, which matters
     * because a skin is not symmetrical.
     */
    private static void aim(ModelRenderer part, float nx, float ny, float nz) {
        part.rotateAngleX = (float) Math.asin(clamp(nz));
        part.rotateAngleY = 0F;
        part.rotateAngleZ = (float) Math.atan2(-nx, ny);
    }

    private static float clamp(float value) {
        return value < -1F ? -1F : value > 1F ? 1F : value;
    }
}
