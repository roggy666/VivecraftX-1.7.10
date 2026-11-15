package lol.gzmc.vivecraftx.util;

import net.minecraft.block.Block;
import net.minecraft.world.World;

/**
 * Vanilla replacements for members OptiFine added to vanilla classes.
 *
 * <p>Vivecraft was built on OptiFine-patched MCP sources, so it uses things that look vanilla but
 * are not. None of these exist in real 1.7.10: {@code MathHelper.deg2Rad},
 * {@code Block.isPassable}, {@code Block.canStopRayTrace}, {@code Block.SoundType.getStepSound},
 * {@code Block.SoundType.getFrequency}, {@code EntityPlayer.yOffset2}, and a five-argument
 * {@code World.rayTraceBlocks}. Checked against the decompiled sources, not assumed.
 */
public final class OptiFineCompatUtil {

    private OptiFineCompatUtil() {}

    /** OptiFine's {@code MathHelper.deg2Rad}. */
    public static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);

    /**
     * OptiFine's {@code Block.isPassable}: true when the block has no collision box, so the
     * player can walk through it.
     */
    public static boolean isPassable(Block block, World world, int x, int y, int z) {
        return block == null || block.getCollisionBoundingBoxFromPool(world, x, y, z) == null;
    }

    /**
     * OptiFine's {@code Block.canStopRayTrace}. Vanilla spells the same question
     * {@code canCollideCheck}, with identical arguments and meaning.
     */
    public static boolean canStopRayTrace(Block block, int meta, boolean includeLiquid) {
        return block != null && block.canCollideCheck(meta, includeLiquid);
    }

    /** OptiFine's {@code SoundType.getStepSound()}; vanilla calls it getStepResourcePath. */
    public static String getStepSound(Block.SoundType sound) {
        return sound.getStepResourcePath();
    }

    /** OptiFine's {@code SoundType.getFrequency()}; vanilla exposes the same field as getPitch. */
    public static float getFrequency(Block.SoundType sound) {
        return sound.getPitch();
    }
}
