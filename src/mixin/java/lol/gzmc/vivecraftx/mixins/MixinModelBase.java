package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.api.IModelExt;

import net.minecraft.client.model.ModelBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Carries the VR model flags. Declared on {@code ModelBase} so {@code ModelBiped} inherits them,
 * which matches where Vivecraft's patch put {@code vrMode}.
 */
@Mixin(ModelBase.class)
public abstract class MixinModelBase implements IModelExt {

    @Unique
    private boolean vivecraftx$vrMode = false;

    @Unique
    private boolean vivecraftx$renderHead = true;

    @Unique
    private boolean vivecraftx$renderHeadWear = true;

    @Unique
    private float vivecraftx$vrOffset = 0.0F;

    @Override
    public boolean vivecraftx$isVrMode() {
        return this.vivecraftx$vrMode;
    }

    @Override
    public void vivecraftx$setVrMode(boolean value) {
        this.vivecraftx$vrMode = value;
    }

    @Override
    public boolean vivecraftx$isRenderHead() {
        return this.vivecraftx$renderHead;
    }

    @Override
    public void vivecraftx$setRenderHead(boolean value) {
        this.vivecraftx$renderHead = value;
    }

    @Override
    public boolean vivecraftx$isRenderHeadWear() {
        return this.vivecraftx$renderHeadWear;
    }

    @Override
    public void vivecraftx$setRenderHeadWear(boolean value) {
        this.vivecraftx$renderHeadWear = value;
    }

    @Override
    public float vivecraftx$getVrOffset() {
        return this.vivecraftx$vrOffset;
    }

    @Override
    public void vivecraftx$setVrOffset(float value) {
        this.vivecraftx$vrOffset = value;
    }
}
