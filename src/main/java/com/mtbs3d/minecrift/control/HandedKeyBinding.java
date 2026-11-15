package com.mtbs3d.minecrift.control;

import net.minecraft.client.settings.KeyBinding;

/**
 * A binding that can be held by either hand independently.
 *
 * <p>Vanilla's key state is a single flag, which is the wrong shape for a VR binding: grabbing a
 * ladder with the left hand and with the right are different events, and a binding that could only
 * say "pressed" would lose which hand did it.
 *
 * <p>The per-hand state is kept here rather than in vanilla's fields, so vanilla's own view of the
 * binding is untouched: anything that reads it the ordinary way sees it pressed if either hand has
 * it, which is what a flat game would expect.
 */
public class HandedKeyBinding extends KeyBinding {

    private final boolean[] pressed = new boolean[ControllerType.values().length];
    private final int[] pressTime = new int[ControllerType.values().length];

    public HandedKeyBinding(String description, int keyCode, String category) {
        super(description, keyCode, category);
    }

    @Override
    public boolean isPressed() {
        boolean any = false;
        // every hand is polled, not short-circuited: isPressed consumes a queued press, so
        // stopping at the first would leave the other hand's press stuck until next time
        for (ControllerType hand : ControllerType.values()) {
            any |= isPressed(hand);
        }
        return any;
    }

    @Override
    public boolean getIsKeyPressed() {
        for (ControllerType hand : ControllerType.values()) {
            if (isKeyDown(hand)) {
                return true;
            }
        }
        return false;
    }

    /** Consumes one queued press for that hand, the way vanilla's {@code isPressed} does. */
    public boolean isPressed(ControllerType hand) {
        if (this.pressTime[hand.ordinal()] > 0) {
            --this.pressTime[hand.ordinal()];
            return true;
        }
        return false;
    }

    public boolean isKeyDown(ControllerType hand) {
        return this.pressed[hand.ordinal()];
    }

    public void pressKey(ControllerType hand) {
        this.pressed[hand.ordinal()] = true;
        ++this.pressTime[hand.ordinal()];
    }

    public void unpressKey(ControllerType hand) {
        this.pressTime[hand.ordinal()] = 0;
        this.pressed[hand.ordinal()] = false;
    }

    /**
     * Whether this binding wins the hand when two bindings share a physical input.
     *
     * <p>Overridden by bindings that should keep their hand even when something higher priority
     * also wants it -- grabbing with one hand should not be cancelled by the other hand doing
     * something more important.
     */
    public boolean isPriorityOnController(ControllerType type) {
        return true;
    }
}
