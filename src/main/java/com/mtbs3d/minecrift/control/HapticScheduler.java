package com.mtbs3d.minecrift.control;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.mtbs3d.minecrift.provider.MCOpenVR;

import jopenvr.JOpenVRLibrary;

import lol.gzmc.vivecraftx.ViveCraftX;

/**
 * Fires haptic pulses on a clock of their own.
 *
 * <p>A pattern of pulses -- the rhythm that tells you a bow is drawn, or that you are climbing --
 * needs them spaced in real time, and the client thread cannot wait between them without dropping
 * frames. So a pattern is queued here and delivered by a thread that does nothing else.
 *
 * <p>One thread rather than a pool, because pulses to the same controller must arrive in the order
 * they were asked for.
 */
public class HapticScheduler {

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "ViveCraftX haptics");
                    // a daemon, so a queued pulse cannot hold the game open on exit
                    thread.setDaemon(true);
                    return thread;
                }
            });

    public void queueHapticPulse(final ControllerType controller, final float durationSeconds,
            final float frequency, final float amplitude, float delaySeconds) {
        this.executor.schedule(new Runnable() {
            @Override
            public void run() {
                trigger(controller, durationSeconds, frequency, amplitude);
            }
        }, (long) (delaySeconds * 1000000), TimeUnit.MICROSECONDS);
    }

    private void trigger(ControllerType controller, float durationSeconds, float frequency,
            float amplitude) {
        if (MCOpenVR.vrInput == null) {
            return;
        }
        int error = MCOpenVR.vrInput.TriggerHapticVibrationAction.apply(
                MCOpenVR.getHapticHandle(controller), 0, durationSeconds, frequency, amplitude,
                JOpenVRLibrary.k_ulInvalidInputValueHandle);
        if (error != 0) {
            ViveCraftX.LOG.warn("Haptic pulse failed: {}", MCOpenVR.getInputError(error));
        }
    }

    public void shutdown() {
        this.executor.shutdownNow();
    }
}
