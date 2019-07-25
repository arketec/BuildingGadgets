package com.direwolf20.buildinggadgets.common;

import com.direwolf20.buildinggadgets.api.BuildingGadgetsAPI;
import com.direwolf20.buildinggadgets.client.ClientProxy;
import com.direwolf20.buildinggadgets.client.gui.GuiMod;
import com.direwolf20.buildinggadgets.common.config.Config;
import com.direwolf20.buildinggadgets.common.config.crafting.CraftingConditionDestruction;
import com.direwolf20.buildinggadgets.common.config.crafting.CraftingConditionPaste;
import com.direwolf20.buildinggadgets.common.config.crafting.RecipeConstructionPaste;
import com.direwolf20.buildinggadgets.common.events.AnvilRepairHandler;
import com.direwolf20.buildinggadgets.common.network.PacketHandler;
import com.direwolf20.buildinggadgets.common.registry.objects.BuildingObjects;
import com.direwolf20.buildinggadgets.common.util.ref.Reference;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DeferredWorkQueue;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

@Mod(value = Reference.MODID)
public final class BuildingGadgets {

    public static Logger LOG = LogManager.getLogger();
    private static BuildingGadgets theMod = null;

    public static BuildingGadgets getInstance() {
        assert theMod != null;
        return theMod;
    }

    private final BuildingGadgetsAPI theAPi;

    public BuildingGadgets() {
        theAPi = new BuildingGadgetsAPI();
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(Type.SERVER, Config.SERVER_CONFIG);
        ModLoadingContext.get().registerConfig(Type.COMMON, Config.COMMON_CONFIG);
        ModLoadingContext.get().registerConfig(Type.CLIENT, Config.CLIENT_CONFIG);

        eventBus.addListener(this::setup);
        eventBus.addListener(this::serverLoad);
        eventBus.addListener(this::finishLoad);
        eventBus.addGenericListener(IRecipeSerializer.class, this::onRecipeRegister);

        eventBus.addListener(Config::onLoad);
        eventBus.addListener(Config::onFileChange);

        MinecraftForge.EVENT_BUS.register(new AnvilRepairHandler());

        // Client only registering
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
            eventBus.addListener((Consumer<FMLClientSetupEvent>) event -> ClientProxy.clientSetup(eventBus));
            ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY, () -> GuiMod::openScreen);
            FMLJavaModLoadingContext.get().getModEventBus().addListener(BuildingObjects::initColorHandlers);
        });
        BuildingObjects.init();
    }

    private void setup(final FMLCommonSetupEvent event) {
        theMod = (BuildingGadgets) ModLoadingContext.get().getActiveContainer().getMod();
        DeferredWorkQueue.runLater(() -> {
            PacketHandler.register();
            CraftingHelper.register(Reference.CONDITION_PASTE_ID, new CraftingConditionPaste());
            CraftingHelper.register(Reference.CONDITION_DESTRUCTION_ID, new CraftingConditionDestruction());
        });
    }

    private void serverLoad(FMLServerStartingEvent event) {
        /*event.getCommandDispatcher().register(
                Commands.literal(Reference.MODID)
                    .then(BlockMapCommand.registerList())
                    .then(BlockMapCommand.registerDelete())
        );*/
    }

    private void finishLoad(FMLLoadCompleteEvent event) {
        BuildingObjects.cleanup();
    }

    private void onRecipeRegister(final RegistryEvent.Register<IRecipeSerializer<?>> e) {
        e.getRegistry().register(
            new RecipeConstructionPaste.Serializer().setRegistryName(
                    new ResourceLocation(Reference.MODID, "construction_paste")
            )
        );
    }

}
