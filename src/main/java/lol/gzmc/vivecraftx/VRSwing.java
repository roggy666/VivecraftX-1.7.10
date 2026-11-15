package lol.gzmc.vivecraftx;

/**
 * What a hand was doing when it last swung.
 *
 * <p>A hand swings for three quite different reasons, and in a headset they should not look alike.
 * Hitting something is a chop: the hand pitches forward and comes back. Using something is a push:
 * the hand moves away from you and returns. Reaching out and pressing something is a turn of the
 * wrist. Flat Minecraft has one animation for all three because it only ever had one arm, drawn at
 * the corner of a screen, where nobody could tell them apart.
 *
 * <p>So the reason has to be recorded where it is known -- at the thing that caused the swing --
 * and read later, where the arm is drawn. The game's own swing carries no such information.
 *
 * <p>Kept per hand, which is where this parts company with modern Vivecraft. There one hand swings
 * at a time by construction, so one value does for both. Here the two hands count their swings
 * separately -- the game counts the main hand and the off-hand mod counts the other -- so both can
 * be mid-swing at once, and a single value would be read by whichever hand asked last.
 */
public final class VRSwing {

    private VRSwing() {}

    public enum Kind {
        /** A chop: hitting something, or mining. */
        ATTACK,
        /** A push: placing, eating, using a thing on a thing. */
        USE,
        /** A turn of the wrist: pressing something the hand is already resting on. */
        INTERACT
    }

    /**
     * Attack rather than nothing, because that is what an unannounced swing nearly always is, and
     * because a hand that has never swung is never drawn swinging anyway.
     */
    private static final Kind[] KINDS = {Kind.ATTACK, Kind.ATTACK};

    /** Called by whatever is about to make the hand swing, before it swings it. */
    public static void began(int hand, Kind kind) {
        if (hand >= 0 && hand < KINDS.length) {
            KINDS[hand] = kind;
        }
    }

    public static Kind kind(int hand) {
        return hand >= 0 && hand < KINDS.length ? KINDS[hand] : Kind.ATTACK;
    }
}
