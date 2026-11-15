package com.mtbs3d.minecrift.control;

import com.mtbs3d.minecrift.provider.MCOpenVR;

/**
 * Which hand. The ordinal is the index the runtime and the rest of the mod use for a controller,
 * so the order matters: right is zero, because Vivecraft treats the right hand as the main one.
 */
public enum ControllerType {
    RIGHT,
    LEFT;

    public TrackedController getController() {
        return MCOpenVR.controllers[this.ordinal()];
    }
}
