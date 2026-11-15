package com.mtbs3d.minecrift.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.utils.KeyboardSimulator;
import com.mtbs3d.minecrift.utils.Vector2;
import com.mtbs3d.minecrift.utils.Vector3;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;

import jopenvr.InputAnalogActionData_t;
import jopenvr.InputDigitalActionData_t;
import jopenvr.JOpenVRLibrary;

import lol.gzmc.vivecraftx.ViveCraftX;
import lol.gzmc.vivecraftx.util.KeyBindingUtil;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

/**
 * One SteamVR action, and the key binding it stands in for.
 *
 * <p>The point of going through SteamVR's action system rather than reading buttons is that the
 * mod stops caring which controller the player owns. It declares what it needs -- "a button for
 * attack", "two axes for walking" -- and SteamVR decides, from a profile the player can edit, which
 * physical input provides it. Reading raw buttons meant a table per controller model inside the
 * mod, and nothing for a model that did not exist when the table was written.
 *
 * <p>Each action can be read as a button or as up to three axes whatever its declared type, with
 * the conversion done here, so a caller that wants a direction does not have to know whether the
 * player bound a thumbstick or two buttons to it.
 */
public class VRInputAction {

    public final KeyBinding keyBinding;
    public final String name;
    public final String requirement;
    public final String type;
    public final VRInputActionSet actionSet;

    private int priority = 0;
    private final boolean[] enabled = new boolean[ControllerType.values().length];
    private final List<KeyListener> listeners = new ArrayList<KeyListener>();
    private ControllerType currentHand = ControllerType.RIGHT;

    /** Only for the "use tracked" axis readers, which keep reporting until the player lets go. */
    private boolean currentlyInUse;

    private long handle;
    private final boolean[] pressed = new boolean[ControllerType.values().length];
    protected final int[] unpressInTicks = new int[ControllerType.values().length];

    private final InputDigitalActionData_t.ByReference[] digitalData =
            new InputDigitalActionData_t.ByReference[ControllerType.values().length];
    private final InputAnalogActionData_t.ByReference[] analogData =
            new InputAnalogActionData_t.ByReference[ControllerType.values().length];

    public VRInputAction(KeyBinding keyBinding, String requirement, String type,
            VRInputActionSet actionSetOverride) {
        this.keyBinding = keyBinding;
        this.requirement = requirement;
        this.type = type;
        this.actionSet = actionSetOverride != null ? actionSetOverride
                : VRInputActionSet.fromKeyBinding(keyBinding);
        // a slash would read as another level of the action path, so one in a binding name has to
        // go; mod bindings are named freely and some of them contain one
        this.name = this.actionSet.name + "/in/" + keyBinding.getKeyDescription().replace('/', '_');

        for (int i = 0; i < ControllerType.values().length; i++) {
            this.enabled[i] = true;
            // JNA is told not to touch these on its own: they are read once per frame at a point
            // we choose, and letting it synchronise them on every field access would mean a
            // native call per read
            this.digitalData[i] = new InputDigitalActionData_t.ByReference();
            this.digitalData[i].setAutoRead(false);
            this.digitalData[i].setAutoWrite(false);
            this.digitalData[i].setAutoSynch(false);
            this.analogData[i] = new InputAnalogActionData_t.ByReference();
            this.analogData[i].setAutoRead(false);
            this.analogData[i].setAutoWrite(false);
            this.analogData[i].setAutoSynch(false);
        }
    }

    public boolean isButtonPressed() {
        if ("boolean".equals(this.type)) {
            return digitalData().bState != 0;
        }
        // an axis counts as pressed past the halfway point, so a binding declared as an axis can
        // still drive something that only understands on and off
        Vector3 axis = getAxis3D(false);
        return Math.abs(axis.getX()) > 0.5F || Math.abs(axis.getY()) > 0.5F
                || Math.abs(axis.getZ()) > 0.5F;
    }

