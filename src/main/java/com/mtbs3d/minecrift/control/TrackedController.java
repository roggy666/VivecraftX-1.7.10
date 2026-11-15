package com.mtbs3d.minecrift.control;

import com.mtbs3d.minecrift.provider.MCOpenVR;

/** One physical controller, as something to ask questions of rather than an index to pass around. */
public class TrackedController {

    protected final ControllerType type;

    public TrackedController(ControllerType type) {
        this.type = type;
    }

    public int getDeviceIndex() {
        return MCOpenVR.controllerDeviceIndex[this.type.ordinal()];
    }

    public ControllerType getType() {
        return this.type;
    }

    public boolean isTracking() {
        return MCOpenVR.isControllerTracking(this.type);
    }

    public void triggerHapticPulse(float durationSeconds, float frequency, float amplitude) {
        MCOpenVR.triggerHapticPulse(this.type, durationSeconds, frequency, amplitude);
    }
}
