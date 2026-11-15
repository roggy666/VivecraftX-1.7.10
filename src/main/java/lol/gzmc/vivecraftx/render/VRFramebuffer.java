package lol.gzmc.vivecraftx.render;

import lol.gzmc.vivecraftx.ViveCraftX;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.shader.Framebuffer;

import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * A framebuffer that can draw into a texture somebody else owns, namely the eye textures OpenVR
 * hands us.
 *
 * <p>Vivecraft got this by patching vanilla {@link Framebuffer} with extra constructors. We
 * subclass instead, for two reasons: {@code Minecraft.framebufferMc} is typed as
 * {@code Framebuffer}, so a subclass drops straight in, and Angelica already mixes into
 * {@code Framebuffer} for its HUD caching, which is overlap worth avoiding when a subclass costs
 * nothing.
 *
 * <p>Attachment goes through {@link OpenGlHelper} rather than raw GL calls, the same way vanilla
 * does it, so the EXT and ARB variants are picked correctly and Angelica's state manager sees the
 * calls it expects.
 */
public class VRFramebuffer extends Framebuffer {

    private final String name;

    /** True once an external texture replaced the one the superclass generated. */
    private boolean external;

    public VRFramebuffer(String name, int width, int height, boolean useDepth) {
        super(width, height, useDepth);
        this.name = name;
    }

    /**
     * A framebuffer drawing into an existing texture.
     *
     * <p>The superclass constructor calls {@link #createFramebuffer}, which we override, so the
     * texture the runtime owns is attached while the framebuffer is being built and no throwaway
     * texture is ever generated. The id is handed over through {@link #PENDING} because a field
     * cannot be assigned before {@code super()} runs; GL work is single threaded, so the window
     * between setting it and consuming it never spans two callers.
     */
    public static VRFramebuffer wrapping(String name, int width, int height, int textureId) {
        PENDING = textureId;
        try {
            return new VRFramebuffer(name, width, height, false);
        } finally {
            PENDING = -1;
        }
    }

    /** The texture {@link #createFramebuffer} should adopt instead of generating one. */
    private static int PENDING = -1;

    /**
     * Builds the framebuffer around the runtime's texture when one is pending, and behaves
     * exactly like vanilla otherwise.
     */
    @Override
    public void createFramebuffer(int width, int height) {
        int adopt = PENDING;
        if (adopt < 0) {
            super.createFramebuffer(width, height);
            return;
        }
        PENDING = -1;

        this.framebufferWidth = width;
        this.framebufferHeight = height;
        this.framebufferTextureWidth = width;
        this.framebufferTextureHeight = height;

        if (!OpenGlHelper.isFramebufferEnabled()) {
            this.framebufferClear();
            return;
        }
        this.framebufferObject = OpenGlHelper.func_153165_e();
        this.framebufferTexture = adopt;
        this.external = true;

        setFramebufferFilter(GL11.GL_NEAREST);
        OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, this.framebufferObject);
        OpenGlHelper.func_153188_a(OpenGlHelper.field_153198_e, OpenGlHelper.field_153200_g,
                GL11.GL_TEXTURE_2D, adopt, 0);

        checkFramebufferComplete();
        this.framebufferClear();
        unbindFramebufferTexture();
    }

    /** Points colour attachment 0 at the given texture, releasing whichever was there before. */
    public void attachColorTexture(int textureId) {
        if (!OpenGlHelper.isFramebufferEnabled()) {
            return;
        }
        int previous = this.framebufferTexture;
        boolean wasExternal = this.external;

        OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, this.framebufferObject);
        OpenGlHelper.func_153188_a(OpenGlHelper.field_153198_e, OpenGlHelper.field_153200_g,
                GL11.GL_TEXTURE_2D, textureId, 0);
        this.framebufferTexture = textureId;
        this.external = true;

        checkFramebufferComplete();
        OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, 0);

        if (previous > 0 && previous != textureId && !wasExternal) {
            // only ever delete a texture this framebuffer generated itself
            TextureUtil.deleteTexture(previous);
        }
        ViveCraftX.LOG.debug("{}: drawing into texture {} ({}x{})", name, textureId,
                this.framebufferWidth, this.framebufferHeight);
    }

    /**
     * Releases the framebuffer but not an external texture, which belongs to OpenVR and is still
     * registered with the compositor.
     */
    @Override
    public void deleteFramebuffer() {
        if (external) {
            this.framebufferTexture = -1;
        }
        super.deleteFramebuffer();
    }

    /**
     * Sets up this framebuffer's texture to be read as a surface in the world rather than blitted
     * one-to-one, and refreshes its mipmaps.
     *
     * <p>A framebuffer texture has one level and a nearest filter, which is right for a blit to
     * the window and wrong for a panel standing two metres away: at that size the interface is
     * minified, and a nearest filter makes text crawl and shimmer with every small head movement.
     * Mipmaps and an anisotropic filter are what stop that, and the mipmaps have to be rebuilt
     * each frame because the interface was just redrawn.
     *
     * <p>Expects the texture to already be bound, and degrades to a plain linear filter where the
     * driver cannot generate mipmaps -- shimmering text is worse than crisp text, but both are
     * better than the incomplete texture that asking for a mipmap filter without mipmaps would
     * produce.
     */
    public void applyPanelFilter() {
        if (!generateMipmaps()) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            return;
        }
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        float anisotropy = maxAnisotropy();
        if (anisotropy > 1.0F) {
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D,
                    EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, anisotropy);
        }
    }

    /** @return false when the driver offers no way to build mipmaps, so the caller can back off */
    private boolean generateMipmaps() {
        if (GLContext.getCapabilities().OpenGL30) {
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            return true;
        }
        if (GLContext.getCapabilities().GL_EXT_framebuffer_object) {
            EXTFramebufferObject.glGenerateMipmapEXT(GL11.GL_TEXTURE_2D);
            return true;
        }
        if (!mipmapWarningGiven) {
            mipmapWarningGiven = true;
            ViveCraftX.LOG.info("No mipmap generation available; the VR interface will use a "
                    + "plain linear filter and its text may shimmer");
        }
        return false;
    }

    private static boolean mipmapWarningGiven;

    /** How much anisotropy to ask for: what we want, or what the driver allows, whichever is less. */
    private static float maxAnisotropy() {
        if (!GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            return 1.0F;
        }
        if (anisotropyLimit < 0) {
            // asking for more than the driver supports is an error, not a request it clamps
            anisotropyLimit = GL11.glGetFloat(
                    EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
        }
        return Math.min(16.0F, anisotropyLimit);
    }

    private static float anisotropyLimit = -1F;

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "VRFramebuffer[" + name + " " + framebufferWidth + "x" + framebufferHeight
                + " fbo=" + framebufferObject + " tex=" + framebufferTexture
                + (external ? " external" : "") + "]";
    }
}
