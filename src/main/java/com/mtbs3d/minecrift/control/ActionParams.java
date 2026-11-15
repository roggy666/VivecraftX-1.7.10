package com.mtbs3d.minecrift.control;

/**
 * How one binding should be declared to SteamVR.
 *
 * <p>{@code requirement} is what SteamVR tells the player about a binding they have not set:
 * "mandatory" refuses to run without it, "suggested" warns, "optional" says nothing. {@code type}
 * is the shape of the input -- a button, or one, two or three axes -- which decides what a player
 * is allowed to bind it to.
 *
 * <p>A separate class rather than Vivecraft's private nested one, because the controls screen
 * needs to read these too.
 */
public class ActionParams {

    public final String requirement;
    public final String type;
    public final VRInputActionSet actionSetOverride;

    public ActionParams(String requirement, String type, VRInputActionSet actionSetOverride) {
        this.requirement = requirement;
        this.type = type;
        this.actionSetOverride = actionSetOverride;
    }
}
