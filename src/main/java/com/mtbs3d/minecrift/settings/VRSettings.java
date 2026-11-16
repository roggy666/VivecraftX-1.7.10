/**
 * Copyright 2013 Mark Browning, StellaArtois
 * Licensed under the LGPL 3.0 or later (See LICENSE.md for details)
 */
package com.mtbs3d.minecrift.settings;

import lol.gzmc.vivecraftx.VRRenderState;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.render.VRKeyboardLayout;

import java.io.*;
import java.util.ArrayList;
import java.util.SortedSet;

import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.settings.profile.ProfileReader;
import com.mtbs3d.minecrift.control.VRControllerButtonMapping;
import com.mtbs3d.minecrift.control.ViveButtons;
import com.mtbs3d.minecrift.settings.profile.ProfileManager;
import com.mtbs3d.minecrift.settings.profile.ProfileWriter;
import com.mtbs3d.minecrift.utils.KeyboardSimulator;

import de.fruitfly.ovr.enums.EyeType;
import jopenvr.VR_IVRSystem_FnTable.GetTrackedDeviceIndexForControllerRole_callback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Items;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.lwjgl.util.Color;

public class VRSettings
{
    public static final int VERSION = 2;
    public static final Logger logger = LogManager.getLogger();
	public static VRSettings inst;
	public JSONObject defaults = new JSONObject();
    public static final int UNKNOWN_VERSION = 0;
    public final String DEGREE  = "\u00b0";

    public static final int INERTIA_NONE = 0;
    public static final int INERTIA_NORMAL = 1;
    public static final int INERTIA_LARGE = 2;
    public static final int INERTIA_MASSIVE = 3;

    public static final float INERTIA_NONE_ADD_FACTOR = 1f / 0.01f;
    public static final float INERTIA_NORMAL_ADD_FACTOR = 1f;
    public static final float INERTIA_LARGE_ADD_FACTOR = 1f / 4f;
    public static final float INERTIA_MASSIVE_ADD_FACTOR = 1f / 16f;
    public static final int RENDER_FIRST_PERSON_FULL = 0;
    public static final int RENDER_FIRST_PERSON_HAND = 1;
    public static final int RENDER_FIRST_PERSON_NONE = 2;
    public static final int RENDER_CROSSHAIR_MODE_ALWAYS = 0;
    public static final int RENDER_CROSSHAIR_MODE_HUD = 1;
    public static final int RENDER_CROSSHAIR_MODE_NEVER = 2;
    public static final int RENDER_BLOCK_OUTLINE_MODE_ALWAYS = 0;
    public static final int RENDER_BLOCK_OUTLINE_MODE_HUD = 1;
    public static final int RENDER_BLOCK_OUTLINE_MODE_NEVER = 2;
  
    /**
     * What the monitor shows while the game is in the headset.
     *
     * <p>Renumbered from minecrift's list, which distinguished between full and one-third frame
     * rate -- a distinction that made sense when the mirror was a second submit to the compositor
     * and makes none now that it is a blit of a texture we already have. Saved by name rather
     * than by number, so that the next change to this list cannot silently reinterpret everybody's
     * setting; old numeric values are read and converted.
     */
    public static final int MIRROR_OFF = 0;
    public static final int MIRROR_ON_CROPPED = 1;
    public static final int MIRROR_ON_SINGLE = 2;
    public static final int MIRROR_ON_DUAL = 3;
    public static final int MIRROR_GUI = 4;
    /**
     * Mixed reality: a third camera, keyed against a green screen. Not reachable from the
     * setting, because the extra camera pass it needs is not written. The constant stays because
     * the tracking for its camera is, and is guarded on this.
     */
    public static final int MIRROR_MIXED_REALITY = 5;
    private static final int MIRROR_LAST_SELECTABLE = MIRROR_GUI;

    /** The interface is never drawn flat on the mirror; it is still there in the world. */
    public static final int GUI_MIRROR_OFF = 0;
    /** Drawn only while no screen is open, so the viewer gets the HUD but not a doubled menu. */
    public static final int GUI_MIRROR_HUD_ONLY = 1;
    public static final int GUI_MIRROR_ALWAYS = 2;

    private static final String[] MIRROR_NAMES = {
            "OFF", "CROPPED", "SINGLE", "DUAL", "GUI", "MIXED_REALITY"
    };

    public static String mirrorModeName(int mode) {
        return mode >= 0 && mode < MIRROR_NAMES.length ? MIRROR_NAMES[mode] : MIRROR_NAMES[1];
    }

    /**
     * Reads a saved mirror mode, in either spelling.
     *
     * <p>A number is one of minecrift's, where 1 and 2 were both the two eyes side by side and 3
     * and 4 were both one eye, differing only in how often they were drawn. The two modes that
     * needed a camera pass of their own fall back to the default, since they cannot be honoured.
     */
    public static int parseMirrorMode(String value) {
        for (int i = 0; i < MIRROR_NAMES.length; i++) {
            if (MIRROR_NAMES[i].equalsIgnoreCase(value)) {
                return i > MIRROR_LAST_SELECTABLE ? MIRROR_ON_CROPPED : i;
            }
        }
        try {
            switch (Integer.parseInt(value.trim())) {
                case 0: return MIRROR_OFF;
                case 1: case 2: return MIRROR_ON_DUAL;
                case 3: case 4: return MIRROR_ON_SINGLE;
                default: return MIRROR_ON_CROPPED;
            }
        } catch (NumberFormatException e) {
            return MIRROR_ON_CROPPED;
        }
    }
    
    public static final int HUD_LOCK_HEAD= 1;
    public static final int HUD_LOCK_HAND= 2;
    public static final int HUD_LOCK_WRIST= 3;
    public static final int HUD_LOCK_BODY= 4;

    public static final int FREEMOVE_CONTROLLER= 1;
    public static final int FREEMOVE_HMD= 2;
    public static final int FREEMOVE_RUNINPLACE= 3;
    public static final int FREEMOVE_JOYPAD = 4;
    
    public static final int NO_SHADER = -1;

    public int version = UNKNOWN_VERSION;

    public int renderFullFirstPersonModelMode = RENDER_FIRST_PERSON_HAND;   // VIVE - hand only by default
    public int shaderIndex = NO_SHADER;
    public String stereoProviderPluginID = "openvr";
    public String badStereoProviderPluginID = "";
    public boolean storeDebugAim = false;
    public int smoothRunTickCount = 20;
    public boolean smoothTick = false;
    //Jrbudda's Options

    public String[] vrQuickCommands;

    //Control
    public boolean vrReverseHands = false;
    public boolean vrReverseShootingEye = false;
    public VRControllerButtonMapping[] buttonMappings;
    public float vrWorldScale = 1.0f;
    public float vrWorldRotation = 0f;
	public float vrWorldRotationCached;
    public float vrWorldRotationIncrement = 45f;
    public float xSensitivity=1f;
    public float ySensitivity=1f;
    public float keyholeX=15;
    public double headToHmdLength=0.10f;
    public float autoCalibration=-1;
    public float manualCalibration=-1;
    public float playerEyeHeight = 1.62f;
    //
    
    //Locomotion
    public int inertiaFactor = INERTIA_NORMAL;
    public boolean walkUpBlocks = true;     // VIVE default to enable climbing
    public boolean simulateFalling = true;  // VIVE if HMD is over empty space, fall
    public boolean weaponCollision = true;  // VIVE weapon hand collides with blocks/enemies
    /** Only a sword hits anything; everything else passes through the world untouched. */
    public boolean onlySwordCollision = false;
    /**
     * Whether a sword may mine.
     *
     * <p>Swinging a sword at a wall takes the wall down in the ordinary game, and in a headset
     * that happens by accident constantly -- you put your arm somewhere and the wall goes. With
     * this off a sword only cuts what a sword is for; with it on, it also mines whatever it is
     * genuinely the right tool for, such as cobwebs.
     */
    public boolean swordBlockCollision = true;
    /**
     * Whether another player has to be reached with the weapon rather than with its swing.
     *
     * <p>A sword carries two and a half metres of reach so that fighting a mob at arm's length
     * works. Pointed at a person standing next to you, that is a stab you did not mean.
     */
    public boolean reducedPlayerReach = true;
    /** Pressing a lever or a chest by putting a hand on it. */
    public boolean realisticBlockInteractEnabled = true;
    /** Petting, milking, shearing and saddling by putting a hand on the animal. */
    public boolean realisticEntityInteractEnabled = true;
    /**
     * Whether you can see your own body when you look down.
     *
     * <p>Without it there is nothing below the headset at all: the two forearms float, because
     * there is nothing for them to be attached to. With it the arms run from real shoulders, and
     * the floating pair is switched off because they would be the same arms twice.
     */
    public boolean renderOwnBody = false;
    /**
     * Whether an arm bends at an elbow and runs back to a shoulder, instead of being a forearm
     * on the controller.
     *
     * <p>Off, as the modern mod's split-arm model is off: one piece is what it gives you unless
     * you ask. One piece is also what the rest of this is built against -- the hotbar lies along
     * that forearm, and a mod that re-cuts the arm's texture has one box to re-cut rather than
     * two it has never heard of.
     */
    public boolean splitArms = false;
    public float movementSpeedMultiplier = 1.0f;   // VIVE - use full speed by default

    /** Degrees of turn per step while turning smoothly, which is what a zero step size means. */
    /** Degrees per second, for players who have turned stepped turning off. */
    public float vrSmoothTurnSpeed = 120.0f;

    /** How far a stick has to leave the middle before it counts as pushed. */
    public float vrJoystickDeadzone = 0.15f;
    public boolean vrFreeMove = false;
    public int vrFreeMoveMode = this.FREEMOVE_CONTROLLER;
    public boolean vrAllowLocoModeSwotch = true;
    public boolean vrLimitedSurvivalTeleport = true;
    public boolean seated = false;
    public boolean seatedUseHMD = false;
    public float jumpThreshold=0.05f;
    public float sneakThreshold=0.4f;
    public boolean realisticJumpEnabled=true;
    public boolean realisticSneakEnabled=true;
    public boolean realisticClimbEnabled=true;

    /**
     * How far a teleport may carry you, in blocks.
     *
     * <p>Separate figures for up, down and along, because they are different kinds of cheating:
     * stepping up a block is nothing, dropping four is a choice you could have made by jumping,
     * and crossing sixteen is the part that replaces walking. Modern Vivecraft's defaults.
     */
    public int vrTeleportUpLimit = 1;
    public int vrTeleportDownLimit = 4;
    public int vrTeleportHorizLimit = 16;

    /**
     * Whether the view stops following the headset up and down.
     *
     * <p>For playing lying down, or slumped on a sofa. The whole tracking space is lifted so the
     * head sits at its standing height, which carries the hands with it -- so they stay where the
     * head expects them instead of ending up at floor level. Everything the game itself does to
     * your height, crouching and swimming and riding, still happens.
     */
    public boolean vrFixedHeight = false;

    /** Whether the hands feel what the world does to you, not only what you do to it. */
    public boolean vrWorldHaptics = true;

    /** Whether a chat message gives the off hand a nudge. */
    public boolean vrChatHaptics = true;

    /** How far in the comfort vignette closes at full speed, as a fraction of the eye. */
    public float fovReductionMin = 0.5f;
    public boolean realisticSwimEnabled=true;
    public boolean realisticRowEnabled=true;
    public float walkMultiplier=1;
    public boolean vrAllowCrawling = false; //unused
    public boolean vrShowBlueCircleBuddy = true;
    public boolean vehicleRotation = false;

    /** Whether stepping out of a vehicle in the room gets you out of it in the game. */
    public boolean realisticDismount = true;

    /** Whether hitting a door, gate or lever works it instead of mining it. */
    public boolean realisticOpening = true;

    /** Whether the keyboard is one standing in the world rather than SteamVR's overlay. */
    public boolean vrPhysicalKeyboard = true;

    /**
     * Whether an ordinary text field taking focus brings a keyboard up by itself.
     *
     * <p>Off by default, as upstream has it: a screen full of fields would otherwise put a
     * keyboard in front of the player every time one of them was touched. The chat line is
     * exempt and always opens one, because it cannot lose focus and so is unambiguously the
     * thing being typed into.
     */
    public boolean autoOpenKeyboard = false;

    /**
     * The characters on the in-world keyboard, and the same again with shift held.
     *
     * <p>Two flat strings sliced into thirteen columns, which is how upstream stores them and is
     * what makes a layout something the player can edit rather than something the mod has to
     * support. A longer string simply grows the board. The named sets in
     * {@link lol.gzmc.vivecraftx.render.VRKeyboardLayout} are presets for these two fields and
     * nothing more: whatever is in here is what the board shows, named or not.
     *
     * <p>Filled in from the game's own language before the defaults are taken, so the preset
     * chosen for a player who has never touched the setting is the one for the language they are
     * already reading -- and so that the Defaults button restores that rather than a null.
     */
    public String keyboardKeys;
    public String keyboardKeysShift;

    /**
     * What each slot of the radial menu does, by the name of the key binding it presses.
     *
     * <p>Names rather than bindings, because the bindings a game has depend on which mods are
     * installed and in what order they loaded, and a number would point at something different
     * every time that changed.
     */
    public String[] vrRadialActions = new String[] {
            "key.drop", "key.chat", "key.inventory", "key.pickItem",
            "vivecraft.key.quickTorch", "key.playerlist",
            "vivecraft.key.rotateLeft", "vivecraft.key.rotateRight"
    };
    public boolean animaltouching = true;
    //
    
    //Rendering
    public boolean useFsaa = true;   // default to off
    public boolean useFOVReduction = false;   // default to off
    public boolean vrUseStencil = true;

