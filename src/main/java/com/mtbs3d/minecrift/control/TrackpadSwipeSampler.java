package com.mtbs3d.minecrift.control;

import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.utils.Vector2;

/**
 * Turns a finger sliding across a trackpad into discrete swipes.
 *
 * <p>A trackpad reports where the finger is, not that it moved, so a swipe has to be inferred. The
 * samples go into a short ring and the travel across it is accumulated; each time the total passes
 * the threshold, that much is taken off and one swipe counted, which is what makes a long drag
 * produce several swipes instead of one.
 *
 * <p>The ring is cleared the moment the finger lifts, so travel is never accumulated across a gap.
 * Otherwise lifting off one side and touching down on the other would read as a swipe.
 */
public class TrackpadSwipeSampler {

    private static final int UP = 0;
    private static final int RIGHT = 1;
    private static final int DOWN = 2;
    private static final int LEFT = 3;

    private final float[] bufferX = new float[5];
    private final float[] bufferY = new float[5];
    private int index;
    private long count;
    private float accumulatorX;
    private float accumulatorY;
    private final int[] swiped = new int[4];

    public float threshold = 0.5F;

    public void update(ControllerType hand, Vector2 position) {
        MCOpenVR.getInputAction(MCOpenVR.keyTrackpadTouch).setCurrentHand(hand);
        if (MCOpenVR.getInputAction(MCOpenVR.keyTrackpadTouch).isButtonPressed()) {
            this.bufferX[this.index] = position.getX();
            this.bufferY[this.index] = position.getY();
            if (++this.index >= this.bufferX.length) {
                this.index = 0;
            }
            ++this.count;
        } else {
            for (int i = 0; i < this.bufferX.length; i++) {
                this.bufferX[i] = 0;
                this.bufferY[i] = 0;
            }
            this.count = 0;
        }

        if (this.count >= this.bufferX.length) {
            int oldest = (this.index + 1) % this.bufferX.length;
            this.accumulatorX += this.bufferX[oldest] - this.bufferX[this.index];
            this.accumulatorY += this.bufferY[oldest] - this.bufferY[this.index];

            if (this.accumulatorX >= this.threshold) {
                this.accumulatorX -= this.threshold;
                ++this.swiped[RIGHT];
            }
            if (this.accumulatorX <= -this.threshold) {
                this.accumulatorX += this.threshold;
                ++this.swiped[LEFT];
            }
            if (this.accumulatorY >= this.threshold) {
                this.accumulatorY -= this.threshold;
                ++this.swiped[UP];
            }
            if (this.accumulatorY <= -this.threshold) {
                this.accumulatorY += this.threshold;
                ++this.swiped[DOWN];
            }
        } else {
            this.accumulatorX = 0;
            this.accumulatorY = 0;
        }
    }

    public boolean isSwipedLeft() {
        return isSwiped(LEFT);
    }

    public boolean isSwipedRight() {
        return isSwiped(RIGHT);
    }

    public boolean isSwipedUp() {
        return isSwiped(UP);
    }

    public boolean isSwipedDown() {
        return isSwiped(DOWN);
    }

    /** Consuming, so one swipe is acted on once. */
    private boolean isSwiped(int direction) {
        if (this.swiped[direction] > 0) {
            --this.swiped[direction];
            return true;
        }
        return false;
    }
}
