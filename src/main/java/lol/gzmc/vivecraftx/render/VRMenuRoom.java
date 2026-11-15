package lol.gzmc.vivecraftx.render;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import com.mtbs3d.minecrift.provider.MCOpenVR;

import lol.gzmc.vivecraftx.ViveCraftX;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.biome.BiomeGenBase;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/**
 * Somewhere to stand while the menu is open.
 *
 * <p>Without a world there is nothing to draw, and a menu panel floating in a black void gives
 * the eyes no floor and no horizon, which is the situation that makes people queasy. So the menu
 * gets a room: a grass plane out to the horizon, with the headset's own play area marked on it so
 * you can see where you can actually walk.
 *
 * <p>This is Vivecraft's own 1.7.10 room, ported as it was. Worth saying, because modern
 * Vivecraft replaced it with a renderer that loads a real saved world as the menu background --
 * two thousand lines tied to a chunk renderer we do not have. The old room is a hundred lines and
 * needs nothing but a block texture, and as a menu backdrop it is not meaningfully worse.
 *
 * <p>The plane is 256 by 256 quads, so it is built into a buffer once and left on the card rather
 * than tessellated every frame for each eye.
 */
public final class VRMenuRoom {

    private VRMenuRoom() {}

    /** Half the plane's extent, in blocks. */
    private static final int SIZE = 128;

    /** Per vertex: position, normal and texture coordinate as floats, then three colour bytes. */
    private static final int STRIDE = 35;

    private static int vbo;

    /** Scratch for the light parameter calls, which all want a buffer of four. */
    private static final FloatBuffer PARAM = BufferUtils.createFloatBuffer(4);

    private static FloatBuffer param(float a, float b, float c, float d) {
        PARAM.clear();
        PARAM.put(a).put(b).put(c).put(d);
        PARAM.flip();
        return PARAM;
    }

    /**
     * Draws the room. The modelview is expected to already be in room space, which {@link VRGui}
     * arranges before calling.
     */
    public static void render() {
        GL11.glClearColor(0.718F, 0.824F, 1.0F, 1.0F);
        // The depth as well as the colour. This buffer last held a world, and its depth values
        // are still in it: without this the room is cut away in the silhouette of the terrain the
        // player was standing in when they quit to the menu.
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);

        Minecraft mc = Minecraft.getMinecraft();
        IIcon icon = Blocks.grass.getBlockTextureFromSide(1);
        mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture);

        GL11.glPushMatrix();

        // Flat ambient light. The room has no sun and nothing in it casts a shadow, so a
        // directional term would only make a perfectly level plane look dented.
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, param(0, 1, 0, 0));
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, param(0.1F, 0.1F, 0.1F, 0F));
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, param(0.9F, 0.9F, 0.9F, 1.0F));
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_SPECULAR, param(1.0F, 1.0F, 1.0F, 1.0F));
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, param(1.0F, 0, 0, 0));
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_LINEAR_ATTENUATION, param(0.0F, 0, 0, 0));
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_QUADRATIC_ATTENUATION, param(0.0F, 0, 0, 0));
        GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, param(0, 0, 0, 1.0F));
        GL11.glShadeModel(GL11.GL_SMOOTH);

        try {
            if (vbo == 0) {
                buildPlane(icon);
            }
            if (vbo != 0) {
                drawPlane();
            }
            drawPlayArea(mc);
        } finally {
            // Unconditionally, because the caller logs a failure here and carries on to the next
            // frame. A level left on the stack each frame fills it within a second, and from then
            // on no matrix anywhere in the game can be saved or restored.
            GL11.glPopMatrix();
            RenderHelper.disableStandardItemLighting();
        }
    }

    private static void buildPlane(IIcon icon) {
        final int color = BiomeGenBase.plains.getBiomeGrassColor(0, 0, 0);
        final byte r = (byte) ((color >> 16) & 0xFF);
        final byte g = (byte) ((color >> 8) & 0xFF);
        final byte b = (byte) (color & 0xFF);

        ByteBuffer buf = BufferUtils.createByteBuffer(SIZE * SIZE * 4 * 4 * STRIDE);
        for (int x = 0; x < SIZE * 2; x++) {
            for (int z = 0; z < SIZE * 2; z++) {
                vertex(buf, x - SIZE, z - SIZE, icon.getMinU(), icon.getMinV(), r, g, b);
                vertex(buf, x - SIZE, z + 1 - SIZE, icon.getMinU(), icon.getMaxV(), r, g, b);
                vertex(buf, x + 1 - SIZE, z + 1 - SIZE, icon.getMaxU(), icon.getMaxV(), r, g, b);
                vertex(buf, x + 1 - SIZE, z - SIZE, icon.getMaxU(), icon.getMinV(), r, g, b);
            }
        }
        buf.rewind();

        vbo = GL15.glGenBuffers();
        if (vbo == 0) {
            ViveCraftX.LOG.warn("Could not allocate the menu room's vertex buffer; "
                    + "the menu will have no floor");
            return;
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private static void vertex(ByteBuffer buf, float x, float z, float u, float v,
            byte r, byte g, byte b) {
        buf.putFloat(x).putFloat(0).putFloat(z);
        buf.putFloat(0).putFloat(1).putFloat(0);
        buf.putFloat(u).putFloat(v);
        buf.put(r).put(g).put(b);
    }

    private static void drawPlane() {
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, STRIDE, 0);
        GL11.glNormalPointer(GL11.GL_FLOAT, STRIDE, 12);
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, STRIDE, 24);
        GL11.glColorPointer(3, GL11.GL_UNSIGNED_BYTE, STRIDE, 32);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL11.glDrawArrays(GL11.GL_QUADS, 0, SIZE * SIZE * 4 * 4);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
    }

    /** The play area, a millimetre above the grass so the two do not fight over the same depth. */
    private static void drawPlayArea(Minecraft mc) {
        float[] area = MCOpenVR.getPlayAreaSize();
        if (area == null) {
            return;
        }
        float width = (float) Math.ceil(area[0]);
        float length = (float) Math.ceil(area[1]);

        mc.renderEngine.bindTexture(Gui.optionsBackground);
        GL11.glTranslatef(-width / 2, 0, -length / 2);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glNormal3f(0, 1, 0);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex3f(0, 0.001F, 0);
        GL11.glTexCoord2f(0, length);
        GL11.glVertex3f(0, 0.001F, length);
        GL11.glTexCoord2f(width, length);
        GL11.glVertex3f(width, 0.001F, length);
        GL11.glTexCoord2f(width, 0);
        GL11.glVertex3f(width, 0.001F, 0);
        GL11.glEnd();
    }

    /** Releases the plane, so a display change cannot leave a buffer behind. */
    public static void shutdown() {
        if (vbo != 0) {
            GL15.glDeleteBuffers(vbo);
            vbo = 0;
        }
    }
}
