package lol.gzmc.vivecraftx.mixins;

import com.mtbs3d.minecrift.control.VRInputAction;
import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.settings.VRSettings;
import com.mtbs3d.minecrift.utils.Vector2;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.util.KeyBindingUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a thumbstick move the player.
 *
 * <p>Vanilla movement reads key presses, and a thumbstick is not a key. The controller profiles
 * bind the stick to an axis -- one action carrying both directions -- because that is the only way
 * a stick can also steer, and nothing in the game knows how to read it. So the stick did nothing
 * at all, which is the whole of "I tried to walk with the stick and could not".
 *
 * <p>Extending {@code MovementInput} rather than shadowing its fields: they are declared there and
 * inherited by the class being mixed into, and a mixin cannot shadow an inherited field. Naming
 * the real superclass gives direct access to them and costs nothing, since the mixin is never
 * instantiated.
 */
@Mixin(MovementInputFromOptions.class)
public abstract class MixinMovementInput extends MovementInput {

    /** True while the axis is what last set the movement, so it can be cleared when it stops. */
    private boolean vivecraftx$movementSetByAxis;

    /**
     * Overrides the key-derived movement with the stick, when the stick is being pushed.
     *
     * <p>At the end of the method rather than in place of it, so that walking on the keyboard
     * keeps working untouched and only an actual push of the stick takes over.
     */
    @Inject(method = "updatePlayerMoveState", at = @At("TAIL"))
    private void vivecraftx$moveByAxis(CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        VRSettings settings = VR.settings();
        if (!VRState.running() || settings == null) {
            return;
        }

        // Crouching physically makes the player crouch. The detector worked and its answer was
        // read in one place only -- to stop the view being dropped a second time -- so bending
        // your knees lowered the camera and the character never sneaked: no slowdown, no ledge
        // protection, the name tag still showing, and no fitting through a one-block gap. Both
        // references close the loop here. Vanilla has already scaled the key-derived movement by
        // the time this runs, so where we are the ones turning sneaking on, the same reduction is
        // applied; if the stick replaces those values below, the test further down scales the new
        // ones instead and nothing is scaled twice.
        if (!this.sneak && mc.currentScreen == null && VR.sneakTracker() != null
                && VR.sneakTracker().sneakOverride) {
            this.sneak = true;
            this.moveStrafe *= 0.3F;
            this.moveForward *= 0.3F;
        }

        if (settings.seated || mc.currentScreen != null || MCOpenVR.keyboardShowing) {
            vivecraftx$letGoOfAxisKeys(mc);
            return;
        }
        // Climbing is done with the hands; letting the stick move you as well would fight it.
        if (VR.climbTracker() != null && mc.thePlayer != null
                && VR.climbTracker().isGrabbingLadder() && !mc.thePlayer.isInWater()) {
            vivecraftx$letGoOfAxisKeys(mc);
            return;
        }

        VRInputAction strafe = MCOpenVR.getInputAction(MCOpenVR.keyFreeMoveStrafe);
        VRInputAction rotate = MCOpenVR.getInputAction(MCOpenVR.keyFreeMoveRotate);
        if (strafe == null || rotate == null) {
            vivecraftx$letGoOfAxisKeys(mc);
            return;
        }
        Vector2 strafeAxis = strafe.getAxis2DUseTracked();
        Vector2 rotateAxis = rotate.getAxis2DUseTracked();

        // Past the deadzone, or not pushed at all. A stick that rests a little off centre
        // would otherwise walk you slowly into a wall for as long as you were not holding it.
        float dead = settings.vrJoystickDeadzone;
        strafeAxis = deaden(strafeAxis, dead);
        rotateAxis = deaden(rotateAxis, dead);

        boolean set = false;
        float forwardAxis = 0;

        if (strafeAxis.getX() != 0 || strafeAxis.getY() != 0) {
            set = true;
            forwardAxis = strafeAxis.getY();
            if (settings.analogMovement) {
                this.moveForward = strafeAxis.getY();
                this.moveStrafe = -strafeAxis.getX();
            } else {
                this.moveForward = digital(strafeAxis.getY());
                this.moveStrafe = digital(-strafeAxis.getX());
            }
        } else if (rotateAxis.getY() != 0) {
            // the other stick steers, but its forward axis still walks
            set = true;
            forwardAxis = rotateAxis.getY();
            this.moveForward = settings.analogMovement ? rotateAxis.getY()
                                                       : digital(rotateAxis.getY());
        }

        if (!set) {
            vivecraftx$letGoOfAxisKeys(mc);
            return;
        }
        this.vivecraftx$movementSetByAxis = true;

        // Pushed all the way forward sprints, so sprinting needs no button of its own. Below that,
        // the range is stretched back out so full walking speed is still reachable.
        if (settings.autoSprint && mc.thePlayer != null) {
            if (forwardAxis >= settings.autoSprintThreshold) {
                mc.thePlayer.setSprinting(true);
                this.moveForward = 1;
            } else if (this.moveForward > 0 && settings.analogMovement) {
                this.moveForward = this.moveForward / settings.autoSprintThreshold;
            }
        }

        // Vanilla applied this to the key-derived values before we replaced them.
        if (this.sneak) {
            this.moveStrafe *= 0.3F;
            this.moveForward *= 0.3F;
        }

        // Anything that asks the key bindings rather than this object -- the teleport tracker, and
        // whatever other mods are watching -- has to see the same answer.
        KeyBindingUtil.setState(mc.gameSettings.keyBindForward, this.moveForward > 0);
        KeyBindingUtil.setState(mc.gameSettings.keyBindBack, this.moveForward < 0);
        KeyBindingUtil.setState(mc.gameSettings.keyBindLeft, this.moveStrafe > 0);
        KeyBindingUtil.setState(mc.gameSettings.keyBindRight, this.moveStrafe < 0);
    }

    /**
     * Lets go of the keys the stick was standing in for.
     *
     * <p>Reached from every way out of the method above, not only from the stick returning to
     * centre. Grabbing a ladder, opening a screen or raising the keyboard with the stick still
     * pushed used to leave those keys held: the game went on reading them, so letting go of the
     * ladder walked you off in whatever direction you had last been pushing.
     */
    private void vivecraftx$letGoOfAxisKeys(Minecraft mc) {
        if (!this.vivecraftx$movementSetByAxis) {
            return;
        }
        KeyBindingUtil.setState(mc.gameSettings.keyBindForward, false);
        KeyBindingUtil.setState(mc.gameSettings.keyBindBack, false);
        KeyBindingUtil.setState(mc.gameSettings.keyBindLeft, false);
        KeyBindingUtil.setState(mc.gameSettings.keyBindRight, false);
        this.vivecraftx$movementSetByAxis = false;
    }

    /** Zeroes an axis that has not left the middle far enough to mean anything. */
    private static Vector2 deaden(Vector2 axis, float deadzone) {
        float x = Math.abs(axis.getX()) > deadzone ? axis.getX() : 0F;
        float y = Math.abs(axis.getY()) > deadzone ? axis.getY() : 0F;
        return new Vector2(x, y);
    }

    /** All or nothing past halfway, which is how a stick stands in for a key. */
    private static float digital(float value) {
        if (value > 0.5F) {
            return 1;
        }
        if (value < -0.5F) {
            return -1;
        }
        return 0;
    }
}
