package lol.gzmc.vivecraftx.util;

import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * The vector helpers Vivecraft's patch bolted onto {@link Vec3}.
 *
 * <p>Vanilla 1.7.10 has no {@code scale}, {@code add}, {@code subtractProperly} or
 * {@code rotateYaw}, and its {@code subtract} returns the operands the other way round, which is
 * why the original went out of its way to add a correctly ordered one.
 *
 * <p>These are static helpers rather than a mixin on {@code Vec3}, because a mixin can only add
 * members behind an interface, and every call site would then need a cast. A plain call reads
 * better and costs nothing.
 *
 * <p>Vanilla keeps the {@code Vec3} constructor non-public, so everything is built through
 * {@link Vec3#createVectorHelper}.
 */
public final class Vec3Util {

    private Vec3Util() {}

    public static Vec3 scale(Vec3 v, double d) {
        return Vec3.createVectorHelper(v.xCoord * d, v.yCoord * d, v.zCoord * d);
    }

    public static Vec3 add(Vec3 a, Vec3 b) {
        return Vec3.createVectorHelper(a.xCoord + b.xCoord, a.yCoord + b.yCoord, a.zCoord + b.zCoord);
    }

    public static Vec3 subtract(Vec3 a, double x, double y, double z) {
        return Vec3.createVectorHelper(a.xCoord - x, a.yCoord - y, a.zCoord - z);
    }

    public static Vec3 subtract(Vec3 a, Vec3 b) {
        return Vec3.createVectorHelper(a.xCoord - b.xCoord, a.yCoord - b.yCoord, a.zCoord - b.zCoord);
    }

    /** Rotates around the Y axis. Matches the original's sign convention exactly. */
    public static Vec3 rotateYaw(Vec3 v, float angle) {
        float cos = MathHelper.cos(angle);
        float sin = MathHelper.sin(angle);
        return Vec3.createVectorHelper(
                v.xCoord * (double) cos + v.zCoord * (double) sin,
                v.yCoord,
                v.zCoord * (double) cos - v.xCoord * (double) sin);
    }
}
