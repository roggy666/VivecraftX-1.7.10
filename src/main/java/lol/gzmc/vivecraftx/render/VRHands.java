package lol.gzmc.vivecraftx.render;

import java.nio.FloatBuffer;

import com.mtbs3d.minecrift.provider.MCOpenVR;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.api.RenderPass;
import lol.gzmc.vivecraftx.VRSwing;
import lol.gzmc.vivecraftx.VRRenderState;
import lol.gzmc.vivecraftx.ViveCraftX;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockTorch;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;

import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.client.IItemRenderer.ItemRendererHelper;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemCarrotOnAStick;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemFlintAndSteel;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemShears;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.storage.MapData;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * The player's arms, drawn where the controllers actually are.
 *
 * <p>Without them the controllers are invisible, and a hand you cannot see is a hand you cannot
 * aim: there is nothing to tell you where your reach ends, and nothing for the wrist interface to
 * sit on.
 *
 * <p>These are the player's own biped arms wearing their own skin rather than a model of the
 * controller hardware. Vivecraft chose that and it is the right choice: it is the player's arm in
 * the world, it matches the arms everybody else sees on them, and it costs no extra assets.
 */
public final class VRHands {

    private VRHands() {}

    /**
     * Where the arm model sits relative to the controller.
     *
     * <p>The model's origin is the shoulder, and what the player is holding is the far end, so the
     * arm has to be pushed back along its own length for the wrist to land on the controller. Both
     * figures come out of the arm cube itself and are the same here as in modern Vivecraft, because
     * the cube has not changed: sideways, half the arm's width in from its outer edge,
     * (5 - 1 + 4 / 2) / 16; backwards, its whole length, (-2 + 2 + 12) / 16. The division by
     * sixteen is because the cube is drawn in sixteenths of a block.
     */
    private static final float ARM_SCALE = 0.4F;
    private static final float ARM_SIDE_OFFSET = -0.375F;
    private static final float ARM_BACK_OFFSET = 0.75F;

    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!VRState.running() || mc.theWorld == null || mc.thePlayer == null
                || VR.settings() == null) {
            return;
        }
        if (!VRCamera.isEyePass(VR.currentPass()) || VR.settings().seated) {
            // Seated play has no tracked hands to draw, and an arm hanging in mid-air where a
            // controller is not would be worse than nothing.
            return;
        }
        if (mc.thePlayer.isInvisible() || mc.gameSettings.thirdPersonView != 0) {
            return;
        }
        RenderPlayer renderer = playerRenderer(mc);
        if (renderer == null || renderer.modelBipedMain == null) {
            return;
        }

        // Not when the body is drawn: those are the same two arms, and the pair that hangs on
        // the controllers would sit inside the pair that hangs on the shoulders.
        if (!VRModelPose.inWorld()) {
            for (int controller = 0; controller < 2; controller++) {
                if (!MCOpenVR.isControllerTracking(controller)) {
                    continue;
                }
                renderArm(mc, renderer, controller, partialTicks);
            }
        }
        if (MCOpenVR.isControllerTracking(0)) {
            renderHeldItem(mc, partialTicks, 0, mc.thePlayer.getHeldItem());
        }
        // And the other hand, when something has given this version of the game a second one.
        if (MCOpenVR.isControllerTracking(1)) {
            ItemStack offhand = lol.gzmc.vivecraftx.compat.BackhandCompat.offhandItem(mc.thePlayer);
            if (offhand != null) {
                renderHeldItem(mc, partialTicks, 1, offhand);
            }
        }
    }

    /**
     * Replaces vanilla's first-person hand and overlays.
     *
     * <p>Vanilla's has to be cancelled rather than merely hidden, because the first thing it does
     * is replace the projection with a flat perspective sized to the window -- which flattens
     * whatever is drawn after it, and is exactly what makes a hand look stuck to a screen rather
     * than attached to an arm.
     *
     * <p>The overlays it also drew are kept, because losing them means no underwater tint, no
     * flames while on fire and no block face with your head inside one, each of which the player
     * needs in order to understand what is happening to them.
     */
    public static void renderHandsAndOverlays(float partialTicks) {
        // An arm and a hand are the wrong shape for typing: the thing that touches a key first
        // is a fist the size of three of them, and the point the keyboard actually tests is
        // buried somewhere inside it. So while the board is up the arms come off entirely and
        // each hand becomes one thin bar ending exactly at that point -- upstream makes the
        // same swap, in the same place in the frame, and for the same reason. Either, never
        // both: a finger growing out of a fist would be worse than either on its own.
        //
        // Only the arms are decided here. The board and the fingers over it are drawn later,
        // after the interface panel, because the panel is drawn with depth testing off whenever
        // hud occlusion is -- which it is by default, so that a block between the player and a
        // menu cannot make the menu unreachable. Drawn before it, the board was painted over by
        // a panel standing further away than itself.
        if (!VRKeyboard.isShowing()) {
            render(partialTicks);
        }
        // The crosshair belongs here too, for the same reason the hands do: this is the last point
        // in the frame where the scene's own framebuffer and depth buffer are still bound.
        VRWorldCrosshair.render(partialTicks);
        VRTeleportArc.render(partialTicks);
        VRBodyMarker.render();
        VRRadialMenu.render(partialTicks);
        renderFirstPersonOverlays(partialTicks);
        VRComfortVignette.render();
    }

    /**
     * The full-eye overlays: fire, suffocation, water.
     *
     * <p>Drawn with both matrices at identity, which puts the quads they build straight into clip
     * space. Their coordinates run from minus one to one, so they cover the eye exactly -- which is
     * what an overlay meant to surround you should do.
     */
    private static void renderFirstPersonOverlays(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.entityRenderer == null) {
            return;
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            mc.entityRenderer.itemRenderer.renderOverlays(partialTicks);
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("A first-person overlay failed to draw", t);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    /**
     * The item in a hand, at that hand's controller.
     *
     * <p>Three things act on it, in this order: where the controller is and which way it points,
     * then the pose for that kind of item, then the pose a model file would carry in a later
     * version. The last of those is the one that is easy to forget and the one that was wrong.
     */
    private static void renderHeldItem(Minecraft mc, float partialTicks, int controller,
            ItemStack held) {
        // An empty hand is the commonest case of all, and saving a block of state in order to
        // draw nothing with it is a cost paid for no reason, so this is settled before the save.
        if (held == null || held.getItem() == null) {
            return;
        }

        // The item is tinted layer by layer, and a layer that throws leaves the tint behind:
        // the catch below swallows it, so the rest of this eye is drawn in whatever colour the
        // last layer wanted. Handing the state block to the driver is the same answer the
        // overlays already use, and it holds on the paths that return early as well.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT);
        try {
            Vec3 pos = VRRenderState.getControllerRenderPos(controller);
            FloatBuffer rotation = VR.roomScale().getControllerMatrix_World(controller);
            if (pos == null || rotation == null) {
                return;
            }
            Vec3 origin = VRRenderState.interpolatedPlayerPos;
            float worldScale = VR.player().worldScale;

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            boolean prepared = false;
            try {
                if (!VRCamera.reapplyEyeCamera()) {
                    return;
                }
                GL11.glEnable(GL12.GL_RESCALE_NORMAL);
                // While the matrix is still the eye's. The two lights this sets up are positioned in
                // whatever matrix is current, so doing it after the controller's pose would pin them
                // to the controller and the item's lit and shadowed faces would swap as the wrist
                // turned.
                RenderHelper.enableStandardItemLighting();
                mc.entityRenderer.enableLightmap(partialTicks);
                prepared = true;
                setHandLight(mc);

                GL11.glTranslated(pos.xCoord - origin.xCoord, pos.yCoord - origin.yCoord,
                        pos.zCoord - origin.zCoord);
                rotation.rewind();
                GL11.glMultMatrix(rotation);
                // The world can be made larger or smaller around the player; the hand and what it
                // holds belong to the world, not to the player.
                GL11.glScalef(worldScale, worldScale, worldScale);

                applySwing(mc, controller, partialTicks);

                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                int drawnAs = drawnAs(held);
                applyItemTransform(held, drawnAs, controller);
                if (held.getItem() instanceof ItemMap) {
                    // A map is not drawn from a sprite, so there is no sprite pose to undo.
                    drawMap(mc, held);
                } else {
                    applyDisplayTransform(drawnAs, isRodLike(held.getItem()));
                    if (drawnAs == DRAWN_AS_ICON) {
                        // Turned inside out by the mirror above: every face now winds the other way,
                        // and the side the game would keep is the side facing away. Drawing both
                        // costs a handful of triangles on something the size of a sword and is
                        // steadier than telling the driver which way round front has become.
                        //
                        // Nothing is read back or put back by hand: the push at the top of this
                        // method already saved the enable bits, face culling among them, and the
                        // pop at the bottom restores them whichever way this exits. Asking the
                        // driver what the flag was is a round trip to answer a question already
                        // answered.
                        GL11.glDisable(GL11.GL_CULL_FACE);
                    }
                    drawItem(mc, held);
                }
            } catch (Throwable t) {
                ViveCraftX.LOG.debug("The held item failed to draw", t);
            } finally {
                if (prepared) {
                    mc.entityRenderer.disableLightmap(partialTicks);
                    RenderHelper.disableStandardItemLighting();
                    GL11.glDisable(GL12.GL_RESCALE_NORMAL);
                }
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * Hands the item to the game to draw.
     *
     * <p>Some items are drawn more than once, one layer over another, and the layers after the
     * first are tinted: that is how leather armour is dyed, how a potion's contents are coloured
     * and how a spawn egg gets its two tones. Drawing only the first layer leaves all of those
     * grey, so the loop is the same one vanilla's own first-person pass uses.
     */
    private static void drawItem(Minecraft mc, ItemStack held) {
        Item item = held.getItem();
        // The first layer is tinted too, and forgetting it is why a potion came out grey: the
        // liquid is the first layer and its colour is the whole of what the bottle says.
        tint(item.getColorFromItemStack(held, 0));
        mc.entityRenderer.itemRenderer.renderItem(mc.thePlayer, held, 0);
        if (item.requiresMultipleRenderPasses()) {
            for (int pass = 1; pass < item.getRenderPasses(held.getItemDamage()); pass++) {
                tint(item.getColorFromItemStack(held, pass));
                mc.entityRenderer.itemRenderer.renderItem(mc.thePlayer, held, pass);
            }
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void tint(int colour) {
        GL11.glColor4f((colour >> 16 & 255) / 255.0F, (colour >> 8 & 255) / 255.0F,
                (colour & 255) / 255.0F, 1.0F);
    }

    private static final ResourceLocation MAP_BACKGROUND =
            new ResourceLocation("textures/map/map_background.png");

    /**
     * The map itself, rather than the blank sheet its sprite shows.
     *
     * <p>A map is the one held thing that is not its own icon: what is on it is drawn on top, by
     * the game, every frame. Vanilla does that from inside a pose built for a hand held up in
     * front of a face, and that pose is not ours -- so only the part of it that describes the
     * map is kept. A quarter turn in it is dropped for the same reason: it was cancelling a
     * quarter turn in the half that has gone.
     *
     * <p>The sheet comes out a block across and centred on the hand, which is the size later
     * versions settled on and the size the figures in the pose above were chosen against.
     */
    private static void drawMap(Minecraft mc, ItemStack held) {
        // Everything this turns off, it turns off for the rest of the eye unless it is put
        // back. Lighting is the one that shows: a later item drawn correctly comes out
        // unshaded, and reads as flat. Handing the whole block of state to the driver to
        // remember is both shorter than undoing each piece and proof against an exception
        // part way through, which is how it escaped in the first place.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT);
        try {
            GL11.glScalef(0.38F, 0.38F, 0.38F);
            GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);
            GL11.glScalef(1F / 128F, 1F / 128F, 1F / 128F);
            GL11.glTranslatef(-64.0F, -64.0F, 0.0F);

            GL11.glDisable(GL11.GL_LIGHTING);
            mc.getTextureManager().bindTexture(MAP_BACKGROUND);
            Tessellator tessellator = Tessellator.instance;
            GL11.glNormal3f(0.0F, 0.0F, -1.0F);
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(-7.0D, 135.0D, 0.0D, 0.0D, 1.0D);
            tessellator.addVertexWithUV(135.0D, 135.0D, 0.0D, 1.0D, 1.0D);
            tessellator.addVertexWithUV(135.0D, -7.0D, 0.0D, 1.0D, 0.0D);
            tessellator.addVertexWithUV(-7.0D, -7.0D, 0.0D, 0.0D, 0.0D);
            tessellator.draw();

            MapData data = ((ItemMap) held.getItem()).getMapData(held, mc.theWorld);
            IItemRenderer custom = MinecraftForgeClient.getItemRenderer(held,
                    ItemRenderType.FIRST_PERSON_MAP);
            if (custom != null) {
                custom.renderItem(ItemRenderType.FIRST_PERSON_MAP, held, mc.thePlayer,
                        mc.getTextureManager(), data);
            } else if (data != null) {
                mc.entityRenderer.getMapItemRenderer().func_148250_a(data, false);
            }
            GL11.glEnable(GL11.GL_LIGHTING);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * Whether the bow is being held with both hands rather than simply carried.
     *
     * <p>Two quite different things to look at, and only one of them is a bow being drawn. Seated
     * play has one hand and nothing to pull against, so the bow is a held object and is posed
     * like one.
     */
    private static boolean roomScaleBow(EntityPlayerSP player) {
        return VR.settings() != null && !VR.settings().seated && VR.bowTracker() != null
                && VR.bowTracker().isActive(player);
    }

    /**
     * The arms, in whichever of the two shapes is asked for.
     *
     * <p>One piece on the controller is the default, and is what the modern mod defaults to as
     * well: its split-arm model is a mode you turn on, not the way it arrives. One piece is also
     * the shape everything else was built against -- the hotbar lies along that forearm, and a
     * skin mod that re-cuts the arm texture has only that one box to re-cut.
     */
    private static void renderArm(Minecraft mc, RenderPlayer renderer, int controller,
            float partialTicks) {
        if (VR.settings() != null && VR.settings().splitArms) {
            renderJointedArm(mc, renderer, controller, partialTicks);
        } else {
            renderJointlessArm(mc, renderer, controller, partialTicks);
        }
    }

    private static void renderJointedArm(Minecraft mc, RenderPlayer renderer, int controller,
            float partialTicks) {
        // The render-time position, which places the controller against the same interpolated
        // play area origin the camera uses. The tick-time position is anchored to the raw origin,
        // which only moves once a tick -- so during anything that moves you quickly, flying or
        // jumping above all, the hands would step while the view glided and the result is hands
        // that shake violently in front of you.
        Vec3 pos = VRRenderState.getControllerRenderPos(controller);
        Vec3 shoulder = shoulderOf(controller);
        if (pos == null || shoulder == null) {
            return;
        }
        Vec3 origin = VRRenderState.interpolatedPlayerPos;
        float worldScale = VR.player().worldScale;

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        boolean prepared = false;
        try {
            if (!VRCamera.reapplyEyeCamera()) {
                return;
            }
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableStandardItemLighting();
            mc.entityRenderer.enableLightmap(partialTicks);
            prepared = true;
            setHandLight(mc);

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(
                    ((AbstractClientPlayer) mc.thePlayer).getLocationSkin());

            // Built between two points rather than hung off the controller: from where the
            // shoulder would be to where the hand is, bending at the elbow in between. Both
            // points are taken for this frame rather than this tick, which is the difference
            // between an arm and an arm that shakes with every step.
            freeArm(renderer, controller)
                    .draw(shoulder, swung(mc, controller, partialTicks, pos, worldScale), origin,
                            worldScale);
        } finally {
            if (prepared) {
                mc.entityRenderer.disableLightmap(partialTicks);
                RenderHelper.disableStandardItemLighting();
                GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }
    private static void renderJointlessArm(Minecraft mc, RenderPlayer renderer, int controller,
            float partialTicks) {
        // The render-time position, which places the controller against the same interpolated
        // play area origin the camera uses. The tick-time position is anchored to the raw origin,
        // which only moves once a tick -- so during anything that moves you quickly, flying or
        // jumping above all, the hands would step while the view glided and the result is hands
        // that shake violently in front of you.
        Vec3 pos = VRRenderState.getControllerRenderPos(controller);
        FloatBuffer rotation = VR.roomScale().getControllerMatrix_World(controller);
        if (pos == null || rotation == null) {
            return;
        }
        Vec3 origin = VRRenderState.interpolatedPlayerPos;
        float worldScale = VR.player().worldScale;

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        boolean prepared = false;
        try {
            if (!VRCamera.reapplyEyeCamera()) {
                return;
            }
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableStandardItemLighting();
            mc.entityRenderer.enableLightmap(partialTicks);
            prepared = true;
            setHandLight(mc);

            // Same origin as everything else in the world, so the arm lands exactly on the
            // controller rather than a fraction of a block from it.
            GL11.glTranslated(pos.xCoord - origin.xCoord, pos.yCoord - origin.yCoord,
                    pos.zCoord - origin.zCoord);
            rotation.rewind();
            GL11.glMultMatrix(rotation);
            GL11.glScalef(worldScale, worldScale, worldScale);

            // Before the arm is placed, so that the swing turns the whole arm about the wrist
            // rather than bending it. The item gets the same transform at the same point, which
            // is what keeps the two moving as one piece.
            applySwing(mc, controller, partialTicks);

            // Scale first, then step back along the controller's own axis, so that the wrist ends
            // up on the controller and the rest of the arm runs back toward the elbow.
            GL11.glScalef(ARM_SCALE, ARM_SCALE, ARM_SCALE);
            GL11.glTranslatef(ARM_SIDE_OFFSET * (controller == 0 ? 1F : -1F), 0F,
                    ARM_BACK_OFFSET);
            GL11.glRotatef(-90F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(180F, 0.0F, 1.0F, 0.0F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            mc.getTextureManager().bindTexture(
                    ((AbstractClientPlayer) mc.thePlayer).getLocationSkin());

            ModelRenderer arm = controller == 0 ? renderer.modelBipedMain.bipedRightArm
                                                : renderer.modelBipedMain.bipedLeftArm;
            // The shoulder pivot the two offsets above are measured from. They say "half the
            // arm's width in from its outer edge, and its whole length back from the shoulder",
            // and the edge and the shoulder are only where this says they are. Vanilla's own
            // posing restores these every frame for the third-person model, so they are set
            // rather than assumed.
            arm.rotationPointX = controller == 0 ? -5.0F : 5.0F;
            arm.rotationPointY = 2.0F;
            arm.rotationPointZ = 0.0F;
            // Whatever the entity renderer last left here would show as an arm bent at a random
            // angle. Vanilla's idle sway alone is almost six degrees.
            arm.rotateAngleX = 0;
            arm.rotateAngleY = 0;
            arm.rotateAngleZ = 0;
            // These are added outside the pivot and, unlike it, are not divided by sixteen, so a
            // stray value here displaces the arm sixteen times as far as the same number above.
            arm.offsetX = 0;
            arm.offsetY = 0;
            arm.offsetZ = 0;
            // Must stay sixteenths: the cube's vertices are baked into a display list the first
            // time a scale is passed, and the pivot is scaled on every draw. Pass anything else
            // later and the two no longer agree.
            arm.render(0.0625F);
        } finally {
            if (prepared) {
                mc.entityRenderer.disableLightmap(partialTicks);
                RenderHelper.disableStandardItemLighting();
                GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    /**
     * How brightly lit the hands are.
     *
     * <p>Switching the lightmap on only turns its texture unit on. Which texel of it a vertex
     * reads comes from a single coordinate that is global and stays where it was put until
     * something puts it somewhere else -- and vanilla's own first-person pass used to set it,
     * so when that pass went, the setting went with it. The arms have been reading whatever the
     * frame last left behind ever since: a burning mob, an enderman's eyes, an explosion, or an
     * open container all leave it at the brightest texel there is. Each of those comes and goes
     * with where you are looking, which is exactly why the hands glowed only sometimes, and
     * sometimes kept glowing.
     *
     * <p>Sampled once, at the head, for both hands and both items. Modern Vivecraft does the
     * same, and the reason is that it is steady: a per-hand sample makes the arms flash as they
     * pass a torch or dip into a block, which is a different kind of flicker rather than none.
     *
     * <p>No floor under it. A floor sounds kind -- hands that never quite vanish in the dark --
     * but it lights them by a rule nothing else in the world obeys, and the result is two
     * objects that plainly do not belong to the scene they are in. Modern Vivecraft floors the
     * light of the grey menu hand and nothing else.
     */
    private static void setHandLight(Minecraft mc) {
        Vec3 head = VR.roomScale().getHMDPos_World();
        double x = head != null ? head.xCoord : mc.thePlayer.posX;
        double y = head != null ? head.yCoord : mc.thePlayer.posY;
        double z = head != null ? head.zCoord : mc.thePlayer.posZ;
        int brightness = mc.theWorld.getLightBrightnessForSkyBlocks(MathHelper.floor_double(x),
                MathHelper.floor_double(y), MathHelper.floor_double(z), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightness % 65536,
                brightness / 65536);
    }

    private static final VRFreeArm[] FREE_ARMS = new VRFreeArm[2];
    private static RenderPlayer freeArmOwner;

    private static VRFreeArm freeArm(RenderPlayer renderer, int controller) {
        if (freeArmOwner != renderer || FREE_ARMS[controller] == null) {
            freeArmOwner = renderer;
            FREE_ARMS[0] = new VRFreeArm(renderer.modelBipedMain, false);
            FREE_ARMS[1] = new VRFreeArm(renderer.modelBipedMain, true);
        }
        return FREE_ARMS[controller];
    }

    /** How far below the headset a shoulder sits, and how far out to either side of it. */
    private static final double SHOULDER_DROP = 0.25D;
    private static final double SHOULDER_SIDE = 0.18D;

    /**
     * Where this arm hangs from.
     *
     * <p>There is no body to ask, so it is worked out from the headset: a little below it and a
     * little to the side, on the axis the player is facing. Taken for this frame, like the hand,
     * because an arm measured between two different moments shakes once per tick.
     */
    private static Vec3 shoulderOf(int controller) {
        Vec3 head = VRRenderState.getEyeRenderPos(RenderPass.Center);
        Vec3 look = VR.roomScale().getHMDDir_World();
        if (head == null || look == null) {
            return null;
        }
        double flat = Math.sqrt(look.xCoord * look.xCoord + look.zCoord * look.zCoord);
        double rx = 0D;
        double rz = 0D;
        if (flat > 1.0E-4D) {
            // The player's own right: the way they are facing, turned a quarter turn.
            rx = -look.zCoord / flat;
            rz = look.xCoord / flat;
        }
        boolean reversed = VR.settings() != null && VR.settings().vrReverseHands;
        double side = (controller == 0) == !reversed ? SHOULDER_SIDE : -SHOULDER_SIDE;
        return Vec3.createVectorHelper(head.xCoord + rx * side, head.yCoord - SHOULDER_DROP,
                head.zCoord + rz * side);
    }

    /**
     * Where the hand is once the swing has moved it.
     *
     * <p>The item is swung by turning the frame it is drawn in, which works because the item is
     * one rigid thing hanging off the controller. An arm built between two points cannot be
     * turned that way -- turning it about the controller would leave the shoulder behind. So the
     * same motion is applied to the one end that is supposed to move, and the arm follows it:
     * the hand goes where the item goes, the shoulder stays where the shoulder is, and the elbow
     * does whatever it has to.
     *
     * <p>The press is the exception and gets nothing. It is a roll of the wrist about the
     * controller's own axis, which moves no point on that axis at all; on the item it shows
     * because the item is off to one side of it, and on a square arm there would be nothing to
     * see even if it did.
     */
    private static Vec3 swung(Minecraft mc, int controller, float partialTicks, Vec3 hand,
            float worldScale) {
        float progress = controller == 0
                ? mc.thePlayer.getSwingProgress(partialTicks)
                : lol.gzmc.vivecraftx.compat.BackhandCompat.offhandSwing(mc.thePlayer, partialTicks);
        if (progress <= 0.0F) {
            return hand;
        }
        double dy = 0D;
        double dz = 0D;
        switch (VRSwing.kind(controller)) {
            case ATTACK: {
                // Where the pivot a fifth of a metre ahead of the controller puts this end.
                double turn = Math.toRadians(swingCurve(progress) * 30.0F);
                dy = 0.2D * Math.sin(turn);
                dz = 0.2D * (1D - Math.cos(turn));
                break;
            }
            case USE: {
                float reach = progress > 0.25F
                        ? MathHelper.sin(progress / 2.0F * (float) Math.PI + (float) Math.PI)
                        : MathHelper.sin(progress * 2.0F * (float) Math.PI);
                dz = -(1.0F + reach) * 0.1F;
                break;
            }
            default:
                return hand;
        }
        FloatBuffer m = VR.roomScale().getControllerMatrix_World(controller);
        if (m == null) {
            return hand;
        }
        m.rewind();
        // Turning a direction given in the controller's own frame into the world's. The buffer
        // is laid out the way the graphics library wants it, a column at a time.
        double wx = m.get(4) * dy + m.get(8) * dz;
        double wy = m.get(5) * dy + m.get(9) * dz;
        double wz = m.get(6) * dy + m.get(10) * dz;
        m.rewind();
        return Vec3.createVectorHelper(hand.xCoord + wx * worldScale,
                hand.yCoord + wy * worldScale, hand.zCoord + wz * worldScale);
    }

    /**
     * The motion of a swing, on whatever is about to be drawn at that hand.
     *
     * <p>Three motions, because a hand swings for three reasons and they do not look alike. The
     * chop pitches about a point a fifth of a metre ahead of the controller rather than about the
     * controller itself, which is the difference between a wrist flicking and a whole item
     * pivoting in mid-air. The push simply moves the hand away and back. The press is a roll of
     * the wrist, and it is the one that goes the other way on the other hand.
     *
     * <p>Applied before anything else, on the bare controller frame, so the hand and what it
     * holds move together. A swing that is not happening costs nothing: the progress is zero and
     * nothing is emitted.
     *
     * <p>Note what does not reach here. A swing made by physically swinging the arm never calls
     * for one of these -- the real arm has already moved, and an animation on top of it would be
     * the same motion played twice. Modern Vivecraft arranges that by going straight to the
     * controller layer from its swing tracker, never through the game's own swing, and so do we.
     */
    private static void applySwing(Minecraft mc, int controller, float partialTicks) {
        float progress = controller == 0
                ? mc.thePlayer.getSwingProgress(partialTicks)
                : lol.gzmc.vivecraftx.compat.BackhandCompat.offhandSwing(mc.thePlayer, partialTicks);
        if (progress <= 0.0F) {
            return;
        }
        switch (VRSwing.kind(controller)) {
            case ATTACK: {
                float turn = swingCurve(progress);
                GL11.glTranslatef(0.0F, 0.0F, 0.2F);
                GL11.glRotatef(turn * 30.0F, 1.0F, 0.0F, 0.0F);
                GL11.glTranslatef(0.0F, 0.0F, -0.2F);
                break;
            }
            case INTERACT: {
                float turn = swingCurve(progress);
                GL11.glRotatef((controller == 0 ? -1.0F : 1.0F) * turn * 45.0F, 0.0F, 0.0F, 1.0F);
                break;
            }
            case USE: {
                float reach = progress > 0.25F
                        ? MathHelper.sin(progress / 2.0F * (float) Math.PI + (float) Math.PI)
                        : MathHelper.sin(progress * 2.0F * (float) Math.PI);
                GL11.glTranslatef(0.0F, 0.0F, -(1.0F + reach) * 0.1F);
                break;
            }
            default:
                break;
        }
    }

    /**
     * Out fast, back slow.
     *
     * <p>The first half of the swing is run at three times the rate, so the strike lands early
     * and the arm spends the rest of the time returning. An even sine would read as a wave.
     */
    private static float swingCurve(float progress) {
        return progress > 0.5F
                ? MathHelper.sin(progress * (float) Math.PI + (float) Math.PI)
                : MathHelper.sin(progress * 3.0F * (float) Math.PI);
    }

    /** A cube, left a quarter turn round by the drawing. */
    private static final int DRAWN_AS_TURNED_BLOCK = 0;
    /** A cube, left facing the way it was handed over. */
    private static final int DRAWN_AS_BLOCK = 1;
    /** A flat sprite given a thickness. */
    private static final int DRAWN_AS_ICON = 2;

    /**
     * The block shapes whose drawing turns them a quarter turn before it starts.
     *
     * <p>Not a property of being a block: the plain cube does it, and so does the chest, but
     * stairs, fences, walls, gates, the cactus, the anvil, the beacon and the dragon egg do not.
     * Undoing a turn that was never made is how every one of those ended up facing sideways.
     */
    private static boolean leavesTurned(int renderType) {
        return renderType == 0 || renderType == 16 || renderType == 22 || renderType == 26
                || renderType == 31 || renderType == 39;
    }

    /**
     * Which of the three ways the game is about to draw this item.
     *
     * <p>Asked separately from where the item should go, because the two answers are needed at
     * different points and the second depends on the first: the placement sits on top of whatever
     * the drawing already does, and the drawing is not the same for a cube as for a flat icon.
     *
     * <p>An item another mod draws is not a fourth case. Whatever that mod puts on the screen,
     * the frame it is handed is one of two that Forge sets up for it, and which one it asks for
     * itself: a cube's, already centred, or the same flat sprite frame everything else gets. So
     * it is answered the same way, and the item ends up in the hand rather than wherever it
     * happened to land.
     */
    private static int drawnAs(ItemStack held) {
        IItemRenderer custom = MinecraftForgeClient.getItemRenderer(held, ItemRenderType.EQUIPPED);
        if (custom != null) {
            return custom.shouldUseRenderHelper(ItemRenderType.EQUIPPED, held,
                    ItemRendererHelper.EQUIPPED_BLOCK) ? DRAWN_AS_BLOCK : DRAWN_AS_ICON;
        }
        Item item = held.getItem();
        Block block = Block.getBlockFromItem(item);
        if (held.getItemSpriteNumber() == 0 && item instanceof ItemBlock && block != null
                && RenderBlocks.renderItemIn3d(block.getRenderType())) {
            return leavesTurned(block.getRenderType()) ? DRAWN_AS_TURNED_BLOCK : DRAWN_AS_BLOCK;
        }
        return DRAWN_AS_ICON;
    }

    /** Whether this is one of the two things a later version hangs off the end of the fist. */
    private static boolean isRodLike(Item item) {
        return item instanceof ItemFishingRod || item instanceof ItemCarrotOnAStick;
    }

    /**
     * Gives the item the pose a model file would have given it in a later version.
     *
     * <p>This is the piece that was missing, and it is why held items were both too large and
     * turned the wrong way. From 1.8 onward an item model carries its own first-person pose, and
     * every figure modern Vivecraft uses was tuned sitting on top of that pose. This version has
     * no model files; instead each of the two drawing paths has one pose baked into it, the same
     * for every item, chosen to look right in a flat game over the shoulder. Those two are not
     * the same thing, and the difference is most of a right angle and half again the size.
     *
     * <p>So this undoes the baked pose and puts the later one in its place. It can be done exactly
     * rather than approximately: every call the drawing makes is a multiplication on the right, so
     * emitting the reverse of them here, before the drawing runs, cancels them term by term. What
     * reaches the model is then the later pose and nothing else.
     *
     * <p>The reseating in the middle is because the two describe the model differently. A later
     * flat model is a thin slab standing in the middle of its own block; this version's is a slab
     * lying against the front of it. Half a block in each direction, and a sixteenth back, puts
     * one where the other would be.
     *
     * <p>Modern Vivecraft asks for the right hand's pose for both hands, so there is no mirroring
     * to do here -- a held item is not handed.
     */
    private static void applyDisplayTransform(int drawnAs, boolean rodLike) {
        if (drawnAs != DRAWN_AS_ICON) {
            // A cube, drawn full size and already centred. Only the pose is wanted: turned to show
            // a corner rather than a face, at a little under half size.
            GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
            GL11.glScalef(0.40F, 0.40F, 0.40F);
            if (drawnAs == DRAWN_AS_TURNED_BLOCK) {
                // This shape's own drawing turns it a quarter turn before centring it. That
                // choice of which face is forward is this version's; the later one has none.
                GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
            }
            return;
        }

        // A flat icon. The later pose:
        if (rodLike) {
            // A rod is held by the end rather than across the palm, and a later version says so
            // in a model of its own: the same tilt, turned to face the other way, and sitting
            // further up and forward. Without this the line would run back out of the wrist.
            GL11.glTranslatef(0.0F, 1.6F / 16F, 0.8F / 16F);
            GL11.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
        } else {
            GL11.glTranslatef(1.13F / 16F, 3.2F / 16F, 1.13F / 16F);
            GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
        }
        GL11.glRotatef(25.0F, 0.0F, 0.0F, 1.0F);
        GL11.glScalef(0.68F, 0.68F, 0.68F);
        // This version lays the sprite on its square the other way round: its first corner takes
        // the picture's bottom right, not its bottom left. So the picture runs across the square
        // along the opposite diagonal -- and a sword's blade, which is drawn along that diagonal,
        // comes out a quarter turn from where the pose above expects to find it. Turning it would
        // line the blade up and leave the picture back to front; this puts both right at once.
        GL11.glScalef(-1.0F, 1.0F, 1.0F);
        // the slab moved from lying against the front of its block to standing in the middle
        GL11.glTranslatef(-0.5F, -0.5F, 1F / 32F);
        // and the baked pose undone, each term reversed and in reverse order
        GL11.glTranslatef(0.9375F, 0.0625F, 0.0F);
        GL11.glRotatef(-335.0F, 0.0F, 0.0F, 1.0F);
        GL11.glRotatef(-50.0F, 0.0F, 1.0F, 0.0F);
        GL11.glScalef(1F / 1.5F, 1F / 1.5F, 1F / 1.5F);
        GL11.glTranslatef(0.0F, 0.3F, 0.0F);
    }

    /**
     * A turn, or a few turns, about the model's own axes.
     *
     * <p>Modern Vivecraft builds these as quaternions and multiplies them together. A fixed set of
     * three angles cannot stand in for that: some items need two turns about the same axis in a
     * particular order, and one needs its turn applied before the move rather than after. So the
     * turns are kept in the order they were asked for and emitted in that order, which is what the
     * multiplication amounts to.
     */
    private static final class Turns {

        private final char[] axis = new char[6];
        private final float[] degrees = new float[6];
        private int count;

        /** Discards what is there and starts again, as assigning a fresh quaternion does. */
        Turns set(char a, float d) {
            this.count = 0;
            return then(a, d);
        }

        Turns identity() {
            this.count = 0;
            return this;
        }

        /** Adds a turn after the ones already asked for. */
        Turns then(char a, float d) {
            this.axis[this.count] = a;
            this.degrees[this.count] = d;
            this.count++;
            return this;
        }

        void copyFrom(Turns other) {
            System.arraycopy(other.axis, 0, this.axis, 0, other.count);
            System.arraycopy(other.degrees, 0, this.degrees, 0, other.count);
            this.count = other.count;
        }

        void emit() {
            for (int i = 0; i < this.count; i++) {
                char a = this.axis[i];
                GL11.glRotatef(this.degrees[i], a == 'x' ? 1F : 0F, a == 'y' ? 1F : 0F,
                        a == 'z' ? 1F : 0F);
            }
        }
    }

    /**
     * Turns the item to sit in the hand the way that kind of item should.
     *
     * <p>There is no one pose that works, because a model points along its own axis and which axis
     * that is depends on what it is: a sword extends from the fist, a torch stands up out of it, a
     * block sits in the palm. These groupings and figures are modern Vivecraft's, taken as they
     * are -- numbers tuned by eye against particular models cannot be rederived, only copied.
     *
     * <p>The one figure computed rather than copied is the tilt. Everything hangs off the frame of
     * the controller's tip, and the tip is where the controller points rather than where the hand
     * does, so a held thing has to be turned back onto the axis of the fist by however far those
     * two differ on this particular controller. Several of the groups below then slide the item
     * along in proportion to that same angle, because a bent grip holds a long object further
     * forward.
     */
    private static void applyItemTransform(ItemStack held, int drawnAs, int controller) {
        Item item = held.getItem();
        float gunAngle = MCOpenVR.getGunAngle();
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;

        float scale = 0.7F;
        float translateX = -0.05F;
        float translateY = 0.005F;
        float translateZ = 0.0F;
        Turns rot = new Turns().set('x', -110.0F + gunAngle);
        Turns preRot = new Turns();

        Block block = Block.getBlockFromItem(item);
        boolean eating = held.getItemUseAction() == EnumAction.eat
                || held.getItemUseAction() == EnumAction.drink;
        boolean rodLike = isRodLike(item);
        boolean blocking = held.getItemUseAction() == EnumAction.block
                && player.getItemInUseCount() > 0 && player.getItemInUse() == held;
        boolean toolLike = item instanceof ItemTool || item instanceof ItemHoe
                || item instanceof ItemShears || item instanceof ItemFlintAndSteel
                // Modern Vivecraft counts a plain shaft as a tool, and holding a stick like a
                // wand rather than flat in the palm is the visible half of that.
                || item == Items.stick || item == Items.bone || item == Items.blaze_rod;

        if (eating) {
            // raised to the mouth, so it is turned to face you
            rot.set('z', 180.0F).then('x', -135.0F);
            translateX += 0.08F;
            // Chewing. The game counts the use down in ticks, so the figure moves while eating and
            // stands still otherwise.
            translateZ += 0.02F + 0.006F * MathHelper.sin((float) player.getItemInUseCount());
            scale = 0.4F;
        } else if (block instanceof BlockTorch) {
            rot.set('x', -45.0F + gunAngle);
            translateY += -0.105F + 0.06F * gunAngle / 40.0F;
            translateZ -= 0.1F;
        } else if (drawnAs != DRAWN_AS_ICON) {
            translateX += 0.05F;
            translateZ -= 0.1F;
            rot.then('x', 90.0F - gunAngle);
            scale = 0.3F;
        } else if (item instanceof ItemMap) {
            rot.set('x', -45.0F);
            translateX = 0.0F;
            translateY = 0.16F;
            translateZ = -0.075F;
            scale = 0.75F;
        } else if (item instanceof ItemBow) {
            if (roomScaleBow(player)) {
                // Nocked, with both hands on it. The string is where the other hand is, so the
                // bow belongs on the line between them rather than on either wrist: it is held
                // out in front, turned out of the fist, and drawn at its own size.
                preRot.copyFrom(rot);
                rot.identity();
                translateX -= 0.0225F;
                translateY -= 0.25F;
                translateZ += 0.025F + 0.03F * gunAngle / 40.0F;
                scale = 1.0F;
            } else {
                // One hand on it and nothing to pull against -- seated play, or the room-scale
                // bow switched off. Then it is simply a thing being held, and the pose that
                // suits a thing being held is nearly the one everything else gets: the long
                // axis turned back level with the fist rather than hanging below it.
                rot.then('x', 90.0F - gunAngle);
                translateY -= 0.1F;
                translateZ += 0.1F;
            }
        } else if (item == Items.compass || item == Items.clock) {
            rot.set('y', 90.0F).then('x', 25.0F);
            scale = 0.4F;
        } else if (rodLike) {
            rot.then('x', 40.0F);
            translateY += -0.02F + gunAngle / 40.0F * 0.1F;
            translateX += 0.05F;
            translateZ -= 0.15F;
            scale = 0.8F;
        } else if (item == Items.arrow) {
            preRot.set('z', -180.0F);
            rot.then('x', -gunAngle);
        } else if (item instanceof ItemSword || toolLike) {
            // the defaults untouched: a sword and a pickaxe both point out of the fist
            scale = 0.7F;
            if (blocking) {
                // Raised flat across the body. A later version hands this job to a shield and
                // poses it the same way: turned broadside to whatever is in front, and a little
                // larger, because the whole point of the gesture is to put something between.
                rot.then('y', controller == 0 ? 90.0F : -90.0F);
                scale *= 1.2F;
            }
        } else {
            rot.set('z', 180.0F).then('x', -135.0F);
            translateX += 0.08F;
            translateZ += -0.08F;
            scale = 0.4F;
        }

        preRot.emit();
        GL11.glTranslatef(translateX, translateY, translateZ);
        rot.emit();
        GL11.glScalef(scale, scale, scale);
    }

    private static RenderPlayer playerRenderer(Minecraft mc) {
        Object renderer = RenderManager.instance.getEntityRenderObject(mc.thePlayer);
        return renderer instanceof RenderPlayer ? (RenderPlayer) renderer : null;
    }
}
