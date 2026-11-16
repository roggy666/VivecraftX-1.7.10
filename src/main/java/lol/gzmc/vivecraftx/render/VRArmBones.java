package lol.gzmc.vivecraftx.render;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;

/**
 * An arm in two pieces, with an elbow between them.
 *
 * <p>A vanilla arm is one box, twelve pixels long, hanging from the shoulder. Aimed at a
 * controller it can only ever be a straight line, so once the hand is further away than the arm
 * is long there is nothing to do but slide the whole arm off the shoulder. That is the hole that
 * reads as the arm coming off the body.
 *
 * <p>Two boxes with a joint between them bend instead, and reach half as far again. The pieces
 * are drawn longer than the bones they stand for -- eight and seven pixels for a pair of fives --
 * so they overlap at the elbow and no gap can open there at any angle. Modern Vivecraft does the
 * same thing for the same reason, and these are its numbers.
 */
public final class VRArmBones {

    /** Where the arm's texture sits. The same patch the vanilla arm is drawn from. */
    private static final int ARM_U = 40;
    private static final int ARM_V = 16;

    /** The full length of a vanilla arm, in model units. */
    private static final int ARM_HEIGHT = 12;

    /** How long each piece is drawn. The excess over the bone is the overlap. */
    private static final int UPPER_LENGTH = 8;
    private static final int LOWER_LENGTH = 7;

    /**
     * The forearm, a hair thinner than the upper arm.
     *
     * <p>Where the two overlap they would otherwise want the same surface, and which one the
     * depth test keeps comes down to rounding -- which flickers as the arm moves.
     */
    private static final float LOWER_SHRINK = -0.05F;

    public final ModelRenderer rightUpper;
    public final ModelRenderer rightLower;
    public final ModelRenderer leftUpper;
    public final ModelRenderer leftLower;

    /** Set while these are standing in for the model's own arms, cleared when they are not. */
    public boolean active;

    public VRArmBones(ModelBiped model) {
        this.rightUpper = piece(model, false, ARM_V, UPPER_LENGTH, 0F);
        this.rightLower = piece(model, false, ARM_V + ARM_HEIGHT - LOWER_LENGTH, LOWER_LENGTH,
                LOWER_SHRINK);
        this.leftUpper = piece(model, true, ARM_V, UPPER_LENGTH, 0F);
        this.leftLower = piece(model, true, ARM_V + ARM_HEIGHT - LOWER_LENGTH, LOWER_LENGTH,
                LOWER_SHRINK);
    }

    /**
     * One piece of an arm.
     *
     * <p>Built around the same corner as the vanilla arm, so the skin lands where it should: the
     * upper piece takes the top rows of the arm's patch of texture and the forearm takes the
     * bottom rows, which is how an arm is drawn on a skin.
     */
    private static ModelRenderer piece(ModelBiped model, boolean left, int v, int length,
            float shrink) {
        ModelRenderer part = new ModelRenderer(model, ARM_U, v);
        part.mirror = left;
        // The two arms hang from pivots at -5 and +5 but are built on opposite sides of them, so
        // that each box ends up outboard of the torso rather than overlapping it. Mirroring does
        // not do this for us: it swaps the box's two X faces, which leaves a box built at -3
        // still spanning -3 to +1. Vanilla's own arms are built at -3 and -1 for the same reason.
        part.addBox(left ? -1.0F : -3.0F, -2.0F, -2.0F, 4, length, 4, shrink);
        part.setRotationPoint(left ? 5.0F : -5.0F, 2.0F, 0.0F);
        return part;
    }

    public void render(float scale) {
        if (!this.active) {
            return;
        }
        this.rightUpper.render(scale);
        this.rightLower.render(scale);
        this.leftUpper.render(scale);
        this.leftLower.render(scale);
    }
}