    public boolean isButtonChanged() {
        if ("boolean".equals(this.type)) {
            return digitalData().bChanged != 0;
        }
        // for an axis, "changed" means it crossed the halfway point since the last frame
        Vector3 axis = getAxis3D(false);
        Vector3 delta = getAxis3D(true);
        return (Math.abs(axis.getX() - delta.getX()) > 0.5F) != (Math.abs(axis.getX()) > 0.5F)
                || (Math.abs(axis.getY() - delta.getY()) > 0.5F) != (Math.abs(axis.getY()) > 0.5F)
                || (Math.abs(axis.getZ() - delta.getZ()) > 0.5F) != (Math.abs(axis.getZ()) > 0.5F);
    }

    public float getAxis1D(boolean delta) {
        if ("boolean".equals(this.type)) {
            return digitalToAnalog(delta);
        }
        if (isAnalog()) {
            return delta ? analogData().deltaX : analogData().x;
        }
        return 0.0F;
    }

    public Vector2 getAxis2D(boolean delta) {
        if ("boolean".equals(this.type)) {
            return new Vector2(digitalToAnalog(delta), 0.0F);
        }
        if ("vector1".equals(this.type)) {
            return new Vector2(delta ? analogData().deltaX : analogData().x, 0.0F);
        }
        if ("vector2".equals(this.type) || "vector3".equals(this.type)) {
            return delta ? new Vector2(analogData().deltaX, analogData().deltaY)
                         : new Vector2(analogData().x, analogData().y);
        }
        return new Vector2();
    }

    public Vector3 getAxis3D(boolean delta) {
        if ("boolean".equals(this.type)) {
            return new Vector3(digitalToAnalog(delta), 0.0F, 0.0F);
        }
        if ("vector1".equals(this.type)) {
            return new Vector3(delta ? analogData().deltaX : analogData().x, 0.0F, 0.0F);
        }
        if ("vector2".equals(this.type)) {
            return delta ? new Vector3(analogData().deltaX, analogData().deltaY, 0.0F)
                         : new Vector3(analogData().x, analogData().y, 0.0F);
        }
        if ("vector3".equals(this.type)) {
            return delta ? new Vector3(analogData().deltaX, analogData().deltaY, analogData().deltaZ)
                         : new Vector3(analogData().x, analogData().y, analogData().z);
        }
        return new Vector3();
    }

    private boolean isAnalog() {
        return "vector1".equals(this.type) || "vector2".equals(this.type)
                || "vector3".equals(this.type);
    }

    /**
     * Reads the axis, and keeps reading it after the action is disabled until the player lets go.
     *
     * <p>Without this, disabling an action mid-motion -- which happens the moment a menu opens or
     * a tracker takes over -- leaves the game believing the stick is still where it was. There is
     * no delta variant because a delta across a disabled frame would be meaningless.
     */
    public float getAxis1DUseTracked() {
        if (this.currentlyInUse || isEnabled()) {
            float axis = getAxis1D(false);
            this.currentlyInUse = axis != 0;
            return axis;
        }
        return 0;
    }

    /** As {@link #getAxis1DUseTracked}, in two axes. */
    public Vector2 getAxis2DUseTracked() {
        if (this.currentlyInUse || isEnabled()) {
            Vector2 axis = getAxis2D(false);
            this.currentlyInUse = axis.getX() != 0 || axis.getY() != 0;
            return axis;
        }
        return new Vector2();
    }

    /** As {@link #getAxis1DUseTracked}, in three axes. */
    public Vector3 getAxis3DUseTracked() {
        if (this.currentlyInUse || isEnabled()) {
            Vector3 axis = getAxis3D(false);
            this.currentlyInUse = axis.getX() != 0 || axis.getY() != 0 || axis.getZ() != 0;
            return axis;
        }
        return new Vector3();
    }

    /**
     * A button read as an axis. The delta is signed so a caller can tell a press from a release,
     * which is what lets a button stand in for a scroll wheel.
     */
    private float digitalToAnalog(boolean delta) {
        if (delta) {
            if (digitalData().bChanged != 0) {
                return digitalData().bState != 0 ? 1.0F : -1.0F;
            }
            return 0.0F;
        }
        return digitalData().bState != 0 ? 1.0F : 0.0F;
    }

