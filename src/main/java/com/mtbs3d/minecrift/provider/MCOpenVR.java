package com.mtbs3d.minecrift.provider;

import lol.gzmc.vivecraftx.VRInputState;

import lol.gzmc.vivecraftx.util.VanillaExt;

import lol.gzmc.vivecraftx.VRGuiState;
import lol.gzmc.vivecraftx.render.VRGui;

import lol.gzmc.vivecraftx.VRRenderState;

import lol.gzmc.vivecraftx.util.KeyBindingUtil;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRAim;

import lol.gzmc.vivecraftx.api.RenderPass;
import lol.gzmc.vivecraftx.util.ChatUtil;

import com.mtbs3d.minecrift.api.*;
import com.mtbs3d.minecrift.control.ActionParams;
import com.mtbs3d.minecrift.control.ControllerType;
import com.mtbs3d.minecrift.control.HandedKeyBinding;
import com.mtbs3d.minecrift.control.HapticScheduler;
import com.mtbs3d.minecrift.control.TrackedController;
import com.mtbs3d.minecrift.control.TrackpadSwipeSampler;
import com.mtbs3d.minecrift.control.VRInputAction;
import com.mtbs3d.minecrift.control.VRInputActionSet;
import com.mtbs3d.minecrift.control.VRControllerButtonMapping;
import com.mtbs3d.minecrift.control.ViveButtons;
import com.mtbs3d.minecrift.render.QuaternionHelper;
import com.mtbs3d.minecrift.settings.VRHotkeys;
import com.mtbs3d.minecrift.settings.AutoCalibration;
import com.mtbs3d.minecrift.settings.VRSettings;
import com.mtbs3d.minecrift.utils.InputInjector;
import com.mtbs3d.minecrift.utils.KeyboardSimulator;
import com.mtbs3d.minecrift.utils.MCReflection;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

import de.fruitfly.ovr.structs.*;
import de.fruitfly.ovr.structs.Matrix4f;
import de.fruitfly.ovr.structs.Vector2f;
import de.fruitfly.ovr.structs.Vector3f;
import de.fruitfly.ovr.util.BufferUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.client.gui.GuiHopper;
import net.minecraft.client.gui.GuiKeyBindingList;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiWinGame;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.client.main.Main;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jopenvr.InputOriginInfo_t;
import jopenvr.InputPoseActionData_t;
import jopenvr.VRActiveActionSet_t;
import jopenvr.VR_IVRApplications_FnTable;
import jopenvr.VR_IVRInput_FnTable;

import lol.gzmc.vivecraftx.ViveCraftX;

import net.minecraft.client.resources.I18n;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.util.ByteArrayBuffer;
import org.lwjgl.LWJGLUtil;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.*;

import jopenvr.*;
import jopenvr.JOpenVRLibrary.EVREventType;

import java.awt.AWTException;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


public class MCOpenVR 
{
	static String initStatus;
	private static boolean initialized;
	private static Minecraft mc;

	static VR_IVRSystem_FnTable vrsystem;
	static VR_IVRCompositor_FnTable vrCompositor;
	static VR_IVROverlay_FnTable vrOverlay;
	static VR_IVRSettings_FnTable vrSettings;
	static VR_IVRRenderModels_FnTable vrRenderModels;
	static VR_IVRChaperone_FnTable vrChaperone;
	static VR_IVRApplications_FnTable vrApplications;

	/**
	 * How SteamVR tells this application apart from every other one.
	 *
	 * <p>Deliberately not the key mainline Vivecraft uses. The key is what the runtime files
	 * controller bindings under, and the two mods do not declare the same actions, so sharing it
	 * would have each one's bindings overwrite the other's whenever the player switched.
	 */
	private static final String APPLICATION_KEY = "lol.gzmc.vivecraftx.steamvrinput";

	private static IntByReference hmdErrorStore = new IntByReference();
	private static IntBuffer hmdErrorStoreBuf;
	private static TrackedDevicePose_t.ByReference hmdTrackedDevicePoseReference;
	private static TrackedDevicePose_t[] hmdTrackedDevicePoses;
	private static TrackedDevicePose_t.ByReference hmdGamePoseReference;
	private static TrackedDevicePose_t[] hmdGamePoses;

	private static Matrix4f[] poseMatrices;
	private static Vec3[] deviceVelocity;

	private LongByReference oHandle = new LongByReference();

	// position/orientation of headset and eye offsets
	static final Matrix4f hmdPose = new Matrix4f();
	public static final Matrix4f hmdRotation = new Matrix4f();
	static Matrix4f hmdProjectionLeftEye;
	static Matrix4f hmdProjectionRightEye;
	static Matrix4f hmdPoseLeftEye = new Matrix4f();
	static Matrix4f hmdPoseRightEye = new Matrix4f();
	static boolean initSuccess = false, flipEyes = false;

	private static IntBuffer hmdDisplayFrequency;

	private static FloatBuffer tlastVsync;
	private static LongBuffer _tframeCount;

	private static float vsyncToPhotons;
	private static double timePerFrame, frameCountRun;
	private static long frameCount;

	public static Vec3History hmdHistory = new Vec3History();
	public static Vec3History hmdPivotHistory = new Vec3History();
	public static Vec3History[] controllerHistory = new Vec3History[] { new Vec3History(), new Vec3History()};

	private static boolean isVive = true;

	// TextureIDs of framebuffers for each eye
	private int LeftEyeTextureId;

	final static VRTextureBounds_t texBounds = new VRTextureBounds_t();
	final static Texture_t texType0 = new Texture_t();
	final static Texture_t texType1 = new Texture_t();

	// aiming
	static float aimYaw = 0;
	static float aimPitch = 0;

	static float laimPitch = 0;
	static float laimYaw = 0;

	static Vec3[] aimSource = new Vec3[3];
	static Vector3f thirdcontrollerDirection = new Vector3f();

	/**
	 * How far every tracked position is shifted before anything else looks at it.
	 *
	 * <p>Worked out fresh each frame from {@link #offsetBase} plus whatever the view is being
	 * adjusted by right now, so that the two cannot fight: recentring owns the base, crouching
	 * and the fixed view height are applied on top of it.
	 */
	static Vector3f offset=new Vector3f(0,0,0);

	/** What recentring set, which crouching and the fixed height are measured from. */
	private static Vector3f offsetBase=new Vector3f(0,0,0);

	/** How far the view is currently crouched, eased so pressing the key is not a lurch. */
	private static float sneakDrop;

	/** How far pressing crouch lowers you, in metres. Enough to read as crouching. */
	private static final float SNEAK_DROP = 0.35f;

	/**
	 * How far the view is crouched right now, for anything that wants to measure the player.
	 *
	 * <p>Every tracked position handed out by this class already has this drop folded into it,
	 * which is right for drawing and wrong for measuring: a detector asking "how low is the
	 * player's head?" would see our own crouch and answer yes. Taking the drop back out turns
	 * the question back into one about the player's body, which is the only one worth asking.
	 */
	public static float getViewCrouchDrop() {
		return sneakDrop;
	}
	static Vector3f headDirection = new Vector3f();
	static Vector3f controllerDirection = new Vector3f();
	static Vector3f lcontrollerDirection = new Vector3f();

	static boolean[] controllerTracking = new boolean[3];

	// Controllers
	private static int RIGHT_CONTROLLER = 0;
	private static int LEFT_CONTROLLER = 1;
	private static int THIRD_CONTROLLER = 2;

	/**
	 * Where each controller is, as the runtime reports it. Only ever written from a pose that
	 * actually arrived.
	 */
	private static Matrix4f[] controllerPose = new Matrix4f[3];

	/**
	 * The same thing moved to the controller's tip, which is the point a hand aims and reaches
	 * from.
	 *
	 * <p>A separate array rather than the tip transform being folded into the one above, because
	 * the one above is not rewritten on a frame the runtime had no pose to give -- a controller
	 * out of the cameras' sight, asleep on a desk, or flat. Folding the tip in on every frame
	 * then multiplied it into an already-tipped matrix, again and again: on a wand, whose tip is
	 * a five-centimetre shift, the hand and everything aimed by it slid away at several metres a
	 * second; on a controller whose tip is also turned, it span. Built fresh from the raw pose
	 * each frame, a lost controller simply stays where it was last seen.
	 */
	private static Matrix4f[] controllerPoseTip = new Matrix4f[3];

	private static Matrix4f[] controllerRotation = new Matrix4f[3];
	private static Matrix4f[] handRotation = new Matrix4f[3];

	public static int[] controllerDeviceIndex = new int[3];
	private static final int maxControllerVelocitySamples = 5;
	private static Vec3[][] controllerVelocitySamples = new Vec3[3][maxControllerVelocitySamples];
	private static int[] controllerVelocitySampleCount = new int[3];
	private static Matrix4f[] controllerTipTransform = new Matrix4f[3];
	private static Matrix4f[] controllerGripTransform = new Matrix4f[3];

	// Vive axes
	private static int k_EAxis_Trigger = 1;
	private static int k_EAxis_TouchPad = 0;

	// Controls


	/**
	 * Where the interface panel is parked, in play-area space, or null while it is on the player.
	 *
	 * <p>Null is the signal that the panel is a heads-up display following the hand or the head,
	 * which is the normal case in a world. It is set when a screen is opened and cleared when the
	 * last one closes, so starting out null is correct: nothing is parked yet.
	 */
	public static Vector3f guiPos_Room = null;
	public static Matrix4f guiRotationPose = new Matrix4f();
	public static  float guiScale = 1.0f;
	public static double startedOpeningInventory = 0;
	public static boolean hudPopup = true;

	/** Last frame's menu button state, for turning a held button into a click and a release. */
	private static boolean guiWasShift;
	private static boolean guiWasLeftClick;
	private static boolean guiWasRightClick;
	private static boolean guiWasMiddleClick;

	/**
	 * Set when the device in a hand changes, so the controller's tip transform is worked out again.
	 *
	 * <p>It used to be recomputed every frame with a note saying it should not be; the transform
	 * depends only on which model is in the hand, and now that the hand's device is reported by its
	 * pose action there is a reliable moment to notice that changing.
	 */
	private static boolean tipTransformsStale = true;

	/**
	 * The soonest the tip transforms may be asked for again after a failure.
	 *
	 * <p>Nothing announces that a controller's model has become available, so a failure has to be
	 * retried rather than waited on. But several of the reasons it fails do not clear at all while
	 * the session lasts -- seated mode first among them, which reports incomplete by design -- and
	 * every attempt costs a thirty-two kilobyte native buffer and a handful of calls into the
	 * runtime. Asked once a second rather than once a frame, which picks a late controller up just
	 * as surely and costs a ninetieth as much. A controller that is swapped for another re-arms
	 * the staleness flag itself, so nothing here has to anticipate that.
	 */
	private static long tipTransformsRetryAt;

	private static final long TIP_TRANSFORM_RETRY_NANOS = 1_000_000_000L;

	/**
	 * Scratch for the render model's name, reused.
	 *
	 * <p>Thirty-two kilobytes of native memory, which is a great deal to allocate and hand to the
	 * collector for the sake of reading one string, and it was being allocated on every attempt.
	 * Created on first use rather than with the class, because a session that never starts VR
	 * should not pay for it at all.
	 */
	private static Pointer renderModelNameScratch;

	// For mouse menu emulation
	public static float controllerMouseX = -1.0f;
	public static float controllerMouseY = -1.0f;
	public static boolean controllerMouseValid;
	public static int controllerMouseTicks;

	//keyboard
	public static boolean keyboardShowing = false;
	byte[] lastTyped = new byte[256];
	byte[] typed = new byte[256];
	static int pollsSinceLastChange = 0;

	// Touchpad samples
	private static Vector2f[][] touchpadSamples = new Vector2f[3][5];
	private static int[] touchpadSampleCount = new int[3];

	private static float[] inventory_swipeX = new float[3];
	private static float[] inventory_swipeY = new float[3];

	static boolean headIsTracking;

	private static int moveModeSwitchcount = 0;

	public static boolean isWalkingAbout;
	private static boolean isFreeRotate;
	private static float walkaboutYawStart;

	private static float hmdForwardYaw;

	public static boolean mrMovingCamActive;
	public static Vec3 mrControllerPos = Vec3.createVectorHelper(0, 0, 0);
	public static float mrControllerPitch;
	public static float mrControllerYaw;
	public static float mrControllerRoll;
	public static float rtbX, rtbY;

	public String getName() {
		return "OpenVR";
	}


	public String getID() {
		return "openvr";
	}


	public boolean isInitialized() { return initialized; }


	public String getVersion() { return "Version TODO"; }

	/** Categories whose bindings go into their own action sets rather than the in-game one. */
	public static final String CATEGORY_GUI = "vivecraft.key.category.gui";
	public static final String CATEGORY_CLIMBEY = "vivecraft.key.category.climbey";
	public static final String CATEGORY_KEYBOARD = "vivecraft.key.category.keyboard";
	public static final String CATEGORY_MISC = "key.categories.misc";

	// ---------------------------------------------------------------------------------------
	// Key bindings
	//
	// Named with translation keys rather than English, both so they can be translated and because
	// the action name SteamVR knows a binding by is built from this name -- which is what lets the
	// binding profiles shipped for each controller model find them.
	//
	// Several are marked a dummy binding: they exist only so that SteamVR has something to bind an
	// axis to, and are never pressed as keys. The game reads their axis directly.
	// ---------------------------------------------------------------------------------------

	public static final KeyBinding keyHotbarNext =
			new KeyBinding("vivecraft.key.hotbarNext", Keyboard.KEY_NEXT, "key.categories.inventory");
	public static final KeyBinding keyHotbarPrev =
			new KeyBinding("vivecraft.key.hotbarPrev", Keyboard.KEY_PRIOR, "key.categories.inventory");
	public static final KeyBinding keyHotbarScroll =
			new KeyBinding("vivecraft.key.hotbarScroll", Keyboard.KEY_NONE, "key.categories.inventory");
	public static final KeyBinding keyHotbarSwipeX =
			new KeyBinding("vivecraft.key.hotbarSwipeX", Keyboard.KEY_NONE, "key.categories.inventory");
	public static final KeyBinding keyHotbarSwipeY =
			new KeyBinding("vivecraft.key.hotbarSwipeY", Keyboard.KEY_NONE, "key.categories.inventory");

	public static final KeyBinding keyRotateLeft =
			new KeyBinding("vivecraft.key.rotateLeft", Keyboard.KEY_LEFT, "key.categories.movement");
	public static final KeyBinding keyRotateRight =
			new KeyBinding("vivecraft.key.rotateRight", Keyboard.KEY_RIGHT, "key.categories.movement");
	public static final KeyBinding keyRotateAxis =
			new KeyBinding("vivecraft.key.rotateAxis", Keyboard.KEY_NONE, "key.categories.movement");
	public static final KeyBinding keyWalkabout =
			new KeyBinding("vivecraft.key.walkabout", Keyboard.KEY_END, "key.categories.movement");
	public static final KeyBinding keyRotateFree =
			new KeyBinding("vivecraft.key.rotateFree", Keyboard.KEY_HOME, "key.categories.movement");
	public static final KeyBinding keyTeleport =
			new KeyBinding("vivecraft.key.teleport", Keyboard.KEY_NONE, "key.categories.movement");
	public static final KeyBinding keyTeleportFallback =
			new KeyBinding("vivecraft.key.teleportFallback", Keyboard.KEY_NONE, "key.categories.movement");
	public static final KeyBinding keyFreeMoveRotate =
			new KeyBinding("vivecraft.key.freeMoveRotate", Keyboard.KEY_NONE, "key.categories.movement");
	public static final KeyBinding keyFreeMoveStrafe =
			new KeyBinding("vivecraft.key.freeMoveStrafe", Keyboard.KEY_NONE, "key.categories.movement");
	public static final KeyBinding keyToggleMovement =
			new KeyBinding("vivecraft.key.toggleMovement", Keyboard.KEY_NONE, "key.categories.movement");

	public static final KeyBinding keyRadialMenu =
			new KeyBinding("vivecraft.key.radialmenu", Keyboard.KEY_NONE, "key.categories.ui");
	public static final KeyBinding keyQuickTorch =
			new KeyBinding("vivecraft.key.quickTorch", Keyboard.KEY_INSERT, "key.categories.gameplay");
	public static final KeyBinding keyMenuButton =
			new KeyBinding("vivecraft.key.ingameMenuButton", Keyboard.KEY_NONE, "key.categories.ui");
	public static final KeyBinding keyToggleKeyboard =
			new KeyBinding("vivecraft.key.toggleKeyboard", Keyboard.KEY_NONE, "key.categories.ui");

	/**
	 * Reports a finger resting on a trackpad. Not a control: the swipe sampler needs it to tell a
	 * swipe from a finger lifting off and touching down again.
	 */
	public static final HandedKeyBinding keyTrackpadTouch =
			new HandedKeyBinding("vivecraft.key.trackpadTouch", Keyboard.KEY_NONE, CATEGORY_MISC);

	/**
	 * Reaching out and pressing: a lever, a chest, a dog.
	 *
	 * <p>Per hand, and switched off for a hand that is not touching anything, so that the same
	 * button can do something else the rest of the time. In the contextual set for that reason,
	 * which is also where the shipped controller profiles already expect to find it.
	 */
	public static final HandedKeyBinding keyVRInteract =
			new HandedKeyBinding("vivecraft.key.vrInteract", Keyboard.KEY_NONE, "key.categories.gameplay");

	/** Climbing, per hand, so each hand can hold a different block. */
	public static final HandedKeyBinding keyClimbeyGrab =
			new HandedKeyBinding("vivecraft.key.climbeyGrab", Keyboard.KEY_NONE, CATEGORY_CLIMBEY);
	public static final HandedKeyBinding keyClimbeyJump =
			new HandedKeyBinding("vivecraft.key.climbeyJump", Keyboard.KEY_NONE, CATEGORY_CLIMBEY);

	/**
	 * Taking hold of the VR keyboard to move it, per hand.
	 *
	 * <p>In the keyboard's own action set, which SteamVR activates only while the board is up.
	 * That is what lets this sit on the grips without taking them away from anything: outside the
	 * keyboard they still mean whatever the player has them bound to, and the binding profiles
	 * already pointed both grips at this set in the expectation of something being there.
	 *
	 * <p>Per hand because either hand should be able to take the board, and because the hand that
	 * took it is the hand the board has to follow.
	 */
	public static final HandedKeyBinding keyKeyboardMove =
			new HandedKeyBinding("vivecraft.key.keyboardMove", Keyboard.KEY_NONE, CATEGORY_KEYBOARD);

	// Clicking about in menus. Their own set, so the same trigger that attacks in the world
	// clicks in a menu without the player binding it twice.
	public static final KeyBinding keyGuiLeftClick =
			new KeyBinding("vivecraft.key.guiLeftClick", Keyboard.KEY_NONE, CATEGORY_GUI);
	public static final KeyBinding keyGuiRightClick =
			new KeyBinding("vivecraft.key.guiRightClick", Keyboard.KEY_NONE, CATEGORY_GUI);
	public static final KeyBinding keyGuiMiddleClick =
			new KeyBinding("vivecraft.key.guiMiddleClick", Keyboard.KEY_NONE, CATEGORY_GUI);
	public static final KeyBinding keyGuiShift =
			new KeyBinding("vivecraft.key.guiShift", Keyboard.KEY_NONE, CATEGORY_GUI);
	public static final KeyBinding keyGuiCtrl =
			new KeyBinding("vivecraft.key.guiCtrl", Keyboard.KEY_NONE, CATEGORY_GUI);
	public static final KeyBinding keyGuiAlt =
			new KeyBinding("vivecraft.key.guiAlt", Keyboard.KEY_NONE, CATEGORY_GUI);
	public static final KeyBinding keyGuiScrollUp =
			new KeyBinding("vivecraft.key.guiScrollUp", Keyboard.KEY_NONE, CATEGORY_GUI);
	public static final KeyBinding keyGuiScrollDown =
			new KeyBinding("vivecraft.key.guiScrollDown", Keyboard.KEY_NONE, CATEGORY_GUI);
	public static final KeyBinding keyGuiScrollAxis =
			new KeyBinding("vivecraft.key.guiScrollAxis", Keyboard.KEY_NONE, CATEGORY_GUI);


