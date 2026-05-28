package dev.whisperlyric_fork.gui;

import com.wzz.registerhelper.RecipeHelper;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModContainers {
    
    public static final DeferredRegister<MenuType<?>> CONTAINERS = 
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, RecipeHelper.MODID);
    
    public static final RegistryObject<MenuType<?>> JEI_SELECTION = 
        CONTAINERS.register("jei_selection", () -> new MenuType<JEISelectionContainer>(JEISelectionContainer::new, net.minecraft.world.flag.FeatureFlagSet.of()));
}