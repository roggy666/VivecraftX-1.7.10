package lol.gzmc.vivecraftx.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import com.mtbs3d.minecrift.api.IRoomscaleAdapter;
import com.mtbs3d.minecrift.provider.OpenVRPlayer;
import com.mtbs3d.minecrift.settings.VRSettings;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.VRRenderState;
import lol.gzmc.vivecraftx.util.VanillaExt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the play area's origin in step with the player.
 *
 * <p>This is the hinge the whole VR camera hangs on. Every VR position is "somewhere in the play
 * area, plus where the play area is in the world", and the camera then translates the world by the
 * difference between the player entity and the eye. If the origin is not maintained, that
 * difference becomes the player's absolute position and the world is pushed hundreds of blocks
 * away.
 *
 * <p>Vivecraft put these as overrides on {@code EntityPlayerSP}. Vanilla does not declare any of
 * them there -- they are inherited from {@code Entity} -- so a mod has to come at them from
 * {@code Entity} instead and check it is looking at the client's own player. That check is a
 * reference comparison and is the first thing each hook does, so every other entity in the world
 * pays one comparison.
 */
@Mixin(Entity.class)
public abstract class MixinEntityRoomScale {

    @Shadow
    public double posX;

    @Shadow
    public double posY;

    @Shadow
    public double posZ;

    @Shadow
    public double motionX;

    @Shadow
    public double motionZ;

    @Shadow
    public boolean onGround;

    @Shadow
    public Entity ridingEntity;

    @Shadow
    public AxisAlignedBB boundingBox;

    @Shadow
    public abstract boolean isRiding();

    @Shadow
    public abstract boolean isSneaking();

    @Shadow
    public double motionY;

    @Shadow
    public boolean inWater;

    /**
     * Whether this entity is the player whose head is in the headset.
     *
     * <p>Also the guard for VR being up at all, so every hook below can assume
     * {@code VR.player()} is live.
     */
    private boolean vivecraftx$isVrPlayer() {
        if (!VRState.running() || VR.player() == null) {
            return false;
        }
        Object self = this;
        return self instanceof EntityPlayerSP && self == Minecraft.getMinecraft().thePlayer;
    }

    /**
     * The server telling us where we are. This is what first gives the play area a position, on
     * joining a world, and what corrects it after a teleport.
     *
     * <p>The first one also settles the player's position properly: until it arrives the entity is
     * at the origin, and Vivecraft marked that with its own flag rather than trusting the
     * coordinates, because spawning legitimately at (0,0,0) is possible.
     */
    @Inject(method = "setPositionAndRotation", at = @At("TAIL"))
    private void vivecraftx$onServerPosition(double x, double y, double z, float yaw, float pitch,
            CallbackInfo ci) {
        if (!vivecraftx$isVrPlayer()) {
            return;
        }
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        OpenVRPlayer.get().snapRoomOriginToPlayerEntity(player, true, false, 1);

        if (!VanillaExt.isInitFromServer(player)) {
            // The height is lowered by the eye offset on the way in, because the two methods
            // disagree about what their y means: the one we are standing at the end of takes it
            // as the entity's own origin, and the one called here adds the eye offset to whatever
            // it is given. Handing it this method's y unchanged therefore placed the player a
            // metre and a half above where the server had just put them -- on every join, every
            // respawn and every dimension change -- and the position reply that goes out two
            // lines later then failed the server's own "did you end up where I said" test, so it
            // ignored the player's movement until they had finished falling back down.
            player.setLocationAndAngles(x, y - (double) player.yOffset, z, yaw, pitch);
            VRRenderState.interpolatedPlayerPos = Vec3.createVectorHelper(x, y, z);
            VanillaExt.setInitFromServer(player, true);
        }
    }

    /**
     * A move large enough that interpolating through it would be wrong: a respawn, a dimension
     * change, a teleport. The play area is moved outright rather than eased.
     *
     * <p>Skipped until the server's first position has arrived, because
     * {@link #vivecraftx$onServerPosition} calls this method itself to settle that first position
     * and the origin is about to be set by the caller anyway.
     */
    @Inject(method = "setLocationAndAngles", at = @At("TAIL"))
    private void vivecraftx$onRelocate(double x, double y, double z, float yaw, float pitch,
            CallbackInfo ci) {
        if (!vivecraftx$isVrPlayer()) {
            return;
        }
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        if (!VanillaExt.isInitFromServer(player)) {
            return;
        }
        OpenVRPlayer.get().snapRoomOriginToPlayerEntity(player, true, false, 1);
    }