    /** Which physical input last drove this action, which is how the hand behind it is found. */
    public long getLastOrigin() {
        if ("boolean".equals(this.type)) {
            return digitalData().activeOrigin;
        }
        if (isAnalog()) {
            return analogData().activeOrigin;
        }
        return JOpenVRLibrary.k_ulInvalidInputValueHandle;
    }

    public ControllerType getCurrentHand() {
        return this.currentHand;
    }

    public void setCurrentHand(ControllerType currentHand) {
        this.currentHand = currentHand;
    }

    /** Pulls this frame's state out of the runtime. Called once a frame, before anything reads it. */
    public void readNewData() {
        if ("boolean".equals(this.type)) {
            if (isHanded()) {
                for (ControllerType hand : ControllerType.values()) {
                    readDigitalData(hand);
                }
            } else {
                readDigitalData(null);
            }
        } else if (isAnalog()) {
            if (isHanded()) {
                for (ControllerType hand : ControllerType.values()) {
                    readAnalogData(hand);
                }
            } else {
                readAnalogData(null);
            }
        }
    }

    private void readDigitalData(ControllerType hand) {
        int index = hand != null ? hand.ordinal() : 0;
        long restrict = hand != null ? MCOpenVR.getControllerHandle(hand)
                                     : JOpenVRLibrary.k_ulInvalidInputValueHandle;
        int error = MCOpenVR.vrInput.GetDigitalActionData.apply(this.handle, this.digitalData[index],
                this.digitalData[index].size(), restrict);
        if (error != 0) {
            throw new RuntimeException("Error reading digital data for '" + this.name + "': "
                    + MCOpenVR.getInputError(error));
        }
        this.digitalData[index].read();
    }

    private void readAnalogData(ControllerType hand) {
        int index = hand != null ? hand.ordinal() : 0;
        long restrict = hand != null ? MCOpenVR.getControllerHandle(hand)
                                     : JOpenVRLibrary.k_ulInvalidInputValueHandle;
        int error = MCOpenVR.vrInput.GetAnalogActionData.apply(this.handle, this.analogData[index],
                this.analogData[index].size(), restrict);
        if (error != 0) {
            throw new RuntimeException("Error reading analog data for '" + this.name + "': "
                    + MCOpenVR.getInputError(error));
        }
        this.analogData[index].read();
    }

    private InputDigitalActionData_t digitalData() {
        return this.digitalData[isHanded() ? this.currentHand.ordinal() : 0];
    }

    private InputAnalogActionData_t analogData() {
        return this.analogData[isHanded() ? this.currentHand.ordinal() : 0];
    }

    /** Every physical input currently bound to this action. */
    public List<Long> getOrigins() {
        if (MCOpenVR.vrInput == null) {
            return Collections.emptyList();
        }
        Pointer p = new Memory(JOpenVRLibrary.k_unMaxActionOriginCount * 8);
        LongByReference longRef = new LongByReference();
        longRef.setPointer(p);
        int error = MCOpenVR.vrInput.GetActionOrigins.apply(
                MCOpenVR.getActionSetHandle(this.actionSet), this.handle, longRef,
                JOpenVRLibrary.k_unMaxActionOriginCount);
        if (error != 0) {
            throw new RuntimeException("Error getting action origins for '" + this.name + "': "
                    + MCOpenVR.getInputError(error));
        }

        List<Long> list = new ArrayList<Long>();
        for (long origin : p.getLongArray(0, JOpenVRLibrary.k_unMaxActionOriginCount)) {
            if (origin != JOpenVRLibrary.k_ulInvalidInputValueHandle) {
                list.add(origin);
            }
        }
        return list;
    }

    public void setHandle(long handle) {
        if (this.handle != 0) {
            throw new IllegalStateException("Handle already assigned!");
        }
        this.handle = handle;
    }

