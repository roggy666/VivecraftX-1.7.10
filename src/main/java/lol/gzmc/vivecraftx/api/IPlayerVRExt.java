package lol.gzmc.vivecraftx.api;

/**
 * Per-player VR state Vivecraft's patch added to {@code EntityPlayerSP}.
 *
 * <p>Per instance, so a holder will not do.
 */
public interface IPlayerVRExt {

    /** False until the server has confirmed the player, which gates teleport movement. */
    boolean vivecraftx$isInitFromServer();

    void vivecraftx$setInitFromServer(boolean value);

    /** Counts down the teleport animation. */
    int vivecraftx$getMovementTeleportTimer();

    void vivecraftx$setMovementTeleportTimer(int value);
}