    //VR on/off
    /**
     * Whether the player wants VR. Saved, so it survives a restart.
     *
     * <p>On by default: somebody who installed a VR mod wants VR, and a first run that came up
     * flat would look like the mod failing to load.
     */
    public boolean vrEnabled = true;
    /** Whether that choice is restored at startup, or every launch starts in VR. */
    public boolean rememberVr = true;
    /** Whether taking the headset off drops the game back to the monitor, and putting it on resumes. */
    public boolean vrHotswitchingEnabled = true;
    /** Whether the on/off button appears on the main menu. */
    public boolean vrToggleButtonEnabled = true;
    /** Whether closing SteamVR closes the game, rather than dropping it to the monitor. */
    public boolean closeWithRuntime = true;
    public boolean insideBlockSolidColor = false; //unused
    public float renderScaleFactor = 1.0f;
    public int displayMirrorMode = MIRROR_ON_CROPPED;
    /**
     * How much of each edge the cropped mirror throws away, on top of the aspect fit.
     *
     * <p>A lens shows its worst at the very edge of the picture, and the renderer draws a margin
     * beyond what is actually visible through it, so the outermost sixth is the part nobody in
     * the headset can see anyway.
     */
    public float mirrorCrop = 0.15F;
    /** Which eye the single-view modes show. */
    public boolean displayMirrorLeftEye = true;
    /** Whether the interface is drawn flat on top of the mirror, and when. */
    public int guiOnMirror = GUI_MIRROR_OFF;
    //
    
    //Mixed Reality
    public Color mixedRealityKeyColor = new Color();
    public float mixedRealityAspectRatio = 16F / 9F;
    public boolean mixedRealityRenderHands = false;
    public boolean mixedRealityUnityLike = true;
    public boolean mixedRealityMRPlusUndistorted = true;
    public boolean mixedRealityAlphaMask = false;
    public float mixedRealityFov = 40;
    public float vrFixedCamposX = 0;
    public float vrFixedCamposY = 0;
    public float vrFixedCamposZ = 0;
    public float vrFixedCamrotPitch = 0;
    public float vrFixedCamrotYaw = 0;
    public float vrFixedCamrotRoll = 0;
    public float mrMovingCamOffsetX = 0;
    public float mrMovingCamOffsetY = 0;
    public float mrMovingCamOffsetZ = 0;
    public float mrMovingCamOffsetPitch = 0;
    public float mrMovingCamOffsetYaw = 0;
    public float mrMovingCamOffsetRoll = 0;
    //
    
    //HUD/GUI
    public boolean vrTouchHotbar = true;    
    public float hudScale = 1.5f;
    public float hudDistance = 1.25f;
    /** How far along the forearm the wrist panel sits, in steps of about eight millimetres. */
    public float vrHudWristOffset = 1.0f;
    public float hudPitchOffset = -2f;
    public float hudYawOffset = 0.0f;
    public boolean floatInventory = true; //false not working yet, have to account for rotation and tilt in MCOpenVR>processGui()
	public boolean menuAlwaysFollowFace;
    /**
     * Where the interface hangs when no screen is open.
     *
     * <p>The wrist, which is what modern Vivecraft settled on. On the hand the interface sits in
     * front of whatever that hand is holding, so it is in the way exactly when you are using the
     * hand; on the wrist it is a watch -- out of sight until you turn your arm to read it.
     */
    public int vrHudLockMode = HUD_LOCK_WRIST;

    /**
     * Whether a thumbstick moves you at the speed it is pushed, rather than all or nothing.
     *
     * <p>Off by default, because partial speed and a snap-turn world are an awkward pair, and
     * because walking slowly is what makes people queasy soonest.
     */
    public boolean analogMovement = false;

    /** Pushing the stick past {@link #autoSprintThreshold} sprints, so sprint needs no button. */
    public boolean autoSprint = true;

    public float autoSprintThreshold = 0.9F;
    public boolean hideGui = false;     // VIVE show gui
    public boolean hudOcclusion = false;

    /**
     * Whether incoming chat fades away the way it does flat, or the latest lines stay put.
     *
     * <p>True, which is vanilla, by default: a line that stays is a line covering part of the
     * panel, and the player who wants that should be the one to ask for it. The option exists
     * because fading assumes you were looking at the message when it arrived, and in a headset
     * you were probably looking at the world.
     */
    public boolean chatFadeAway = true;

    /**
     * Whether SteamVR's binding editor shows the sets that are plumbing rather than controls.
     *
     * <p>Off by default, because the editor is confusing enough with the controls alone, and a
     * player who rebinds the technical sets by accident gets behaviour nobody can explain.
     */
    public boolean allowAdvancedBindings = false;
    public float crosshairScale = 1.0f;
    public int renderInGameCrosshairMode = RENDER_CROSSHAIR_MODE_ALWAYS;
    public int renderBlockOutlineMode = RENDER_BLOCK_OUTLINE_MODE_ALWAYS;
    public float hudOpacity = 0.95f;
    public boolean menuBackground = false;
    public float   menuCrosshairScale = 1f;
    public boolean useCrosshairOcclusion = false;
	public boolean crosshairScalesWithDistance = false;
	public boolean seatedHudAltMode = true;
	public int forceHardwareDetection = 0; // 0 = off, 1 = vive, 2 = oculus

    //
     	
    private Minecraft mc;

	
    public VRSettings( Minecraft minecraft, File dataDir )
    {
        // Assumes GameSettings (and hence optifine's settings) have been read first

    	mc = minecraft;
    	inst = this;

        // Before the defaults are taken, so that the keyboard the player gets on a fresh install
        // is in the language they are already playing in, and so that the Defaults button has
        // something real to restore.
        VRKeyboardLayout startingLayout = VRKeyboardLayout.forLanguage(
                minecraft.gameSettings == null ? null : minecraft.gameSettings.language);
        this.keyboardKeys = startingLayout.keys;
        this.keyboardKeysShift = startingLayout.shifted;

        // Store our class defaults to a member variable for later use
    	storeDefaults();

        // Legacy config files. Note that in general these files will be by-passed
        // by the Profile handling in ProfileManager. loadOptions and saveOptions ill
        // be redirected to the profile manager using ProfileReader and ProfileWriter
        // respectively.

        // Load settings from the file
        this.loadOptions();
    }

    public void loadOptions()
    {
        loadOptions(null);
    }

    public void loadDefaults()
    {
        loadOptions(this.defaults);
    }
    
