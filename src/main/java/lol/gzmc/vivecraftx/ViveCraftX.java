package lol.gzmc.vivecraftx;

import lol.gzmc.vivecraftx.api.IKeyBindingExt;
import lol.gzmc.vivecraftx.api.IPlayerItemInUseExt;
import lol.gzmc.vivecraftx.compat.AngelicaCompat;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * VR for Minecraft 1.7.10, rebuilt as an ordinary Forge mod.
 *
 * <p>The original Vivecraft shipped as a patch set applied to decompiled MCP sources, which
 * forced it to replace vanilla classes wholesale and to reach Forge reflectively through
 * OptiFine's {@code Reflector}. As a mod we compile against Forge directly and reach vanilla
 * through mixins instead, which is what lets us coexist with other mods that touch rendering.
 *
 * <p>The inherited Vivecraft code lives under {@code com.mtbs3d.minecrift} and is called from
 * {@code lol.gzmc.vivecraftx}, which is the mod proper: the rendering, the frame timing, the
 * networking, and the glue that takes the place of the fields the patch set added to vanilla.
 */
@Mod(
        modid = ViveCraftX.MODID,
        name = "ViveCraftX",
        version = ViveCraftX.VERSION,
        acceptedMinecraftVersions = "[1.7.10]",
        // Both optional. Load after them so their own setup is done before ours looks for it.
        dependencies = "after:angelica;after:backhand",
        // Purely client side: the server neither has nor needs it, so do not gate joining.
        acceptableRemoteVersions = "*"
)
public class ViveCraftX {

    public static final String MODID = "vivecraftx";
    public static final String VERSION = "0.1.0";

    public static final Logger LOG = LogManager.getLogger("ViveCraftX");

    /**
     * Runs on a server as well as a client, which only one thing here needs.
     *
     * <p>Players in headsets cannot show each other their hands without something in the middle
     * to pass them on, because clients cannot talk to each other directly. So the channel is
     * opened on whichever side this is: on a client to send and to listen, on a server to relay
     * to whoever is close enough to see. A server without this mod never relays, and players
     * there see each other exactly as they did before -- nothing breaks, the hands are just not
     * there. Everything else in the mod is still client only.
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("ViveCraftX {} starting", VERSION);
        lol.gzmc.vivecraftx.net.VRNetwork.init();
        lol.gzmc.vivecraftx.net.VRServerConfig.load(event.getSuggestedConfigurationFile());
        FMLCommonHandler.instance().bus().register(
                new lol.gzmc.vivecraftx.net.VRServerTeleports.Ticker());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new lol.gzmc.vivecraftx.net.VRServerGreeting());
    }

    @Mod.EventHandler
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        LOG.info("Angelica {}", AngelicaCompat.isPresent() ? "detected - compat paths active"
                                                           : "absent - running standalone");
        checkMixins();
        // One handler, both buses: tick events come down the FML bus and the screen-drawing
        // events down Forge's.
        ClientEvents events = new ClientEvents();
        FMLCommonHandler.instance().bus().register(events);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(events);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new VRToggleButton());
    }

    /**
     * Confirms the mixins actually applied.
     *
     * <p>A config that fails to load is fatal because it is marked required, but a mixin that
     * loads and then quietly fails to apply is not, and the first symptom would be a
     * ClassCastException somewhere far away. Checking the interfaces here turns that into one
     * clear line at startup.
     */
    private static void checkMixins() {
        boolean keyBinding = IKeyBindingExt.class.isAssignableFrom(KeyBinding.class);
        boolean player = IPlayerItemInUseExt.class.isAssignableFrom(EntityPlayer.class);
        if (keyBinding && player) {
            LOG.info("Mixins applied");
        } else {
            LOG.error("Mixins did NOT apply (KeyBinding={}, EntityPlayer={}) - VR input and item "
                    + "use will not work", keyBinding, player);
        }
    }
}
