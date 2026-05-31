package com.wzz.registerhelper;

import com.wzz.registerhelper.gui.ConfigScreen;
import com.wzz.registerhelper.init.ModKeyMappings;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import static com.wzz.registerhelper.RecipeHelper.MODID;

public class RecipeHelperClient {
    public static void init() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        modEventBus.addListener(ModKeyMappings::register);
        
        net.minecraftforge.fml.ModList.get().getModContainerById(MODID).ifPresent(container -> container.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new ConfigScreen(parent)
                )
        ));
    }
}