    public void loadOptions(JSONObject theProfiles)
    {
        // Load Minecrift options
        try
        {
            ProfileReader optionsVRReader = new ProfileReader(ProfileManager.PROFILE_SET_VR, theProfiles);

            String var2 = "";
           
            while ((var2 = optionsVRReader.readLine()) != null)
            {
                try
                {
                    // Split once, not at every colon. The writer side has always done it this
                    // way; the reader had not, so any setting whose value contained a colon was
                    // silently truncated at the first one when it was read back. The keyboard
                    // layouts are the first setting where that is fatal rather than merely
                    // wrong -- a QWERTY grid has a colon key on it.
                    String[] optionTokens = var2.split(":", 2);

                    if (optionTokens[0].equals("version"))
                    {
                        this.version = Integer.parseInt(optionTokens[1]);
                    }

//                    if (optionTokens[0].equals("firstLoad"))
//                    {
//                        this.firstLoad = optionTokens[1].equals("true");
//                    }

                    if (optionTokens[0].equals("stereoProviderPluginID"))
                    {
                        this.stereoProviderPluginID = optionTokens[1];
                    }

                    if (optionTokens[0].equals("badStereoProviderPluginID"))
                    {
                        if (optionTokens.length > 1) {  // Trap if no entry
                            this.badStereoProviderPluginID = optionTokens[1];
                        }
                    }

                    if (optionTokens[0].equals("hudOpacity"))
                    {
                        this.hudOpacity = this.parseFloat(optionTokens[1]);
                        if(hudOpacity< 0.15f)
                        	hudOpacity = 1.0f;
                    }
                    if (optionTokens[0].equals("menuBackground"))
                    {
                        this.menuBackground = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("renderFullFirstPersonModelMode"))
                    {
                        this.renderFullFirstPersonModelMode = Integer.parseInt(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("shaderIndex"))
                    {
                        this.shaderIndex = Integer.parseInt(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("walkUpBlocks"))
                    {
                        this.walkUpBlocks = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("displayMirrorMode"))
                    {
                        this.displayMirrorMode = parseMirrorMode(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("mirrorCrop"))
                    {
                        this.mirrorCrop = Float.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("displayMirrorLeftEye"))
                    {
                        this.displayMirrorLeftEye = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("guiOnMirror"))
                    {
                        this.guiOnMirror = Integer.parseInt(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("vrEnabled"))
                    {
                        this.vrEnabled = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("rememberVr"))
                    {
                        this.rememberVr = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("vrHotswitching"))
                    {
                        this.vrHotswitchingEnabled = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("vrToggleButton"))
                    {
                        this.vrToggleButtonEnabled = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("closeWithRuntime"))
                    {
                        this.closeWithRuntime = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("mixedRealityKeyColor"))
                    {
                        String[] split = optionTokens[1].split(",");
                        this.mixedRealityKeyColor = new Color(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
                    }

                    if (optionTokens[0].equals("mixedRealityRenderHands"))
                    {
                        this.mixedRealityRenderHands = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("mixedRealityUnityLike"))
                    {
                        this.mixedRealityUnityLike = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("mixedRealityUndistorted"))
                    {
                        this.mixedRealityMRPlusUndistorted = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("mixedRealityAlphaMask"))
                    {
                        this.mixedRealityAlphaMask = optionTokens[1].equals("true");
                    }
                    
                    if (optionTokens[0].equals("mixedRealityFov"))
                    {
                        this.mixedRealityFov = this.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("insideBlockSolidColor"))
                    {
                        this.insideBlockSolidColor = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("hudScale"))
                    {
                        this.hudScale = this.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("renderScaleFactor"))
                    {
                        this.renderScaleFactor = this.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("vrHudLockMode"))
                    {
                        this.vrHudLockMode =  Integer.parseInt(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("vrHudWristOffset"))
                    {
                        this.vrHudWristOffset = this.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("hudDistance"))
                    {
                        this.hudDistance = this.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("hudPitchOffset"))
                    {
                        this.hudPitchOffset = this.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("hudYawOffset"))
                    {
                        this.hudYawOffset = this.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("useFsaa"))
                    {
                        this.useFsaa = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("vrSmoothTurnSpeed"))
                    {
                        this.vrSmoothTurnSpeed = this.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("vrJoystickDeadzone"))
                    {
                        this.vrJoystickDeadzone = this.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("movementSpeedMultiplier"))
                    {
                        this.movementSpeedMultiplier = this.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("renderInGameCrosshairMode"))
                    {
                        this.renderInGameCrosshairMode = Integer.parseInt(optionTokens[1]);
                    }
                    
                    if (optionTokens[0].equals("crosshairScalesWithDistance"))
                    {
                    	 this.crosshairScalesWithDistance = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("renderBlockOutlineMode"))
                    {
                        this.renderBlockOutlineMode = Integer.parseInt(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("crosshairScale"))
                    {
                        this.crosshairScale = this.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("menuCrosshairScale"))
                    {
                        this.menuCrosshairScale = this.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("renderInGameCrosshairMode"))
                    {
                        this.renderInGameCrosshairMode = Integer.parseInt(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("renderBlockOutlineMode"))
                    {
                        this.renderBlockOutlineMode = Integer.parseInt(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("analogMovement"))
                    {
                        this.analogMovement = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("autoSprint"))
                    {
                        this.autoSprint = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("autoSprintThreshold"))
                    {
                        this.autoSprintThreshold = this.parseFloat(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("hudOcclusion"))
                    {
                        this.hudOcclusion = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("menuAlwaysFollowFace"))
                    {
                        this.menuAlwaysFollowFace = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("useCrosshairOcclusion"))
                    {
                        this.useCrosshairOcclusion = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("inertiaFactor"))
                    {
                        this.inertiaFactor = Integer.parseInt(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("smoothRunTickCount"))
                    {
                        this.smoothRunTickCount = Integer.parseInt(optionTokens[1]);
                    }

                    if (optionTokens[0].equals("smoothTick"))
                    {
                        this.smoothTick = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("hideGui"))
                    {
                        this.hideGui = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("chatFadeAway"))
                    {
                        this.chatFadeAway = optionTokens[1].equals("true");
                    }

                    // VIVE START - new options
                    if (optionTokens[0].equals("simulateFalling"))
                    {
                        this.simulateFalling = optionTokens[1].equals("true");
                    }
                    if (optionTokens[0].equals("weaponCollision"))
                    {
                        this.weaponCollision = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("onlySwordCollision"))
                    {
                        this.onlySwordCollision = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("swordBlockCollision"))
                    {
                        this.swordBlockCollision = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("reducedPlayerReach"))
                    {
                        this.reducedPlayerReach = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("realisticBlockInteract"))
                    {
                        this.realisticBlockInteractEnabled = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("renderOwnBody"))
                    {
                        this.renderOwnBody = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("splitArms"))
                    {
                        this.splitArms = optionTokens[1].equals("true");
                    }

                    if (optionTokens[0].equals("realisticEntityInteract"))
                    {
                        this.realisticEntityInteractEnabled = optionTokens[1].equals("true");
                    }
                    if (optionTokens[0].equals("animalTouching"))
                    {
                        this.animaltouching = optionTokens[1].equals("true");
                    }
                    // VIVE END - new options
                    //JRBUDDA
                    if (optionTokens[0].equals("allowCrawling"))
                    {
                        this.vrAllowCrawling = optionTokens[1].equals("true");
                    }
                    if (optionTokens[0].equals("allowModeSwitch"))
                    {
                        this.vrAllowLocoModeSwotch = optionTokens[1].equals("true");
                    }
                    if (optionTokens[0].equals("freeMoveDefault"))
                    {
                        this.vrFreeMove = optionTokens[1].equals("true");
                    }
                    if (optionTokens[0].equals("limitedTeleport"))
                    {
                        this.vrLimitedSurvivalTeleport = optionTokens[1].equals("true");
                    }
                    if (optionTokens[0].equals("reverseHands"))
                    {
                        this.vrReverseHands = optionTokens[1].equals("true");
                    }
                    if (optionTokens[0].equals("stencilOn"))
                    {
                        this.vrUseStencil = optionTokens[1].equals("true");
                    }
                    if (optionTokens[0].equals("bcbOn"))
                    {
                        this.vrShowBlueCircleBuddy = optionTokens[1].equals("true");
                    }
                    if (optionTokens[0].equals("worldScale"))
                    {
                        this.vrWorldScale = this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("worldRotation"))
                    {
                        this.vrWorldRotation = this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("vrWorldRotationIncrement"))
                    {
                        this.vrWorldRotationIncrement =  this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("vrFixedCamposX"))
                    {
                        this.vrFixedCamposX =  this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("vrFixedCamposY"))
                    {
                        this.vrFixedCamposY =  this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("vrFixedCamposZ"))
                    {
                        this.vrFixedCamposZ =  this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("vrFixedCamrotPitch"))
                    {
                        this.vrFixedCamrotPitch =this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("vrFixedCamrotYaw"))
                    {
                        this.vrFixedCamrotYaw =this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("vrFixedCamrotRoll"))
                    {
                        this.vrFixedCamrotRoll =this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("mrMovingCamOffsetX"))
                    {
                        this.mrMovingCamOffsetX =  this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("mrMovingCamOffsetY"))
                    {
                        this.mrMovingCamOffsetY =  this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("mrMovingCamOffsetZ"))
                    {
                        this.mrMovingCamOffsetZ =  this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("mrMovingCamOffsetPitch"))
                    {
                        this.mrMovingCamOffsetPitch =this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("mrMovingCamOffsetYaw"))
                    {
                        this.mrMovingCamOffsetYaw =this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("mrMovingCamOffsetRoll"))
                    {
                        this.mrMovingCamOffsetRoll =this.parseFloat(optionTokens[1]);
                    }
                    if (optionTokens[0].equals("vrTouchHotbar"))
                    {
                    	  this.vrTouchHotbar = optionTokens[1].equals("true");
                    }
                    if (optionTokens[0].equals("seated"))
                    {
                    	  this.seated = optionTokens[1].equals("true");
                    }

                    if(optionTokens[0].equals("jumpThreshold")){
                        this.jumpThreshold=this.parseFloat(optionTokens[1]);
                    }

                    if(optionTokens[0].equals("sneakThreshold")){
                        this.sneakThreshold=this.parseFloat(optionTokens[1]);
                    }

                    if(optionTokens[0].equals("realisticSneakEnabled")){
                        this.realisticSneakEnabled=optionTokens[1].equals("true");
                    }
                    if(optionTokens[0].equals("seatedhmd")){
                        this.seatedUseHMD=optionTokens[1].equals("true");
                    }
                    if(optionTokens[0].equals("seatedHudAltMode")){
                        this.seatedHudAltMode=optionTokens[1].equals("true");
                    }
                    if(optionTokens[0].equals("realisticJumpEnabled")){
                        this.realisticJumpEnabled=optionTokens[1].equals("true");
                    }
                    if(optionTokens[0].equals("fovReductionMin")){
                        this.fovReductionMin=this.parseFloat(optionTokens[1]);
                    }

                    if(optionTokens[0].equals("vrChatHaptics")){
                        this.vrChatHaptics=optionTokens[1].equals("true");
                    }

                    if(optionTokens[0].equals("vrWorldHaptics")){
                        this.vrWorldHaptics=optionTokens[1].equals("true");
                    }

                    if(optionTokens[0].equals("vrFixedHeight")){
                        this.vrFixedHeight=optionTokens[1].equals("true");
                    }

                    if(optionTokens[0].equals("vrTeleportUpLimit")){
                        this.vrTeleportUpLimit=Integer.parseInt(optionTokens[1]);
                    }

                    if(optionTokens[0].equals("vrTeleportDownLimit")){
                        this.vrTeleportDownLimit=Integer.parseInt(optionTokens[1]);
                    }

                    if(optionTokens[0].equals("vrTeleportHorizLimit")){
                        this.vrTeleportHorizLimit=Integer.parseInt(optionTokens[1]);
                    }

                    if(optionTokens[0].equals("realisticClimbEnabled")){
                        this.realisticClimbEnabled=optionTokens[1].equals("true");
                    }
                    if(optionTokens[0].equals("realisticSwimEnabled")){
                        this.realisticSwimEnabled=optionTokens[1].equals("true");
                    }
                    if(optionTokens[0].equals("realisticRowEnabled")){
                        this.realisticRowEnabled=optionTokens[1].equals("true");
                    }

                    if(optionTokens[0].equals("headToHmdLength")){
                        this.headToHmdLength=parseFloat(optionTokens[1]);
                    }

                    if(optionTokens[0].equals("walkMultiplier")){
                        this.walkMultiplier=parseFloat(optionTokens[1]);
                    }
                    
                    if (optionTokens[0].equals("vrFreeMoveMode"))
                    {
                        this.vrFreeMoveMode =  Integer.parseInt(optionTokens[1]);
                    }

                    if(optionTokens[0].equals("xSensitivity")){
                        this.xSensitivity=parseFloat(optionTokens[1]);
                    }

                    if(optionTokens[0].equals("ySensitivity")){
                        this.ySensitivity=parseFloat(optionTokens[1]);
                    }

                    if(optionTokens[0].equals("keyholeX")){
                        this.keyholeX=parseFloat(optionTokens[1]);
                    }

                    if(optionTokens[0].equals("autoCalibration")){
                        this.autoCalibration=parseFloat(optionTokens[1]);
                    }

                    if(optionTokens[0].equals("manualCalibration")){
                        this.manualCalibration=parseFloat(optionTokens[1]);
                    }
                    
                    if(optionTokens[0].startsWith("radialAction")){
                        try {
                            int slot = Integer.parseInt(optionTokens[0].substring(12));
                            if (slot >= 0 && slot < this.vrRadialActions.length) {
                                this.vrRadialActions[slot] = optionTokens[1];
                            }
                        } catch (NumberFormatException ignored) {
                            // a hand-edited name that is not a number; leave the default
                        }
                    }

                    if(optionTokens[0].equals("vrPhysicalKeyboard")){
                        this.vrPhysicalKeyboard=optionTokens[1].equals("true");
                    }

                    if(optionTokens[0].equals("autoOpenKeyboard")){
                        this.autoOpenKeyboard=optionTokens[1].equals("true");
                    }

                    // An empty grid would leave a board with no keys on it and no way back, so
                    // a blank value keeps whatever the language picked rather than being taken
                    // at its word.
                    if(optionTokens[0].equals("keyboardKeys") && !optionTokens[1].isEmpty()){
                        this.keyboardKeys=optionTokens[1];
                    }

                    if(optionTokens[0].equals("keyboardKeysShift") && !optionTokens[1].isEmpty()){
                        this.keyboardKeysShift=optionTokens[1];
                    }

                    if(optionTokens[0].equals("realisticOpening")){
                        this.realisticOpening=optionTokens[1].equals("true");
                    }

                    if(optionTokens[0].equals("realisticDismount")){
                        this.realisticDismount=optionTokens[1].equals("true");
                    }

                    if(optionTokens[0].equals("vehicleRotation")){
                        this.vehicleRotation=optionTokens[1].equals("true");
                    }
                    
                    if(optionTokens[0].equals("fovReduction")){
                        this.useFOVReduction=optionTokens[1].equals("true");
                    }
                    
                    if(optionTokens[0].equals("forceHardwareDetection")){
                        this.forceHardwareDetection=Integer.parseInt(optionTokens[1]);
                    }

                    if (optionTokens[0].startsWith("BUTTON_") || optionTokens[0].startsWith("OCULUS_"))
                    {
                       VRControllerButtonMapping vb = new VRControllerButtonMapping(
                    		   Enum.valueOf(ViveButtons.class, optionTokens[0]),"");
                                               
                       String[] pts = optionTokens[1].split("_");
                      
                       if (pts.length == 1 || !optionTokens[1].startsWith("keyboard")) {
                           vb.FunctionDesc = optionTokens[1];
                           vb.FunctionExt = 0;
                       } else {
                           vb.FunctionDesc = pts[0];
                           vb.FunctionExt = (char) pts[1].getBytes()[0];
                       }
                                         
                       this.buttonMappings[vb.Button.ordinal()] = vb;
                    }       
                    if(optionTokens[0].startsWith("QUICKCOMMAND_")){
                    	 String[] pts = optionTokens[0].split("_");
                    	 int i = Integer.parseInt(pts[1]);
                    	 if (optionTokens.length == 1) 
                        	 vrQuickCommands[i] = "";
                    	 else
                        	 vrQuickCommands[i] = optionTokens[1];

                    }
                    
                    //END JRBUDDA
         
                }
                catch (Exception var7)
                {
                    logger.warn("Skipping bad VR option: " + var2);
                    var7.printStackTrace();
                }
            }           
            optionsVRReader.close();
        }
        catch (Exception var8)
        {
            logger.warn("Failed to load VR options!");
            var8.printStackTrace();
        }
    }

	public void processBindings() {
		//process button mappings     
		int offset = 0;
		for (int i = 0; i < buttonMappings.length;i++){
			VRControllerButtonMapping vb = buttonMappings[i];

			if(vb==null) { //shouldnt
		        vb = new VRControllerButtonMapping(ViveButtons.values()[i + offset],"none");
		        buttonMappings[i] = vb;
			}
			
			if(vb.FunctionDesc.equals("none")){
				vb.key = null;
				vb.FunctionExt = 0;
			} else 	if(vb.FunctionDesc.startsWith("keyboard")){
				vb.key = null;
	    		if(vb.FunctionDesc.contains("-")) vb.FunctionExt = 0;
			} else {
		        KeyBinding[] var3 = mc.gameSettings.keyBindings;
		        for (final KeyBinding keyBinding : var3) {	
		        	if (keyBinding.getKeyDescription().equals(vb.FunctionDesc)){
		        		vb.key = keyBinding;    
		        		vb.FunctionExt = 0;
		        		break;
		        	}
				}					
			}
			
			// "none" is how an unbound button is stored, not a lookup failure
			if (vb.key == null && !vb.FunctionDesc.startsWith("keyboard")
					&& !"none".equals(vb.FunctionDesc)) {
				System.out.println("Unknown key binding: " + vb.FunctionDesc);
			}
		}
	}

    public void resetSettings()
    {
        // Get the Minecrift defaults
        loadDefaults();
    }
    
    /**
     * Shorthand for the option values below. They are all plain lang keys with no arguments,
     * and the switch reads badly with the fully qualified call repeated on every line.
     */
    private static String tr(String key)
    {
        return I18n.format(key);
    }

    private static String onOff(boolean on)
    {
        return tr(on ? "vivecraftx.value.on" : "vivecraftx.value.off");
    }

    private static String yesNo(boolean yes)
    {
        return tr(yes ? "vivecraftx.value.yes" : "vivecraftx.value.no");
    }

    public String getKeyBinding( VRSettings.VrOptions par1EnumOptions )
    {
        String var2 = par1EnumOptions.getDisplayName();

        String var3 = var2 + ": ";
        String var4 = var3;
        String var5;

        switch( par1EnumOptions) {
            case OTHER_HUD_SETTINGS:
                return var2;
            case OTHER_RENDER_SETTINGS:
                return var2;
            case LOCOMOTION_SETTINGS:
                return var2;
	        case MOVEMENT_MULTIPLIER:
	            return var4 + String.format("%.2f", new Object[] { Float.valueOf(this.movementSpeedMultiplier) });
	        case HUD_OPACITY:
	        	if( this.hudOpacity > 0.99)
	        		return var4 + tr("vivecraftx.value.opaque");
	            return var4 + String.format("%.2f", new Object[] { Float.valueOf(this.hudOpacity) });
            case RENDER_MENU_BACKGROUND:
                return var4 + onOff(this.menuBackground);
	        case HUD_HIDE:
	            return var4 + yesNo(this.hideGui);
	        case CHAT_FADE_AWAY:
	            return var4 + tr(this.chatFadeAway ? "vivecraftx.value.chatPersistence.fades"
	                                               : "vivecraftx.value.chatPersistence.stays");
	        case RENDER_FULL_FIRST_PERSON_MODEL_MODE:
                if (this.renderFullFirstPersonModelMode == RENDER_FIRST_PERSON_FULL)
                    return var4 + tr("vivecraftx.value.firstPerson.full");
                else if (this.renderFullFirstPersonModelMode == RENDER_FIRST_PERSON_HAND)
                    return var4 + tr("vivecraftx.value.firstPerson.hand");
                else if (this.renderFullFirstPersonModelMode == RENDER_FIRST_PERSON_NONE)
                    return var4 + tr("vivecraftx.value.firstPerson.none");
            case MIRROR_DISPLAY:
                switch(this.displayMirrorMode) {
                    case MIRROR_OFF:
                    default:
                        return var4 + tr("vivecraftx.value.off");
                    case MIRROR_ON_CROPPED:
                        return var4 + tr("vivecraftx.value.mirror.cropped");
                    case MIRROR_ON_SINGLE:
                        return var4 + tr("vivecraftx.value.mirror.singleEye");
                    case MIRROR_ON_DUAL:
                        return var4 + tr("vivecraftx.value.mirror.bothEyes");
                    case MIRROR_GUI:
                        return var4 + tr("vivecraftx.value.mirror.interfaceOnly");
                }
            case MIRROR_CROP:
                return var4 + String.format("%.0f%%", new Object[] {
                        Float.valueOf(this.mirrorCrop * 100F) });
            case MIRROR_EYE:
                return var4 + tr(this.displayMirrorLeftEye ? "vivecraftx.value.eye.left" : "vivecraftx.value.eye.right");
            case MIRROR_GUI:
                switch (this.guiOnMirror) {
                    case GUI_MIRROR_HUD_ONLY:
                        return var4 + tr("vivecraftx.value.mirrorGui.hudOnly");
                    case GUI_MIRROR_ALWAYS:
                        return var4 + tr("vivecraftx.value.mirrorGui.always");
                    case GUI_MIRROR_OFF:
                    default:
                        return var4 + tr("vivecraftx.value.off");
                }
            case VR_HOTSWITCH:
                return var4 + onOff(this.vrHotswitchingEnabled);
            case VR_REMEMBER:
                return var4 + onOff(this.rememberVr);
            case VR_TOGGLE_BUTTON:
                return var4 + onOff(this.vrToggleButtonEnabled);
            case VR_CLOSE_WITH_RUNTIME:
                return var4 + tr(this.closeWithRuntime ? "vivecraftx.value.steamVrClosed.quit" : "vivecraftx.value.steamVrClosed.stayFlat");
            case MIXED_REALITY_KEY_COLOR:
                if (this.mixedRealityKeyColor.equals(new Color(0, 0, 0))) {
                	return var4 + tr("vivecraftx.value.color.black");
                } else if (this.mixedRealityKeyColor.equals(new Color(255, 0, 0))) {
                	return var4 + tr("vivecraftx.value.color.red");
                } else if (this.mixedRealityKeyColor.equals(new Color(255, 255, 0))) {
                	return var4 + tr("vivecraftx.value.color.yellow");
                } else if (this.mixedRealityKeyColor.equals(new Color(0, 255, 0))) {
                	return var4 + tr("vivecraftx.value.color.green");
                } else if (this.mixedRealityKeyColor.equals(new Color(0, 255, 255))) {
                	return var4 + tr("vivecraftx.value.color.cyan");
                } else if (this.mixedRealityKeyColor.equals(new Color(0, 0, 255))) {
                	return var4 + tr("vivecraftx.value.color.blue");
                } else if (this.mixedRealityKeyColor.equals(new Color(255, 0, 255))) {
                	return var4 + tr("vivecraftx.value.color.magenta");
                }
                return var4 + this.mixedRealityKeyColor.getRed() + " " + this.mixedRealityKeyColor.getGreen() + " " + this.mixedRealityKeyColor.getBlue();
             case MIXED_REALITY_RENDER_HANDS:
                return var4 + yesNo(this.mixedRealityRenderHands);
            case MIXED_REALITY_UNITY_LIKE:
                 return var4 + tr(this.mixedRealityUnityLike ? "vivecraftx.value.layout.unity" : "vivecraftx.value.layout.sideBySide");
            case MIXED_REALITY_UNDISTORTED:
                return var4 + yesNo(this.mixedRealityMRPlusUndistorted);
            case MIXED_REALITY_ALPHA_MASK:
                return var4 + yesNo(this.mixedRealityAlphaMask);
            case MIXED_REALITY_FOV:
            	return var4 + String.format("%.0f°", new Object[] { Float.valueOf(VR.settings().mixedRealityFov) });
            case INSIDE_BLOCK_SOLID_COLOR:
            	return var4 + tr(this.insideBlockSolidColor ? "vivecraftx.value.insideBlock.solidColor" : "vivecraftx.value.insideBlock.texture");
            case WALK_UP_BLOCKS:
                return var4 + yesNo(this.walkUpBlocks);
 	        case HUD_SCALE:
	            return var4 + String.format("%.2f", new Object[] { Float.valueOf(this.hudScale) });
            case HUD_LOCK_TO:
                switch (this.vrHudLockMode) {
                // VIVE - lock to hand instead of body
                case HUD_LOCK_HAND:
                	return var4 + " " + tr("vivecraftx.value.hudLock.hand");
                case HUD_LOCK_HEAD:
                	return var4 + " " + tr("vivecraftx.value.hudLock.head");
                case HUD_LOCK_WRIST:
                	return var4 + " " + tr("vivecraftx.value.hudLock.wrist");
                case HUD_LOCK_BODY:
                    return var4 + " " + tr("vivecraftx.value.hudLock.body");
                }
	        case HUD_DISTANCE:
	            return var4 + String.format("%.2f", new Object[] { Float.valueOf(this.hudDistance) });
	        case HUD_WRIST_OFFSET:
	            return var4 + String.format("%.2f", new Object[] { Float.valueOf(this.vrHudWristOffset) });
	        case FOV_REDUCTION_MIN:
	            return var4 + String.format("%.2f", new Object[] { Float.valueOf(this.fovReductionMin) });
	        case SMOOTH_TURN_SPEED:
	            return var4 + String.format("%.0f", new Object[] { Float.valueOf(this.vrSmoothTurnSpeed) })
	                    + tr("vivecraftx.value.degreesPerSecond");
	        case JOYSTICK_DEADZONE:
	            return var4 + String.format("%.2f", new Object[] { Float.valueOf(this.vrJoystickDeadzone) });
	        case TELEPORT_UP_LIMIT:
	            return var4 + this.vrTeleportUpLimit;
	        case TELEPORT_DOWN_LIMIT:
	            return var4 + this.vrTeleportDownLimit;
	        case TELEPORT_HORIZ_LIMIT:
	            return var4 + this.vrTeleportHorizLimit;
	        case HUD_PITCH:
	            return var4 + String.format("%.0f", new Object[] { Float.valueOf(this.hudPitchOffset) });
            case HUD_YAW:
            	return var4 + String.format("%.0f", new Object[] { Float.valueOf(this.hudYawOffset) });
            case RENDER_SCALEFACTOR:
            	return var4 + String.format("%.1f", new Object[] { Float.valueOf(this.renderScaleFactor) });
            case FSAA:
            	return var4 + onOff(this.useFsaa);
            case CROSSHAIR_SCALE:
	            return var4 + String.format("%.2f", new Object[] { Float.valueOf(this.crosshairScale) });
            case MENU_CROSSHAIR_SCALE:
                return var4 + String.format("%.2f", new Object[] { Float.valueOf(this.menuCrosshairScale) });
            case CROSSHAIR_SCALES_WITH_DISTANCE:
	        	return var4 + onOff(this.crosshairScalesWithDistance);
	        case RENDER_CROSSHAIR_MODE:
                if (this.renderInGameCrosshairMode == RENDER_CROSSHAIR_MODE_HUD)
                    return var4 + tr("vivecraftx.value.showMode.withHud");
                else if (this.renderInGameCrosshairMode == RENDER_CROSSHAIR_MODE_ALWAYS)
                    return var4 + tr("vivecraftx.value.showMode.always");
                else if (this.renderInGameCrosshairMode == RENDER_CROSSHAIR_MODE_NEVER)
                    return var4 + tr("vivecraftx.value.showMode.never");
	        case RENDER_BLOCK_OUTLINE_MODE:
                if (this.renderBlockOutlineMode == RENDER_BLOCK_OUTLINE_MODE_HUD)
                    return var4 + tr("vivecraftx.value.showMode.withHud");
                else if (this.renderBlockOutlineMode == RENDER_BLOCK_OUTLINE_MODE_ALWAYS)
                    return var4 + tr("vivecraftx.value.showMode.always");
                else if (this.renderBlockOutlineMode == RENDER_BLOCK_OUTLINE_MODE_NEVER)
                    return var4 + tr("vivecraftx.value.showMode.never");
	        case HUD_OCCLUSION:
	        	return var4 + onOff(this.hudOcclusion);
	        case MENU_ALWAYS_FOLLOW_FACE:
	        	return var4 + tr(this.menuAlwaysFollowFace ? "vivecraftx.value.menuFollow.always" : "vivecraftx.value.menuFollow.seated");
	        case CROSSHAIR_OCCLUSION:
	        	return var4 + onOff(this.useCrosshairOcclusion);
	        case MONO_FOV:
	        	return var4 + String.format("%.0f°", new Object[] { Float.valueOf(this.mc.gameSettings.fovSetting) });
	        case INERTIA_FACTOR:
	        	if (this.inertiaFactor == INERTIA_NONE)
	        		return var4 + tr("vivecraftx.value.inertia.automan");
	        	else if (this.inertiaFactor == INERTIA_NORMAL)
	        		return var4 + tr("vivecraftx.value.inertia.normal");
                else if (this.inertiaFactor == INERTIA_LARGE)
                    return var4 + tr("vivecraftx.value.inertia.aLot");
                else if (this.inertiaFactor == INERTIA_MASSIVE)
                    return var4 + tr("vivecraftx.value.inertia.evenMore");
                // VIVE START - new options
            case SIMULATE_FALLING:
                return var4 + onOff(this.simulateFalling);
            case ONLY_SWORD_COLLISION:
                return var4 + tr(this.onlySwordCollision ? "vivecraftx.value.hitsWith.swordsOnly" : "vivecraftx.value.hitsWith.anything");
            case SWORD_BLOCK_COLLISION:
                return var4 + yesNo(this.swordBlockCollision);
            case REDUCED_PLAYER_REACH:
                return var4 + yesNo(this.reducedPlayerReach);
            case REALISTIC_BLOCK_INTERACT:
                return var4 + yesNo(this.realisticBlockInteractEnabled);
            case REALISTIC_ENTITY_INTERACT:
                return var4 + yesNo(this.realisticEntityInteractEnabled);
            case RENDER_OWN_BODY:
                return var4 + yesNo(this.renderOwnBody);
            case SPLIT_ARMS:
                return var4 + yesNo(this.splitArms);
            case WEAPON_COLLISION:
                return var4 + onOff(this.weaponCollision);
            case ANIMAL_TOUCHING:
                return var4 + onOff(this.animaltouching);
                // VIVE END - new options
                //JRBUDDA
            case ALLOW_MODE_SWITCH:
                return var4 + onOff(this.vrAllowLocoModeSwotch);
            case MOVE_MODE:
            	if(this.vrFreeMove == false){
            		return var4 + tr("vivecraftx.value.moveMode.teleport");
            	} else return var4 + tr("vivecraftx.value.moveMode.freeMove");
            case ALLOW_CRAWLING:
                return var4 + onOff(this.vrAllowCrawling);
            case LIMIT_TELEPORT:
                return var4 + onOff(this.vrLimitedSurvivalTeleport);
            case REVERSE_HANDS:
            	return var4 + onOff(this.vrReverseHands);
            case STENCIL_ON:
            	return var4 + onOff(this.vrUseStencil);
            case BCB_ON:
            	return var4 + onOff(this.vrShowBlueCircleBuddy);
            case WORLD_SCALE:
	            return var4 + String.format("%.2f", new Object[] { Float.valueOf(this.vrWorldScale)})+ "x" ;
            case WORLD_ROTATION:
	            return var4 + String.format("%.0f", new Object[] { Float.valueOf(this.vrWorldRotation) });
            case WORLD_ROTATION_INCREMENT:
	            return this.vrWorldRotationIncrement == 0f ? var4 + tr("vivecraftx.value.smooth")
	                    : var4 + String.format("%.0f", new Object[] { Float.valueOf(this.vrWorldRotationIncrement) });
            case TOUCH_HOTBAR:
            	return var4 + onOff(this.vrTouchHotbar);
            case PLAY_MODE_SEATED:
            	return var4 + tr(this.seated ? "vivecraftx.value.playMode.seated" : "vivecraftx.value.playMode.standing");
                //END JRBUDDA
            case REALISTIC_JUMP:
                return var4 + onOff(this.realisticJumpEnabled);
            case SEATED_HMD:
                return var4 + tr(this.seatedUseHMD ? "vivecraftx.value.direction.hmd" : "vivecraftx.value.direction.crosshair");
            case SEATED_HUD_XHAIR:
                return var4 + tr(this.seatedHudAltMode ? "vivecraftx.value.direction.crosshair" : "vivecraftx.value.direction.hmd");
            case REALISTIC_SNEAK:
                return var4 + onOff(this.realisticSneakEnabled);
            case REALISTIC_CLIMB:
                return var4 + onOff(this.realisticClimbEnabled);
            case FIXED_HEIGHT:
                return var4 + onOff(this.vrFixedHeight);
            case WORLD_HAPTICS:
                return var4 + onOff(this.vrWorldHaptics);
            case CHAT_HAPTICS:
                return var4 + onOff(this.vrChatHaptics);
            case REALISTIC_SWIM:
                return var4 + onOff(this.realisticSwimEnabled);
            case REALISTIC_ROW:
                return var4 + onOff(this.realisticRowEnabled);
            case VEHICLE_ROTATION:
                return var4 + onOff(this.vehicleRotation);
            case REALISTIC_DISMOUNT:
                return var4 + onOff(this.realisticDismount);
            case REALISTIC_OPENING:
                return var4 + onOff(this.realisticOpening);
            case PHYSICAL_KEYBOARD:
                return var4 + tr(this.vrPhysicalKeyboard ? "vivecraftx.value.keyboard.inWorld" : "vivecraftx.value.keyboard.steamVr");
            case AUTO_OPEN_KEYBOARD:
                return var4 + onOff(this.autoOpenKeyboard);
            case KEYBOARD_LAYOUT:
                return var4 + VRKeyboardLayout.label(this.keyboardKeys, this.keyboardKeysShift);
            case CALIBRATE_HEIGHT:
                return var2;
            case WALK_MULTIPLIER:
                return var4+ String.format("%.1f",walkMultiplier);
            case X_SENSITIVITY:
                return var4+ String.format("%.2f",xSensitivity);
            case Y_SENSITIVITY:
                return var4+ String.format("%.2f",ySensitivity);
            case KEYHOLE:
                return var4+ String.format("%.0f",keyholeX);
            case RESET_ORIGIN:
                return var2;
            case FREEMOVE_MODE:
                switch (this.vrFreeMoveMode) {
                // VIVE - lock to hand instead of body
                case FREEMOVE_CONTROLLER:
                	return var4 + " " + tr("vivecraftx.value.freemove.controller");
                case FREEMOVE_HMD:
                	return var4 + " " + tr("vivecraftx.value.freemove.hmd");
                case FREEMOVE_RUNINPLACE:
                	return var4 + " " + tr("vivecraftx.value.freemove.runInPlace");
                case FREEMOVE_JOYPAD:
                	return var4 + " " + tr("vivecraftx.value.freemove.joypad");
                }
            case FOV_REDUCTION:
                return var4 + onOff(this.useFOVReduction);
 	        default:
	        	// An option with no case of its own still has to say what it is: an empty string
	        	// here would draw a blank button, which is how an unfinished option used to look.
	        	return var2;
        }
    }

    public float getOptionFloatValue(VRSettings.VrOptions par1EnumOptions)
    {
    	switch( par1EnumOptions ) {
			case MOVEMENT_MULTIPLIER :
				return this.movementSpeedMultiplier ;
			case HUD_SCALE :
				return this.hudScale ;
			case HUD_OPACITY :
				return this.hudOpacity ;
			case HUD_DISTANCE :
				return this.hudDistance ;
			case HUD_WRIST_OFFSET :
				return this.vrHudWristOffset ;
			case FOV_REDUCTION_MIN :
				return this.fovReductionMin ;
			case SMOOTH_TURN_SPEED :
				return this.vrSmoothTurnSpeed ;
			case JOYSTICK_DEADZONE :
				return this.vrJoystickDeadzone ;
			case TELEPORT_UP_LIMIT :
				return this.vrTeleportUpLimit ;
			case TELEPORT_DOWN_LIMIT :
				return this.vrTeleportDownLimit ;
			case TELEPORT_HORIZ_LIMIT :
				return this.vrTeleportHorizLimit ;
			case HUD_PITCH :
				return this.hudPitchOffset ;
            case HUD_YAW :
                return this.hudYawOffset ;
			case CROSSHAIR_SCALE :
				return this.crosshairScale ;
            case MENU_CROSSHAIR_SCALE :
                return this.menuCrosshairScale ;
            case WALK_MULTIPLIER:
                return this.walkMultiplier;
            case X_SENSITIVITY:
                return this.xSensitivity;
            case Y_SENSITIVITY:
                return this.ySensitivity;
            case KEYHOLE:
                return this.keyholeX;
            // VIVE START - new options
            case WORLD_SCALE:          	
            	if(vrWorldScale ==  0.1f) return 0;
            	if(vrWorldScale ==  0.25f) return 1;
            	if(vrWorldScale >=  0.5f && vrWorldScale <=  2.0f) return (vrWorldScale / 0.1f) - 3f;
            	if(vrWorldScale == 3) return 18;
            	if(vrWorldScale == 4) return 19;
            	if(vrWorldScale == 6) return 20;
            	if(vrWorldScale == 8) return 21;
            	if(vrWorldScale == 10) return 22;
            	if(vrWorldScale == 12) return 23;
            	if(vrWorldScale == 16) return 24;
            	if(vrWorldScale == 20) return 25;
            	if(vrWorldScale == 30) return 26;
            	if(vrWorldScale == 50) return 27;
            	if(vrWorldScale == 75) return 28;
            	if(vrWorldScale == 100) return 29;
            	return 7;
            case WORLD_ROTATION:
                return vrWorldRotation;
            case WORLD_ROTATION_INCREMENT:
            	if(vrWorldRotationIncrement == 0f) return 0;
            	if(vrWorldRotationIncrement == 10f) return 1;
            	if(vrWorldRotationIncrement == 36f) return 2;
            	if(vrWorldRotationIncrement == 45f) return 3;
            	if(vrWorldRotationIncrement == 90f) return 4;
            	if(vrWorldRotationIncrement == 180f) return 5;
            	return 0;
            case MONO_FOV:
            	return this.mc.gameSettings.fovSetting;
			case MIXED_REALITY_FOV:
				return this.mixedRealityFov;
            case MIRROR_CROP:
                return this.mirrorCrop;
            case RENDER_SCALEFACTOR:
            	return this.renderScaleFactor;
            // VIVE END - new options
            default:
                return 0.0f;
    	}
    }
    /**
     * For non-float options. Toggles the option on/off, or cycles through the list i.e. render distances.
     */
    public void setOptionValue(VRSettings.VrOptions par1EnumOptions, int par2)
    {
    	switch( par1EnumOptions )
    	{
            case RENDER_MENU_BACKGROUND:
                this.menuBackground = !this.menuBackground;
                break;
	        case HUD_HIDE:
	            this.hideGui = !this.hideGui;
	            break;
	        case CHAT_FADE_AWAY:
	            this.chatFadeAway = !this.chatFadeAway;
	            break;
	        case RENDER_FULL_FIRST_PERSON_MODEL_MODE:
                this.renderFullFirstPersonModelMode++;
                if (this.renderFullFirstPersonModelMode > RENDER_FIRST_PERSON_NONE)
                    this.renderFullFirstPersonModelMode = RENDER_FIRST_PERSON_FULL;
	            break;
             case MIRROR_DISPLAY:
                this.displayMirrorMode++;
                if (this.displayMirrorMode > MIRROR_LAST_SELECTABLE)
                    this.displayMirrorMode = MIRROR_OFF;
                break;
            case MIRROR_EYE:
                this.displayMirrorLeftEye = !this.displayMirrorLeftEye;
                break;
            case MIRROR_GUI:
                this.guiOnMirror = (this.guiOnMirror + 1) % 3;
                break;
            case VR_HOTSWITCH:
                this.vrHotswitchingEnabled = !this.vrHotswitchingEnabled;
                break;
            case VR_REMEMBER:
                this.rememberVr = !this.rememberVr;
                break;
            case VR_TOGGLE_BUTTON:
                this.vrToggleButtonEnabled = !this.vrToggleButtonEnabled;
                break;
            case VR_CLOSE_WITH_RUNTIME:
                this.closeWithRuntime = !this.closeWithRuntime;
                break;
            case MIXED_REALITY_KEY_COLOR:
            	if (this.mixedRealityKeyColor.equals(new Color(0, 0, 0))) {
            		this.mixedRealityKeyColor = new Color(255, 0, 0);
	            } else if (this.mixedRealityKeyColor.equals(new Color(255, 0, 0))) {
	            	this.mixedRealityKeyColor = new Color(255, 255, 0);
	            } else if (this.mixedRealityKeyColor.equals(new Color(255, 255, 0))) {
	            	this.mixedRealityKeyColor = new Color(0, 255, 0);
	            } else if (this.mixedRealityKeyColor.equals(new Color(0, 255, 0))) {
	            	this.mixedRealityKeyColor = new Color(0, 255, 255);
	            } else if (this.mixedRealityKeyColor.equals(new Color(0, 255, 255))) {
	            	this.mixedRealityKeyColor = new Color(0, 0, 255);
	            } else if (this.mixedRealityKeyColor.equals(new Color(0, 0, 255))) {
	            	this.mixedRealityKeyColor = new Color(255, 0, 255);
	            } else if (this.mixedRealityKeyColor.equals(new Color(255, 0, 255))) {
	            	this.mixedRealityKeyColor = new Color(0, 0, 0);
	            } else {
	            	this.mixedRealityKeyColor = new Color(0, 0, 0);
	            }
                break;
            case MIXED_REALITY_RENDER_HANDS:
            	this.mixedRealityRenderHands = !this.mixedRealityRenderHands;
            	break;
            case MIXED_REALITY_UNITY_LIKE:
            	this.mixedRealityUnityLike = !this.mixedRealityUnityLike;
            	VRRenderState.reinitFramebuffers = true;
            	break;
            case MIXED_REALITY_UNDISTORTED:
            	this.mixedRealityMRPlusUndistorted = !this.mixedRealityMRPlusUndistorted;
            	VRRenderState.reinitFramebuffers = true;
            	break;
            case MIXED_REALITY_ALPHA_MASK:
            	this.mixedRealityAlphaMask = !this.mixedRealityAlphaMask;
            	VRRenderState.reinitFramebuffers = true;
            	break;
            case INSIDE_BLOCK_SOLID_COLOR:
            	this.insideBlockSolidColor = !this.insideBlockSolidColor;
            	break;
            case WALK_UP_BLOCKS:
                this.walkUpBlocks = !this.walkUpBlocks;
                break;
             case HUD_LOCK_TO:
                switch (this.vrHudLockMode) {
                // VIVE - lock to hand instead of body
                case HUD_LOCK_HAND:
                	this.vrHudLockMode = HUD_LOCK_HEAD;
                	break;
                case HUD_LOCK_HEAD:
                   	this.vrHudLockMode = HUD_LOCK_WRIST;
                	break;
                case HUD_LOCK_WRIST:
                   	this.vrHudLockMode = HUD_LOCK_HAND;
                	break;
                case HUD_LOCK_BODY:
                    this.vrHudLockMode = HUD_LOCK_HAND;
                }
                break;
	        case FSAA:
	            this.useFsaa = !this.useFsaa;
	            break;
  	        case RENDER_CROSSHAIR_MODE:
	            this.renderInGameCrosshairMode++;
                if (this.renderInGameCrosshairMode > RENDER_CROSSHAIR_MODE_NEVER)
                    this.renderInGameCrosshairMode = RENDER_CROSSHAIR_MODE_ALWAYS;
	            break;
	        case RENDER_BLOCK_OUTLINE_MODE:
                this.renderBlockOutlineMode++;
                if (this.renderBlockOutlineMode > RENDER_BLOCK_OUTLINE_MODE_NEVER)
                    this.renderBlockOutlineMode = RENDER_BLOCK_OUTLINE_MODE_ALWAYS;
	            break;
	            
	        case HUD_OCCLUSION:
	            this.hudOcclusion = !this.hudOcclusion;
	            break;
	        case MENU_ALWAYS_FOLLOW_FACE:
	            this.menuAlwaysFollowFace = !this.menuAlwaysFollowFace;
	            break;
            case CROSSHAIR_OCCLUSION:
                this.useCrosshairOcclusion = !this.useCrosshairOcclusion;
                break;
             case INERTIA_FACTOR:
                this.inertiaFactor +=1;
                if (this.inertiaFactor > INERTIA_MASSIVE)
                    this.inertiaFactor = INERTIA_NONE;
                break;
             // VIVE START - new options
            case SIMULATE_FALLING:
                this.simulateFalling = !this.simulateFalling;
                break;
            case WEAPON_COLLISION:
                this.weaponCollision = !this.weaponCollision;
                break;
            case ONLY_SWORD_COLLISION:
                this.onlySwordCollision = !this.onlySwordCollision;
                break;
            case SWORD_BLOCK_COLLISION:
                this.swordBlockCollision = !this.swordBlockCollision;
                break;
            case REDUCED_PLAYER_REACH:
                this.reducedPlayerReach = !this.reducedPlayerReach;
                break;
            case REALISTIC_BLOCK_INTERACT:
                this.realisticBlockInteractEnabled = !this.realisticBlockInteractEnabled;
                break;
            case RENDER_OWN_BODY:
                this.renderOwnBody = !this.renderOwnBody;
                break;
            case SPLIT_ARMS:
                this.splitArms = !this.splitArms;
                break;
            case REALISTIC_ENTITY_INTERACT:
                this.realisticEntityInteractEnabled = !this.realisticEntityInteractEnabled;
                break;
            case ANIMAL_TOUCHING:
                this.animaltouching = !this.animaltouching;
                break;
            // VIVE END - new options
                //JRBUDDA
            case ALLOW_MODE_SWITCH:
                this.vrAllowLocoModeSwotch = !this.vrAllowLocoModeSwotch;
                break;
            case MOVE_MODE:
                this.vrFreeMove = !this.vrFreeMove;
                VR.player().setFreeMoveMode(vrFreeMove);
                break;
            case ALLOW_CRAWLING:
                this.vrAllowCrawling = !this.vrAllowCrawling;
                break;
            case LIMIT_TELEPORT:
                this.vrLimitedSurvivalTeleport = !this.vrLimitedSurvivalTeleport;
                break;
            case REVERSE_HANDS:
                this.vrReverseHands = !this.vrReverseHands;
                break;
            case STENCIL_ON:
                this.vrUseStencil = !this.vrUseStencil;
                break;
            case BCB_ON:
                this.vrShowBlueCircleBuddy = !this.vrShowBlueCircleBuddy;
                break;
            case TOUCH_HOTBAR:
                this.vrTouchHotbar = !this.vrTouchHotbar;
                break;
            case PLAY_MODE_SEATED:
                this.seated = !this.seated;
                break;
                //JRBUDDA
            case REALISTIC_JUMP:
                realisticJumpEnabled = !realisticJumpEnabled;
                break;
            case SEATED_HMD:
                seatedUseHMD = !seatedUseHMD;
                break;
            case SEATED_HUD_XHAIR:
                seatedHudAltMode = !seatedHudAltMode;
                break;
            case REALISTIC_SWIM:
                realisticSwimEnabled = !realisticSwimEnabled;
                break;
            case REALISTIC_CLIMB:
                realisticClimbEnabled = !realisticClimbEnabled;
                break;
            case FIXED_HEIGHT:
                vrFixedHeight = !vrFixedHeight;
                break;
            case WORLD_HAPTICS:
                vrWorldHaptics = !vrWorldHaptics;
                break;
            case CHAT_HAPTICS:
                vrChatHaptics = !vrChatHaptics;
                break;
            case REALISTIC_ROW:
                realisticRowEnabled = !realisticRowEnabled;
                break;
            case REALISTIC_SNEAK:
                realisticSneakEnabled = !realisticSneakEnabled;
                break;
            case VEHICLE_ROTATION:
                vehicleRotation = !vehicleRotation;
                break;
            case REALISTIC_DISMOUNT:
                realisticDismount = !realisticDismount;
                break;
            case REALISTIC_OPENING:
                realisticOpening = !realisticOpening;
                break;
            case PHYSICAL_KEYBOARD:
                vrPhysicalKeyboard = !vrPhysicalKeyboard;
                break;
            case AUTO_OPEN_KEYBOARD:
                autoOpenKeyboard = !autoOpenKeyboard;
                break;
            case KEYBOARD_LAYOUT:
                String[] nextLayout = VRKeyboardLayout.next(keyboardKeys, keyboardKeysShift);
                keyboardKeys = nextLayout[0];
                keyboardKeysShift = nextLayout[1];
                break;
            case CALIBRATE_HEIGHT:
                if(seated) {
                    MCOpenVR.resetPosition();
                }
                playerEyeHeight = (float) VR.roomScale().getHMDPos_Room().yCoord;
                // The figure the rest of the mod actually reads. Setting only the one above
                // left the guessed height in charge, so calibrating by hand did nothing that
                // anything else could see -- including the fixed view height, which is built
                // entirely on knowing how tall the player is when standing.
                AutoCalibration.calibrateManual();
                break;
            case FREEMOVE_MODE:
                switch (this.vrFreeMoveMode) {
                case FREEMOVE_CONTROLLER:
                	this.vrFreeMoveMode = FREEMOVE_HMD;
                	break;
                case FREEMOVE_HMD:
                   	this.vrFreeMoveMode = FREEMOVE_RUNINPLACE;
                	break;
                case FREEMOVE_RUNINPLACE:
                	this.vrFreeMoveMode = FREEMOVE_JOYPAD;
                	break;
                case FREEMOVE_JOYPAD:
                	this.vrFreeMoveMode = FREEMOVE_CONTROLLER;
                	break;
                }
                break;
            case FOV_REDUCTION:
            	useFOVReduction = !useFOVReduction;
            	break;     
            case CROSSHAIR_SCALES_WITH_DISTANCE:
            	crosshairScalesWithDistance = !crosshairScalesWithDistance;
            	break;
            default:
            	break;
    	}

        this.saveOptions();
    }

    public void setOptionFloatValue(VRSettings.VrOptions par1EnumOptions, float par2)
    {
    	switch( par1EnumOptions ) {
	        case MOVEMENT_MULTIPLIER:
	            this.movementSpeedMultiplier = par2;
	            break;
	        case HUD_SCALE:
	            this.hudScale = par2;
	        	break;
	        case HUD_OPACITY:
	            this.hudOpacity = par2;
	        	break;
	        case HUD_DISTANCE:
	            this.hudDistance = par2;
	        	break;
	        case HUD_WRIST_OFFSET:
	            this.vrHudWristOffset = par2;
	            break;
	        case FOV_REDUCTION_MIN:
	            this.fovReductionMin = par2;
	            break;
	        case SMOOTH_TURN_SPEED:
	            this.vrSmoothTurnSpeed = par2;
	            break;
	        case JOYSTICK_DEADZONE:
	            this.vrJoystickDeadzone = par2;
	            break;
	        case TELEPORT_UP_LIMIT:
	            this.vrTeleportUpLimit = (int) par2;
	            break;
	        case TELEPORT_DOWN_LIMIT:
	            this.vrTeleportDownLimit = (int) par2;
	            break;
	        case TELEPORT_HORIZ_LIMIT:
	            this.vrTeleportHorizLimit = (int) par2;
	            break;
	        case HUD_PITCH:
	            this.hudPitchOffset = par2;
	        	break;
            case HUD_YAW:
                this.hudYawOffset = par2;
                break;
	        case CROSSHAIR_SCALE:
	            this.crosshairScale = par2;
	        	break;
            case MENU_CROSSHAIR_SCALE:
                this.menuCrosshairScale = par2;
                break;
            case WALK_MULTIPLIER:
                this.walkMultiplier=par2;
                break;
            // VIVE START - new options
            case WORLD_SCALE:
            	if(par2 ==  0) vrWorldScale = 0.1f;
            	else if(par2 ==  1) vrWorldScale = 0.25f;
            	else if(par2 >=  2 && par2 <=  17) vrWorldScale = (float) (par2 * 0.1 + 0.3);
            	else if(par2 == 18) vrWorldScale = 3f;
            	else if(par2 == 19) vrWorldScale = 4f;
            	else if(par2 == 20) vrWorldScale = 6f;
            	else if(par2 == 21) vrWorldScale = 8f;
            	else if(par2 == 22) vrWorldScale = 10f;
            	else if(par2 == 23) vrWorldScale = 12f;
            	else if(par2 == 24) vrWorldScale = 16f;
            	else if(par2 == 25) vrWorldScale = 20f;
               	else if(par2 == 26) vrWorldScale = 30f;
               	else if(par2 == 27) vrWorldScale = 50f;
               	else if(par2 == 28) vrWorldScale = 75f;
               	else if(par2 == 29) vrWorldScale = 100f;           	         	
            	else vrWorldScale = 1;           	
                break;
            case WORLD_ROTATION:
                this.vrWorldRotation = par2;
                break;
            case WORLD_ROTATION_INCREMENT:
            	// Zero is not "no turning" -- it is turning that is not in steps at all, which
            	// is why it is first in the cycle rather than hidden in a config file.
            	if(par2 == 0f) this.vrWorldRotationIncrement =  0f;
            	if(par2 == 1f) this.vrWorldRotationIncrement =  10f;
            	if(par2 == 2f) this.vrWorldRotationIncrement =  36f;
            	if(par2 == 3f) this.vrWorldRotationIncrement =  45f;
            	if(par2 == 4f) this.vrWorldRotationIncrement =  90f;
            	if(par2 == 5f) this.vrWorldRotationIncrement =  180f;
                break;
            case X_SENSITIVITY:
                this.xSensitivity=par2;
                break;
            case Y_SENSITIVITY:
                this.ySensitivity=par2;
                break;
            case KEYHOLE:
            	this.keyholeX=par2;
            	break;
            case MONO_FOV:
            	this.mc.gameSettings.fovSetting = par2;
            	break;
	        case MIXED_REALITY_FOV:
	            this.mixedRealityFov = par2;
	        	break;
            case MIRROR_CROP:
                this.mirrorCrop = par2;
                break;
            case RENDER_SCALEFACTOR:
            	this.renderScaleFactor = par2;
            	break;
            	// VIVE END - new options
            default:
            	break;
    	}
	
        this.saveOptions();
    }



    public void saveOptions()
    {
        saveOptions(null); // Use null for current profile
    }

    private void storeDefaults()
    {
        saveOptions(this.defaults);
    }

    private void saveOptions(JSONObject theProfiles)
    {
        // Save Minecrift settings
        try
        {
            ProfileWriter var5 = new ProfileWriter(ProfileManager.PROFILE_SET_VR, theProfiles);

            var5.println("version:" + version);
            var5.println("newlyCreated:" + false );
            //var5.println("firstLoad:" + this.firstLoad );  
            var5.println("playerEyeHeight:" + this.playerEyeHeight);
            var5.println("stereoProviderPluginID:"+ this.stereoProviderPluginID);
            var5.println("badStereoProviderPluginID:"+ this.badStereoProviderPluginID);
            var5.println("hudOpacity:" + this.hudOpacity);
            var5.println("menuBackground:" + this.menuBackground);
            var5.println("renderFullFirstPersonModelMode:" + this.renderFullFirstPersonModelMode);
            var5.println("shaderIndex:" + this.shaderIndex);
            var5.println("displayMirrorMode:" + mirrorModeName(this.displayMirrorMode));
            var5.println("mirrorCrop:" + this.mirrorCrop);
            var5.println("displayMirrorLeftEye:" + this.displayMirrorLeftEye);
            var5.println("guiOnMirror:" + this.guiOnMirror);
            var5.println("vrEnabled:" + this.vrEnabled);
            var5.println("rememberVr:" + this.rememberVr);
            var5.println("vrHotswitching:" + this.vrHotswitchingEnabled);
            var5.println("vrToggleButton:" + this.vrToggleButtonEnabled);
            var5.println("closeWithRuntime:" + this.closeWithRuntime);
            var5.println("mixedRealityKeyColor:" + this.mixedRealityKeyColor.getRed() + "," + this.mixedRealityKeyColor.getGreen() + "," + this.mixedRealityKeyColor.getBlue());
            var5.println("mixedRealityRenderHands:" + this.mixedRealityRenderHands);
            var5.println("mixedRealityUnityLike:" + this.mixedRealityUnityLike);
            var5.println("mixedRealityUndistorted:" + this.mixedRealityMRPlusUndistorted);
            var5.println("mixedRealityAlphaMask:" + this.mixedRealityAlphaMask);
            var5.println("mixedRealityFov:" + this.mixedRealityFov);
            var5.println("insideBlockSolidColor:" + this.insideBlockSolidColor);
            var5.println("walkUpBlocks:" + this.walkUpBlocks);
            var5.println("hudScale:" + this.hudScale);
            var5.println("renderScaleFactor:" + this.renderScaleFactor);
            var5.println("vrHudLockMode:" + this.vrHudLockMode);
            var5.println("hudDistance:" + this.hudDistance);
            var5.println("vrHudWristOffset:" + this.vrHudWristOffset);
            var5.println("vrTeleportUpLimit:" + this.vrTeleportUpLimit);
            var5.println("vrTeleportDownLimit:" + this.vrTeleportDownLimit);
            var5.println("vrTeleportHorizLimit:" + this.vrTeleportHorizLimit);
            var5.println("hudPitchOffset:" + this.hudPitchOffset);
            var5.println("hudYawOffset:" + this.hudYawOffset);
            var5.println("useFsaa:" + this.useFsaa);
            var5.println("movementSpeedMultiplier:" + this.movementSpeedMultiplier);
            var5.println("renderInGameCrosshairMode:" + this.renderInGameCrosshairMode);
            var5.println("renderBlockOutlineMode:" + this.renderBlockOutlineMode);
            var5.println("hudOcclusion:" + this.hudOcclusion);
            var5.println("analogMovement:" + this.analogMovement);
            var5.println("autoSprint:" + this.autoSprint);
            var5.println("autoSprintThreshold:" + this.autoSprintThreshold);
            var5.println("menuAlwaysFollowFace:" + this.menuAlwaysFollowFace);
            var5.println("useCrosshairOcclusion:" + this.useCrosshairOcclusion);
            var5.println("crosshairScale:" + this.crosshairScale);
            var5.println("menuCrosshairScale:" + this.menuCrosshairScale);
            var5.println("crosshairScalesWithDistance:" + this.crosshairScalesWithDistance);
            var5.println("inertiaFactor:" + this.inertiaFactor);
            var5.println("smoothRunTickCount:" + this.smoothRunTickCount);
            var5.println("smoothTick:" + this.smoothTick);
            var5.println("hideGui:" + this.hideGui);
            var5.println("chatFadeAway:" + this.chatFadeAway);
            //VIVE
            var5.println("simulateFalling:" + this.simulateFalling);
            var5.println("weaponCollision:" + this.weaponCollision);
            var5.println("onlySwordCollision:" + this.onlySwordCollision);
            var5.println("swordBlockCollision:" + this.swordBlockCollision);
            var5.println("reducedPlayerReach:" + this.reducedPlayerReach);
            var5.println("realisticBlockInteract:" + this.realisticBlockInteractEnabled);
            var5.println("realisticEntityInteract:" + this.realisticEntityInteractEnabled);
            var5.println("renderOwnBody:" + this.renderOwnBody);
            var5.println("splitArms:" + this.splitArms);
            var5.println("animalTouching:" + this.animaltouching);
            //END VIVE
            
            //JRBUDDA
            var5.println("allowCrawling:" + this.vrAllowCrawling);
            var5.println("allowModeSwitch:" + this.vrAllowLocoModeSwotch);   
            var5.println("freeMoveDefault:" + this.vrFreeMove);
            var5.println("limitedTeleport:" + this.vrLimitedSurvivalTeleport);
            var5.println("reverseHands:" + this.vrReverseHands);
            var5.println("stencilOn:" + this.vrUseStencil);
            var5.println("bcbOn:" + this.vrShowBlueCircleBuddy);
            var5.println("worldScale:" + this.vrWorldScale);
            var5.println("worldRotation:" + this.vrWorldRotation);
            var5.println("vrWorldRotationIncrement:" + this.vrWorldRotationIncrement);
            var5.println("vrFixedCamposX:" + this.vrFixedCamposX);
            var5.println("vrFixedCamposY:" + this.vrFixedCamposY);
            var5.println("vrFixedCamposZ:" + this.vrFixedCamposZ);
            var5.println("vrFixedCamrotPitch:" + this.vrFixedCamrotPitch);
            var5.println("vrFixedCamrotYaw:" + this.vrFixedCamrotYaw);
            var5.println("vrFixedCamrotRoll:" + this.vrFixedCamrotRoll);
            var5.println("mrMovingCamOffsetX:" + this.mrMovingCamOffsetX);
            var5.println("mrMovingCamOffsetY:" + this.mrMovingCamOffsetY);
            var5.println("mrMovingCamOffsetZ:" + this.mrMovingCamOffsetZ);
            var5.println("mrMovingCamOffsetPitch:" + this.mrMovingCamOffsetPitch);
            var5.println("mrMovingCamOffsetYaw:" + this.mrMovingCamOffsetYaw);
            var5.println("mrMovingCamOffsetRoll:" + this.mrMovingCamOffsetRoll);
            var5.println("vrTouchHotbar:" + this.vrTouchHotbar);
            var5.println("seatedhmd:" + this.seatedUseHMD);
            var5.println("seatedHudAltMode:" + this.seatedHudAltMode);
            var5.println("seated:" + this.seated);
            var5.println("jumpThreshold:" + this.jumpThreshold);
            var5.println("sneakThreshold:" + this.sneakThreshold);
            var5.println("realisticJumpEnabled:" + this.realisticJumpEnabled);
            var5.println("realisticSwimEnabled:" + this.realisticSwimEnabled);
            var5.println("realisticClimbEnabled:" + this.realisticClimbEnabled);
            var5.println("vrFixedHeight:" + this.vrFixedHeight);
            var5.println("vrWorldHaptics:" + this.vrWorldHaptics);
            var5.println("vrChatHaptics:" + this.vrChatHaptics);
            var5.println("fovReductionMin:" + this.fovReductionMin);
            var5.println("vrSmoothTurnSpeed:" + this.vrSmoothTurnSpeed);
            var5.println("vrJoystickDeadzone:" + this.vrJoystickDeadzone);
            var5.println("realisticRowEnabled:" + this.realisticRowEnabled);
            var5.println("realisticSneakEnabled:" + this.realisticSneakEnabled);
            var5.println("headToHmdLength:" + this.headToHmdLength);
            var5.println("walkMultiplier:" + this.walkMultiplier);
            var5.println("vrFreeMoveMode:" + this.vrFreeMoveMode);
            var5.println("xSensitivity:" + this.xSensitivity);
            var5.println("ySensitivity:" + this.ySensitivity);
            var5.println("keyholeX:" + this.keyholeX);
            var5.println("autoCalibration:" + this.autoCalibration);
            var5.println("manualCalibration:" + this.manualCalibration);
            var5.println("vehicleRotation:" + this.vehicleRotation);
            var5.println("realisticDismount:" + this.realisticDismount);
            var5.println("realisticOpening:" + this.realisticOpening);
            var5.println("vrPhysicalKeyboard:" + this.vrPhysicalKeyboard);
            var5.println("autoOpenKeyboard:" + this.autoOpenKeyboard);
            var5.println("keyboardKeys:" + this.keyboardKeys);
            var5.println("keyboardKeysShift:" + this.keyboardKeysShift);
            for (int i = 0; i < this.vrRadialActions.length; i++) {
                var5.println("radialAction" + i + ":" + this.vrRadialActions[i]);
            }
            var5.println("fovReduction:" + this.useFOVReduction);
            var5.println("forceHardwareDetection:" + this.forceHardwareDetection);

            if (vrQuickCommands == null) vrQuickCommands = getQuickCommandsDefaults(); //defaults
            
            // Every slot, not the first eleven of twelve: the editor offers twelve and the radial
            // menu shows twelve, so one of them was editable and then thrown away on save.
            for (int i = 0; i < vrQuickCommands.length ; i++){
            	var5.println("QUICKCOMMAND_" + i + ":" + vrQuickCommands[i]);
            }
   
           
            if (buttonMappings == null) resetBindings(); //defaults
              
            for (int i = 0; i<buttonMappings.length;i++){
            	VRControllerButtonMapping vb = buttonMappings[i];
            	var5.println(vb.toString());
			}
            
            //END JRBUDDA
            var5.close();
        }
        catch (Exception var3)
        {
            logger.warn("Failed to save VR options: " + var3.getMessage());
            var3.printStackTrace();
        }
    }

    public void resetBindings(){
    	buttonMappings = getBindingsDefaults();
    	processBindings();
    }
    
  
    public void setMinecraftPlayerEyeHeight(float eyeHeight)
    {
        this.playerEyeHeight = eyeHeight;
    }

    public float getMinecraftPlayerEyeHeight(){
        return playerEyeHeight;
    }



    /**
     * Parses a string into a float.
     */
    private float parseFloat(String par1Str)
    {
        return par1Str.equals("true") ? 1.0F : (par1Str.equals("false") ? 0.0F : Float.parseFloat(par1Str));
    }

    public float getHeadTrackSensitivity()
    {
        //if (this.useQuaternions)
            return 1.0f;

        //return this.headTrackSensitivity;  // TODO: If head track sensitivity is working again... if
    }

    public static double getInertiaAddFactor(int inertiaFactor)
    {
        float addFac = INERTIA_NORMAL_ADD_FACTOR;
        switch (inertiaFactor)
        {
            case INERTIA_NONE:
                addFac = INERTIA_NONE_ADD_FACTOR;
                break;
            case INERTIA_LARGE:
                addFac = INERTIA_LARGE_ADD_FACTOR;
                break;
            case INERTIA_MASSIVE:
                addFac = INERTIA_MASSIVE_ADD_FACTOR;
                break;
        }
        return addFac;
    }


    public static enum VrOptions
    {
        HUD_SCALE("vivecraftx.option.hudScale", true, false),
        HUD_DISTANCE("vivecraftx.option.hudDistance", true, false),
        HUD_WRIST_OFFSET("vivecraftx.option.hudWristOffset", true, false),
        FIXED_HEIGHT("vivecraftx.option.fixedHeight", false, true),
        WORLD_HAPTICS("vivecraftx.option.worldHaptics", false, true),
        CHAT_HAPTICS("vivecraftx.option.chatHaptics", false, true),
        REALISTIC_DISMOUNT("vivecraftx.option.realisticDismount", false, true),
        REALISTIC_OPENING("vivecraftx.option.realisticOpening", false, true),
        PHYSICAL_KEYBOARD("vivecraftx.option.physicalKeyboard", false, true),
        AUTO_OPEN_KEYBOARD("vivecraftx.option.autoOpenKeyboard", false, true),
        KEYBOARD_LAYOUT("vivecraftx.option.keyboardLayout", false, true),
        FOV_REDUCTION_MIN("vivecraftx.option.fovReductionMin", true, false),
        SMOOTH_TURN_SPEED("vivecraftx.option.smoothTurnSpeed", true, false),
        TELEPORT_UP_LIMIT("vivecraftx.option.teleportUpLimit", true, false),
        TELEPORT_DOWN_LIMIT("vivecraftx.option.teleportDownLimit", true, false),
        TELEPORT_HORIZ_LIMIT("vivecraftx.option.teleportHorizLimit", true, false),
        HUD_PITCH("vivecraftx.option.hudPitch", true, false),
        HUD_YAW("vivecraftx.option.hudYaw", true, false),
        HUD_LOCK_TO("vivecraftx.option.hudLockTo", false, true),
        HUD_OPACITY("vivecraftx.option.hudOpacity", true, false),
        RENDER_MENU_BACKGROUND("vivecraftx.option.renderMenuBackground", false, true),
        HUD_HIDE("vivecraftx.option.hudHide", false, true),
        HUD_OCCLUSION("vivecraftx.option.hudOcclusion", false, true),
        MENU_ALWAYS_FOLLOW_FACE("vivecraftx.option.menuAlwaysFollowFace", false, true),
        CROSSHAIR_OCCLUSION("vivecraftx.option.crosshairOcclusion", false, true),
        CHAT_FADE_AWAY("vivecraftx.option.chatFadeAway", false, true),
        DUMMY("vivecraftx.option.dummy", false, true),
        DUMMY_SMALL("vivecraftx.option.dummySmall", false, true),
        VR_RENDERER("vivecraftx.option.vrRenderer", false, true),
        VR_HEAD_ORIENTATION("vivecraftx.option.vrHeadOrientation", false, true),
        VR_HEAD_POSITION("vivecraftx.option.vrHeadPosition", false, true),
        VR_CONTROLLER("vivecraftx.option.vrController", false, true),
        CROSSHAIR_SCALE("vivecraftx.option.crosshairScale", true, false),
        MENU_CROSSHAIR_SCALE("vivecraftx.option.menuCrosshairScale", true, false),
        RENDER_CROSSHAIR_MODE("vivecraftx.option.renderCrosshairMode", false, true),
        CROSSHAIR_ROLL("vivecraftx.option.crosshairRoll", false, true),
        CROSSHAIR_SCALES_WITH_DISTANCE("vivecraftx.option.crosshairScalesWithDistance", false, true),
        RENDER_BLOCK_OUTLINE_MODE("vivecraftx.option.renderBlockOutlineMode", false, true),
        LOAD_MUMBLE_LIB("vivecraftx.option.loadMumbleLib", false, true),
        RENDER_OWN_HEADWEAR("vivecraftx.option.renderOwnHeadwear", false, true),
        RENDER_FULL_FIRST_PERSON_MODEL_MODE("vivecraftx.option.renderFullFirstPersonModelMode", false, true),
        RENDER_PLAYER_OFFSET("vivecraftx.option.renderPlayerOffset", true, false),


        //HMD/render
        FSAA("vivecraftx.option.fsaa", false, true),
        MIRROR_DISPLAY("vivecraftx.option.mirrorDisplay", false, true),
        MIRROR_CROP("vivecraftx.option.mirrorCrop", true, false),
        MIRROR_EYE("vivecraftx.option.mirrorEye", false, true),
        MIRROR_GUI("vivecraftx.option.mirrorGui", false, true),
        VR_HOTSWITCH("vivecraftx.option.vrHotswitch", false, true),
        VR_REMEMBER("vivecraftx.option.vrRemember", false, true),
        VR_TOGGLE_BUTTON("vivecraftx.option.vrToggleButton", false, true),
        VR_CLOSE_WITH_RUNTIME("vivecraftx.option.vrCloseWithRuntime", false, true),
        MIXED_REALITY_KEY_COLOR("vivecraftx.option.mixedRealityKeyColor", false, false),
        MIXED_REALITY_RENDER_HANDS("vivecraftx.option.mixedRealityRenderHands", false, true),
        MIXED_REALITY_UNITY_LIKE("vivecraftx.option.mixedRealityUnityLike", false, true),
        MIXED_REALITY_UNDISTORTED("vivecraftx.option.mixedRealityUndistorted", false, true),
        MIXED_REALITY_ALPHA_MASK("vivecraftx.option.mixedRealityAlphaMask", false, true),
        MIXED_REALITY_FOV("vivecraftx.option.mixedRealityFov", true, false),
        
        INSIDE_BLOCK_SOLID_COLOR("vivecraftx.option.insideBlockSolidColor", false, true),
        WALK_UP_BLOCKS("vivecraftx.option.walkUpBlocks", false, true),
        //Movement/aiming controls
        DECOUPLE_LOOK_MOVE("vivecraftx.option.decoupleLookMove", false, true),
        MOVEMENT_MULTIPLIER("vivecraftx.option.movementMultiplier", true, false),
        STRAFE_MULTIPLIER("vivecraftx.option.strafeMultiplier", true, false),
        PITCH_AFFECTS_CAMERA("vivecraftx.option.pitchAffectsCamera", false, true),
        JOYSTICK_DEADZONE("vivecraftx.option.joystickDeadzone",true,false),
        KEYHOLE_HEAD_RELATIVE("vivecraftx.option.keyholeHeadRelative",false,true),
        MOUSE_AIM_TYPE("vivecraftx.option.mouseAimType",false,true),
        CROSSHAIR_HEAD_RELATIVE("vivecraftx.option.crosshairHeadRelative",false,true),
        MOVEAIM_HYDRA_USE_CONTROLLER_ONE("vivecraftx.option.moveaimHydraUseControllerOne", false, true),
        JOYSTICK_AIM_TYPE("vivecraftx.option.joystickAimType", false, false),
        AIM_PITCH_OFFSET("vivecraftx.option.aimPitchOffset",true,false),
        INERTIA_FACTOR("vivecraftx.option.inertiaFactor",false,true),

        // VIVE START - new options
        SIMULATE_FALLING("vivecraftx.option.simulateFalling", false, true),
        WEAPON_COLLISION("vivecraftx.option.weaponCollision", false, true),
        ONLY_SWORD_COLLISION("vivecraftx.option.onlySwordCollision", false, true),
        SWORD_BLOCK_COLLISION("vivecraftx.option.swordBlockCollision", false, true),
        REDUCED_PLAYER_REACH("vivecraftx.option.reducedPlayerReach", false, true),
        REALISTIC_BLOCK_INTERACT("vivecraftx.option.realisticBlockInteract", false, true),
        REALISTIC_ENTITY_INTERACT("vivecraftx.option.realisticEntityInteract", false, true),
        RENDER_OWN_BODY("vivecraftx.option.renderOwnBody", false, true),
        SPLIT_ARMS("vivecraftx.option.splitArms", false, true),
        ANIMAL_TOUCHING("vivecraftx.option.animalTouching", false, true),
        // VIVE END - new options

        //JRBUDDA VIVE
        ALLOW_CRAWLING("vivecraftx.option.allowCrawling",false, true),
        ALLOW_MODE_SWITCH("vivecraftx.option.allowModeSwitch",false, true),
        MOVE_MODE("vivecraftx.option.moveMode",false, true),
        LIMIT_TELEPORT("vivecraftx.option.limitTeleport",false, true),
        REVERSE_HANDS("vivecraftx.option.reverseHands",false, true),
        STENCIL_ON("vivecraftx.option.stencilOn", false, true), 
        BCB_ON("vivecraftx.option.bcbOn", false, true),    
        WORLD_SCALE("vivecraftx.option.worldScale", true, false),
        WORLD_ROTATION("vivecraftx.option.worldRotation", true, false),
        WORLD_ROTATION_INCREMENT("vivecraftx.option.worldRotationIncrement", true, false),
        TOUCH_HOTBAR("vivecraftx.option.touchHotbar", false, true),
        PLAY_MODE_SEATED("vivecraftx.option.playModeSeated", false, true),
        RENDER_SCALEFACTOR("vivecraftx.option.renderScalefactor", true, false),
        MONO_FOV("vivecraftx.option.monoFov", true, false),
        //END JRBUDDA
        REALISTIC_JUMP("vivecraftx.option.realisticJump",false,true),
        REALISTIC_SNEAK("vivecraftx.option.realisticSneak",false,true),
        REALISTIC_CLIMB("vivecraftx.option.realisticClimb",false,true),
        REALISTIC_SWIM("vivecraftx.option.realisticSwim",false,true),
        REALISTIC_ROW("vivecraftx.option.realisticRow",false,true),
        CALIBRATE_HEIGHT("vivecraftx.option.calibrateHeight",false,true),
        WALK_MULTIPLIER("vivecraftx.option.walkMultiplier",true,false),
        FREEMOVE_MODE("vivecraftx.option.freemoveMode", false, true),
        VEHICLE_ROTATION("vivecraftx.option.vehicleRotation",false,true),
        //SEATED
        RESET_ORIGIN("vivecraftx.option.resetOrigin",false,true),
        X_SENSITIVITY("vivecraftx.option.xSensitivity",true,false),
        Y_SENSITIVITY("vivecraftx.option.ySensitivity",true,false),
        KEYHOLE("vivecraftx.option.keyhole",true,false),
        FOV_REDUCTION("vivecraftx.option.fovReduction",false,true),
        // OTher buttons
        OTHER_HUD_SETTINGS("vivecraftx.option.otherHudSettings", false, true),
        OTHER_RENDER_SETTINGS("vivecraftx.option.otherRenderSettings", false, true),
        LOCOMOTION_SETTINGS("vivecraftx.option.locomotionSettings", false, true), 
        SEATED_HMD("vivecraftx.option.seatedHmd",false,true),
        SEATED_HUD_XHAIR("vivecraftx.option.seatedHudXhair",false,true); 

//        ANISOTROPIC_FILTERING("options.anisotropicFiltering", true, false, 1.0F, 16.0F, 0.0F)
//                {
//                    protected float snapToStep(float p_148264_1_)
//                    {
//                        return (float) MathHelper.roundUpToPowerOfTwo((int) p_148264_1_);
//                    }
//                },

        private final boolean enumFloat;
        private final boolean enumBoolean;
        private final String enumString;
        private final float valueStep;
        private float valueMin;
        private float valueMax;


        public static VRSettings.VrOptions getEnumOptions(int par0)
        {
            VRSettings.VrOptions[] aoptions = values();
            int j = aoptions.length;

            for (int k = 0; k < j; ++k)
            {
                VRSettings.VrOptions options = aoptions[k];

                if (options.returnEnumOrdinal() == par0)
                {
                    return options;
                }
            }

            return null;
        }

        private VrOptions(String par3Str, boolean isfloat, boolean isbool)
        {
            this(par3Str, isfloat, isbool, 0.0F, 1.0F, 0.0F);
        }

        private VrOptions(String p_i45004_3_, boolean p_i45004_4_, boolean p_i45004_5_, float p_i45004_6_, float p_i45004_7_, float p_i45004_8_)
        {
            this.enumString = p_i45004_3_;
            this.enumFloat = p_i45004_4_;
            this.enumBoolean = p_i45004_5_;
            this.valueMin = p_i45004_6_;
            this.valueMax = p_i45004_7_;
            this.valueStep = p_i45004_8_;
        }
        
        public boolean getEnumFloat()
        {
            return this.enumFloat;
        }

        public boolean getEnumBoolean()
        {
            return this.enumBoolean;
        }

        public int returnEnumOrdinal()
        {
            return this.ordinal();
        }

        /**
         * The option's lang key, which doubles as its stable identifier: the screens compare
         * these to decide which buttons to grey out, so this deliberately returns the key and
         * not the translation.
         */
        public String getEnumString()
        {
            return this.enumString;
        }

        /** The option's own name as the player reads it, without its current value. */
        public String getDisplayName()
        {
            return I18n.format(this.enumString);
        }

        public float getValueMax()
        {
            return this.valueMax;
        }

        public void setValueMax(float p_148263_1_)
        {
            this.valueMax = p_148263_1_;
        }

        protected float snapToStep(float p_148264_1_)
        {
            if (this.valueStep > 0.0F)
            {
                p_148264_1_ = this.valueStep * (float)Math.round(p_148264_1_ / this.valueStep);
            }

            return p_148264_1_;
        }

        VrOptions(String p_i45005_3_, boolean p_i45005_4_, boolean p_i45005_5_, float p_i45005_6_, float p_i45005_7_, float p_i45005_8_, Object p_i45005_9_)
        {
            this(p_i45005_3_, p_i45005_4_, p_i45005_5_, p_i45005_6_, p_i45005_7_, p_i45005_8_);
        }
    }

    public static synchronized void initSettings( Minecraft mc, File dataDir )
    {
        ProfileManager.init(dataDir);
        mc.gameSettings = new GameSettings( mc, dataDir );
       // mc.gameSettings.saveOptions();
        VR.setSettings(new VRSettings( mc, dataDir ));
        VR.settings().saveOptions();
    }

    public static synchronized void loadAll( Minecraft mc )
    {
        mc.gameSettings.loadOptions();
        VR.settings().loadOptions();
    }

    public static synchronized void saveAll( Minecraft mc )
    {
        mc.gameSettings.saveOptions();
        VR.settings().saveOptions();
    }

    public static synchronized void resetAll( Minecraft mc )
    {
        // Vanilla GameSettings has no reset in 1.7.10; Vivecraft's patch added one that wiped
        // every vanilla option too. Resetting someone's render distance and volume because they
        // reset their VR settings is surprising, and copying ~40 defaults here would only drift,
        // so only the VR settings are reset.
        VR.settings().resetSettings();
    }

    public static synchronized String getCurrentProfile()
    {
        return ProfileManager.getCurrentProfileName();
    }

    public static synchronized boolean profileExists(String profile)
    {
        return ProfileManager.profileExists(profile);
    }

    public static synchronized SortedSet<String> getProfileList()
    {
        return ProfileManager.getProfileList();
    }

    public static synchronized boolean setCurrentProfile(String profile)
    {
        StringBuilder error = new StringBuilder();
        return setCurrentProfile(profile, error);
    }

    public static synchronized boolean setCurrentProfile(String profile, StringBuilder error)
    {
        boolean result = true;
        Minecraft mc = Minecraft.getMinecraft();

        // Save settings in current profile
        VRSettings.saveAll(mc);

        // Set the new profile
        result = ProfileManager.setCurrentProfile(profile, error);

        if (result) {
            // Load new profile
            VRSettings.loadAll(mc);
        }

        return result;
    }

    public static synchronized boolean createProfile(String profile, boolean useDefaults, StringBuilder error)
    {
        boolean result = true;
        Minecraft mc = Minecraft.getMinecraft();
        String originalProfile = VRSettings.getCurrentProfile();

        // Save settings in original profile
        VRSettings.saveAll(mc);

        // Create the new profile
        if (!ProfileManager.createProfile(profile, error))
            return false;

        // Set the new profile
        ProfileManager.setCurrentProfile(profile, error);

        // Save existing settings as new profile...

        if (useDefaults) {
            // ...unless set to use defaults
            VRSettings.resetAll(mc);
        }

        // Save new profile settings to file
        VRSettings.saveAll(mc);

        // Select the original profile
        ProfileManager.setCurrentProfile(originalProfile, error);
        VRSettings.loadAll(mc);

        return result;
    }

    public static synchronized boolean deleteProfile(String profile)
    {
        StringBuilder error = new StringBuilder();
        return deleteProfile(profile, error);
    }

    public static synchronized boolean deleteProfile(String profile, StringBuilder error)
    {
        Minecraft mc = Minecraft.getMinecraft();

        // Save settings in current profile
        VRSettings.saveAll(mc);

        // Nuke the profile data
        if (!ProfileManager.deleteProfile(profile, error))
            return false;

        // Load settings in case the selected profile has changed
        VRSettings.loadAll(mc);

        return true;
    }

    public static synchronized boolean duplicateProfile(String originalProfile, String newProfile, StringBuilder error)
    {
        Minecraft mc = Minecraft.getMinecraft();

        // Save settings in current profile
        VRSettings.saveAll(mc);

        // Duplicate the profile data
        if (!ProfileManager.duplicateProfile(originalProfile, newProfile, error))
            return false;

        return true;
    }

    public static synchronized boolean renameProfile(String originalProfile, String newProfile, StringBuilder error)
    {
        Minecraft mc = Minecraft.getMinecraft();

        // Save settings in current profile
        VRSettings.saveAll(mc);

        // Rename the profile
        if (!ProfileManager.renameProfile(originalProfile, newProfile, error))
            return false;

        return true;
    }
    
    public String[] getQuickCommandsDefaults(){
    	
    	String[] out = new String[12];
    	out[0] = "/gamemode 0";
    	out[1] = "/gamemode 1";
    	out[2] = "/help";
    	out[3] = "/home";
    	out[4] = "/sethome";
    	out[5] = "/spawn";
    	out[6] = "hi!";
    	out[7] = "bye!";
    	out[8] = "folow me!";
    	out[9] = "take this!";
    	out[10] = "thank you!";
    	out[11] = "praise the sun!";

    	return out;
    	
    }

    private VRControllerButtonMapping[] getBindingsDefaults(){

    	VRControllerButtonMapping[] out = new VRControllerButtonMapping[ViveButtons.values().length];
    		//vive
    		out[ViveButtons.BUTTON_RIGHT_TRIGGER.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_RIGHT_TRIGGER, "key.attack");
    		out[ViveButtons.BUTTON_RIGHT_TRIGGER_FULLCLICK.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_RIGHT_TRIGGER_FULLCLICK, "none");
    		out[ViveButtons.BUTTON_RIGHT_GRIP.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_RIGHT_GRIP, "key.pickItem");
    		out[ViveButtons.BUTTON_RIGHT_APPMENU.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_RIGHT_APPMENU, "key.drop");
    		out[ViveButtons.BUTTON_RIGHT_TOUCHPAD_BL.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_RIGHT_TOUCHPAD_BL, "key.use");
    		out[ViveButtons.BUTTON_RIGHT_TOUCHPAD_BR.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_RIGHT_TOUCHPAD_BR, "key.use");
    		out[ViveButtons.BUTTON_RIGHT_TOUCHPAD_UL.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_RIGHT_TOUCHPAD_UL, "key.use");
    		out[ViveButtons.BUTTON_RIGHT_TOUCHPAD_UR.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_RIGHT_TOUCHPAD_UR, "key.use");
    		out[ViveButtons.BUTTON_LEFT_TRIGGER.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_LEFT_TRIGGER, "key.forward");
    		out[ViveButtons.BUTTON_LEFT_TRIGGER_FULLCLICK.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_LEFT_TRIGGER_FULLCLICK, "key.sprint");
    		out[ViveButtons.BUTTON_LEFT_GRIP.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_LEFT_GRIP, "key.sneak");
    		out[ViveButtons.BUTTON_LEFT_APPMENU.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_LEFT_APPMENU, "none");
    		out[ViveButtons.BUTTON_LEFT_TOUCHPAD_BL.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_LEFT_TOUCHPAD_BL, "key.jump");
    		out[ViveButtons.BUTTON_LEFT_TOUCHPAD_BR.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_LEFT_TOUCHPAD_BR, "key.jump");
    		out[ViveButtons.BUTTON_LEFT_TOUCHPAD_UL.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_LEFT_TOUCHPAD_UL, "key.inventory");
    		out[ViveButtons.BUTTON_LEFT_TOUCHPAD_UR.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_LEFT_TOUCHPAD_UR, "key.inventory");
    		out[ViveButtons.BUTTON_LEFT_TOUCHPAD_SWIPE_UP.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_LEFT_TOUCHPAD_SWIPE_UP, "none");
    		out[ViveButtons.BUTTON_LEFT_TOUCHPAD_SWIPE_DOWN.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_LEFT_TOUCHPAD_SWIPE_DOWN, "none");
    		out[ViveButtons.BUTTON_LEFT_TOUCHPAD_SWIPE_LEFT.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_LEFT_TOUCHPAD_SWIPE_LEFT, "Hotbar Prev");
    		out[ViveButtons.BUTTON_LEFT_TOUCHPAD_SWIPE_RIGHT.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_LEFT_TOUCHPAD_SWIPE_RIGHT, "Hotbar Next");
    		out[ViveButtons.BUTTON_RIGHT_TOUCHPAD_SWIPE_UP.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_RIGHT_TOUCHPAD_SWIPE_UP, "none");
    		out[ViveButtons.BUTTON_RIGHT_TOUCHPAD_SWIPE_DOWN.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_RIGHT_TOUCHPAD_SWIPE_DOWN, "none");
    		out[ViveButtons.BUTTON_RIGHT_TOUCHPAD_SWIPE_LEFT.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_RIGHT_TOUCHPAD_SWIPE_LEFT, "Hotbar Prev");
    		out[ViveButtons.BUTTON_RIGHT_TOUCHPAD_SWIPE_RIGHT.ordinal()] = new VRControllerButtonMapping(ViveButtons.BUTTON_RIGHT_TOUCHPAD_SWIPE_RIGHT, "Hotbar Next");

    		//touch
    		out[ViveButtons.OCULUS_RIGHT_INDEX_TRIGGER_PRESS.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_INDEX_TRIGGER_PRESS, "key.attack");
    		out[ViveButtons.OCULUS_RIGHT_INDEX_TRIGGER_TOUCH.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_INDEX_TRIGGER_TOUCH, "none");
    		out[ViveButtons.OCULUS_RIGHT_A_PRESS.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_A_PRESS, "key.use");
    		out[ViveButtons.OCULUS_RIGHT_B_PRESS.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_B_PRESS, "key.drop");
    		out[ViveButtons.OCULUS_RIGHT_A_TOUCH.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_A_TOUCH, "none");
    		out[ViveButtons.OCULUS_RIGHT_B_TOUCH.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_B_TOUCH, "none");
    		out[ViveButtons.OCULUS_RIGHT_HAND_TRIGGER_PRESS.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_HAND_TRIGGER_PRESS, "key.pickItem");
    		out[ViveButtons.OCULUS_RIGHT_HAND_TRIGGER_TOUCH.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_HAND_TRIGGER_TOUCH, "none");
    		out[ViveButtons.OCULUS_RIGHT_STICK_PRESS.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_STICK_PRESS, "key.inventory");
    		out[ViveButtons.OCULUS_RIGHT_STICK_TOUCH.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_STICK_TOUCH, "none");
    		//out[ViveButtons.OCULUS_RIGHT_THUMBPAD_TOUCH.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_THUMBPAD_TOUCH, "none");
    		out[ViveButtons.OCULUS_LEFT_INDEX_TRIGGER_PRESS.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_INDEX_TRIGGER_PRESS, "key.forward");
    		out[ViveButtons.OCULUS_LEFT_INDEX_TRIGGER_TOUCH.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_INDEX_TRIGGER_TOUCH, "none");
    		out[ViveButtons.OCULUS_LEFT_X_PRESS.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_X_PRESS, "key.chat");
    		out[ViveButtons.OCULUS_LEFT_Y_PRESS.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_Y_PRESS, "none");
    		out[ViveButtons.OCULUS_LEFT_X_TOUCH.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_X_TOUCH, "none");
    		out[ViveButtons.OCULUS_LEFT_Y_TOUCH.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_Y_TOUCH, "none");
    		out[ViveButtons.OCULUS_LEFT_HAND_TRIGGER_PRESS.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_HAND_TRIGGER_PRESS, "key.playerlist");
    		out[ViveButtons.OCULUS_LEFT_HAND_TRIGGER_TOUCH.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_HAND_TRIGGER_TOUCH, "none");
    		out[ViveButtons.OCULUS_LEFT_STICK_PRESS.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_STICK_PRESS, "key.sprint");
    		out[ViveButtons.OCULUS_LEFT_STICK_TOUCH.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_STICK_TOUCH, "none");
    		out[ViveButtons.OCULUS_LEFT_STICK_LEFT.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_STICK_LEFT, "Rotate Left");
    		out[ViveButtons.OCULUS_LEFT_STICK_RIGHT.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_STICK_RIGHT, "Rotate Right");
    		out[ViveButtons.OCULUS_LEFT_STICK_UP.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_STICK_UP, "key.jump");
    		out[ViveButtons.OCULUS_LEFT_STICK_DOWN.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_STICK_DOWN, "key.sneak");
    		out[ViveButtons.OCULUS_RIGHT_STICK_UP.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_STICK_UP, "none");
    		out[ViveButtons.OCULUS_RIGHT_STICK_DOWN.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_STICK_DOWN, "none");
    		out[ViveButtons.OCULUS_RIGHT_STICK_LEFT.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_STICK_LEFT, "Hotbar Prev");
    		out[ViveButtons.OCULUS_RIGHT_STICK_RIGHT.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_RIGHT_STICK_RIGHT, "Hotbar Next");

    		//out[ViveButtons.OCULUS_LEFT_THUMBPAD_TOUCH.ordinal()] = new VRControllerButtonMapping(ViveButtons.OCULUS_LEFT_THUMBPAD_TOUCH, "none");
    	return out;

    }
    
    
}