	public MCOpenVR()
	{
		super();

		for (int c=0;c<3;c++)
		{
			aimSource[c] = Vec3.createVectorHelper(0.0D, 0.0D, 0.0D);
			for (int sample = 0; sample < 5; sample++)
			{
				touchpadSamples[c][sample] = new Vector2f(0, 0);
			}
			touchpadSampleCount[c] = 0;
			controllerPose[c] = new Matrix4f();
			controllerPoseTip[c] = new Matrix4f();
			controllerRotation[c] = new Matrix4f();
			controllerDeviceIndex[c] = -1;
			controllerTipTransform[c] = new Matrix4f();
			controllerGripTransform[c] = new Matrix4f();
			handRotation[c] = new Matrix4f();


			//controllerVelocitySamples[c] = new Vec3[2][maxControllerVelocitySamples];
			controllerVelocitySampleCount[c] = 0;
			for (int i=0;i<maxControllerVelocitySamples;i++)
			{
				controllerVelocitySamples[c][i] = Vec3.createVectorHelper(0, 0, 0);
			}
		}
	}

	/** What the shipped library is called inside the jar, and on disk, for each platform. */
	private static String[] nativeNamesFor(String osFolder) {
		if ("win64".equals(osFolder)) {
			return new String[] { "win32-x86-64/openvr_api.dll", "openvr_api.dll" };
		}
		if ("win32".equals(osFolder)) {
			return new String[] { "win32-x86/openvr_api.dll", "openvr_api.dll" };
		}
		if ("linux64".equals(osFolder)) {
			return new String[] { "linux-x86-64/libopenvr_api.so", "libopenvr_api.so" };
		}
		if ("linux32".equals(osFolder)) {
			return new String[] { "linux-x86/libopenvr_api.so", "libopenvr_api.so" };
		}
		if ("osx32".equals(osFolder)) {
			return new String[] { "darwin/libopenvr_api.dylib", "libopenvr_api.dylib" };
		}
		return null;
	}

	/**
	 * Writes the runtime library out of the jar next to the game, if it is not already there.
	 *
	 * <p>Written once and then left alone: rewriting it every launch would mean replacing a file
	 * the system may still have mapped from the last run, and its absence is the only thing worth
	 * reacting to.
	 *
	 * <p>Failure here is not fatal -- the library can still be found the old way. This only makes
	 * the ordinary case deterministic, and puts the file somewhere a person can see it.
	 */
	private static void unpackNativeTo(File dir, String osFolder) {
		String[] names = nativeNamesFor(osFolder);
		if (names == null) {
			return;
		}
		File target = new File(dir, names[1]);
		if (target.isFile() && target.length() > 0) {
			return;
		}
		InputStream in = null;
		OutputStream out = null;
		try {
			in = MCOpenVR.class.getClassLoader().getResourceAsStream(names[0]);
			if (in == null) {
				ViveCraftX.LOG.warn("The OpenVR library for {} is not in the jar", osFolder);
				return;
			}
			dir.mkdirs();
			out = new FileOutputStream(target);
			byte[] buffer = new byte[65536];
			int read;
			while ((read = in.read(buffer)) > 0) {
				out.write(buffer, 0, read);
			}
			out.close();
			out = null;
			ViveCraftX.LOG.info("Unpacked the OpenVR library to {}", target.getAbsolutePath());
		} catch (Throwable t) {
			ViveCraftX.LOG.warn("Could not unpack the OpenVR library; falling back to the copy the "
					+ "binding makes for itself", t);
		} finally {
			try {
				if (out != null) {
					out.close();
				}
				if (in != null) {
					in.close();
				}
			} catch (IOException ignored) {
				// nothing useful to do about a failed close on a file being abandoned
			}
		}
	}

	private static boolean tried;


	public static boolean init()  throws Exception
	{

		if ( initialized )
			return true;

		if ( tried )
			return initialized;


		tried = true;

		mc = Minecraft.getMinecraft();
		// look in .minecraft first for openvr_api.dll
		File minecraftDir = Minecraft.getMinecraft().mcDataDir; // misleading name, actually the .minecraft directory
		File workingDir = new File(System.getProperty("user.dir"));

		String osname = System.getProperty("os.name").toLowerCase();
		String osarch= System.getProperty("os.arch").toLowerCase();

		String osFolder = "win32";

		if (osname.contains("windows")){	
			if (osarch.contains("64"))
			{
				osFolder = "win64";
			}
		}
		else if( osname.contains("linux")){
			osFolder = "linux32";
			if (osarch.contains("64"))
			{
				osFolder = "linux64";
			}
		}
		else if( osname.contains("mac")){
			osFolder = "osx32";
		}


		// Put the runtime library somewhere real before anything asks for it. These two search
		// paths are inherited from minecrift, which shipped the library into the game folder
		// with its installer; a mod has no installer, so nothing was ever there and the load
		// fell through to the one remaining option -- the binding unpacking it into the system
		// temporary folder and loading it from there. That works until it does not, and when
		// it does not the class fails for the rest of the session however many times anybody
		// asks again.
		unpackNativeTo(new File(minecraftDir, osFolder), osFolder);

		String openVRPath = new File(minecraftDir, osFolder).getPath();
		ViveCraftX.LOG.info("OpenVR search path: {}", openVRPath);
		NativeLibrary.addSearchPath("openvr_api", openVRPath);

		String openVRPath2 = new File(workingDir, osFolder).getPath();
		if (!openVRPath2.equals(openVRPath)) {
			ViveCraftX.LOG.info("OpenVR search path: {}", openVRPath2);
			NativeLibrary.addSearchPath("openvr_api", openVRPath2);
		}

		if(jopenvr.JOpenVRLibrary.VR_IsHmdPresent() == 0){
			initStatus =  "VR Headset not detected.";
			return false;
		}

		try {
			initializeJOpenVR();
			initOpenVRCompositor(true) ;
			initOpenVROverlay() ;	
			initOpenVRSettings();
			initOpenVRRenderModels();
			initOpenVRChaperone();
			initOpenVRApplications();
			initOpenVRInput();
		} catch (Exception e) {
			e.printStackTrace();
			initSuccess = false;
			initStatus = e.getLocalizedMessage();
			return false;
		}

		System.out.println( "OpenVR initialized & VR connected." );


		for(int i=0;i<poseMatrices.length;i++)
		{
			poseMatrices[i] = new Matrix4f();
		}

		HmdMatrix34_t matL = vrsystem.GetEyeToHeadTransform.apply(JOpenVRLibrary.EVREye.EVREye_Eye_Left);
		OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(matL, hmdPoseLeftEye);

		HmdMatrix34_t matR = vrsystem.GetEyeToHeadTransform.apply(JOpenVRLibrary.EVREye.EVREye_Eye_Right);
		OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(matR, hmdPoseRightEye);

initialized = true;
		return true;
	}

	final int rotationIncrement = 0;

	/** Why VR did not come up, for the caller to report. */
	/**
	 * Registers the VR key bindings with Forge.
	 *
	 * <p>Vivecraft appended them straight onto {@code gameSettings.keyBindings} from inside
	 * init(), which meant they only existed when a headset did, and bypassed Forge entirely so
	 * mods that manage the controls screen never saw them. They are registered up front now,
	 * whether or not VR comes up, and through Forge.
	 */
	public static void registerKeyBindings() {
		for (KeyBinding kb : getKeyBindings()) {
			cpw.mods.fml.client.registry.ClientRegistry.registerKeyBinding(kb);
		}
	}

	private static Set<KeyBinding> ourBindings;

	/**
	 * The bindings this mod owns.
	 *
	 * <p>Needed in two places beyond registering them: they are never sent as synthetic keystrokes,
	 * since the mod has to act on them whether or not the player gave them a key, and they are
	 * excluded from the set of bindings treated as belonging to other mods.
	 */
	public static Set<KeyBinding> getKeyBindings() {
		if (ourBindings == null) {
			ourBindings = new LinkedHashSet<KeyBinding>(Arrays.asList(
					keyHotbarNext, keyHotbarPrev, keyHotbarScroll, keyHotbarSwipeX,
					keyHotbarSwipeY, keyRotateLeft, keyRotateRight, keyRotateAxis,
					keyWalkabout, keyRotateFree, keyTeleport, keyTeleportFallback,
					keyFreeMoveRotate, keyFreeMoveStrafe, keyToggleMovement, keyQuickTorch,
					keyMenuButton, keyToggleKeyboard, keyRadialMenu, keyTrackpadTouch,
					keyVRInteract, keyClimbeyGrab,
					keyClimbeyJump, keyKeyboardMove,
					keyGuiLeftClick, keyGuiRightClick, keyGuiMiddleClick,
					keyGuiShift, keyGuiCtrl, keyGuiAlt, keyGuiScrollUp, keyGuiScrollDown,
					keyGuiScrollAxis));
		}
		return ourBindings;
	}

	public static String getInitStatus() {
		return initStatus;
	}

	public static boolean isError(){
		return hmdErrorStore.getValue() != 0 || hmdErrorStoreBuf.get(0) != 0;
	}

	public static int getError(){
		return hmdErrorStore.getValue() != 0 ? hmdErrorStore.getValue() : hmdErrorStoreBuf.get(0);
	}