    /**
     * Carried by something else: a boat, a horse, a minecart.
     *
     * <p>The play area has to follow the vehicle, keeping the player's offset within it, or they
     * would be left standing where the vehicle was. Only the vehicle's position is taken, not its
     * rotation -- a cart going round a bend should not spin the room.
     */
    /** The vehicle's facing last time it moved, for turning the play area with it. */
    @org.spongepowered.asm.mixin.Unique
    private float vivecraftx$lastVehicleYaw = Float.NaN;

    @Inject(method = "setPosition", at = @At("TAIL"))
    private void vivecraftx$onMountedMove(double x, double y, double z, CallbackInfo ci) {
        if (!vivecraftx$isVrPlayer() || !isRiding() || ridingEntity == null) {
            vivecraftx$lastVehicleYaw = Float.NaN;
            return;
        }
        Vec3 origin = OpenVRPlayer.get().getRoomOriginPos_World();
        Vec3 head = OpenVRPlayer.get().getHMDPos_World();
        if (origin == null || head == null) {
            return;
        }

        // Turning with the vehicle. Off by default, and rightly: a minecart going round a bend
        // turns your whole world under you, which is exactly the sensation a rollercoaster gives
        // and exactly what makes some people ill. For everyone else it is what makes a cart feel
        // like a ride rather than a sideways slide.
        if (VR.settings().vehicleRotation) {
            float yaw = ridingEntity.rotationYaw;
            if (!Float.isNaN(vivecraftx$lastVehicleYaw)) {
                float turned = net.minecraft.util.MathHelper.wrapAngleTo180_float(
                        yaw - vivecraftx$lastVehicleYaw);
                if (Math.abs(turned) > 0.01F) {
                    VR.settings().vrWorldRotation =
                            (VR.settings().vrWorldRotation - turned) % 360F;
                }
            }
            vivecraftx$lastVehicleYaw = yaw;
        } else {
            vivecraftx$lastVehicleYaw = Float.NaN;
        }
        OpenVRPlayer.get().setRoomOrigin(
                ridingEntity.posX + (origin.xCoord - head.xCoord),
                ridingEntity.posY + ridingEntity.getMountedYOffset(),
                ridingEntity.posZ + (origin.zCoord - head.zCoord),
                false, false);
    }

