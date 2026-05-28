package dev.whisperlyric_fork.create;

import com.wzz.registerhelper.gui.recipe.layout.LayoutManager;
import com.wzz.registerhelper.util.RegisterHelper;
import dev.whisperlyric_fork.create.layout.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class CreateIntegration {
    
    private static boolean initialized = false;
    
    public static void init() {
        if (initialized) return;
        initialized = true;
        
        if (!ModList.get().isLoaded("create")) {
            return;
        }
        
        FMLJavaModLoadingContext.get().getModEventBus()
            .addListener(CreateIntegration::onCommonSetup);
    }
    
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            registerLayouts();
        });
    }
    
    private static void registerLayouts() {
        LayoutManager.registerLayout("create_basin_mixing", new BasinMixingLayout());
        LayoutManager.registerLayout("create_basin_pressing", new BasinPressingLayout());
    }
}