    public int getPriority() {
        return this.priority;
    }

    public VRInputAction setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    /**
     * Whether this action should act on its input right now.
     *
     * <p>More than a flag, because two actions can be bound to the same physical input and only
     * one of them should answer for it. The one with the higher priority wins -- unless it is a
     * handed binding that has given up its claim on that hand, which is how grabbing with one hand
     * stays possible while the other hand is doing something more important.
     *
     * <p>For a handed binding this is about the hand set by {@link #setCurrentHand}.
     */
    public boolean isEnabled() {
        if (!isEnabledRaw(this.currentHand)) {
            return false;
        }

        long lastOrigin = getLastOrigin();
        ControllerType hand = MCOpenVR.getOriginControllerType(lastOrigin);
        if (hand == null && isHanded()) {
            return false;
        }

        for (VRInputAction action : MCOpenVR.getInputActions()) {
            if (action != this && action.isEnabledRaw(hand) && action.isActive()
                    && action.getPriority() > getPriority()
                    && action.getOrigins().contains(lastOrigin)) {
                if (action.isHanded()) {
                    return !((HandedKeyBinding) action.keyBinding).isPriorityOnController(hand);
                }
                return false;
            }
        }
        return true;
    }

    public boolean isEnabledRaw(ControllerType hand) {
        if (isHanded()) {
            return hand != null && this.enabled[hand.ordinal()];
        }
        return this.enabled[0];
    }

    public boolean isEnabledRaw() {
        for (ControllerType hand : ControllerType.values()) {
            if (isEnabledRaw(hand)) {
                return true;
            }
        }
        return false;
    }

    public VRInputAction setEnabled(ControllerType hand, boolean enabled) {
        if (!isHanded()) {
            throw new IllegalStateException("Not a handed key binding!");
        }
        this.enabled[hand.ordinal()] = enabled;
        return this;
    }

    public VRInputAction setEnabled(boolean enabled) {
        if (isHanded()) {
            for (ControllerType hand : ControllerType.values()) {
                this.enabled[hand.ordinal()] = enabled;
            }
        } else {
            this.enabled[0] = enabled;
        }
        return this;
    }

    /** Whether the player has this action bound to anything at all on the active controller. */
    public boolean isActive() {
        if ("boolean".equals(this.type)) {
            return digitalData().bActive != 0;
        }
        if (isAnalog()) {
            return analogData().bActive != 0;
        }
        return false;
    }

    public boolean isHanded() {
        return this.keyBinding instanceof HandedKeyBinding;
    }

    public void registerListener(KeyListener listener) {
        this.listeners.add(listener);
        Collections.sort(this.listeners, Collections.reverseOrder(
                new Comparator<KeyListener>() {
                    @Override
                    public int compare(KeyListener a, KeyListener b) {
                        return Integer.compare(a.getPriority(), b.getPriority());
                    }
                }));
    }

    public void unregisterListener(KeyListener listener) {
        this.listeners.remove(listener);
    }

