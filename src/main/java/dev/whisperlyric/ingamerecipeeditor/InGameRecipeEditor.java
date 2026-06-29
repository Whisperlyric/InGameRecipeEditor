package dev.whisperlyric.ingamerecipeeditor;

import dev.whisperlyric.ingamerecipeeditor.init.ModKeyMappings;
import dev.whisperlyric.ingamerecipeeditor.network.NetworkHandler;
import dev.whisperlyric.ingamerecipeeditor.workspace.IngredientCycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;


@Mod("ingamerecipeeditor")
public class InGameRecipeEditor {
    
    public static final String MOD_ID = "ingamerecipeeditor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    @SuppressWarnings("removal")
    public InGameRecipeEditor() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ModKeyMappings::register);
        
        LOGGER.info("InGameRecipeEditor mod initialized");
    }
    
    private void onCommonSetup(final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            RecipeSystemInitializer.initialize();
            LOGGER.info("Recipe system initialized");
            
            NetworkHandler.init();
            LOGGER.info("Network handler initialized");
        });
    }

    private void onClientSetup(final net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        // Ensure IngredientCycleManager class is loaded and initialized on client
        event.enqueueWork(IngredientCycleManager::init);
    }
}