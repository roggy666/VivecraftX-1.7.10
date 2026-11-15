package lol.gzmc.vivecraftx.api;

/**
 * VR flags Vivecraft's patch added to the model classes.
 *
 * <p>Unlike the fields it put on {@code Minecraft}, these are per model instance, so they cannot
 * become a holder: every model would share one flag. They are {@code @Unique} mixin state.
 *
 * <p>{@code vrMode} comes from {@code ModelBase}, the rest from {@code ModelBiped}; one interface
 * covers both, since {@code ModelBiped} extends {@code ModelBase}.
 */
public interface IModelExt {

    boolean vivecraftx$isVrMode();

    void vivecraftx$setVrMode(boolean value);

    boolean vivecraftx$isRenderHead();

    void vivecraftx$setRenderHead(boolean value);

    boolean vivecraftx$isRenderHeadWear();

    void vivecraftx$setRenderHeadWear(boolean value);

    float vivecraftx$getVrOffset();

    void vivecraftx$setVrOffset(float value);
}