    /**
     * Sends the player where the hand is pointing rather than where the entity is facing.
     *
     * <p>Vanilla turns the movement vector by the entity's own yaw, which in a flat game is the
     * direction you are looking. In VR nothing drives that yaw from the headset, so the entity
     * faces wherever the mouse last left it and pushing the stick forward walks off in an
     * unrelated direction. That is not a near miss; it is the whole reason stick movement felt
     * random.
     *
     * <p>Which direction counts as forward is the player's choice. Pointing with the off hand is
     * the default and the reason room-scale works at all: it lets you walk one way while looking
     * another, which is the thing a flat game cannot do. Following the headset instead is offered
     * for people who find that confusing.
     *
     * <p>Pitch is only taken into account while flying or swimming, where up and down are real
     * directions to travel in. On the ground, aiming the controller down must not slow you to a
     * crawl.
     */
    @WrapMethod(method = "moveFlying")
    private void vivecraftx$moveWhereAimed(float strafe, float forward, float friction,
            Operation<Void> original) {
        if (!vivecraftx$isVrPlayer() || VR.settings() == null) {
            original.call(strafe, forward, friction);
            return;
        }
        // On teleport locomotion the stick does not drive the body at all, and both references
        // open this method by saying so. Without it, aiming a teleport -- which is done by
        // pushing the stick, and so sets the same movement input -- quietly accumulated momentum
        // the whole time: walking about your own room then did nothing for the few ticks that
        // momentum took to decay after every single aim, because the room-scale mover stands
        // down while the entity is still sliding, and auto-sprint came on for a player who was
        // not moving.
        if (!VR.player().getFreeMoveMode()) {
            return;
        }
        float magnitude = strafe * strafe + forward * forward;
        if (magnitude < 1.0E-4F) {
            return;
        }
        // Vanilla's normalisation: a diagonal is not faster than a straight line, and friction
        // sets the step size.
        magnitude = MathHelper.sqrt_float(magnitude);
        if (magnitude < 1.0F) {
            magnitude = 1.0F;
        }
        magnitude = friction / magnitude;
        // The player's own speed setting, which had a slider in the menu and was read by
        // nothing. Walking in VR at full vanilla speed is quick enough to be unpleasant for
        // some people, and this is how they were supposed to be able to say so.
        magnitude *= VR.settings().movementSpeedMultiplier;
        strafe *= magnitude;
        forward *= magnitude;

        Vec3 direction = Vec3.createVectorHelper(strafe, 0, forward);
        IRoomscaleAdapter room = VR.roomScale();
        VRSettings settings = VR.settings();
        int mode = settings.vrFreeMoveMode;

        if (settings.seated) {
            direction.rotateAroundY((float) -Math.toRadians(room.getControllerOffhandYaw_World()));
        } else {
            if (this.isRiding() || ((EntityPlayerSP) (Object) this).capabilities.isFlying
                    || this.inWater) {
                if (mode == VRSettings.FREEMOVE_HMD || mode == VRSettings.FREEMOVE_RUNINPLACE) {
                    direction.rotateAroundX((float) Math.toRadians(room.getHMDPitch_World()));
                } else {
                    direction.rotateAroundX(
                            (float) Math.toRadians(room.getControllerOffhandPitch_World()));
                }
            }
            if (mode == VRSettings.FREEMOVE_HMD) {
                direction.rotateAroundY((float) -Math.toRadians(room.getHMDYaw_World()));
            } else if (mode == VRSettings.FREEMOVE_RUNINPLACE) {
                direction.rotateAroundY((float) -Math.toRadians(VR.runTracker().getYaw()));
                double speed = VR.runTracker().getSpeed();
                direction.xCoord *= speed;
                direction.yCoord *= speed;
                direction.zCoord *= speed;
            } else {
                direction.rotateAroundY(
                        (float) -Math.toRadians(room.getControllerOffhandYaw_World()));
            }
        }

        // Inertia is a comfort setting: easing into a step is gentler than snapping into it, and
        // it only applies on the ground, where that easing is what you would feel.
        double addFactor = 1.0D;
        boolean flying = ((EntityPlayerSP) (Object) this).capabilities.isFlying;
        if (!flying && !this.inWater) {
            addFactor = VRSettings.getInertiaAddFactor(settings.inertiaFactor);
        }
        this.motionX += direction.xCoord * addFactor;
        this.motionZ += direction.zCoord * addFactor;
        // Flight needs the vertical push amplified, or aiming up barely climbs.
        this.motionY += direction.yCoord * (flying ? 5.0D : 1.0D);
    }

    /**
     * Walking, falling, and being pushed.
     *
     * <p>Three outcomes, and which one applies is the difference between room-scale and a flat
     * game. With free movement on and the player actually moving, the entity moves as usual and
     * the play area is carried along by the same amount, so the player stays where they are
     * standing in their room. Otherwise the entity is allowed to fall but not to walk, because in
     * room-scale the player's own feet move them and the entity follows from
     * {@code OpenVRPlayer.onLivingUpdate}; letting the keyboard move it too would fight that.
     * Where neither applies the entity is pinned and reported as grounded.
     *
     * <p>Wrapped rather than injected because two of the three outcomes need the original with
     * different arguments and the third needs it not to run at all.
     */
    @WrapMethod(method = "moveEntity")
    private void vivecraftx$roomScaleMove(double x, double y, double z, Operation<Void> original) {
        if (!vivecraftx$isVrPlayer()) {
            original.call(x, y, z);
            return;
        }
        if (isRiding()) {
            // the vehicle moves us; setPosition above keeps the room with it
            return;
        }

        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        boolean freeMove = OpenVRPlayer.get().getFreeMoveMode();
        boolean allowFalling = freeMove
                || (VR.settings().simulateFalling && !player.isOnLadder() && !isSneaking())
                || VR.climbTracker().isActive(player);

        Vec3 origin = OpenVRPlayer.get().getRoomOriginPos_World();

        if (freeMove && (player.moveForward != 0 || Math.abs(motionX) > 0.01
                || Math.abs(motionZ) > 0.01)) {
            double offsetX = origin.xCoord - posX;
            double offsetZ = origin.zCoord - posZ;
            original.call(x, y, z);
            OpenVRPlayer.get().setRoomOrigin(posX + offsetX, boundingBox.minY, posZ + offsetZ,
                    false, false);
            return;
        }

        if (allowFalling) {
            original.call(0.0D, y, 0.0D);
            OpenVRPlayer.get().setRoomOrigin(origin.xCoord, boundingBox.minY, origin.zCoord,
                    false, false);
            return;
        }

        this.onGround = true;
    }
}
