package lol.gzmc.vivecraftx.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import lol.gzmc.vivecraftx.ViveCraftX;

import net.minecraft.client.Minecraft;

/**
 * Carries out, on the eye passes, the work Angelica would have done inside
 * {@code setupCameraTransform} if we had not taken that method over.
 *
 * <p>Taking a vanilla method over means inheriting its obligations. Angelica contributes two
 * things to the camera setup that have nothing to do with VR, and dropping them would be a
 * regression we caused:
 *
 * <ul>
 *   <li>NotFine raises the far clip so distant clouds are not sliced off. Purely about clouds.</li>
 *   <li>Angelica records the field of view in its own rendering state, which Iris shader packs
 *       read as a uniform. It only reads the value, it does not change it.</li>
 *   <li>Celeritas, Angelica's terrain renderer, only re-walks its visibility graph when the
 *       camera moves to a new chunk or the player entity turns. A head turn does neither, so the
 *       visible set stays frozen from whenever the body last rotated and chunks at the edge of
 *       vision stay dropped. Modern Vivecraft hit the same thing and does the same thing about
 *       it.</li>
 *   <li>Angelica guards the one render call per frame: it posts its own per-frame event, which
 *       several of its subsystems listen for, and it skips the frame when its frame limiter says
 *       to. We redirect the same call, and only one redirect of a call can win, so without this
 *       Angelica's guard is silently dropped -- the log says as much, plainly, and it would be a
 *       regression we caused.</li>
 * </ul>
 *
 * <p>The one thing deliberately not carried over is Angelica's view-bobbing condition, because
 * bobbing the camera inside a headset is what makes people ill. That is a VR decision, not an
 * oversight.
 *
 * <p>Everything is resolved reflectively and cached, so without Angelica each call is a null
 * check.
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public final class AngelicaRenderCompat {

    private AngelicaRenderCompat() {}

    private static boolean resolved;

    private static Field notFineMinFarPlane;      // jss.notfine.core.SettingsManager
    private static Method notFineCloudHeight;
    private static Field vboCloudsEnabled;        // AngelicaConfig
    private static Method getCloudRenderer;       // CloudRenderer
    private static Method requiredFarPlane;
    private static Object renderingState;         // RenderingState.INSTANCE
    private static Method setFov;
    private static Method postClientEvent;        // ClientEvent.post(ClientEventType)
    private static Object onTickEvent;            // ClientEventType.ON_TICK
    private static Method fpsReducerSkip;         // FpsReducer.skipRender()
    private static Method fpsReducerSkipped;      // FpsReducer.onRenderSkipped()
    private static Method scheduleTerrainUpdate;  // IRenderGlobalExt on RenderGlobal

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!AngelicaCompat.isPresent()) {
            return;
        }
        try {
            Class<?> settings = Class.forName("jss.notfine.core.SettingsManager");
            notFineMinFarPlane = settings.getField("minimumFarPlaneDistance");
            notFineCloudHeight = settings.getMethod("currentCloudHeight");

            Class<?> config = Class.forName("com.gtnewhorizons.angelica.config.AngelicaConfig");
            vboCloudsEnabled = config.getField("enableVBOClouds");

            Class<?> clouds = Class.forName("com.gtnewhorizons.angelica.render.CloudRenderer");
            getCloudRenderer = clouds.getMethod("getCloudRenderer");
            requiredFarPlane = clouds.getMethod("getRequiredFarPlaneDistance");

            Class<?> state = Class.forName("com.gtnewhorizons.angelica.rendering.RenderingState");
            renderingState = state.getField("INSTANCE").get(null);
            setFov = state.getMethod("setFov", float.class);

            Class<?> eventType = Class.forName("com.gtnewhorizons.angelica.event.ClientEventType");
            Class<?> event = Class.forName("com.gtnewhorizons.angelica.event.ClientEvent");
            postClientEvent = event.getMethod("post", eventType);
            onTickEvent = Enum.valueOf((Class<Enum>) eventType.asSubclass(Enum.class), "ON_TICK");

            Class<?> fps = Class.forName("com.gtnewhorizons.angelica.rendering.FpsReducer");
            fpsReducerSkip = fps.getMethod("skipRender");
            fpsReducerSkipped = fps.getMethod("onRenderSkipped");

            // Angelica puts this on RenderGlobal through an interface of its own, which is a
            // published way in rather than an internal we would be reaching around.
            Class<?> renderGlobalExt = Class.forName(
                    "com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalExt");
            scheduleTerrainUpdate = renderGlobalExt.getMethod("angelica$scheduleTerrainUpdate");

            // The chunk visibility search runs on another thread and is not waited for: it is
            // collected at the start of the next frame. That is a good trade for one view and a
            // bad one for two, because the second eye then draws a list the first eye's search
            // produced -- a different frame's worth of visible chunks in each eye.
            Class<?> celeritas = Class.forName(
                    "com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer");
            // getInstanceOrNull rather than getInstance, which throws when the terrain renderer
            // has not been built yet. The catch below is a kill switch -- it drops the handle so
            // the failure is reported once rather than every frame -- and a throw from here would
            // trip it over a frame that had no terrain to wait for in the first place, silently
            // giving up the fix for the rest of the session. Changing dimensions tears the
            // renderer down and puts us in exactly that frame.
            celeritasInstance = celeritas.getMethod("getInstanceOrNull");
            renderSectionManager = celeritas.getMethod("getRenderSectionManager");
            finishGraphUpdates = renderSectionManager.getReturnType()
                    .getMethod("finishAllGraphUpdates");

            ViveCraftX.LOG.info("Angelica render compat wired: cloud far plane, shader fov and "
                    + "the frame guard are kept on VR passes");
        } catch (Throwable t) {
            ViveCraftX.LOG.warn("Angelica is present but its render hooks did not resolve; "
                    + "clouds may be clipped and shader packs may read a stale fov", t);
            notFineMinFarPlane = null;
            renderingState = null;
        }
    }

    /**
     * Raises the far clip the way NotFine does, so clouds are not clipped on an eye pass.
     *
     * @param original what we would otherwise have used
     * @return the far clip to actually use
     */
    public static float farPlane(float original) {
        resolve();
        if (notFineMinFarPlane == null) {
            return original;
        }
        try {
            float required = notFineMinFarPlane.getInt(null);
            if (vboCloudsEnabled != null && vboCloudsEnabled.getBoolean(null)) {
                Object renderer = getCloudRenderer.invoke(null);
                if (renderer != null) {
                    required = Math.max(required, (Float) requiredFarPlane.invoke(renderer));
                }
            } else if (notFineCloudHeight != null) {
                required += (Float) notFineCloudHeight.invoke(null);
            }
            return Math.max(original, required);
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("Could not read Angelica's cloud far plane", t);
            notFineMinFarPlane = null;
            return original;
        }
    }

    /**
     * Asks Celeritas to work out which chunks are visible again.
     *
     * <p>It decides that for itself only when the camera crosses into another chunk or the player
     * entity turns, which is the right trade flat: the visibility graph is expensive to walk and
     * the view only changes when one of those happens. In a headset the view changes when the head
     * turns, and the head is not the entity, so without this the visible set is whatever it was
     * when the body last moved -- and chunks at the edge of vision are simply missing, most
     * obviously from somewhere with a long view.
     *
     * <p>Called once a frame rather than once an eye: the first eye walks the graph and the second
     * reuses it, which is also what stops the two eyes disagreeing and flickering.
     */
    private static java.lang.reflect.Method celeritasInstance;
    private static java.lang.reflect.Method renderSectionManager;
    private static java.lang.reflect.Method finishGraphUpdates;

    /**
     * Waits for the chunk visibility search before anything is drawn with its answer.
     *
     * <p>Angelica hands the search to a background thread and does not wait: whatever the
     * previous frame produced is what gets drawn, and the new answer is collected at the start
     * of the next frame. One view never notices, because every frame is a frame behind and that
     * is invisible. Two views do, and badly: the first eye draws the list left over from last
     * frame, then its own search finishes and the second eye draws that one instead. The eyes
     * are looking at different sets of chunks, and the difference between those sets is exactly
     * the edge of vision -- which is where chunks were flickering, and why it happened when
     * turning rather than when standing still.
     *
     * <p>Waiting costs what the thread was saving. It is the same thing Angelica's own setting
     * does when asked for a synchronous search, except that this only asks for it while a
     * headset is on.
     */
    public static void waitForChunkVisibility() {
        resolve();
        if (finishGraphUpdates == null) {
            return;
        }
        try {
            Object renderer = celeritasInstance.invoke(null);
            if (renderer == null) {
                return;
            }
            Object manager = renderSectionManager.invoke(renderer);
            if (manager != null) {
                finishGraphUpdates.invoke(manager);
            }
        } catch (Throwable t) {
            ViveCraftX.LOG.warn("Could not wait for Angelica's chunk visibility; the two eyes "
                    + "may disagree about which chunks are visible when turning", t);
            finishGraphUpdates = null;
        }
    }

    public static void scheduleTerrainUpdate() {
        resolve();
        if (scheduleTerrainUpdate == null) {
            return;
        }
        Object renderGlobal = Minecraft.getMinecraft().renderGlobal;
        if (renderGlobal == null) {
            return;
        }
        try {
            scheduleTerrainUpdate.invoke(renderGlobal);
        } catch (Throwable t) {
            ViveCraftX.LOG.warn("Could not ask Angelica to recheck chunk visibility; chunks may "
                    + "go missing at the edge of vision when turning the head", t);
            scheduleTerrainUpdate = null;
        }
    }

    /**
     * Posts Angelica's own per-frame event, which its frame limiter and several of its render
     * subsystems listen for.
     */
    public static void onFrameStart() {
        resolve();
        if (postClientEvent == null) {
            return;
        }
        try {
            postClientEvent.invoke(null, onTickEvent);
        } catch (Throwable t) {
            ViveCraftX.LOG.warn("Could not post Angelica's frame event; its frame limiter and "
                    + "anything else listening for it will not run", t);
            postClientEvent = null;
        }
    }

    /** Whether Angelica's frame limiter wants this frame dropped. */
    public static boolean shouldSkipFrame() {
        resolve();
        if (fpsReducerSkip == null) {
            return false;
        }
        try {
            return (Boolean) fpsReducerSkip.invoke(null);
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("Could not ask Angelica whether to skip the frame", t);
            fpsReducerSkip = null;
            return false;
        }
    }

    /** Tells Angelica's frame limiter the frame was in fact dropped, so it can keep its pace. */
    public static void onFrameSkipped() {
        resolve();
        if (fpsReducerSkipped == null) {
            return;
        }
        try {
            fpsReducerSkipped.invoke(null);
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("Could not tell Angelica the frame was skipped", t);
            fpsReducerSkipped = null;
        }
    }

    /**
     * Reports the effective field of view, which Angelica captures for shader packs.
     *
     * <p>On an eye pass the lenses decide it, so we hand over what the headset's projection
     * implies rather than the setting, which is the honest value for a shader to read.
     */
    public static void reportFov(float fov) {
        resolve();
        if (renderingState == null) {
            return;
        }
        try {
            setFov.invoke(renderingState, fov);
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("Could not report fov to Angelica", t);
            renderingState = null;
        }
    }
}