	private static void initializeJOpenVR() throws Exception { 
		hmdErrorStoreBuf = IntBuffer.allocate(1);
		vrsystem = null;
		JOpenVRLibrary.VR_InitInternal(hmdErrorStoreBuf, JOpenVRLibrary.EVRApplicationType.EVRApplicationType_VRApplication_Scene);

		if(!isError()) {
			// ok, try and get the vrsystem pointer..
			vrsystem = new VR_IVRSystem_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVRSystem_Version, hmdErrorStoreBuf));
		}

		if( vrsystem == null || isError()) {
			throw new Exception(jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));		
		} else {

			vrsystem.setAutoSynch(false);
			vrsystem.read();

			System.out.println("OpenVR initialized & VR connected.");

			tlastVsync = FloatBuffer.allocate(1);
			_tframeCount = LongBuffer.allocate(1);

			hmdDisplayFrequency = IntBuffer.allocate(1);
			hmdDisplayFrequency.put( (int) JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_DisplayFrequency_Float);
			hmdTrackedDevicePoseReference = new TrackedDevicePose_t.ByReference();
			hmdTrackedDevicePoses = (TrackedDevicePose_t[])hmdTrackedDevicePoseReference.toArray(JOpenVRLibrary.k_unMaxTrackedDeviceCount);
			poseMatrices = new Matrix4f[JOpenVRLibrary.k_unMaxTrackedDeviceCount];
			for(int i=0;i<poseMatrices.length;i++) poseMatrices[i] = new Matrix4f();

			timePerFrame = 1.0 / hmdDisplayFrequency.get(0);

			// disable all this stuff which kills performance
			hmdTrackedDevicePoseReference.setAutoRead(false);
			hmdTrackedDevicePoseReference.setAutoWrite(false);
			hmdTrackedDevicePoseReference.setAutoSynch(false);
			for(int i=0;i<JOpenVRLibrary.k_unMaxTrackedDeviceCount;i++) {
				hmdTrackedDevicePoses[i].setAutoRead(false);
				hmdTrackedDevicePoses[i].setAutoWrite(false);
				hmdTrackedDevicePoses[i].setAutoSynch(false);
			}

			initSuccess = true;
		}
	}

	private static Pointer ptrFomrString(String in){
		Pointer p = new Memory(in.length()+1);
		p.setString(0, in);
		return p;

	}

	static void debugOut(){
		for(Field i :JOpenVRLibrary.ETrackedDeviceProperty.class.getDeclaredFields()){
			try {
				String[] ts = i.getName().split("_");
				String Type = ts[ts.length - 1];
				String out = "";


				if (Type.equals("Float")) {
					out += i.getName() + " " + vrsystem.GetFloatTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, i.getInt(null), hmdErrorStore);
				}
				if (Type.equals("String")) {
					Pointer pointer = new Memory(JOpenVRLibrary.k_unMaxPropertyStringSize);
					int len = vrsystem.GetStringTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, i.getInt(null), pointer, JOpenVRLibrary.k_unMaxPropertyStringSize - 1, hmdErrorStore);
					out += i.getName() + " " + pointer.getString(0);
				} else if (Type.equals("Bool")) {
					out += i.getName() + " " + vrsystem.GetBoolTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, i.getInt(null), hmdErrorStore);
				} else if (Type.equals("Int32")) {
					out += i.getName() + " " + vrsystem.GetInt32TrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, i.getInt(null), hmdErrorStore);
				} else if (Type.equals("Uint64")) {
					out += i.getName() + " " + vrsystem.GetUint64TrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, i.getInt(null), hmdErrorStore);
				}

				System.out.println(out);
			}catch (IllegalAccessException e){
				e.printStackTrace();
			}
		}

		System.out.println("TrackingSpace: "+vrCompositor.GetTrackingSpace.apply());
	}

	// needed for in-game keyboard
	public static void initOpenVROverlay() throws Exception
	{
		vrOverlay =   new VR_IVROverlay_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVROverlay_Version, hmdErrorStoreBuf));
		if (vrOverlay != null &&  !isError()) {     		
			vrOverlay.setAutoSynch(false);
			vrOverlay.read();					
			System.out.println("OpenVR Overlay initialized OK");
		} else {
			if (getError() != 0) {
				System.out.println("VROverlay init failed: " + jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
				vrOverlay = null;
			} else {
				throw new Exception(jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
			}	
		}
	}


	public static void initOpenVRSettings() throws Exception
	{
		vrSettings = new VR_IVRSettings_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVRSettings_Version, hmdErrorStoreBuf));
		if (vrSettings != null &&  !isError()) {     		
			vrSettings.setAutoSynch(false);
			vrSettings.read();					
			System.out.println("OpenVR Settings initialized OK");
		} else {
			if (getError() != 0) {
				System.out.println("VRSettings init failed: " + jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
				vrSettings = null;
			} else {
				throw new Exception(jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
			}	
		}
	}

	public static void initOpenVRCompositor(boolean set) throws Exception
	{
		if( set && vrsystem != null ) {
			vrCompositor = new VR_IVRCompositor_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVRCompositor_Version, hmdErrorStoreBuf));
			if(vrCompositor != null && !isError()){                
				System.out.println("OpenVR Compositor initialized OK.");
				vrCompositor.setAutoSynch(false);
				vrCompositor.read();
				vrCompositor.SetTrackingSpace.apply(JOpenVRLibrary.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseStanding);

				int buffsize=20;
				Pointer s=new Memory(buffsize);

				//vrCompositor.GetTrackingSpace.apply();
				debugOut();

				vrsystem.GetStringTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd,JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_ManufacturerName_String,s,buffsize,hmdErrorStore);
				String id=s.getString(0);
				System.out.println("Device manufacturer is: "+id);

				if(!id.equals("HTC")) {
					isVive=false;
					VR.settings().loadOptions();
				}

				//TODO: detect tracking system
				if(VR.settings().seated && !isVive)
					resetPosition();
				else
					clearOffset();

			} else {
				throw new Exception(jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));			 
			}
		}
		if( vrCompositor == null ) {
			System.out.println("Skipping VR Compositor...");
			if( vrsystem != null ) {
				vsyncToPhotons = vrsystem.GetFloatTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_SecondsFromVsyncToPhotons_Float, hmdErrorStore);


			} else {
				vsyncToPhotons = 0f;
			}
		}

		// left eye
		texBounds.uMax = 1f;
		texBounds.uMin = 0f;
		texBounds.vMax = 1f;
		texBounds.vMin = 0f;
		texBounds.setAutoSynch(false);
		texBounds.setAutoRead(false);
		texBounds.setAutoWrite(false);
		texBounds.write();


		// texture type
		texType0.eColorSpace = JOpenVRLibrary.EColorSpace.EColorSpace_ColorSpace_Gamma;
		texType0.eType = JOpenVRLibrary.ETextureType.ETextureType_TextureType_OpenGL;
		texType0.setAutoSynch(false);
		texType0.setAutoRead(false);
		texType0.setAutoWrite(false);
		texType0.handle = Pointer.createConstant(-1);
		texType0.write();


		// texture type
		texType1.eColorSpace = JOpenVRLibrary.EColorSpace.EColorSpace_ColorSpace_Gamma;
		texType1.eType = JOpenVRLibrary.ETextureType.ETextureType_TextureType_OpenGL;
		texType1.setAutoSynch(false);
		texType1.setAutoRead(false);
		texType1.setAutoWrite(false);
		texType1.handle = Pointer.createConstant(-1);
		texType1.write();

		System.out.println("OpenVR Compositor initialized OK.");

	}

	public static void initOpenVRRenderModels() throws Exception
	{
		vrRenderModels = new VR_IVRRenderModels_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVRRenderModels_Version, hmdErrorStoreBuf));
		if (vrRenderModels != null && !isError()) {
			vrRenderModels.setAutoSynch(false);
			vrRenderModels.read();			
			System.out.println("OpenVR RenderModels initialized OK");
		} else {
			if (getError() != 0) {
				System.out.println("VRRenderModels init failed: " + jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
				vrRenderModels = null;
			} else {
				throw new Exception(jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
			}
		}
	}

	private static void initOpenVRChaperone() throws Exception {
		vrChaperone = new VR_IVRChaperone_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVRChaperone_Version, hmdErrorStoreBuf));
		if (vrChaperone != null && hmdErrorStore.getValue() == 0) {
			vrChaperone.setAutoSynch(false);
			vrChaperone.read();
			System.out.println("OpenVR chaperone initialized.");
		} else {
			if (getError() != 0) {
				System.out.println("VRChaperone init failed: " + jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
				vrChaperone = null;
			} else {
				throw new Exception(jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
			}
		}
	}

	/**
	 * The interface that lets us tell SteamVR who we are.
	 *
	 * <p>Not fatal if it is missing. Without it the runtime still works; it simply has no name
	 * for us and labels the session with the process, which is {@code javaw.exe}.
	 */
	private static void initOpenVRApplications() {
		vrApplications = new VR_IVRApplications_FnTable(JOpenVRLibrary.VR_GetGenericInterface(
				JOpenVRLibrary.IVRApplications_Version, hmdErrorStoreBuf));
		if (vrApplications != null && !isError()) {
			vrApplications.setAutoSynch(false);
			vrApplications.read();
			ViveCraftX.LOG.info("OpenVR Applications initialised");
		} else {
			ViveCraftX.LOG.warn("OpenVR Applications unavailable ({}); SteamVR will show the "
					+ "process name instead of ours",
					jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError())
							.getString(0));
			vrApplications = null;
		}
	}

	public boolean initOpenVRControlPanel()
	{
		return true;
		//		vrControlPanel = new VR_IVRSettings_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVRControlPanel_Version, hmdErrorStore));
		//		if(vrControlPanel != null && hmdErrorStore.get(0) == 0){
		//			System.out.println("OpenVR Control Panel initialized OK.");
		//			return true;
		//		} else {
		//			initStatus = "OpenVR Control Panel error: " + JOpenVRLibrary.VR_GetStringForHmdError(hmdErrorStore.get(0)).getString(0);
		//			return false;
		//		}
	}

	private String lasttyped = "";


	public static void poll(long frameIndex)
	{
		Minecraft.getMinecraft().mcProfiler.startSection("input");
		boolean sleeping = (mc.theWorld !=null && mc.thePlayer != null && mc.thePlayer.isPlayerSleeping());		

		pollInputEvents();

		// Everything downstream reads this frame's action state, so it is refreshed first and
		// exactly once: the runtime reports changes since the last call, and asking twice would
		// make the second half of a press look like no change at all.
		pollActions();
		processInputs();
		processBindings();

		if (!VR.settings().seated) {
			Minecraft.getMinecraft().mcProfiler.endStartSection("gui");
			if (mc.currentScreen != null) {
				processGui();
			}
			if (mc.currentScreen == null && VR.settings().vrTouchHotbar
					&& VR.settings().vrHudLockMode != VRSettings.HUD_LOCK_HEAD && hudPopup) {
				processHotbar();
			}
		}

		Minecraft.getMinecraft().mcProfiler.endStartSection("updatePose");
		updatePose();
		Minecraft.getMinecraft().mcProfiler.endSection();
	}


	private static Vec3 vecFromVector(Vector3f in){
		return Vec3.createVectorHelper(in.x, in.y, in.z);
	}

	private static void processHotbar() {

		if(mc.thePlayer == null) return;
		if(mc.thePlayer.inventory == null) return;

		Vec3 main = getAimSource(0);
		Vec3 off = getAimSource(1);

		// Where the hotbar actually is, worked out by the code that drew it. Written out here as
		// its own set of figures it would have to be kept in step by hand, and it was not: the
		// panel was moved onto the back of the wrist and these stayed where the old one had been,
		// about ten centimetres away and facing the other way, so touching the hotbar selected
		// slots from empty air under the arm instead.
		Vec3[] ends = VRGui.hotbarEnds();
		if (ends == null) return;

		Matrix4f aim = getAimRotation(1);
		Vec3 barStartos = vecFromVector(aim.transform(
				new Vector3f((float) ends[0].xCoord, (float) ends[0].yCoord,
						(float) ends[0].zCoord)));
		Vec3 barEndos = vecFromVector(aim.transform(
				new Vector3f((float) ends[1].xCoord, (float) ends[1].yCoord,
						(float) ends[1].zCoord)));


		Vec3 barStart = off.addVector(barStartos.xCoord, barStartos.yCoord, barStartos.zCoord);	
		Vec3 barEnd = off.addVector(barEndos.xCoord, barEndos.yCoord, barEndos.zCoord);

		Vec3 u = barEnd.subtract(barStart);
		Vec3 pq = main.subtract(barStart);
		float dist = (float) (pq.crossProduct(u).lengthVector() / u.lengthVector());

		if(dist > 0.06) return;

		float fact = (float) (pq.dotProduct(u) / (u.xCoord*u.xCoord + u.yCoord*u.yCoord + u.zCoord*u.zCoord));

		if(fact < 0) return;

		Vec3 w2 = pq.subtract(Vec3.createVectorHelper(u.xCoord*fact, u.yCoord*fact, u.zCoord*fact));

		Vec3 point = w2.subtract(main);
		float linelen = (float) barStart.subtract(barEnd).lengthVector();
		float ilen = (float) barStart.subtract(point).lengthVector();

		float pos = ilen / linelen * 9; 

		int box = (int) Math.floor(pos);
		if(pos - Math.floor(pos) < 0.1) return;

		if(box > 8) return;
		if(box < 0) return;
		//all that maths for this.
		if(box != mc.thePlayer.inventory.currentItem){
			mc.thePlayer.inventory.currentItem = box;	
			triggerHapticPulse(0, 750);
		}
	}

	static GuiTextField keyboardGui;
	private static int quickTorchPreviousSlot;

	public static boolean setKeyboardOverlayShowing(boolean showingState, GuiTextField gui) {
		if (vrOverlay == null) return false;
		try {
			if(VR.settings().seated) showingState = false;
			keyboardGui = gui;
			int ret = 1;
			if (showingState) {
				pollsSinceLastChange = 0; // User deliberately tried to show keyboard, shouldn't have chance of immediately resetting   
				Pointer pointer = new Memory(3);
				pointer.setString(0, "mc");
				Pointer empty = new Memory(1);
				empty.setString(0, "");

				ret = vrOverlay.ShowKeyboard.apply(0, 0, pointer, 256, empty, (byte)1, 0)	;	

				keyboardShowing = 0 == ret; //0 = no error, > 0 see EVROverlayError	

				if (ret != 0) {
					String err =  vrOverlay.GetOverlayErrorNameFromEnum.apply(ret).getString(0);
					System.out.println("VR Overlay Error: " + err);
					if(err.equalsIgnoreCase("VROverlayError_KeyboardAlreadyInUse")) keyboardShowing = true;
				}

				if (!Display.isActive()) ChatUtil.print("Warning: Game window is not in focus, VR keyboard will not function.");

			} else {
				try {
					vrOverlay.HideKeyboard.apply();				
				} catch (Error e) {
					// TODO: handle exception
				}
				keyboardShowing = false;
			}


		} catch (Exception e) {
			// TODO: handle exception
		}

		return keyboardShowing;
	}



	//TODO: to hell with all these conversions.
	//sets mouse position for currentscreen
	private static void processGui() {

		if(guiRotationPose == null) return;

		Vector3f controllerPos = new Vector3f();
		//OpenVRUtil.convertMatrix4ftoTranslationVector(controllerPose[0]);
		Vec3 con = VRRenderState.getControllerRenderPos(0);
		controllerPos.x	= (float) con.xCoord;
		controllerPos.y	= (float) con.yCoord;
		controllerPos.z	= (float) con.zCoord;

		Vec3 controllerdir = VR.player().getControllerMainDir_World();
		Vector3f cdir = new Vector3f((float)controllerdir.xCoord,(float) controllerdir.yCoord,(float) controllerdir.zCoord);
		Vector3f forward = new Vector3f(0,0,1);

		// The pose is kept in the play area's frame; the panel is drawn with the play area's own
		// turn applied on top. The ray has to be aimed at where the panel was drawn, not at where
		// it is stored, or after a snap turn it misses the panel entirely -- and a ray that misses
		// leaves no cursor on screen at all.
		boolean inMenuRoom = mc.theWorld == null || mc.currentScreen instanceof GuiWinGame;
		Matrix4f panelRot = inMenuRoom ? guiRotationPose : Matrix4f.multiply(
				Matrix4f.rotationY((float) Math.toRadians(VR.settings().vrWorldRotation)),
				guiRotationPose);

		Vector3f guiNormal = panelRot.transform(forward);
		Vector3f guiRight = panelRot.transform(new Vector3f(1,0,0));
		Vector3f guiUp = panelRot.transform(new Vector3f(0,1,0));

		float guiWidth = 1.0f;		
		float guiHalfWidth = guiWidth * 0.5f;		
		float guiHeight = 1.0f;	
		float guiHalfHeight = guiHeight * 0.5f;

		Vector3f gp;
		if (inMenuRoom) {
			gp = new Vector3f(guiPos_Room.x, guiPos_Room.y, guiPos_Room.z);
		} else {
			Vec3 turned = Vec3.createVectorHelper(guiPos_Room.x, guiPos_Room.y, guiPos_Room.z);
			turned.rotateAroundY((float) Math.toRadians(VR.settings().vrWorldRotation));
			gp = new Vector3f(
					(float) (turned.xCoord + VRRenderState.interPolatedRoomOrigin.xCoord),
					(float) (turned.yCoord + VRRenderState.interPolatedRoomOrigin.yCoord),
					(float) (turned.zCoord + VRRenderState.interPolatedRoomOrigin.zCoord));
		}

		Vector3f guiTopLeft = gp.subtract(guiUp.divide(1.0f / guiHalfHeight)).subtract(guiRight.divide(1.0f/guiHalfWidth));
		Vector3f guiTopRight = gp.subtract(guiUp.divide(1.0f / guiHalfHeight)).add(guiRight.divide(1.0f / guiHalfWidth));

		//Vector3f guiBottomLeft = guiPos.add(guiUp.divide(1.0f / guiHalfHeight)).subtract(guiRight.divide(1.0f/guiHalfWidth));
		//Vector3f guiBottomRight = guiPos.add(guiUp.divide(1.0f / guiHalfHeight)).add(guiRight.divide(1.0f/guiHalfWidth));

		float guiNormalDotControllerDirection = guiNormal.dot(cdir);
		if (Math.abs(guiNormalDotControllerDirection) > 0.00001f)
		{//pointed normal to the GUI
			float intersectDist = -guiNormal.dot(controllerPos.subtract(guiTopLeft)) / guiNormalDotControllerDirection;
			Vector3f pointOnPlane = controllerPos.add(cdir.divide(1.0f/intersectDist));

			Vector3f relativePoint = pointOnPlane.subtract(guiTopLeft);
			float u = relativePoint.dot(guiRight.divide(1.0f/guiWidth));
			float v = relativePoint.dot(guiUp.divide(1.0f/guiWidth));


			// adjust vertical for aspect ratio
			v = ( (v - 0.5f) * ((float)mc.displayWidth / (float)mc.displayHeight) ) + 0.5f;

			// This inverts the size of the quad VRGui actually draws, so the plane the ray
			// intersects is the plane the player can see. It used to be a literal 0.68f with a
			// note from jrbudda wondering where the number came from and guessing it was one
			// over the default hud scale of 1.5; the guess was right, and taking it from the
			// same constant the quad uses makes the two provably the same plane instead of two
			// numbers that happen to be close. It also gains the two percent of panel width the
			// approximation was costing at the edges.
			float rayScale = VRGui.rayScale();

			u = ( u - 0.5f ) * rayScale / guiScale + 0.5f;
			v = ( v - 0.5f ) * rayScale / guiScale + 0.5f;

			if (u<0 || v<0 || u>1 || v>1)
			{
				// offscreen
				controllerMouseX = -1.0f;
				controllerMouseY = -1.0f;
			}
			else if (controllerMouseX == -1.0f)
			{
				controllerMouseX = (int) (u * mc.displayWidth);
				controllerMouseY = (int) (v * mc.displayHeight);
			}
			else
			{
				// apply some smoothing between mouse positions
				float newX = (int) (u * mc.displayWidth);
				float newY = (int) (v * mc.displayHeight);
				controllerMouseX = controllerMouseX * 0.7f + newX * 0.3f;
				controllerMouseY = controllerMouseY * 0.7f + newY * 0.3f;
			}

			// copy to mc for debugging
			VRGuiState.guiU = u;
			VRGuiState.guiV = v;
			VRGuiState.intersectDist = intersectDist;
			VRGuiState.pointOnPlaneX = pointOnPlane.x;
			VRGuiState.pointOnPlaneY = pointOnPlane.y;
			VRGuiState.pointOnPlaneZ = pointOnPlane.z;
			VRGuiState.guiTopLeftX = guiTopLeft.x;
			VRGuiState.guiTopLeftY = guiTopLeft.y;
			VRGuiState.guiTopLeftZ = guiTopLeft.z;
			VRGuiState.guiTopRightX = guiTopRight.x;
			VRGuiState.guiTopRightY = guiTopRight.y;
			VRGuiState.guiTopRightZ = guiTopRight.z;
			VRGuiState.controllerPosX = controllerPos.x;
			VRGuiState.controllerPosY = controllerPos.y;
			VRGuiState.controllerPosZ = controllerPos.z;
		}

		// Which button clicks in a menu is the player's to decide, so these come from their
		// bindings rather than from a hard-coded button per controller model. The edges are kept
		// here because a click is a press and a release rather than a state, and the previous
		// frame's state is the only way to tell one from the other.
		boolean pressedshift = keyGuiShift.getIsKeyPressed();
		boolean pressedleftclick = keyGuiLeftClick.getIsKeyPressed();
		boolean pressedrightclick = keyGuiRightClick.getIsKeyPressed();
		boolean pressedmiddleclick = keyGuiMiddleClick.getIsKeyPressed();

		boolean lastpressedShift = guiWasShift;
		boolean lastpressedleftclick = guiWasLeftClick;
		boolean lastpressedrightclick = guiWasRightClick;
		boolean lastpressedmiddleclick = guiWasMiddleClick;

		guiWasShift = pressedshift;
		guiWasLeftClick = pressedleftclick;
		guiWasRightClick = pressedrightclick;
		guiWasMiddleClick = pressedmiddleclick;

		if (controllerDeviceIndex[LEFT_CONTROLLER] != -1) {
			//Shift
			if (mc.currentScreen != null && pressedshift && !lastpressedShift)				
			{
				//press Shift
				VRInputState.pressShiftFake = true;
				if (Display.isActive()) KeyboardSimulator.robot.keyPress(KeyEvent.VK_SHIFT);
			}


			if (mc.currentScreen != null && !pressedshift && lastpressedShift)			
			{
				//release Shift
				VRInputState.pressShiftFake = false;
				if (Display.isActive()) KeyboardSimulator.robot.keyRelease(KeyEvent.VK_SHIFT);
			}	
			//end Shift
		}

		if (controllerMouseX >= 0 && controllerMouseX < mc.displayWidth
				&& controllerMouseY >=0 && controllerMouseY < mc.displayHeight)
		{
			// mouse on screen
			int mouseX = Math.min(Math.max((int) controllerMouseX, 0), mc.displayWidth);
			int mouseY = Math.min(Math.max((int) controllerMouseY, 0), mc.displayHeight);

			if (controllerDeviceIndex[RIGHT_CONTROLLER] != -1)
			{
				InputInjector.mouseMoveEvent(mouseX, mouseY); // Needs to be called first, since it only puts an event if delta != 0
				Mouse.setCursorPosition(mouseX, mouseY);
				controllerMouseValid = true;

				//LMB
				if (mc.currentScreen != null && pressedleftclick && !lastpressedleftclick)
				{ //press left mouse button
					if (Display.isActive()) 
						KeyboardSimulator.robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
					else VanillaExt.mouseDown(mc.currentScreen, mouseX, mouseY, 0);
				}	

				if (mc.currentScreen != null && pressedleftclick)				
					VanillaExt.mouseDrag(mc.currentScreen, mouseX, mouseY);//Signals mouse move


				if (mc.currentScreen != null && !pressedleftclick && lastpressedleftclick) {
					//release left mouse button
					if (Display.isActive()) KeyboardSimulator.robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
					else VanillaExt.mouseUp(mc.currentScreen, mouseX, mouseY, 0);
				}
				//end LMB

				//RMB
				if (mc.currentScreen != null && pressedrightclick && !lastpressedrightclick) {
					//press right mouse button
					if (Display.isActive()) KeyboardSimulator.robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
					else VanillaExt.mouseDown(mc.currentScreen, mouseX, mouseY, 1);
				}	

				if (mc.currentScreen != null && pressedrightclick)
					VanillaExt.mouseDrag(mc.currentScreen, mouseX, mouseY);//Signals mouse move

				if (mc.currentScreen != null && !pressedrightclick && lastpressedrightclick) {
					//release right mouse button
					if (Display.isActive()) KeyboardSimulator.robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
					else VanillaExt.mouseUp(mc.currentScreen, mouseX, mouseY, 1);
				}	
				//end RMB	

				//MMB
				if (mc.currentScreen != null && pressedmiddleclick && !lastpressedmiddleclick) {
					//press middle mouse button
					if (Display.isActive()) KeyboardSimulator.robot.mousePress(InputEvent.BUTTON2_DOWN_MASK);
					else VanillaExt.mouseDown(mc.currentScreen, mouseX, mouseY, 2);
				}	

				if (mc.currentScreen != null && pressedmiddleclick) 
					VanillaExt.mouseDrag(mc.currentScreen, mouseX, mouseY);//Signals mouse move

				if (mc.currentScreen != null && !pressedmiddleclick && lastpressedmiddleclick) {
					//release middle mouse button
					if (Display.isActive()) KeyboardSimulator.robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
					else VanillaExt.mouseUp(mc.currentScreen, mouseX, mouseY, 2);
				}	
				//end MMB

			}
		} else { //mouse off screen
			if(controllerMouseTicks == 0)
				controllerMouseValid = false;

			if(controllerMouseTicks>0)controllerMouseTicks--;

			if (mc.thePlayer != null && !(mc.currentScreen instanceof GuiWinGame))
			{
				boolean pressedRMB = pressedrightclick && !lastpressedrightclick;
				boolean pressedLMB = pressedleftclick && !lastpressedleftclick;

				if (pressedLMB || pressedRMB)
				{
					//	{mc.thePlayer.closeScreen();}			
				}
			}
		}
	}



	// The legacy button layer lived here: a table of button numbers per controller model, polled
	// by hand, with a separate copy of the whole thing for Oculus hardware. It is gone because
	// SteamVR stops answering legacy button queries once an action manifest is loaded, and because
	// the thing it was for -- knowing which button is which on which controller -- is exactly what
	// the action system does instead, including for controllers that did not exist yet.

	/**
	 * Lets a later attempt try again after one has failed.
	 *
	 * <p>{@link #init()} remembers that it has been tried and refuses to repeat itself, which is
	 * right while nothing has changed and wrong the moment the player presses the button: the
	 * usual reason the first attempt failed is that SteamVR was not running yet, and by the time
	 * somebody asks a second time it probably is.
	 */
	public static void allowRetry() {
		if (!initialized) {
			tried = false;
		}
	}

	/**
	 * Whether the headset is on somebody's head.
	 *
	 * <p>The runtime tracks this per device and reports it as an activity level. Two of the
	 * levels mean a person is there -- one interacting, one who has been still long enough for
	 * the runtime to notice -- and the rest mean the headset is sitting on the desk.
	 */
	private static boolean warnedAboutActivityLevel;

	public static boolean isHeadsetInUse() {
		if (vrsystem == null) {
			return false;
		}
		int level = vrsystem.GetTrackedDeviceActivityLevel.apply(
				JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd);
		switch (level) {
			case JOpenVRLibrary.EDeviceActivityLevel
					.EDeviceActivityLevel_k_EDeviceActivityLevel_UserInteraction:
			case JOpenVRLibrary.EDeviceActivityLevel
					.EDeviceActivityLevel_k_EDeviceActivityLevel_UserInteraction_Timeout:
				return true;
			case JOpenVRLibrary.EDeviceActivityLevel
					.EDeviceActivityLevel_k_EDeviceActivityLevel_Idle:
			case JOpenVRLibrary.EDeviceActivityLevel
					.EDeviceActivityLevel_k_EDeviceActivityLevel_Standby:
				return false;
			default:
				// A runtime that will not say is not a reason to refuse to draw. Answering "no"
				// here would mean a headset that reports nothing never gets a picture at all,
				// with nothing on screen to explain why.
				if (!warnedAboutActivityLevel) {
					warnedAboutActivityLevel = true;
					ViveCraftX.LOG.warn("The runtime does not report whether the headset is "
							+ "being worn (level {}); taking it as worn", level);
				}
				return true;
		}
	}

	/**
	 * Reads the runtime's messages without touching the compositor.
	 *
	 * <p>For when VR is up but idle. The full poll waits on the compositor for the next frame,
	 * which is exactly the wrong thing to do while not drawing any -- it would block the game
	 * thread outright -- but the message queue still has to be drained, or SteamVR shutting down
	 * goes unnoticed and the headset coming back is never acted on.
	 */
	public static void pollRuntimeEventsOnly() {
		pollInputEvents();
	}

	public static void destroy()
	{
		if (initialized)
		{
			JOpenVRLibrary.VR_ShutdownInternal();
			initialized = false;
		}
	}




	//	public HmdParameters getHMDInfo()
	//	{
	//		HmdParameters hmd = new HmdParameters();
	//		if ( isInitialized() )
	//		{
	//			IntBuffer rtx = IntBuffer.allocate(1);
	//			IntBuffer rty = IntBuffer.allocate(1);
	//			vrsystem.GetRecommendedRenderTargetSize.apply(rtx, rty);
	//
	//			hmd.Type = HmdType.ovrHmd_Other;
	//			hmd.ProductName = "OpenVR";
	//			hmd.Manufacturer = "Unknown";
	//			hmd.AvailableHmdCaps = 0;
	//			hmd.DefaultHmdCaps = 0;
	//			hmd.AvailableTrackingCaps = HmdParameters.ovrTrackingCap_Orientation | HmdParameters.ovrTrackingCap_Position;
	//			hmd.DefaultTrackingCaps = HmdParameters.ovrTrackingCap_Orientation | HmdParameters.ovrTrackingCap_Position;
	//			hmd.Resolution = new Sizei( rtx.get(0) * 2, rty.get(0) );
	//
	//			float topFOV = vrsystem.GetFloatTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_FieldOfViewTopDegrees_Float, hmdErrorStore);
	//			float bottomFOV = vrsystem.GetFloatTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_FieldOfViewBottomDegrees_Float, hmdErrorStore);
	//			float leftFOV = vrsystem.GetFloatTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_FieldOfViewLeftDegrees_Float, hmdErrorStore);
	//			float rightFOV = vrsystem.GetFloatTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_FieldOfViewRightDegrees_Float, hmdErrorStore);
	//
	//			hmd.DefaultEyeFov[0] = new FovPort((float)Math.tan(topFOV),(float)Math.tan(bottomFOV),(float)Math.tan(leftFOV),(float)Math.tan(rightFOV));
	//			hmd.DefaultEyeFov[1] = new FovPort((float)Math.tan(topFOV),(float)Math.tan(bottomFOV),(float)Math.tan(leftFOV),(float)Math.tan(rightFOV));
	//			hmd.MaxEyeFov[0] = new FovPort((float)Math.tan(topFOV),(float)Math.tan(bottomFOV),(float)Math.tan(leftFOV),(float)Math.tan(rightFOV));
	//			hmd.MaxEyeFov[1] = new FovPort((float)Math.tan(topFOV),(float)Math.tan(bottomFOV),(float)Math.tan(leftFOV),(float)Math.tan(rightFOV));
	//			hmd.DisplayRefreshRate = 90.0f;
	//		}
	//
	//		return hmd;
	//	}




	
	public static float joyPadX, joyPadZ;

	//OK the fundamental problem with this is Minecraft uses a LWJGL event buffer for keyboard and mouse inputs. It polls those devices faster
	//and presents the game with a nice queue of things that happened. With OpenVR we're polling the controllers directly on the -game- (edit render?) loop.
	//This means we should only set keys as pressed when they change state, or they will repeat.
	//And we should still unpress the key when released.
	//TODO: make a new class that polls more quickly and provides Minecraft.java with a HTCController.next() event queue. (unless openVR has one?)

	//TODO: move somewhere else.



	//jrbuda:: oh hello there you are.
	private static void pollInputEvents()
	{
		if(vrsystem == null) return;

		//TODO: use this for everything, maybe.
		jopenvr.VREvent_t event = new jopenvr.VREvent_t();

		while (vrsystem.PollNextEvent.apply(event, event.size() ) > 0)
		{

			switch (event.eventType) {
			case EVREventType.EVREventType_VREvent_KeyboardClosed:
				//'huzzah'
				keyboardShowing = false;
				if (mc.currentScreen instanceof GuiChat && !VR.settings().seated) {
					GuiTextField field = (GuiTextField)MCReflection.getField(MCReflection.chatInputField, mc.currentScreen);
					if (field != null) {
						String s = field.getText().trim();
						if (!s.isEmpty()) {
							((GuiChat) mc.currentScreen).func_146403_a(s);
						}
					}
					mc.displayGuiScreen((GuiScreen)null);
				}
				break;
			case EVREventType.EVREventType_VREvent_KeyboardCharInput:
				byte[] inbytes = event.data.getPointer().getByteArray(0, 8);	
				int len = 0;			
				for (byte b : inbytes) {
					if(b>0)len++;
				}
				String str = new String(inbytes,0,len, StandardCharsets.UTF_8);
				if (mc.currentScreen != null) { // experimental, needs testing
					try {
						for (char ch : str.toCharArray()) {
							int[] codes = KeyboardSimulator.getLWJGLCodes(ch);
							int code = codes.length > 0 ? codes[codes.length - 1] : 0;
							if (InputInjector.isSupported()) InputInjector.typeKey(code, ch);
							else VanillaExt.keyTyped(mc.currentScreen, ch, code);
							break;
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					KeyboardSimulator.type(str); //holy shit it works.
				}
				break;
			case EVREventType.EVREventType_VREvent_Quit:
				// SteamVR is shutting down. Taking the game with it is the right default -- the
				// usual reason it is shutting down is that the player closed it, meaning they are
				// finished -- but somebody who only wanted the headset back is better served by
				// the game carrying on flat.
				if (VR.settings() != null && !VR.settings().closeWithRuntime) {
					// Tells the runtime it may go; without it SteamVR waits out its timeout on us.
					vrsystem.AcknowledgeQuit_Exiting.apply();
					ViveCraftX.LOG.info("SteamVR closed; carrying on without VR");
					lol.gzmc.vivecraftx.VRState.runtimeClosed();
				} else {
					mc.shutdown();
				}
				break;
			default:
				break;
			}
		}
	}







	private static void updatePose()
	{
		if ( vrsystem == null || vrCompositor == null )
			return;
		int ret = vrCompositor.WaitGetPoses.apply(hmdTrackedDevicePoseReference, JOpenVRLibrary.k_unMaxTrackedDeviceCount, null, 0);

		if(ret>0)System.out.println("Compositor Error: GetPoseError " + OpenVRStereoRenderer.getCompostiorError(ret)); 

		if(ret == 101){ //this is so dumb but it works.
			triggerHapticPulse(0, 500);
			triggerHapticPulse(1, 500);
		}

		if(controllerDeviceIndex[0] != -1 && controllerDeviceIndex[1] !=-1)
			controllerDeviceIndex[THIRD_CONTROLLER] = -1;

		for (int nDevice = 0; nDevice < JOpenVRLibrary.k_unMaxTrackedDeviceCount; ++nDevice )
		{
			hmdTrackedDevicePoses[nDevice].read();
			if ( hmdTrackedDevicePoses[nDevice].bPoseIsValid != 0 )
			{
				jopenvr.OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(hmdTrackedDevicePoses[nDevice].mDeviceToAbsoluteTracking, poseMatrices[nDevice]);
			}		

			if(VR.settings().displayMirrorMode == VRSettings.MIRROR_MIXED_REALITY){
				int c = vrsystem.GetTrackedDeviceClass.apply(nDevice);
				int r = vrsystem.GetControllerRoleForTrackedDeviceIndex.apply(nDevice);
				if((c == 2 && r == 0) || c == 3)
					controllerDeviceIndex[THIRD_CONTROLLER] = nDevice;
			} 

		}

		if (hmdTrackedDevicePoses[JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd].bPoseIsValid != 0 )
		{
			OpenVRUtil.Matrix4fCopy(poseMatrices[JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd], hmdPose);
			headIsTracking = true;
		}
		else
		{
			headIsTracking = false;
			OpenVRUtil.Matrix4fSetIdentity(hmdPose);
			hmdPose.M[1][3] = 1.62f;
		}

		updateViewOffset(headIsTracking);

		// Which device is in which hand comes from the pose actions rather than from asking the
		// runtime to enumerate devices and guess roles. It is the same question the action system
		// already answers for buttons, and answering it the same way means a controller swapped
		// between hands mid-session is simply reported in its new hand.
		// Reversed hands has to move the poses as well, and this is the only place that can do
		// it. The reversed binding profile swaps which physical controller the buttons come from,
		// but it leaves the two hand poses bound to their own hands -- so the main hand's trigger
		// was being read from the player's physical left while the item, the arm and the aim ray
		// were still drawn at the physical right. You held the sword in one hand and pulled the
		// trigger in the other. With both ends swapped a hand index means the same device all the
		// way through, which is what the render side's own flip already assumes.
		boolean reversed = VR.settings() != null && VR.settings().vrReverseHands;
		if (inputInitialized) {
			updateControllerPose(RIGHT_CONTROLLER, reversed ? leftPoseHandle : rightPoseHandle);
			updateControllerPose(LEFT_CONTROLLER, reversed ? rightPoseHandle : leftPoseHandle);
		}

		// The third "controller" is the mixed-reality camera, which is a tracker rather than a
		// hand and so has no pose action; it is still found by role.
		if (controllerDeviceIndex[THIRD_CONTROLLER] != -1) {
			controllerTracking[THIRD_CONTROLLER] = true;
			OpenVRUtil.Matrix4fCopy(poseMatrices[controllerDeviceIndex[THIRD_CONTROLLER]],
					controllerPose[THIRD_CONTROLLER]);
		} else {
			controllerTracking[THIRD_CONTROLLER] = false;
		}

		if (tipTransformsStale) {
			// Keeps trying while any controller has yet to answer, since nothing will tell us
			// when its model becomes available -- but at a walking pace rather than once a frame,
			// because the commonest reason for failing is a setting that will not change back.
			long now = System.nanoTime();
			if (now - tipTransformsRetryAt >= 0L) {
				tipTransformsRetryAt = now + TIP_TRANSFORM_RETRY_NANOS;
				tipTransformsStale = !getTipTransforms();
			}
		}

		updateAim();
	}

	/**
	 *
	 * @return Play area size or null if not valid
	 */
	public static float[] getPlayAreaSize() {
		if (vrChaperone == null || vrChaperone.GetPlayAreaSize == null) return null;
		FloatByReference bufz = new FloatByReference();
		FloatByReference bufx = new FloatByReference();
		byte valid = vrChaperone.GetPlayAreaSize.apply(bufx, bufz);
		if (valid == 1) return new float[]{bufx.getValue(), bufz.getValue()};
		return null;
	}

	/**
	 * @return The coordinate of the 'center' eye position relative to the head yaw plane
	 */

	public static Vec3 getCenterEyePosition() {
		Vector3f pos = OpenVRUtil.convertMatrix4ftoTranslationVector(hmdPose);
		return Vec3.createVectorHelper(pos.x + offset.x, pos.y + offset.y, pos.z + offset.z);
	}

	/**
	 * @return The coordinate of the left or right eye position relative to the head yaw plane
	 */

	static Vec3 getEyePosition(RenderPass eye)
	{
		Matrix4f hmdToEye = hmdPoseRightEye;
		if ( eye == RenderPass.Left )
		{
			hmdToEye = hmdPoseLeftEye;
		} else if ( eye == RenderPass.Right)
		{
			hmdToEye = hmdPoseRightEye;
		} else {
			hmdToEye = null;
		}

		if(hmdToEye == null){
			Matrix4f pose = hmdPose;
			Vector3f pos = OpenVRUtil.convertMatrix4ftoTranslationVector(pose);
			return Vec3.createVectorHelper(pos.x + offset.x, pos.y + offset.y, pos.z + offset.z);
		} else {
			Matrix4f pose = Matrix4f.multiply( hmdPose, hmdToEye );
			Vector3f pos = OpenVRUtil.convertMatrix4ftoTranslationVector(pose);
			return Vec3.createVectorHelper(pos.x + offset.x, pos.y + offset.y, pos.z + offset.z);
		}
	}


	/**
	 * Gets the Yaw(Y) from YXZ Euler angle representation of orientation
	 *
	 * @return The Head Yaw, in degrees
	 */

	static float getHeadYawDegrees()
	{
		Quatf quat = OpenVRUtil.convertMatrix4ftoRotationQuat(hmdPose);

		EulerOrient euler = OpenVRUtil.getEulerAnglesDegYXZ(quat);

		return euler.yaw;
	}

	/**
	 * Gets the Pitch(X) from YXZ Euler angle representation of orientation
	 *
	 * @return The Head Pitch, in degrees
	 */

	static float getHeadPitchDegrees()
	{
		Quatf quat = OpenVRUtil.convertMatrix4ftoRotationQuat(hmdPose);

		EulerOrient euler = OpenVRUtil.getEulerAnglesDegYXZ(quat);

		return euler.pitch;
	}

	/**
	 * Gets the orientation quaternion
	 *
	 * @return quaternion w, x, y & z components
	 */


	final String k_pch_SteamVR_Section = "steamvr";
	final String k_pch_SteamVR_RenderTargetMultiplier_Float = "renderTargetMultiplier";

	/**
	 * Turns a position in the world into one in the play area.
	 *
	 * <p>The play area sits somewhere in the world and can be turned under the player by a snap
	 * turn; both have to come off. Panel poses are kept in this space because that is the space
	 * they are read back in -- and because a panel anchored to the room turns with the player when
	 * they snap-turn, instead of being left behind facing the way they used to be standing.
	 */
	private static Vector3f worldToRoom(Vector3f world) {
		Vec3 local = Vec3.createVectorHelper(
				world.x - VRRenderState.interPolatedRoomOrigin.xCoord,
				world.y - VRRenderState.interPolatedRoomOrigin.yCoord,
				world.z - VRRenderState.interPolatedRoomOrigin.zCoord);
		local.rotateAroundY((float) Math.toRadians(-VR.settings().vrWorldRotation));
		return new Vector3f((float) local.xCoord, (float) local.yCoord, (float) local.zCoord);
	}

	static void onGuiScreenChanged(GuiScreen previousScreen, GuiScreen newScreen)
	{
		KeyBinding.unPressAllKeys();
		// Releasing the keys is not enough on its own: the button that closed the screen is very
		// likely still physically held, and the next frame activates the in-game action set, where
		// the same trigger means attack. Reported as a change from nothing to pressed, it swung at
		// whatever happened to be in front of the player the instant they left a menu.
		ignorePressesNextFrame();
		KeyboardSimulator.robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
		KeyboardSimulator.robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
		KeyboardSimulator.robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
		KeyboardSimulator.robot.keyRelease(KeyEvent.VK_SHIFT);

		if(newScreen == null 	|| (mc.thePlayer!=null && !mc.thePlayer.isEntityAlive())){
			guiPos_Room = null;
			guiRotationPose = null;
		}

		// main menu/win game/
		if (mc.theWorld==null || newScreen instanceof GuiWinGame ) {
			//TODO reset scale things
			MCOpenVR.guiScale = 2.0f;
			VR.player().worldScale = 1;
			VR.settings().vrWorldRotationCached = VR.settings().vrWorldRotation;
			VR.settings().vrWorldRotation = 0;
			VR.player().worldRotationRadians = (float) Math.toRadians( VR.settings().vrWorldRotation);
			float[] playArea = getPlayAreaSize();
			// In the play area's own space, like every other pose stored in this field, and
			// unlike what stood here: the room origin was added on, which is a position in the
			// world. Starting the game and going no further, that origin is still (0,0,0) and
			// adding it changes nothing, which is why this looked right for as long as nobody
			// came back out of a world. Quitting to the title screen from inside one, it is
			// wherever the player was standing, and the menu was placed that many blocks from
			// the camera -- past the far clip, leaving the headset showing the menu room's
			// background colour and nothing in it.
			guiPos_Room = new Vector3f(
					0F,
					1.3F,
					(playArea != null ? -playArea[1] / 2 : -1.5F) - 0.3F);


			guiRotationPose = new Matrix4f();
			guiRotationPose.M[0][0] = guiRotationPose.M[1][1] = guiRotationPose.M[2][2] = guiRotationPose.M[3][3] = 1.0F;
			guiRotationPose.M[0][1] = guiRotationPose.M[1][0] = guiRotationPose.M[2][3] = guiRotationPose.M[3][1] = 0.0F;
			guiRotationPose.M[0][2] = guiRotationPose.M[1][2] = guiRotationPose.M[2][0] = guiRotationPose.M[3][2] = 0.0F;
			guiRotationPose.M[0][3] = guiRotationPose.M[1][3] = guiRotationPose.M[2][1] = guiRotationPose.M[3][0] = 0.0F;

			return;
		} else { //these dont update when screen open.
			if (VR.settings().vrWorldRotationCached != 0) {
				VR.settings().vrWorldRotation = VR.settings().vrWorldRotationCached;
				VR.settings().vrWorldRotationCached = 0;
			}
		}		

		// i am dead view 
		if (mc.thePlayer!=null && !mc.thePlayer.isEntityAlive())
		{
			// Without the world turn: the panel's pose is kept in the play area's own frame and
			// the turn is applied again when it is drawn.
			Matrix4f max = MCOpenVR.hmdRotation;
			MCOpenVR.guiScale = 1.0f*VR.player().worldScale;
			Vec3 v = VRRenderState.getEyeRenderPos(RenderPass.Center);
			Vec3 d = VR.roomScale().getHMDDir_World();
			Vector3f guiPos_World = new Vector3f(
					(float) (v.xCoord + d.xCoord*VR.player().worldScale),
					(float) (v.yCoord + d.yCoord*VR.player().worldScale),
					(float) (v.zCoord + d.zCoord*VR.player().worldScale));

			Quatf orientationQuat = OpenVRUtil.convertMatrix4ftoRotationQuat(max);

			guiRotationPose = new Matrix4f(orientationQuat);

			guiRotationPose.M[3][3] = 1.0f;

			guiPos_Room = worldToRoom(guiPos_World);

		}  else if ( previousScreen==null && newScreen != null	|| 
				newScreen instanceof GuiContainerCreative 
				|| newScreen instanceof GuiChat) {			

			Quatf controllerOrientationQuat;
			// Over the block it belongs to, unless the player has asked for these to come to them
			// instead. Reading false here sends the screen down the same path the inventory takes,
			// which places it in front of the face at the inventory's own distance and scale.
			boolean appearOverBlock = !VR.settings().containerGuiAtFace
					&& ((newScreen instanceof GuiCrafting)
					|| (newScreen instanceof GuiChest)
					|| (newScreen instanceof GuiHopper)
					|| (newScreen instanceof GuiFurnace)
					|| (newScreen instanceof GuiBrewingStand)
					|| (newScreen instanceof GuiBeacon)
					|| (newScreen instanceof GuiDispenser)
					|| (newScreen instanceof GuiEnchantment)
					|| (newScreen instanceof GuiRepair))
					;

			// A miss has to be ruled out and not merely a null: the aim ray now reports one
			// rather than nothing at all, and its block coordinates are wherever the ray ran out
			// -- up to sixty-four blocks away in the sky. Opening a container is always a hit, so
			// in practice this never fires on a miss; checking anyway, because the alternative
			// failure is a crafting table's interface hanging in the air over the horizon.
			if(appearOverBlock && !VRAim.hitNothing(mc.objectMouseOver)){

				guiScale =(float) (Math.sqrt(VR.player().worldScale) * 2);
				Vector3f guiPos_World =new Vector3f((float) mc.objectMouseOver.blockX + 0.5f,
						(float) mc.objectMouseOver.blockY + 1.7f,
						(float) mc.objectMouseOver.blockZ + 0.5f);

				Vec3 pos = VR.roomScale().getHMDPos_World();
				Vector3f look = new Vector3f();
				look.x = (float) (guiPos_World.x - pos.xCoord);
				look.y = (float) (guiPos_World.y - pos.yCoord);
				look.z = (float) (guiPos_World.z - pos.zCoord);

				float pitch = (float) Math.asin(look.y/look.length());
				float yaw = (float) ((float) Math.PI + Math.atan2(look.x, look.z));    
				guiRotationPose = Matrix4f.rotationY(
						yaw - (float) Math.toRadians(VR.settings().vrWorldRotation));
				Matrix4f tilt = OpenVRUtil.rotationXMatrix(pitch);	
				guiRotationPose = Matrix4f.multiply(guiRotationPose,tilt);	

				guiPos_Room = worldToRoom(guiPos_World);

			}				
			else{
				Vec3 adj = Vec3.createVectorHelper(0,0,-2);
				if (newScreen instanceof GuiChat){
					adj = Vec3.createVectorHelper(0.3,1,-2);
				} else if (newScreen instanceof GuiScreenBook || newScreen instanceof GuiEditSign) {
					adj = Vec3.createVectorHelper(0,1,-2);
				}

				Vec3 v = VR.player().getHMDPos_World();
				Vec3 e = VR.roomScale().getCustomHMDVector(adj);

				Vector3f guiPos_World = new Vector3f(
						(float) (e.xCoord * VR.player().worldScale / 2 + v.xCoord),
						(float) (e.yCoord* VR.player().worldScale / 2 + v.yCoord),
						(float) (e.zCoord* VR.player().worldScale / 2 + v.zCoord));
				guiScale = VR.player().worldScale;
				if(mc.theWorld == null) guiScale = 2.0f;

				guiRotationPose = Matrix4f.rotationY((float) Math.toRadians(getHeadYawDegrees()));
				Matrix4f tilt = OpenVRUtil.rotationXMatrix((float)Math.toRadians(VR.roomScale().getHMDPitch_World()));	
				guiRotationPose = Matrix4f.multiply(guiRotationPose,tilt);		

				guiPos_Room = worldToRoom(guiPos_World);

			}
		}




	}

	//-------------------------------------------------------
	// IBodyAimController

	float getBodyPitchDegrees() {
		return 0; //Always return 0 for body pitch
	}

	float getAimYaw() {
		return aimYaw;
	}

	float getAimPitch() {
		return aimPitch;
	}

	Vector3f forward = new Vector3f(0,0,-1);

	Vec3 getAimVector( int controller ) {
		Matrix4f aimRotation = getAimRotation(controller);
		Vector3f controllerDirection = aimRotation.transform(forward);
		Vec3 out = Vec3.createVectorHelper(controllerDirection.x, controllerDirection.y,controllerDirection.z);
		return out;

	}


	public static Matrix4f getAimRotation( int controller ) {
		return controller == 0 ? controllerRotation[0]: controllerRotation[1];
	}


	/**
	 * Where the hand has been over the last moment, averaged, in the same frame as the head.
	 *
	 * <p>The history holds raw tracker positions. The head's position does not -- it carries the
	 * tracking shift, which recentring and the fixed view height both write to. Measuring one
	 * against the other without this would give a distance that is not a distance.
	 */
	public static Vec3 getSmoothedAimSource(int controller, double seconds) {
		Vec3 raw = controllerHistory[controller].averagePosition(seconds);
		if (VR.settings() != null && VR.settings().seated) {
			return raw;
		}
		return raw.addVector(offset.x, offset.y, offset.z);
	}

	static Vec3 getAimSource( int controller ) {
		if(!VR.settings().seated) 	return Vec3.createVectorHelper(aimSource[controller].xCoord + offset.x, aimSource[controller].yCoord + offset.y, aimSource[controller].zCoord + offset.z);
		return Vec3.createVectorHelper(aimSource[controller].xCoord, aimSource[controller].yCoord, aimSource[controller].zCoord);
	}


	/**
	 * Buzzes one of the hands, by the index the gameplay code thinks in.
	 *
	 * <p>This used to ask the runtime directly, by device, the way it was done before SteamVR had
	 * an input system. That call stops being answered the moment an action manifest is loaded --
	 * which this mod does at startup -- so every buzz in the game had been silently going nowhere:
	 * hitting something, drawing a bow, grabbing a ladder, eating, changing hotbar slot. It goes
	 * through the action system now, like everything else.
	 *
	 * @param controller 0 for the main hand, 1 for the other
	 * @param strength how long to buzz for, in millionths of a second
	 */
	public static void triggerHapticPulse(int controller, int strength) {
		triggerHapticPulse(controller == 0 ? ControllerType.RIGHT : ControllerType.LEFT, strength);
	}



	private static void updateAim() {
		if (mc==null)
			return;
		Vector3f forward = new Vector3f(0,0,-1);

		hmdRotation.M[0][0] = hmdPose.M[0][0];
		hmdRotation.M[0][1] = hmdPose.M[0][1];
		hmdRotation.M[0][2] = hmdPose.M[0][2];
		hmdRotation.M[0][3] = 0.0F;
		hmdRotation.M[1][0] = hmdPose.M[1][0];
		hmdRotation.M[1][1] = hmdPose.M[1][1];
		hmdRotation.M[1][2] = hmdPose.M[1][2];
		hmdRotation.M[1][3] = 0.0F;
		hmdRotation.M[2][0] = hmdPose.M[2][0];
		hmdRotation.M[2][1] = hmdPose.M[2][1];
		hmdRotation.M[2][2] = hmdPose.M[2][2];
		hmdRotation.M[2][3] = 0.0F;
		hmdRotation.M[3][0] = 0.0F;
		hmdRotation.M[3][1] = 0.0F;
		hmdRotation.M[3][2] = 0.0F;
		hmdRotation.M[3][3] = 1.0F;

		headDirection = hmdRotation.transform(forward);

		Vec3 eye = getCenterEyePosition();
		hmdHistory.add(eye);
		Vector3f v3 = MCOpenVR.hmdRotation.transform(new Vector3f(0,-.1f, .1f));
		hmdPivotHistory.add(Vec3.createVectorHelper(v3.x+eye.xCoord, v3.y+eye.yCoord, v3.z+eye.zCoord));	

		handRotation[0].M[0][0] = controllerPose[0].M[0][0];
		handRotation[0].M[0][1] = controllerPose[0].M[0][1];
		handRotation[0].M[0][2] = controllerPose[0].M[0][2];
		handRotation[0].M[0][3] = 0.0F;
		handRotation[0].M[1][0] = controllerPose[0].M[1][0];
		handRotation[0].M[1][1] = controllerPose[0].M[1][1];
		handRotation[0].M[1][2] = controllerPose[0].M[1][2];
		handRotation[0].M[1][3] = 0.0F;
		handRotation[0].M[2][0] = controllerPose[0].M[2][0];
		handRotation[0].M[2][1] = controllerPose[0].M[2][1];
		handRotation[0].M[2][2] = controllerPose[0].M[2][2];
		handRotation[0].M[2][3] = 0.0F;
		handRotation[0].M[3][0] = 0.0F;
		handRotation[0].M[3][1] = 0.0F;
		handRotation[0].M[3][2] = 0.0F;
		handRotation[0].M[3][3] = 1.0F;	

		if(VR.settings().seated){
			// Seated play aims with the head, so both hands are reported at it.
			OpenVRUtil.Matrix4fCopy(hmdPose, controllerPoseTip[0]);
			OpenVRUtil.Matrix4fCopy(hmdPose, controllerPoseTip[1]);
		} else	
			controllerPoseTip[0] = Matrix4f.multiply(controllerPose[0], controllerTipTransform[0]);

		// grab controller position in tracker space, scaled to minecraft units
		Vector3f controllerPos = OpenVRUtil.convertMatrix4ftoTranslationVector(controllerPoseTip[0]);

		aimSource[0] = Vec3.createVectorHelper(controllerPos.x, controllerPos.y, controllerPos.z);

		controllerHistory[0].add(aimSource[0]);

		// build matrix describing controller rotation
		controllerRotation[0].M[0][0] = controllerPoseTip[0].M[0][0];
		controllerRotation[0].M[0][1] = controllerPoseTip[0].M[0][1];
		controllerRotation[0].M[0][2] = controllerPoseTip[0].M[0][2];
		controllerRotation[0].M[0][3] = 0.0F;
		controllerRotation[0].M[1][0] = controllerPoseTip[0].M[1][0];
		controllerRotation[0].M[1][1] = controllerPoseTip[0].M[1][1];
		controllerRotation[0].M[1][2] = controllerPoseTip[0].M[1][2];
		controllerRotation[0].M[1][3] = 0.0F;
		controllerRotation[0].M[2][0] = controllerPoseTip[0].M[2][0];
		controllerRotation[0].M[2][1] = controllerPoseTip[0].M[2][1];
		controllerRotation[0].M[2][2] = controllerPoseTip[0].M[2][2];
		controllerRotation[0].M[2][3] = 0.0F;
		controllerRotation[0].M[3][0] = 0.0F;
		controllerRotation[0].M[3][1] = 0.0F;
		controllerRotation[0].M[3][2] = 0.0F;
		controllerRotation[0].M[3][3] = 1.0F;

		if(VR.settings().seated && mc.currentScreen == null){
			org.lwjgl.util.vector.Matrix4f temp = new org.lwjgl.util.vector.Matrix4f();

			float hRange = 110;
			float vRange = 180;
			double h = Mouse.getX() / (double) mc.displayWidth * hRange - (hRange / 2);
			double v = Mouse.getY() / (double) mc.displayHeight * vRange - (vRange / 2);

			double nPitch=-v;

			if(Display.isActive()){
				float rotStart = VR.settings().keyholeX;
				float rotSpeed = 2000 * VR.settings().xSensitivity;
				int leftedge=(int)((-rotStart + (hRange / 2)) *(double) mc.displayWidth / hRange )+1;
				int rightedge=(int)((rotStart + (hRange / 2)) *(double) mc.displayWidth / hRange )-1;
				float rotMul = ((float)Math.abs(h) - rotStart) / ((hRange / 2) - rotStart); // Scaled 0...1 from rotStart to FOV edge
				if(rotMul > 0.15) rotMul = 0.15f;

				if(h < -rotStart){
					VR.settings().vrWorldRotation += rotSpeed * rotMul * VRRenderState.getFrameDelta();
					VR.settings().vrWorldRotation %= 360; // Prevent stupidly large values
					hmdForwardYaw = (float)Math.toDegrees(Math.atan2(headDirection.x, headDirection.z));    
					//	VR.player().checkandUpdateRotateScale(true);
					Mouse.setCursorPosition(leftedge,Mouse.getY());
					h=-rotStart;
				}
				if(h > rotStart){
					VR.settings().vrWorldRotation -= rotSpeed * rotMul * VRRenderState.getFrameDelta();
					VR.settings().vrWorldRotation %= 360; // Prevent stupidly large values
					hmdForwardYaw = (float)Math.toDegrees(Math.atan2(headDirection.x, headDirection.z));    
					//	VR.player().checkandUpdateRotateScale(true);
					Mouse.setCursorPosition(rightedge,Mouse.getY());
					h=rotStart;
				}

				double ySpeed=0.5 * VR.settings().ySensitivity;
				nPitch=aimPitch+(v)*ySpeed;
				nPitch=MathHelper.clamp_double(nPitch,-89.9,89.9);
				Mouse.setCursorPosition(Mouse.getX(),mc.displayHeight/2);

			}
			temp.rotate((float) Math.toRadians(-nPitch), new org.lwjgl.util.vector.Vector3f(1,0,0));

			temp.rotate((float) Math.toRadians(-180 + h - hmdForwardYaw), new org.lwjgl.util.vector.Vector3f(0,1,0));

			controllerRotation[0].M[0][0] = temp.m00;
			controllerRotation[0].M[0][1] = temp.m01;
			controllerRotation[0].M[0][2] = temp.m02;

			controllerRotation[0].M[1][0] = temp.m10;
			controllerRotation[0].M[1][1] = temp.m11;
			controllerRotation[0].M[1][2] = temp.m12;

			controllerRotation[0].M[2][0] = temp.m20;
			controllerRotation[0].M[2][1] = temp.m21;
			controllerRotation[0].M[2][2] = temp.m22;
		}

		// Calculate aim angles from controller orientation
		// Minecraft entities don't have a roll, so just base it on a direction
		controllerDirection = controllerRotation[0].transform(forward);
		aimPitch = (float)Math.toDegrees(Math.asin(controllerDirection.y/controllerDirection.length()));
		aimYaw = (float)Math.toDegrees(Math.atan2(controllerDirection.x, controllerDirection.z));

		handRotation[1].M[0][0] = controllerPose[1].M[0][0];
		handRotation[1].M[0][1] = controllerPose[1].M[0][1];
		handRotation[1].M[0][2] = controllerPose[1].M[0][2];
		handRotation[1].M[0][3] = 0.0F;
		handRotation[1].M[1][0] = controllerPose[1].M[1][0];
		handRotation[1].M[1][1] = controllerPose[1].M[1][1];
		handRotation[1].M[1][2] = controllerPose[1].M[1][2];
		handRotation[1].M[1][3] = 0.0F;
		handRotation[1].M[2][0] = controllerPose[1].M[2][0];
		handRotation[1].M[2][1] = controllerPose[1].M[2][1];
		handRotation[1].M[2][2] = controllerPose[1].M[2][2];
		handRotation[1].M[2][3] = 0.0F;
		handRotation[1].M[3][0] = 0.0F;
		handRotation[1].M[3][1] = 0.0F;
		handRotation[1].M[3][2] = 0.0F;
		handRotation[1].M[3][3] = 1.0F;	

		// update off hand aim
		if(!VR.settings().seated) 
			controllerPoseTip[1] = Matrix4f.multiply(controllerPose[1], controllerTipTransform[1]);

		// update off hand aim
		Vector3f leftControllerPos = OpenVRUtil.convertMatrix4ftoTranslationVector(controllerPoseTip[1]);

		aimSource[1] = Vec3.createVectorHelper(leftControllerPos.x, leftControllerPos.y, leftControllerPos.z);

		controllerHistory[1].add(aimSource[1]);

		// build matrix describing controller rotation
		controllerRotation[1].M[0][0] = controllerPoseTip[1].M[0][0];
		controllerRotation[1].M[0][1] = controllerPoseTip[1].M[0][1];
		controllerRotation[1].M[0][2] = controllerPoseTip[1].M[0][2];
		controllerRotation[1].M[0][3] = 0.0F;
		controllerRotation[1].M[1][0] = controllerPoseTip[1].M[1][0];
		controllerRotation[1].M[1][1] = controllerPoseTip[1].M[1][1];
		controllerRotation[1].M[1][2] = controllerPoseTip[1].M[1][2];
		controllerRotation[1].M[1][3] = 0.0F;
		controllerRotation[1].M[2][0] = controllerPoseTip[1].M[2][0];
		controllerRotation[1].M[2][1] = controllerPoseTip[1].M[2][1];
		controllerRotation[1].M[2][2] = controllerPoseTip[1].M[2][2];
		controllerRotation[1].M[2][3] = 0.0F;
		controllerRotation[1].M[3][0] = 0.0F;
		controllerRotation[1].M[3][1] = 0.0F;
		controllerRotation[1].M[3][2] = 0.0F;
		controllerRotation[1].M[3][3] = 1.0F;

		if(VR.settings().seated){
			aimSource[1] = getCenterEyePosition();
			aimSource[0] = getCenterEyePosition();
		}

		lcontrollerDirection = controllerRotation[1].transform(forward);
		laimPitch = (float)Math.toDegrees(Math.asin(lcontrollerDirection.y/lcontrollerDirection.length()));
		laimYaw = (float)Math.toDegrees(Math.atan2(lcontrollerDirection.x, lcontrollerDirection.z));

		boolean debugThirdController = false;
		if(controllerTracking[2] || debugThirdController){ //third controller
			Vector3f thirdControllerPos = OpenVRUtil.convertMatrix4ftoTranslationVector(controllerPose[2]);
			aimSource[2] = Vec3.createVectorHelper(
					thirdControllerPos.x,
					thirdControllerPos.y,
					thirdControllerPos.z);

			// build matrix describing controller rotation
			controllerRotation[2].M[0][0] = controllerPose[2].M[0][0];
			controllerRotation[2].M[0][1] = controllerPose[2].M[0][1];
			controllerRotation[2].M[0][2] = controllerPose[2].M[0][2];
			controllerRotation[2].M[0][3] = 0.0F;
			controllerRotation[2].M[1][0] = controllerPose[2].M[1][0];
			controllerRotation[2].M[1][1] = controllerPose[2].M[1][1];
			controllerRotation[2].M[1][2] = controllerPose[2].M[1][2];
			controllerRotation[2].M[1][3] = 0.0F;
			controllerRotation[2].M[2][0] = controllerPose[2].M[2][0];
			controllerRotation[2].M[2][1] = controllerPose[2].M[2][1];
			controllerRotation[2].M[2][2] = controllerPose[2].M[2][2];
			controllerRotation[2].M[2][3] = 0.0F;
			controllerRotation[2].M[3][0] = 0.0F;
			controllerRotation[2].M[3][1] = 0.0F;
			controllerRotation[2].M[3][2] = 0.0F;
			controllerRotation[2].M[3][3] = 1.0F;

			thirdcontrollerDirection = controllerRotation[2].transform(forward);

			if(controllerDeviceIndex[THIRD_CONTROLLER]!=-1 && VR.settings().displayMirrorMode == VRSettings.MIRROR_MIXED_REALITY || debugThirdController) {
				VRHotkeys.snapMRCam(mc, debugThirdController ? 0 : 2);
				mrMovingCamActive = true;
			} else {
				mrMovingCamActive = false;
			}
		}
	}

	public static Matrix4f getHandRotation( int controller ) {
		return controller == 0 ? handRotation[0]: handRotation[1];
	}

	/**
	 * Works out where the usable end of each controller is, in its own space.
	 *
	 * <p>This matters more than it sounds: the transform is multiplied into the controller pose,
	 * so everything hanging off the hand -- the aim ray, the held item, the interface anchored to
	 * the wrist -- is placed through it. A wrong answer here moves and rotates all of them at once.
	 *
	 * <p>Asked for by input source rather than by controller state. The older call wants a filled
	 * {@code VRControllerState_t}, which nothing fills any more: once an action manifest is loaded
	 * SteamVR stops answering legacy button queries, so that struct stays zeroed and the transform
	 * it produced was meaningless. Modern Vivecraft uses this call for the same reason.
	 *
	 * <p>Returns whether every controller answered. Oculus runtimes raise no device events to say
	 * a model has become available, so a failure has to be retried rather than waited on.
	 */
	private static boolean getTipTransforms() {
		if (vrRenderModels == null) {
			return false;
		}
		boolean complete = true;
		if (renderModelNameScratch == null) {
			renderModelNameScratch = new Memory(JOpenVRLibrary.k_unMaxPropertyStringSize);
		}
		Pointer modelName = renderModelNameScratch;

		for (int i = 0; i < 2; i++) {
			if (controllerDeviceIndex[i] == JOpenVRLibrary.k_unTrackedDeviceIndexInvalid
					|| controllerDeviceIndex[i] == -1 || VR.settings().seated) {
				OpenVRUtil.Matrix4fSetIdentity(controllerTipTransform[i]);
				complete = false;
				continue;
			}
			long sourceHandle = i == RIGHT_CONTROLLER ? rightControllerHandle : leftControllerHandle;
			if (sourceHandle == JOpenVRLibrary.k_ulInvalidInputValueHandle) {
				OpenVRUtil.Matrix4fSetIdentity(controllerTipTransform[i]);
				complete = false;
				continue;
			}

			vrsystem.GetStringTrackedDeviceProperty.apply(controllerDeviceIndex[i],
					JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_RenderModelName_String,
					modelName, JOpenVRLibrary.k_unMaxPropertyStringSize - 1, hmdErrorStore);

			RenderModel_ControllerMode_State_t.ByReference modeState =
					new RenderModel_ControllerMode_State_t.ByReference();
			RenderModel_ComponentState_t.ByReference componentState =
					new RenderModel_ComponentState_t.ByReference();

			byte ok = vrRenderModels.GetComponentStateForDevicePath.apply(modelName,
					ptrFomrString("tip"), sourceHandle, modeState, componentState);
			if (ok == 0) {
				// Identity rather than whatever was there: a stale or garbage transform is worse
				// than none, because none simply puts the hand at the controller.
				OpenVRUtil.Matrix4fSetIdentity(controllerTipTransform[i]);
				OpenVRUtil.Matrix4fSetIdentity(controllerGripTransform[i]);
				complete = false;
				continue;
			}
			OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(componentState.mTrackingToComponentLocal,
					controllerTipTransform[i]);

			// The grip is not used to place anything -- the tip frame does that -- but the angle
			// between the two is what says how far a held item must be turned back.
			byte gripOk = vrRenderModels.GetComponentStateForDevicePath.apply(modelName,
					ptrFomrString("handgrip"), sourceHandle, modeState, componentState);
			if (gripOk == 0) {
				OpenVRUtil.Matrix4fSetIdentity(controllerGripTransform[i]);
			} else {
				OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(
						componentState.mTrackingToComponentLocal, controllerGripTransform[i]);
			}
		}
		updateGunAngle();
		return complete;
	}

	public static double getCurrentTimeSecs()
	{
		return System.nanoTime() / 1000000000d;
	}
	
	/** How far the controller's business end points off its own handle, in degrees. */
	private static float gunAngle;

	/**
	 * Whether the controller's business end points somewhere other than along the handle.
	 *
	 * <p>This decides whether a held item has to be tilted back onto the axis of the fist. On a
	 * wand the tip is roughly in line with the grip and no tilt is wanted; on anything shaped like
	 * a pistol grip -- Touch, Index, WMR -- the tip points well off the handle, and an item placed
	 * in the aim frame would lie along the aim ray instead of along the hand.
	 *
	 * <p>Measured from the driver's own description of where the tip is, rather than guessed from
	 * the headset's manufacturer. The older test asked whether the headset was made by HTC, which
	 * answers the question only by coincidence and gets a Vive wand on a third-party headset
	 * exactly backwards.
	 */
	public static float getGunAngle() {
		return gunAngle;
	}

	/** Kept for the one place that still wants a yes or no rather than an angle. */
	public static boolean isGunStyle() {
		return gunAngle > 10F;
	}

	/**
	 * Measures how far the controller's tip points off the axis of the fist holding it.
	 *
	 * <p>A held item has to be rotated back onto the axis of the fist, because it hangs off the
	 * tip frame and the tip is where the controller points rather than where the hand does. How
	 * far back is not a constant: it is a property of the controller's shape, and the driver
	 * describes that shape to us in the two component transforms. On a wand the two axes nearly
	 * coincide and the answer is near zero; on anything with a pistol grip it is thirty-odd
	 * degrees, and it differs between a Rift, a Quest and an Index.
	 *
	 * <p>Older Vivecraft asked instead whether the headset was made by HTC and applied a fixed
	 * 39.4 degrees if not. That is one controller's figure applied to every controller, and for a
	 * wand on a third-party headset it is exactly backwards.
	 */
	private static void updateGunAngle() {
		Vector3f forward = new Vector3f(0, 0, -1);
		// Directions, not points. Both components sit several centimetres off the controller's
		// own origin, and carrying that offset into the axis tilts it by degrees before the two
		// are even compared -- which is the whole quantity being measured.
		Vector3f tipForward = controllerTipTransform[RIGHT_CONTROLLER].transformDirection(forward);
		Vector3f gripForward = controllerGripTransform[RIGHT_CONTROLLER]
				.transformDirection(forward);
		float tipLength = tipForward.length();
		float gripLength = gripForward.length();
		if (tipLength < 1.0E-5F || gripLength < 1.0E-5F) {
			gunAngle = 0F;
			return;
		}
		Vector3f tipDir = tipForward.divide(tipLength);
		Vector3f gripDir = gripForward.divide(gripLength);
		double dot = Math.abs(tipDir.dot(gripDir));
		if (dot > 1) {
			dot = 1;
		}
		gunAngle = (float) Math.toDegrees(Math.acos(dot));
		reportGunAngle(tipDir, gripDir);
	}

	/** The last angle written to the log, so a steady controller says it once rather than always. */
	private static float reportedGunAngle = Float.NaN;

	/**
	 * Says what shape the driver thinks the controller is.
	 *
	 * <p>This one number decides how far every held thing is tilted out of the controller's own
	 * axis, and it is read from the controller's render model rather than chosen. When an item
	 * sits wrong in the hand this is the first thing worth seeing, because nothing else in the
	 * pose is a measurement -- the rest are constants.
	 */
	private static void reportGunAngle(Vector3f tip, Vector3f grip) {
		if (!Float.isNaN(reportedGunAngle) && Math.abs(gunAngle - reportedGunAngle) < 0.5F) {
			return;
		}
		reportedGunAngle = gunAngle;
		ViveCraftX.LOG.info(String.format(
				"Controller shape: gun angle %.2f deg, tip forward (%.3f, %.3f, %.3f),"
						+ " grip forward (%.3f, %.3f, %.3f)",
				gunAngle, tip.x, tip.y, tip.z, grip.x, grip.y, grip.z));
	}

	public static boolean isVive() {
		return VR.settings().forceHardwareDetection == 0 ? isVive : VR.settings().forceHardwareDetection == 1;
	}

	/**
	 * Works out how far to shift everything the trackers report, this frame.
	 *
	 * <p>Two things move the view besides the headset. A fixed view height, for playing lying
	 * down or slumped back, lifts the whole tracking space so the head sits where it would if
	 * the player were standing. Crouching lowers it. Both move the space rather than the head
	 * alone, so the hands come along and stay where the head expects them instead of ending up
	 * at floor level.
	 *
	 * <p>Crouching only counts when it came from the key. If the player physically crouched,
	 * their head has already gone down and lowering the view as well would double it.
	 */
	private static void updateViewOffset(boolean headIsTracking) {
		VRSettings settings = VR.settings();
		if (settings == null) {
			return;
		}

		boolean crouching = false;
		if (mc != null && mc.thePlayer != null && mc.thePlayer.isSneaking()) {
			crouching = VR.sneakTracker() == null || !VR.sneakTracker().sneakOverride;
		}
		float want = crouching ? SNEAK_DROP : 0f;
		sneakDrop += (want - sneakDrop) * 0.15f;
		if (Math.abs(want - sneakDrop) < 0.002f) {
			sneakDrop = want;
		}

		offset.x = offsetBase.x;
		offset.z = offsetBase.z;
		offset.y = offsetBase.y - sneakDrop;

		if (settings.vrFixedHeight && headIsTracking) {
			offset.y += AutoCalibration.getStoredPlayerHeight() - hmdPose.M[1][3];
		}
	}

	public static void resetPosition() {
		Vec3 cp = getCenterEyePosition();
		Vec3 pos = Vec3.createVectorHelper(cp.xCoord*-1 + offset.x,cp.yCoord*-1 + offset.y,cp.zCoord*-1 + offset.z);
		offsetBase=new Vector3f((float) pos.xCoord,(float)pos.yCoord+1.62f,(float)pos.zCoord);
	}

	public static void clearOffset() {
		offsetBase=new Vector3f(0,0,0);
	}

	// ---------------------------------------------------------------------------------------
	// SteamVR Input
	//
	// The mod declares what it needs -- "a button for attack", "two axes for walking" -- and
	// SteamVR decides which physical input provides it, from a profile the player can edit in its
	// own binding editor. What it replaced was a table of button numbers per controller model kept
	// inside the mod, which by construction had nothing to say about a controller that did not
	// exist when the table was written.
	//
	// The action set is declared in a manifest written out at startup rather than shipped as a
	// file, because it has to list every key binding present, and which bindings exist depends on
	// which mods are installed.
	// ---------------------------------------------------------------------------------------

	public static VR_IVRInput_FnTable vrInput;

	private static boolean inputInitialized;


	public static final String ACTION_LEFT_HAND = "/actions/global/in/lefthand";
	public static final String ACTION_RIGHT_HAND = "/actions/global/in/righthand";
	public static final String ACTION_LEFT_HAPTIC = "/actions/global/out/lefthaptic";
	public static final String ACTION_RIGHT_HAPTIC = "/actions/global/out/righthaptic";

	/** Keyed by action path, which is what the runtime knows them by. */
	private static final Map<String, VRInputAction> inputActions =
			new LinkedHashMap<String, VRInputAction>();

	/** The same actions, keyed by key binding, which is how the game asks for them. */
	private static final Map<String, VRInputAction> inputActionsByKeyBinding =
			new HashMap<String, VRInputAction>();

	private static final Map<VRInputActionSet, Long> actionSetHandles =
			new EnumMap<VRInputActionSet, Long>(VRInputActionSet.class);

	private static long leftPoseHandle;
	private static long rightPoseHandle;
	private static long leftHapticHandle;
	private static long rightHapticHandle;
	private static long leftControllerHandle;
	private static long rightControllerHandle;

	private static InputPoseActionData_t.ByReference poseData;
	private static InputOriginInfo_t.ByReference originInfo;
	private static VRActiveActionSet_t.ByReference activeActionSetsReference;

	private static HapticScheduler hapticScheduler;

	public static final TrackedController[] controllers = new TrackedController[] {
			new TrackedController(ControllerType.RIGHT),
			new TrackedController(ControllerType.LEFT) };

	/** Which bindings existed before any mod added one. See MixinGameSettings. */
	private static Set<KeyBinding> vanillaBindingSet = Collections.emptySet();

	public static void setVanillaBindings(KeyBinding[] bindings) {
		vanillaBindingSet = new HashSet<KeyBinding>(Arrays.asList(bindings));
	}

	public static boolean isModBinding(KeyBinding kb) {
		return !vanillaBindingSet.contains(kb) && !getKeyBindings().contains(kb);
	}

	/**
	 * Bindings that must be driven directly rather than as a synthetic keystroke.
	 *
	 * <p>Our own, because the mod needs them whether or not the player has given them a key, and
	 * chat and inventory, because sending those as keystrokes would open the screen and then type
	 * into it.
	 */
	public static boolean isSafeBinding(KeyBinding kb) {
		return getKeyBindings().contains(kb)
				|| kb == mc.gameSettings.keyBindChat
				|| kb == mc.gameSettings.keyBindInventory;
	}

	public static boolean isInputInitialized() {
		return inputInitialized;
	}

	/**
	 * Offers or withdraws the interact binding for one hand.
	 *
	 * <p>Withdrawn, SteamVR stops reporting the button for that hand, so a trigger that both
	 * attacks and interacts goes back to attacking when there is nothing in front of the hand.
	 */
	public static void setInteractEnabled(ControllerType hand, boolean enabled) {
		VRInputAction action = getInputAction(keyVRInteract);
		if (action != null) {
			action.setEnabled(hand, enabled);
		}
	}

	public static VRInputAction getInputActionByName(String name) {
		return inputActions.get(name);
	}

	public static VRInputAction getInputAction(String keyBindingDesc) {
		return inputActionsByKeyBinding.get(keyBindingDesc);
	}

	public static VRInputAction getInputAction(KeyBinding keyBinding) {
		return getInputAction(keyBinding.getKeyDescription());
	}

	public static Collection<VRInputAction> getInputActions() {
		return Collections.unmodifiableCollection(inputActions.values());
	}

	public static Collection<VRInputAction> getInputActionsInSet(VRInputActionSet set) {
		List<VRInputAction> list = new ArrayList<VRInputAction>();
		for (VRInputAction action : inputActions.values()) {
			if (action.actionSet == set) {
				list.add(action);
			}
		}
		return Collections.unmodifiableCollection(list);
	}

	public static long getActionSetHandle(VRInputActionSet actionSet) {
		Long handle = actionSetHandles.get(actionSet);
		return handle == null ? 0L : handle.longValue();
	}

	/**
	 * The handle for a hand, which is what restricts an action read to one controller.
	 *
	 * <p>Swapped when the player has reversed hands, so that everything downstream can keep saying
	 * "main hand" and get the hand the player actually uses.
	 */
	public static long getControllerHandle(ControllerType hand) {
		boolean reversed = VR.settings() != null && VR.settings().vrReverseHands;
		boolean right = hand == ControllerType.RIGHT;
		if (reversed) {
			right = !right;
		}
		return right ? rightControllerHandle : leftControllerHandle;
	}

	/**
	 * The handle for a hand's haptics.
	 *
	 * <p>Swapped with the hands, like every other per-hand handle: the point of naming a hand is
	 * to reach the hand the player is using, and buzzing the wrong one is worse than not buzzing.
	 */
	public static long getHapticHandle(ControllerType hand) {
		boolean reversed = VR.settings() != null && VR.settings().vrReverseHands;
		boolean right = hand == ControllerType.RIGHT;
		if (reversed) {
			right = !right;
		}
		return right ? rightHapticHandle : leftHapticHandle;
	}

	public static boolean isControllerTracking(int controller) {
		return controller >= 0 && controller < controllerTracking.length
				&& controllerTracking[controller];
	}

	public static boolean isControllerTracking(ControllerType controller) {
		return isControllerTracking(controller.ordinal());
	}

	/** Haptics through the action system, which is what lets the player rebind which hand buzzes. */
	public static void triggerHapticPulse(ControllerType controller, float durationSeconds,
			float frequency, float amplitude) {
		if (hapticScheduler == null) {
			return;
		}
		hapticScheduler.queueHapticPulse(controller, durationSeconds, frequency, amplitude, 0);
	}

	/**
	 * The older form, in microseconds and with no frequency to give: what the trackers that
	 * predate the action system still call.
	 *
	 * <p>The frequency matters more than it looks. These pulses last between a quarter and three
	 * milliseconds, so asking for one cycle per second means the motor is told to begin a
	 * vibration whose first half-cycle is five hundred milliseconds long and then stopped --
	 * which is nothing at all. A hundred and sixty hertz is what upstream passes here, and at
	 * that rate even the shortest of these is half a cycle of real movement. Everything still on
	 * this form was silently buzzing nothing: landing a hit, breaking a block, the whole bow
	 * draw and release, grabbing a ladder, eating, the backpack, the touch hotbar.
	 */
	public static void triggerHapticPulse(ControllerType controller, int durationMicroseconds) {
		triggerHapticPulse(controller, durationMicroseconds / 1000000F, 160F, 1F);
	}

	public static HapticScheduler getHapticScheduler() {
		return hapticScheduler;
	}

	/** Whether the player has this binding set to anything on a controller they are holding. */
	public static boolean isBoundInActiveActionSets(KeyBinding binding) {
		if (!inputInitialized) {
			return false;
		}
		return !getInputAction(binding).getOrigins().isEmpty();
	}

	public static ControllerType findActiveBindingControllerType(KeyBinding binding) {
		if (!inputInitialized) {
			return null;
		}
		long origin = getInputAction(binding).getLastOrigin();
		if (origin != JOpenVRLibrary.k_ulInvalidInputValueHandle) {
			return getOriginControllerType(origin);
		}
		return null;
	}

	public static TrackedController findActiveBindingController(KeyBinding binding) {
		ControllerType type = findActiveBindingControllerType(binding);
		return type != null ? type.getController() : null;
	}

	/**
	 * Buzzes whichever hand actually triggered a binding.
	 *
	 * <p>The point of asking rather than assuming: the player may have put the binding on either
	 * hand, and feedback on the wrong one is worse than none.
	 */
	public static void triggerBindingHapticPulse(KeyBinding binding, float durationSeconds,
			float frequency, float amplitude) {
		TrackedController controller = findActiveBindingController(binding);
		if (controller != null) {
			controller.triggerHapticPulse(durationSeconds, frequency, amplitude);
		}
	}

	public static void triggerBindingHapticPulse(KeyBinding binding, int durationMicroseconds) {
		triggerBindingHapticPulse(binding, durationMicroseconds / 1000000F, 160F, 1F);
	}

	// --- origins: which physical input drove an action, and therefore which hand ---

	private static void readOriginInfo(long inputValueHandle) {
		int error = vrInput.GetOriginTrackedDeviceInfo.apply(inputValueHandle, originInfo,
				originInfo.size());
		if (error != 0) {
			throw new RuntimeException("Error reading origin info: " + getInputError(error));
		}
		originInfo.read();
	}

	public static InputOriginInfo_t getOriginInfo(long inputValueHandle) {
		InputOriginInfo_t copy = new InputOriginInfo_t();
		readOriginInfo(inputValueHandle);
		copy.devicePath = originInfo.devicePath;
		copy.trackedDeviceIndex = originInfo.trackedDeviceIndex;
		copy.rchRenderModelComponentName = originInfo.rchRenderModelComponentName;
		return copy;
	}

	/** What SteamVR calls this input in the player's own language, for the controls screen. */
	public static String getOriginName(long handle) {
		Pointer p = new Memory(JOpenVRLibrary.k_unMaxPropertyStringSize + 1);
		int error = vrInput.GetOriginLocalizedName.apply(handle, p,
				JOpenVRLibrary.k_unMaxPropertyStringSize,
				JOpenVRLibrary.EVRInputStringBits.EVRInputStringBits_VRInputString_All);
		if (error != 0) {
			throw new RuntimeException("Error getting origin name: " + getInputError(error));
		}
		return p.getString(0);
	}

	public static ControllerType getOriginControllerType(long inputValueHandle) {
		if (inputValueHandle == JOpenVRLibrary.k_ulInvalidInputValueHandle || originInfo == null) {
			return null;
		}
		readOriginInfo(inputValueHandle);
		if (originInfo.trackedDeviceIndex != JOpenVRLibrary.k_unTrackedDeviceIndexInvalid) {
			if (originInfo.trackedDeviceIndex == controllerDeviceIndex[RIGHT_CONTROLLER]) {
				return ControllerType.RIGHT;
			}
			if (originInfo.trackedDeviceIndex == controllerDeviceIndex[LEFT_CONTROLLER]) {
				return ControllerType.LEFT;
			}
		}
		return null;
	}

	public static String getInputError(int code) {
		switch (code) {
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_None:
				return "None";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_NameNotFound:
				return "NameNotFound";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_WrongType:
				return "WrongType";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_InvalidHandle:
				return "InvalidHandle";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_InvalidParam:
				return "InvalidParam";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_NoSteam:
				return "NoSteam";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_MaxCapacityReached:
				return "MaxCapacityReached";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_IPCError:
				return "IPCError";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_NoActiveActionSet:
				return "NoActiveActionSet";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_InvalidDevice:
				return "InvalidDevice";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_InvalidSkeleton:
				return "InvalidSkeleton";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_InvalidBoneCount:
				return "InvalidBoneCount";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_InvalidCompressedData:
				return "InvalidCompressedData";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_NoData:
				return "NoData";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_BufferTooSmall:
				return "BufferTooSmall";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_MismatchedActionManifest:
				return "MismatchedActionManifest";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_MissingSkeletonData:
				return "MissingSkeletonData";
			case JOpenVRLibrary.EVRInputError.EVRInputError_VRInputError_InvalidBoneIndex:
				return "InvalidBoneIndex";
			default:
				return "Unknown (" + code + ")";
		}
	}

	// --- bringing the action system up ---

	public static void initOpenVRInput() throws Exception {
		vrInput = new VR_IVRInput_FnTable(JOpenVRLibrary.VR_GetGenericInterface(
				JOpenVRLibrary.IVRInput_Version, hmdErrorStoreBuf));
		if (vrInput == null || isError()) {
			vrInput = null;
			throw new Exception("VR Input not available: " + getInitStatus());
		}
		vrInput.setAutoSynch(false);
		vrInput.read();

		poseData = new InputPoseActionData_t.ByReference();
		poseData.setAutoRead(false);
		poseData.setAutoWrite(false);
		poseData.setAutoSynch(false);

		originInfo = new InputOriginInfo_t.ByReference();
		originInfo.setAutoRead(false);
		originInfo.setAutoWrite(false);
		originInfo.setAutoSynch(false);

		hapticScheduler = new HapticScheduler();
		ViveCraftX.LOG.info("VR Input initialised");
	}

	/**
	 * Declares every key binding as an action and tells the runtime about them.
	 *
	 * <p>Every binding, not only ours: a player should be able to put another mod's key on a
	 * controller button, and the only way SteamVR can offer that is if it knows the binding exists.
	 */
	public static void initInputAndApplication() {
		populateInputActions();
		if (vrInput == null) {
			return;
		}
		generateActionManifest();
		loadActionManifest();
		loadActionHandles();
		installApplicationManifest();
		inputInitialized = true;
		ViveCraftX.LOG.info("Declared {} actions to SteamVR across {} sets", inputActions.size(),
				VRInputActionSet.values().length);
	}

	private static void populateInputActions() {
		Map<String, ActionParams> actionParams = getSpecialActionParams();
		for (KeyBinding keyBinding : mc.gameSettings.keyBindings) {
			ActionParams params = actionParams.get(keyBinding.getKeyDescription());
			if (params == null) {
				params = new ActionParams("optional", "boolean", null);
			}
			VRInputAction action = new VRInputAction(keyBinding, params.requirement, params.type,
					params.actionSetOverride);
			inputActions.put(action.name, action);
		}
		for (VRInputAction action : inputActions.values()) {
			inputActionsByKeyBinding.put(action.keyBinding.getKeyDescription(), action);
		}
	}

	/**
	 * The bindings that are not a plain button.
	 *
	 * <p>Anything absent is declared as an optional button, which is right for the great majority.
	 * The ones here either need more than on and off -- walking is an axis, so a thumbstick works
	 * and is not reduced to four directions -- or need to keep working while a menu is open, which
	 * is what the global set is for.
	 */
	private static Map<String, ActionParams> getSpecialActionParams() {
		Map<String, ActionParams> map = new HashMap<String, ActionParams>();

		addActionParams(map, mc.gameSettings.keyBindForward, "optional", "vector1", null);
		addActionParams(map, mc.gameSettings.keyBindBack, "optional", "vector1", null);
		addActionParams(map, mc.gameSettings.keyBindLeft, "optional", "vector1", null);
		addActionParams(map, mc.gameSettings.keyBindRight, "optional", "vector1", null);
		addActionParams(map, mc.gameSettings.keyBindAttack, "suggested", "boolean", null);
		addActionParams(map, mc.gameSettings.keyBindUseItem, "suggested", "boolean", null);
		// inventory and chat have to answer while a screen is open, or there is no way back out
		addActionParams(map, mc.gameSettings.keyBindInventory, "suggested", "boolean",
				VRInputActionSet.GLOBAL);
		addActionParams(map, mc.gameSettings.keyBindChat, "optional", "boolean",
				VRInputActionSet.GLOBAL);

		addActionParams(map, keyTrackpadTouch, "optional", "boolean", VRInputActionSet.TECHNICAL);

		addActionParams(map, keyHotbarScroll, "optional", "vector2", null);
		addActionParams(map, keyHotbarSwipeX, "optional", "vector2", null);
		addActionParams(map, keyHotbarSwipeY, "optional", "vector2", null);
		addActionParams(map, keyRotateLeft, "optional", "vector1", null);
		addActionParams(map, keyRotateRight, "optional", "vector1", null);
		addActionParams(map, keyRotateAxis, "optional", "vector2", null);
		addActionParams(map, keyFreeMoveRotate, "optional", "vector2", null);
		addActionParams(map, keyFreeMoveStrafe, "optional", "vector2", null);
		addActionParams(map, keyTeleportFallback, "suggested", "vector1", null);
		// the menu button has to answer from inside a screen as well as outside it
		addActionParams(map, keyMenuButton, "suggested", "boolean", VRInputActionSet.GLOBAL);
		addActionParams(map, keyToggleKeyboard, "optional", "boolean", VRInputActionSet.GLOBAL);
		addActionParams(map, keyRadialMenu, "suggested", "boolean", null);
		// In the contextual set so it can be switched off per hand while that hand has nothing
		// to press, which is the whole point of it.
		addActionParams(map, keyVRInteract, "suggested", "boolean",
				com.mtbs3d.minecrift.control.VRInputActionSet.CONTEXTUAL);
		addActionParams(map, keyClimbeyGrab, "suggested", "boolean", null);
		addActionParams(map, keyClimbeyJump, "suggested", "boolean", null);
		addActionParams(map, keyKeyboardMove, "suggested", "boolean", null);
		addActionParams(map, keyGuiLeftClick, "suggested", "boolean", null);
		addActionParams(map, keyGuiRightClick, "suggested", "boolean", null);
		addActionParams(map, keyGuiMiddleClick, "optional", "boolean", null);
		addActionParams(map, keyGuiShift, "suggested", "boolean", null);
		addActionParams(map, keyGuiScrollAxis, "optional", "vector2", null);

		return map;
	}

	private static void addActionParams(Map<String, ActionParams> map, KeyBinding keyBinding,
			String requirement, String type, VRInputActionSet actionSetOverride) {
		if (keyBinding == null) {
			return;
		}
		map.put(keyBinding.getKeyDescription(),
				new ActionParams(requirement, type, actionSetOverride));
	}

	/**
	 * Writes the action manifest, which is what SteamVR reads to populate its binding editor.
	 *
	 * <p>Generated rather than shipped because it has to name every binding present, and that
	 * depends on which mods are installed. The per-controller default profiles beside it are
	 * shipped, since those are about hardware rather than about this installation.
	 */
	private static void generateActionManifest() {
		Map<String, Object> jsonMap = new LinkedHashMap<String, Object>();

		List<Map<String, Object>> actionSets = new ArrayList<Map<String, Object>>();
		for (VRInputActionSet actionSet : VRInputActionSet.values()) {
			String usage = actionSet.usage;
			if (actionSet.advanced
					&& (VR.settings() == null || !VR.settings().allowAdvancedBindings)) {
				usage = "hidden";
			}
			Map<String, Object> entry = new LinkedHashMap<String, Object>();
			entry.put("name", actionSet.name);
			entry.put("usage", usage);
			actionSets.add(entry);
		}
		jsonMap.put("action_sets", actionSets);

		// sorted, because the player has to find things in this list in SteamVR
		List<VRInputAction> sorted = new ArrayList<VRInputAction>(inputActions.values());
		Collections.sort(sorted, new java.util.Comparator<VRInputAction>() {
			@Override
			public int compare(VRInputAction a, VRInputAction b) {
				return a.keyBinding.compareTo(b.keyBinding);
			}
		});

		List<Map<String, Object>> actions = new ArrayList<Map<String, Object>>();
		for (VRInputAction action : sorted) {
			Map<String, Object> entry = new LinkedHashMap<String, Object>();
			entry.put("name", action.name);
			entry.put("requirement", action.requirement);
			entry.put("type", action.type);
			actions.add(entry);
		}
		// the hand poses and the haptic outputs are not key bindings, so they are declared here
		actions.add(action(ACTION_LEFT_HAND, "suggested", "pose"));
		actions.add(action(ACTION_RIGHT_HAND, "suggested", "pose"));
		actions.add(action(ACTION_LEFT_HAPTIC, "suggested", "vibration"));
		actions.add(action(ACTION_RIGHT_HAPTIC, "suggested", "vibration"));
		jsonMap.put("actions", actions);

		Map<String, Object> localization = new LinkedHashMap<String, Object>();
		localization.put("language_tag", "en_US");
		for (VRInputAction action : sorted) {
			localization.put(action.name, I18n.format(action.keyBinding.getKeyCategory())
					+ " - " + I18n.format(action.keyBinding.getKeyDescription()));
		}
		for (VRInputActionSet actionSet : VRInputActionSet.values()) {
			localization.put(actionSet.name, I18n.format(actionSet.localizedName));
		}
		localization.put(ACTION_LEFT_HAND, "Left Hand Pose");
		localization.put(ACTION_RIGHT_HAND, "Right Hand Pose");
		localization.put(ACTION_LEFT_HAPTIC, "Left Hand Haptic");
		localization.put(ACTION_RIGHT_HAPTIC, "Right Hand Haptic");
		List<Map<String, Object>> localizationList = new ArrayList<Map<String, Object>>();
		localizationList.add(localization);
		jsonMap.put("localization", localizationList);

		List<Map<String, Object>> defaults = new ArrayList<Map<String, Object>>();
		defaults.add(binding("vive_controller", "vive_defaults.json"));
		defaults.add(binding("oculus_touch", "oculus_defaults.json"));
		defaults.add(binding("holographic_controller", "wmr_defaults.json"));
		defaults.add(binding("knuckles", "knuckles_defaults.json"));
		defaults.add(binding("vive_cosmos_controller", "cosmos_defaults.json"));
		defaults.add(binding("vive_tracker_camera", "tracker_defaults.json"));
		jsonMap.put("default_bindings", defaults);

		try {
			new File("openvr/input").mkdirs();
			OutputStreamWriter writer = new OutputStreamWriter(
					new FileOutputStream("openvr/input/action_manifest.json"),
					Charset.forName("UTF-8"));
			try {
				new GsonBuilder().setPrettyPrinting().create().toJson(jsonMap, writer);
			} finally {
				writer.close();
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to write action manifest", e);
		}

		// The reversed profiles swap the hands at the source -- but only the buttons and the
		// sticks. Their pose and haptic bindings are identical to the ordinary ones, each tied to
		// its own physical hand, so those two have to be swapped in code: see updateAim for the
		// poses and getHapticHandle for the buzz. The device a handed button read is restricted to
		// is swapped in code as well, in getControllerHandle, because it has to name the same
		// physical controller the file has just bound that action to.
		String rev = VR.settings() != null && VR.settings().vrReverseHands ? "_reversed" : "";
		copyAsset("input/vive_defaults" + rev + ".json", "openvr/input/vive_defaults.json");
		copyAsset("input/oculus_defaults" + rev + ".json", "openvr/input/oculus_defaults.json");
		copyAsset("input/wmr_defaults" + rev + ".json", "openvr/input/wmr_defaults.json");
		copyAsset("input/knuckles_defaults" + rev + ".json", "openvr/input/knuckles_defaults.json");
		copyAsset("input/cosmos_defaults" + rev + ".json", "openvr/input/cosmos_defaults.json");
		copyAsset("input/tracker_defaults.json", "openvr/input/tracker_defaults.json");
	}

	/**
	 * The name and picture SteamVR shows for this session.
	 *
	 * <p>Without it the runtime has nothing to call us by, so its status window, its notifications
	 * and its binding editor all say {@code javaw.exe} -- which is accurate and useless, and is
	 * the same for every Java application the player runs. Telling it otherwise takes an
	 * application manifest: a small file naming the application, and the process id to attach that
	 * name to.
	 *
	 * <p>The manifest is written out rather than shipped ready-made, for one reason: the picture.
	 * SteamVR is given a path to it, and the only path that is certainly right is the absolute one
	 * of the copy we have just unpacked, which is not knowable until the game is running. Upstream
	 * sidesteps this by pointing at an image on its website; a file in the jar does not go stale
	 * when a web host changes, and works offline.
	 *
	 * <p>{@code openvr/custom.vrmanifest}, if the player makes one, is used untouched instead.
	 *
	 * <p>Registered as temporary, so it lasts as long as the session: this is not an application
	 * SteamVR can launch -- it is a name for one that is already running -- and leaving a permanent
	 * entry behind would put an unlaunchable item in the player's VR library for ever.
	 */
	private static void installApplicationManifest() {
		if (vrApplications == null) {
			return;
		}
		File dir = new File("openvr");
		dir.mkdirs();

		File manifest = new File(dir, "custom.vrmanifest");
		if (!manifest.exists()) {
			manifest = new File(dir, "vivecraftx.vrmanifest");
			copyAsset("vivecraftx.png", "openvr/vivecraftx.png");
			if (!writeApplicationManifest(manifest,
					new File(dir, "vivecraftx.png").getAbsolutePath())) {
				return;
			}
		}

		String appKey = readAppKey(manifest);
		if (appKey == null) {
			return;
		}

		// SteamVR cannot cope with a path outside ASCII, and the failure it gives for one says
		// nothing about why. A player whose Windows account name has a letter with an accent in it
		// would otherwise be left guessing.
		String path = manifest.getAbsolutePath();
		for (int i = 0; i < path.length(); i++) {
			if (path.charAt(i) > 127) {
				ViveCraftX.LOG.warn("The game directory path contains characters SteamVR cannot "
						+ "read ({}), so the application name cannot be registered", path);
				return;
			}
		}

		if (vrApplications.IsApplicationInstalled.apply(ptrFomrString(appKey)) != 0) {
			ViveCraftX.LOG.info("Application manifest already known to SteamVR");
		} else {
			int error = vrApplications.AddApplicationManifest.apply(ptrFomrString(path), (byte) 1);
			if (error != 0) {
				ViveCraftX.LOG.warn("SteamVR would not take the application manifest: {}",
						getApplicationsError(error));
				return;
			}
		}

		// OpenVR says a pid of zero means the calling process. It does not; it has to be told.
		// Java 8 has no way to ask for its own pid, so it comes out of the runtime bean's name,
		// which is "pid@host" on every JVM this will ever run on.
		int pid;
		try {
			pid = Integer.parseInt(java.lang.management.ManagementFactory.getRuntimeMXBean()
					.getName().split("@")[0]);
		} catch (Exception e) {
			ViveCraftX.LOG.warn("Could not work out this process's id, so SteamVR will keep "
					+ "calling us by the name of the Java executable", e);
			return;
		}

		int error = vrApplications.IdentifyApplication.apply(pid, ptrFomrString(appKey));
		if (error != 0) {
			ViveCraftX.LOG.warn("SteamVR would not attach the name to this process: {}",
					getApplicationsError(error));
		} else {
			ViveCraftX.LOG.info("SteamVR now knows this session as VivecraftX");
		}
	}

	private static boolean writeApplicationManifest(File target, String imagePath) {
		Map<String, Object> app = new LinkedHashMap<String, Object>();
		app.put("app_key", APPLICATION_KEY);
		app.put("image_path", imagePath);
		// There is no binary to launch: SteamVR only ever sees this application because it is
		// already running, so the launch fields are present to satisfy the format and go nowhere.
		app.put("launch_type", "url");
		app.put("url", "steam://launch/");

		Map<String, Object> english = new LinkedHashMap<String, Object>();
		english.put("name", "VivecraftX");
		english.put("description", "VR for Minecraft 1.7.10");
		Map<String, Object> russian = new LinkedHashMap<String, Object>();
		russian.put("name", "VivecraftX");
		russian.put("description", I18n.format("vivecraft.app.description"));
		Map<String, Object> strings = new LinkedHashMap<String, Object>();
		strings.put("en_us", english);
		strings.put("ru_ru", russian);
		app.put("strings", strings);

		List<Map<String, Object>> applications = new ArrayList<Map<String, Object>>();
		applications.add(app);
		Map<String, Object> root = new LinkedHashMap<String, Object>();
		root.put("source", "builtin");
		root.put("applications", applications);

		try {
			OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(target),
					Charset.forName("UTF-8"));
			try {
				new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
			} finally {
				writer.close();
			}
			return true;
		} catch (IOException e) {
			ViveCraftX.LOG.warn("Could not write the application manifest", e);
			return false;
		}
	}

	/**
	 * Read back out of the file rather than assumed, so a hand-written custom manifest brings its
	 * own key with it -- registering one key and then claiming another would name nothing.
	 */
	@SuppressWarnings("rawtypes")
	private static String readAppKey(File manifest) {
		try {
			java.io.Reader reader = new java.io.InputStreamReader(new java.io.FileInputStream(
					manifest), Charset.forName("UTF-8"));
			try {
				Map map = new GsonBuilder().create().fromJson(reader, Map.class);
				return ((Map) ((List) map.get("applications")).get(0)).get("app_key").toString();
			} finally {
				reader.close();
			}
		} catch (Exception e) {
			ViveCraftX.LOG.warn("Could not read the application key out of {}", manifest, e);
			return null;
		}
	}

	private static String getApplicationsError(int code) {
		try {
			return vrApplications.GetApplicationsErrorNameFromEnum.apply(code).getString(0);
		} catch (Throwable t) {
			return "error " + code;
		}
	}

	private static Map<String, Object> action(String name, String requirement, String type) {
		Map<String, Object> entry = new LinkedHashMap<String, Object>();
		entry.put("name", name);
		entry.put("requirement", requirement);
		entry.put("type", type);
		return entry;
	}

	private static Map<String, Object> binding(String controllerType, String url) {
		Map<String, Object> entry = new LinkedHashMap<String, Object>();
		entry.put("controller_type", controllerType);
		entry.put("binding_url", url);
		return entry;
	}

	/** Unpacks one of the shipped binding profiles next to the manifest, where SteamVR looks. */
	private static void copyAsset(String name, String target) {
		InputStream in = MCOpenVR.class.getResourceAsStream("/assets/vivecraftx/" + name);
		if (in == null) {
			ViveCraftX.LOG.warn("Missing bundled binding profile {}; SteamVR will have no "
					+ "defaults for that controller", name);
			return;
		}
		try {
			try {
				OutputStream out = new FileOutputStream(target);
				try {
					byte[] buffer = new byte[8192];
					int read;
					while ((read = in.read(buffer)) > 0) {
						out.write(buffer, 0, read);
					}
				} finally {
					out.close();
				}
			} finally {
				in.close();
			}
		} catch (IOException e) {
			ViveCraftX.LOG.warn("Could not write binding profile {}", target, e);
		}
	}

	private static void loadActionManifest() {
		int error = vrInput.SetActionManifestPath.apply(ptrFomrString(
				new File("openvr/input/action_manifest.json").getAbsolutePath()));
		if (error != 0) {
			throw new RuntimeException("Failed to load action manifest: " + getInputError(error));
		}
	}

	private static void loadActionHandles() {
		LongByReference longRef = new LongByReference();

		for (VRInputAction action : inputActions.values()) {
			int error = vrInput.GetActionHandle.apply(ptrFomrString(action.name), longRef);
			if (error != 0) {
				throw new RuntimeException("Error getting action handle for '" + action.name
						+ "': " + getInputError(error));
			}
			action.setHandle(longRef.getValue());
		}

		leftPoseHandle = getActionHandle(ACTION_LEFT_HAND);
		rightPoseHandle = getActionHandle(ACTION_RIGHT_HAND);
		leftHapticHandle = getActionHandle(ACTION_LEFT_HAPTIC);
		rightHapticHandle = getActionHandle(ACTION_RIGHT_HAPTIC);

		for (VRInputActionSet actionSet : VRInputActionSet.values()) {
			int error = vrInput.GetActionSetHandle.apply(ptrFomrString(actionSet.name), longRef);
			if (error != 0) {
				throw new RuntimeException("Error getting action set handle for '" + actionSet.name
						+ "': " + getInputError(error));
			}
			actionSetHandles.put(actionSet, Long.valueOf(longRef.getValue()));
		}

		leftControllerHandle = getInputSourceHandle("/user/hand/left");
		rightControllerHandle = getInputSourceHandle("/user/hand/right");
	}

	private static long getActionHandle(String name) {
		LongByReference longRef = new LongByReference();
		int error = vrInput.GetActionHandle.apply(ptrFomrString(name), longRef);
		if (error != 0) {
			throw new RuntimeException("Error getting action handle for '" + name + "': "
					+ getInputError(error));
		}
		return longRef.getValue();
	}

	public static long getInputSourceHandle(String path) {
		LongByReference longRef = new LongByReference();
		int error = vrInput.GetInputSourceHandle.apply(ptrFomrString(path), longRef);
		if (error != 0) {
			throw new RuntimeException("Error getting input source handle for '" + path + "': "
					+ getInputError(error));
		}
		return longRef.getValue();
	}

	/**
	 * Which sets are live this frame.
	 *
	 * <p>This is what lets one button mean two things: in a menu the GUI set is active and the
	 * in-game set is not, so the same trigger clicks instead of swinging. The global set is always
	 * active, which is how the button that opens the menu can also close it.
	 */
	private static VRActiveActionSet_t[] getActiveActionSets() {
		List<VRInputActionSet> list = new ArrayList<VRInputActionSet>();
		list.add(VRInputActionSet.GLOBAL);
		list.add(VRInputActionSet.MOD);
		list.add(VRInputActionSet.MIXED_REALITY);
		list.add(VRInputActionSet.TECHNICAL);

		if (mc.currentScreen == null) {
			list.add(VRInputActionSet.INGAME);
			for (VRInputAction action : getInputActionsInSet(VRInputActionSet.CONTEXTUAL)) {
				if (action.isEnabledRaw()) {
					list.add(VRInputActionSet.CONTEXTUAL);
					break;
				}
			}
		} else {
			list.add(VRInputActionSet.GUI);
		}
		// Either kind of keyboard. This read the overlay's flag alone, which meant the set was
		// never active for the board standing in the world -- and the board is the default, so
		// the actions that only make sense while a keyboard is up could never be reported at all.
		if (keyboardShowing || lol.gzmc.vivecraftx.render.VRKeyboard.isShowing()) {
			list.add(VRInputActionSet.KEYBOARD);
		}

		activeActionSetsReference = new VRActiveActionSet_t.ByReference();
		activeActionSetsReference.setAutoRead(false);
		activeActionSetsReference.setAutoWrite(false);
		activeActionSetsReference.setAutoSynch(false);

		VRActiveActionSet_t[] sets =
				(VRActiveActionSet_t[]) activeActionSetsReference.toArray(list.size());
		for (int i = 0; i < list.size(); i++) {
			sets[i].ulActionSet = getActionSetHandle(list.get(i));
			sets[i].ulRestrictedToDevice = JOpenVRLibrary.k_ulInvalidInputValueHandle;
			sets[i].nPriority = 0;
			sets[i].setAutoRead(false);
			sets[i].setAutoWrite(false);
			sets[i].setAutoSynch(false);
			sets[i].write();
		}
		activeActionSetsReference.write();
		return sets;
	}

	/**
	 * Hands this frame's active sets to the runtime and pulls every action's state back.
	 *
	 * <p>Must happen before anything reads an action, and exactly once a frame: the runtime reports
	 * changes since the last call, so calling it twice would report the second half of a press as
	 * no change at all.
	 */
	/**
	 * Reads one hand's pose from its pose action.
	 *
	 * <p>Asking for the pose "for next frame" rather than now is deliberate: the runtime
	 * extrapolates to when the frame will actually be on screen, which is what keeps a hand from
	 * lagging behind the controller.
	 */
	private static void updateControllerPose(int controller, long actionHandle) {
		if (vrInput == null || poseData == null) {
			controllerTracking[controller] = false;
			return;
		}
		int error = vrInput.GetPoseActionDataForNextFrame.apply(actionHandle,
				JOpenVRLibrary.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseStanding,
				poseData, poseData.size(), JOpenVRLibrary.k_ulInvalidInputValueHandle);
		if (error != 0) {
			ViveCraftX.LOG.debug("Could not read the pose for controller {}: {}", controller,
					getInputError(error));
			controllerTracking[controller] = false;
			return;
		}
		poseData.read();

		if (poseData.activeOrigin == JOpenVRLibrary.k_ulInvalidInputValueHandle) {
			// nothing is bound to this hand's pose, so there is no hand there
			controllerDeviceIndex[controller] = JOpenVRLibrary.k_unTrackedDeviceIndexInvalid;
			controllerTracking[controller] = false;
			return;
		}

		readOriginInfo(poseData.activeOrigin);
		int deviceIndex = originInfo.trackedDeviceIndex;
		if (deviceIndex != controllerDeviceIndex[controller]) {
			// a different physical device is in this hand now, so anything cached off its model
			// has to be worked out again
			tipTransformsStale = true;
		}
		controllerDeviceIndex[controller] = deviceIndex;

		if (deviceIndex != JOpenVRLibrary.k_unTrackedDeviceIndexInvalid
				&& poseData.pose.bPoseIsValid != 0) {
			jopenvr.OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(
					poseData.pose.mDeviceToAbsoluteTracking, poseMatrices[deviceIndex]);
			OpenVRUtil.Matrix4fCopy(poseMatrices[deviceIndex], controllerPose[controller]);
			controllerTracking[controller] = true;
			return;
		}
		controllerTracking[controller] = false;
	}

	public static void pollActions() {
		if (!inputInitialized || vrInput == null) {
			return;
		}
		VRActiveActionSet_t[] sets = getActiveActionSets();
		int error = vrInput.UpdateActionState.apply(activeActionSetsReference, sets[0].size(),
				sets.length);
		if (error != 0) {
			throw new RuntimeException("Error updating action state: " + getInputError(error));
		}
		for (VRInputAction action : inputActions.values()) {
			action.readNewData();
		}
	}
	// ---------------------------------------------------------------------------------------
	// Turning actions into key presses, and acting on the ones the mod handles itself
	// ---------------------------------------------------------------------------------------

	/** Set when a press should not carry into the next frame. See {@link #processInputAction}. */
	private static boolean ignorePressesNextFrame;

	private static final Map<String, TrackpadSwipeSampler> trackpadSwipeSamplers =
			new HashMap<String, TrackpadSwipeSampler>();

	public static void ignorePressesNextFrame() {
		ignorePressesNextFrame = true;
	}

	/**
	 * Turns one action's state into presses and releases of its key binding.
	 *
	 * <p>Only on a change, so holding a button does not re-press it every frame, and the release is
	 * unconditional when the action goes inactive: an action can stop being readable while held --
	 * a controller going to sleep, a menu opening -- and without this the key would stay down
	 * forever.
	 */
	private static void processInputAction(VRInputAction action) {
		if (!action.isActive() || !action.isEnabledRaw()) {
			action.unpressBinding();
			return;
		}
		if (!action.isButtonChanged()) {
			return;
		}
		if (action.isButtonPressed() && action.isEnabled()) {
			// Closing a menu by clicking a button should not then click in the world, so the frame
			// a menu closes on swallows its presses.
			if (!ignorePressesNextFrame) {
				action.pressBinding();
			}
		} else {
			action.unpressBinding();
		}
    }

	/** Every action, once a frame, after {@link #pollActions} has refreshed them. */
	public static void processInputs() {
		if (!inputInitialized || VR.settings() == null || VR.settings().seated) {
			return;
		}

		for (VRInputAction action : inputActions.values()) {
			if (action.isHanded()) {
				for (ControllerType hand : ControllerType.values()) {
					action.setCurrentHand(hand);
					processInputAction(action);
				}
			} else {
				processInputAction(action);
			}
		}

		// Scrolling a menu: the axis is turned into wheel events rather than key presses, because
		// that is the only thing scrollable lists listen to.
		processScrollInput(keyGuiScrollAxis, new Runnable() {
			@Override
			public void run() {
				scrollWheel(-120);
			}
		}, new Runnable() {
			@Override
			public void run() {
				scrollWheel(120);
			}
		});

		processScrollInput(keyHotbarScroll, new Runnable() {
			@Override
			public void run() {
				changeHotbar(-1);
			}
		}, new Runnable() {
			@Override
			public void run() {
				changeHotbar(1);
			}
		});

		processSwipeInput(keyHotbarSwipeX, new Runnable() {
			@Override
			public void run() {
				changeHotbar(1);
			}
		}, new Runnable() {
			@Override
			public void run() {
				changeHotbar(-1);
			}
		}, null, null);

		processSwipeInput(keyHotbarSwipeY, null, null, new Runnable() {
			@Override
			public void run() {
				changeHotbar(-1);
			}
		}, new Runnable() {
			@Override
			public void run() {
				changeHotbar(1);
			}
		});

		ignorePressesNextFrame = false;
	}

	private static void scrollWheel(int amount) {
		if (Display.isActive()) {
			KeyboardSimulator.robot.mouseWheel(amount);
		} else if (InputInjector.isSupported()) {
			InputInjector.mouseWheel(amount);
		}
	}

	/**
	 * An axis read as a scroll wheel: each frame the axis moved, one notch in that direction.
	 *
	 * <p>The delta is what is tested rather than the value, so resting a thumb on a stick does not
	 * scroll forever.
	 */
	private static void processScrollInput(KeyBinding keyBinding, Runnable upCallback,
			Runnable downCallback) {
		VRInputAction action = getInputAction(keyBinding);
		if (action == null || !action.isEnabled()
				|| action.getLastOrigin() == JOpenVRLibrary.k_ulInvalidInputValueHandle
				|| action.getAxis2D(true).getY() == 0) {
			return;
		}
		float value = action.getAxis2D(false).getY();
		if (value > 0) {
			upCallback.run();
		} else if (value < 0) {
			downCallback.run();
		}
	}

	/**
	 * A trackpad read as swipes, with a buzz on each one.
	 *
	 * <p>The buzz matters more than it sounds: a swipe has no travel limit to feel, so without it
	 * there is nothing to tell you the swipe registered except the thing on screen changing.
	 */
	private static void processSwipeInput(KeyBinding keyBinding, Runnable leftCallback,
			Runnable rightCallback, Runnable upCallback, Runnable downCallback) {
		VRInputAction action = getInputAction(keyBinding);
		if (action == null || !action.isEnabled()
				|| action.getLastOrigin() == JOpenVRLibrary.k_ulInvalidInputValueHandle) {
			return;
		}
		ControllerType controller = findActiveBindingControllerType(keyBinding);
		if (controller == null) {
			return;
		}
		TrackpadSwipeSampler sampler = trackpadSwipeSamplers.get(keyBinding.getKeyDescription());
		if (sampler == null) {
			sampler = new TrackpadSwipeSampler();
			trackpadSwipeSamplers.put(keyBinding.getKeyDescription(), sampler);
		}
		sampler.update(controller, action.getAxis2D(false));

		if (sampler.isSwipedUp() && upCallback != null) {
			triggerHapticPulse(controller, 0.001F, 400, 0.5F);
			upCallback.run();
		}
		if (sampler.isSwipedDown() && downCallback != null) {
			triggerHapticPulse(controller, 0.001F, 400, 0.5F);
			downCallback.run();
		}
		if (sampler.isSwipedLeft() && leftCallback != null) {
			triggerHapticPulse(controller, 0.001F, 400, 0.5F);
			leftCallback.run();
		}
		if (sampler.isSwipedRight() && rightCallback != null) {
			triggerHapticPulse(controller, 0.001F, 400, 0.5F);
			rightCallback.run();
		}
	}

	/**
	 * The bindings the mod acts on itself rather than handing to the game.
	 *
	 * <p>These are the ones with no keyboard equivalent -- turning the world, grabbing the world to
	 * drag yourself round, the menu button that has to work both in and out of a screen.
	 */
	public static void processBindings() {
		if (inputActions.isEmpty() || VR.settings() == null) {
			return;
		}
		boolean sleeping = mc.theWorld != null && mc.thePlayer != null
				&& mc.thePlayer.isPlayerSleeping();
		boolean gui = mc.currentScreen != null;

		processMovementModeToggle();
		if (!gui) {
			processWorldGrab();
		}
		processWorldRotation();
		processHotbarKeys();
		processQuickTorch();
		// Held open rather than toggled, so letting go is the choice -- which is why this reads
		// the key's state rather than asking whether it was pressed since last time.
		lol.gzmc.vivecraftx.render.VRRadialMenu.process(keyRadialMenu.getIsKeyPressed());
		if (!gui && mc.thePlayer != null) {
			VR.interactTracker().processBindings(mc,
					(net.minecraft.client.entity.EntityClientPlayerMP) mc.thePlayer);
		}
		processScreenEscapes(gui, sleeping);
		processMenuButton(gui);
		processKeyboardToggle();
	}

	/**
	 * Switches between walking and teleporting.
	 *
	 * <p>Its own binding, and only that. This also used to happen when pick-block was held for
	 * four seconds, from a time when a controller had no spare buttons -- but pick-block is on
	 * the grip on most of the profiles shipped here, and a grip gets held. The result was that
	 * resting a finger on it silently changed how you move, every four seconds, for as long as
	 * you held it.
	 */
	private static void processMovementModeToggle() {
		if (!VR.settings().vrAllowLocoModeSwotch) {
			return;
		}
		if (keyToggleMovement.isPressed()) {
			VR.player().setFreeMoveMode(!VR.player().getFreeMoveMode());
		}
	}

	/**
	 * Grabbing the world to turn it, and the free-rotate variant.
	 *
	 * <p>Both work by remembering the controller's yaw when the grab started and following it,
	 * which is what makes the world feel attached to the hand. Whichever hand the player bound it
	 * to is the hand that turns it.
	 */
	private static void processWorldGrab() {
		float mainYaw = aimYaw;
		float offYaw = laimYaw;

		if (keyWalkabout.getIsKeyPressed()) {
			float yaw = handYawFor(keyWalkabout, mainYaw, offYaw);
			if (!isWalkingAbout) {
				isWalkingAbout = true;
				walkaboutYawStart = VR.settings().vrWorldRotation + yaw;
			} else {
				VR.settings().vrWorldRotation = (walkaboutYawStart - yaw) % 360;
			}
		} else {
			isWalkingAbout = false;
		}

		if (keyRotateFree.getIsKeyPressed()) {
			float yaw = handYawFor(keyRotateFree, mainYaw, offYaw);
			if (!isFreeRotate) {
				isFreeRotate = true;
				walkaboutYawStart = VR.settings().vrWorldRotation - yaw;
			} else {
				VR.settings().vrWorldRotation = walkaboutYawStart + yaw;
			}
		} else {
			isFreeRotate = false;
		}
	}

	/** Which hand's aim to follow, asked of the runtime rather than assumed. */
	private static float handYawFor(KeyBinding binding, float mainYaw, float offYaw) {
		TrackedController controller = findActiveBindingController(binding);
		return controller != null && controller.getType() == ControllerType.LEFT ? offYaw : mainYaw;
	}

	/**
	 * Turning the world in place.
	 *
	 * <p>With a rotation increment set, each press turns by that much, which is the comfortable
	 * option: a sudden step is easier on the stomach than a slow slide. With the increment at zero
	 * the player has asked for smooth turning, and the axis drives it directly.
	 *
	 * <p>The smooth speed is in degrees per second, and is multiplied by how long the last frame
	 * took. It was per frame before, which meant the world turned at whatever rate the machine
	 * happened to be rendering -- twice as fast on a good day as on a bad one, and differently
	 * again the moment anything else on the computer wanted the graphics card.
	 */
	private static void processWorldRotation() {
		VRSettings settings = VR.settings();
		if (settings.vrWorldRotationIncrement == 0) {
			float speed = settings.vrSmoothTurnSpeed * VRRenderState.getFrameDelta();
			float axis = getInputAction(keyRotateAxis).getAxis2DUseTracked().getX();
			if (axis == 0) {
				axis = getInputAction(keyFreeMoveRotate).getAxis2DUseTracked().getX();
			}
			// A stick never quite returns to the middle, and turning slowly for ever because of
			// it is both disorienting and impossible to notice the cause of.
			if (Math.abs(axis) <= settings.vrJoystickDeadzone) {
				axis = 0;
			}
			if (axis != 0) {
				settings.vrWorldRotation = (settings.vrWorldRotation - speed * axis) % 360;
			}
			float left = getInputAction(keyRotateLeft).getAxis1DUseTracked();
			if (left > settings.vrJoystickDeadzone) {
				settings.vrWorldRotation = (settings.vrWorldRotation + speed * left) % 360;
			}
			float right = getInputAction(keyRotateRight).getAxis1DUseTracked();
			if (right > settings.vrJoystickDeadzone) {
				settings.vrWorldRotation = (settings.vrWorldRotation - speed * right) % 360;
			}
		} else {
			if (keyRotateAxis.isPressed() || keyFreeMoveRotate.isPressed()) {
				float axis = getInputAction(keyRotateAxis).getAxis2D(false).getX();
				if (axis == 0) {
					axis = getInputAction(keyFreeMoveRotate).getAxis2D(false).getX();
				}
				if (Math.abs(axis) > 0.5F) {
					settings.vrWorldRotation -=
							settings.vrWorldRotationIncrement * Math.signum(axis);
					settings.vrWorldRotation %= 360;
				}
			}
			if (keyRotateLeft.isPressed()) {
				settings.vrWorldRotation =
						(settings.vrWorldRotation + settings.vrWorldRotationIncrement) % 360;
			}
			if (keyRotateRight.isPressed()) {
				settings.vrWorldRotation =
						(settings.vrWorldRotation - settings.vrWorldRotationIncrement) % 360;
			}
		}
	}

	private static void processHotbarKeys() {
		if (keyHotbarNext.isPressed()) {
			changeHotbar(-1);
			triggerBindingHapticPulse(keyHotbarNext, 250);
		}
		if (keyHotbarPrev.isPressed()) {
			changeHotbar(1);
			triggerBindingHapticPulse(keyHotbarPrev, 250);
		}
	}

	/** Places a torch from anywhere in the hotbar and switches straight back. */
	private static void processQuickTorch() {
		if (!keyQuickTorch.isPressed() || mc.thePlayer == null || mc.currentScreen != null) {
			return;
		}
		for (int slot = 0; slot < 9; slot++) {
			ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(slot);
			if (itemStack != null && itemStack.getUnlocalizedName().equals("tile.torch")) {
				quickTorchPreviousSlot = mc.thePlayer.inventory.currentItem;
				mc.thePlayer.inventory.currentItem = slot;
				VanillaExt.rightClickMouse(mc);
				mc.thePlayer.inventory.currentItem = quickTorchPreviousSlot;
				quickTorchPreviousSlot = -1;
				break;
			}
		}
	}

	/**
	 * The ways out of a screen that the screen itself does not provide.
	 *
	 * <p>Containers only listen to the keyboard to close, and starting to move should close
	 * whatever is open rather than walking blind behind a menu.
	 */
	private static void processScreenEscapes(boolean gui, boolean sleeping) {
		if (gui && !sleeping && mc.thePlayer != null && !(mc.currentScreen instanceof GuiWinGame)
				&& (keyTeleport.getIsKeyPressed()
						|| mc.gameSettings.keyBindForward.getIsKeyPressed())) {
			mc.thePlayer.closeScreen();
		}
		if (mc.currentScreen instanceof GuiContainer && mc.thePlayer != null
				&& mc.gameSettings.keyBindInventory.isPressed()) {
			mc.thePlayer.closeScreen();
		}
		if (mc.currentScreen instanceof GuiChat && mc.gameSettings.keyBindChat.isPressed()) {
			mc.displayGuiScreen(null);
		}
	}

	/**
	 * The menu button, which has to work from both sides.
	 *
	 * <p>Out of a screen it opens the pause menu; in one it sends escape, because that is what
	 * every screen already understands and the alternative is a list of special cases.
	 */
	private static void processMenuButton(boolean gui) {
		if (!keyMenuButton.isPressed()) {
			return;
		}
		if (keyboardShowing) {
			setKeyboardOverlayShowing(false, null);
			return;
		}
		if (!gui) {
			mc.displayInGameMenu();
		} else if (Display.isActive()) {
			KeyboardSimulator.robot.keyPress(KeyEvent.VK_ESCAPE);
			KeyboardSimulator.robot.keyRelease(KeyEvent.VK_ESCAPE);
		} else if (mc.thePlayer != null) {
			mc.thePlayer.closeScreen();
		}
	}

	/**
	 * Shows or hides a keyboard, and chooses which kind.
	 *
	 * <p>The one standing in the world is the better of the two and is the default: SteamVR's own
	 * is a dashboard overlay that floats over everything, dims the game, cannot be moved, needs
	 * the game window to have focus, and refuses to appear at all in seated mode. The overlay
	 * stays available for anyone who would rather have it, and is still what seated play gets.
	 */
	private static void processKeyboardToggle() {
		if (keyToggleKeyboard.isPressed()) {
			boolean physical = VR.settings() != null && VR.settings().vrPhysicalKeyboard
					&& !VR.settings().seated;
			if (physical) {
				lol.gzmc.vivecraftx.render.VRKeyboard.setShowing(
						!lol.gzmc.vivecraftx.render.VRKeyboard.isShowing());
			} else {
				setKeyboardOverlayShowing(!keyboardShowing, null);
			}
		}
		try {
			lol.gzmc.vivecraftx.render.VRKeyboard.process();
		} catch (Throwable t) {
			// The board is a gadget standing on top of the session, not a part of it. A fault in
			// it used to travel up to the catch-all around the poll in VRFrame.onFrameStart,
			// which reads every throwable as the headset runtime having stopped answering and
			// ends VR outright -- so a key that could not be worked out dropped the player back
			// to the monitor mid-sentence. Put the board away and carry on instead.
			lol.gzmc.vivecraftx.ViveCraftX.LOG.error("The VR keyboard failed and has been put away", t);
			lol.gzmc.vivecraftx.render.VRKeyboard.setShowing(false);
		}
	}

	/**
	 * Moves the selected hotbar slot.
	 *
	 * <p>Through the wheel rather than the inventory directly where possible, so that mods watching
	 * for a scroll see it: several of them replace what the wheel does.
	 */
	private static void changeHotbar(int dir) {
		if (mc.thePlayer == null) {
			return;
		}
		// Never let go while hanging on. Vivecraft narrowed this to climbing picks, which this
		// version's climb tracker has no notion of; hanging at all is the case that matters, and
		// being unable to change slot mid-climb is better than falling.
		if (VR.climbTracker().isGrabbingLadder()) {
			return;
		}
		if (mc.currentScreen == null && Display.isActive()) {
			KeyboardSimulator.robot.mouseWheel(-dir * 120);
		} else {
			mc.thePlayer.inventory.changeCurrentItem(dir);
		}
	}

}
