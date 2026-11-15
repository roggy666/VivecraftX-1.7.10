package lol.gzmc.vivecraftx.api;

/**
 * The bits of {@code EntityRenderer} the VR camera needs that vanilla keeps to itself.
 */
public interface IEntityRendererExt {

    /** Render distance in blocks, which also sets the far clip the headset projection uses. */
    float vivecraftx$getFarPlaneDistance();

    /** Ticks since startup; drives the nether portal wobble. */
    int vivecraftx$getRendererUpdateCount();
}