    /**
     * Offers the press to the listeners, highest priority first.
     *
     * @return true if one of them took it, in which case the key binding is left alone
     */
    public boolean notifyListeners(boolean pressed, ControllerType hand) {
        for (KeyListener listener : this.listeners) {
            if (pressed ? listener.onPressed(hand) : listener.onUnpressed(hand)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs down the delayed releases.
     *
     * <p>A release is delayed by a tick rather than done at once because the game samples key
     * state once a tick: a press and release inside one tick would never be seen.
     */
    public void tick() {
        if (isHanded()) {
            for (int i = 0; i < ControllerType.values().length; i++) {
                if (this.unpressInTicks[i] > 0 && --this.unpressInTicks[i] == 0) {
                    unpressBindingImmediately(ControllerType.values()[i]);
                }
            }
        } else if (this.unpressInTicks[0] > 0 && --this.unpressInTicks[0] == 0) {
            unpressBindingImmediately(null);
        }
    }

    public void pressBinding(ControllerType hand) {
        if (isHanded()) {
            if (hand == null || this.pressed[hand.ordinal()]) {
                return;
            }
            this.pressed[hand.ordinal()] = true;
            if (notifyListeners(true, hand)) {
                return;
            }
            ((HandedKeyBinding) this.keyBinding).pressKey(hand);
        } else {
            if (this.pressed[0]) {
                return;
            }
            this.pressed[0] = true;
            if (notifyListeners(true, null)) {
                return;
            }
            pressKey();
        }
    }

    public void pressBinding() {
        pressBinding(this.currentHand);
    }

    public void unpressBinding(int unpressInTicks, ControllerType hand) {
        if (isHanded()) {
            if (hand == null || !this.pressed[hand.ordinal()]) {
                return;
            }
            this.unpressInTicks[hand.ordinal()] = unpressInTicks;
        } else {
            if (!this.pressed[0]) {
                return;
            }
            this.unpressInTicks[0] = unpressInTicks;
        }
    }

    public void unpressBinding(int unpressInTicks) {
        unpressBinding(unpressInTicks, this.currentHand);
    }

    public void unpressBinding() {
        unpressBinding(1);
    }

    public void unpressBindingImmediately(ControllerType hand) {
        if (isHanded()) {
            if (hand == null || !this.pressed[hand.ordinal()]) {
                return;
            }
            this.pressed[hand.ordinal()] = false;
            if (notifyListeners(false, hand)) {
                return;
            }
            ((HandedKeyBinding) this.keyBinding).unpressKey(hand);
        } else {
            if (!this.pressed[0]) {
                return;
            }
            this.pressed[0] = false;
            if (notifyListeners(false, null)) {
                return;
            }
            unpressKey();
        }
    }

    /**
     * Presses the key the binding is set to, through the operating system where possible.
     *
     * <p>Going through a synthetic key event rather than setting the binding's own state is what
     * makes a controller button work in a text field, in another mod's key handler, and anywhere
     * else that reads the keyboard rather than the binding. It only works while the window has
     * focus, and some bindings must never be sent this way -- the ones the mod needs even when the
     * player has them bound to nothing -- so both cases fall back to driving the binding directly.
     *
     * <p>Vivecraft also skipped this for bindings carrying a Forge key modifier, which 1.7.10 has
     * no concept of, so that condition is simply absent rather than ported.
     */
    public void pressKey() {
        int awtCode = KeyboardSimulator.translateToAWT(this.keyBinding.getKeyCode());
        if (canSimulate(awtCode)) {
            try {
                KeyboardSimulator.robot.keyPress(awtCode);
                return;
            } catch (Exception e) {
                ViveCraftX.LOG.debug("Could not synthesise a key press for {} (code {})",
                        this.keyBinding.getKeyDescription(), awtCode, e);
            }
        }
        KeyBindingUtil.press(this.keyBinding);
    }

    public void unpressKey() {
        int awtCode = KeyboardSimulator.translateToAWT(this.keyBinding.getKeyCode());
        if (canSimulate(awtCode)) {
            try {
                KeyboardSimulator.robot.keyRelease(awtCode);
                return;
            } catch (Exception e) {
                ViveCraftX.LOG.debug("Could not synthesise a key release for {} (code {})",
                        this.keyBinding.getKeyDescription(), awtCode, e);
            }
        }
        KeyBindingUtil.unpress(this.keyBinding);
    }

    private boolean canSimulate(int awtCode) {
        return Display.isActive() && awtCode != Keyboard.KEY_NONE
                && !MCOpenVR.isSafeBinding(this.keyBinding);
    }

    /**
     * Something that wants first refusal on an action before it reaches the key binding.
     *
     * <p>How a tracker claims an input: the bow tracker takes the trigger while a bow is drawn and
     * returns true, so attack never fires.
     */
    public interface KeyListener {

        /** @return true to consume the press */
        boolean onPressed(ControllerType hand);

        /** @return true to consume the release */
        boolean onUnpressed(ControllerType hand);

        int getPriority();
    }
}
