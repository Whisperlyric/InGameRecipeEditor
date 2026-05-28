package dev.whisperlyric_fork.mekanism;

import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig;
import com.wzz.registerhelper.gui.recipe.layout.LayoutManager;
import com.wzz.registerhelper.util.RegisterHelper;
import dev.whisperlyric_fork.mekanism.layout.ChemicalCrystallizerLayout;
import dev.whisperlyric_fork.mekanism.layout.EnergyConversionLayout;
import dev.whisperlyric_fork.mekanism.layout.RotaryCondensentratorLayout;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class MekanismIntegration {
    
    private static boolean initialized = false;
    
    public static void init() {
        if (initialized) return;
        initialized = true;
        
        if (!ModList.get().isLoaded("mekanism")) {
            return;
        }
        
        FMLJavaModLoadingContext.get().getModEventBus()
            .addListener(MekanismIntegration::onCommonSetup);
    }
    
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            registerLayouts();
            registerRecipeTypes();
        });
    }
    
    private static void registerLayouts() {
        LayoutManager.registerLayout("mekanism_energy_conversion", new EnergyConversionLayout());
        LayoutManager.registerLayout("mekanism_rotary_condensentrator", new RotaryCondensentratorLayout());
        LayoutManager.registerLayout("mekanism_chemical_crystallizer", new ChemicalCrystallizerLayout());
    }
    
    private static void registerRecipeTypes() {
        MekanismProcessor processor = new MekanismProcessor();
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:crushing", "通用机械:粉碎机")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "crushing")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:enriching", "通用机械:富集仓")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "enriching")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:smelting", "通用机械:电力熔炼炉")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "smelting")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:combining", "通用机械:融合机")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "combining")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:compressing", "通用机械:锇压缩机")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "compressing")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:purifying", "通用机械:提纯仓")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "purifying")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:injecting", "通用机械:化学压射室")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "injecting")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:metallurgic_infusing", "通用机械:冶金灌注机")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "metallurgic_infusing")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:sawing", "通用机械:精密锯木机")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "sawing")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:chemical_infusing", "通用机械:化学灌注器")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "chemical_infusing")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:crystallizing", "通用机械:化学结晶器")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "crystallizing")
                .property("layout", "mekanism_chemical_crystallizer")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:dissolution", "通用机械:化学溶解室")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "dissolution")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:energy_conversion", "通用机械:物品到能量")
                .modId("mekanism")
                .gridSize(1, 1)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "energy_conversion")
                .property("layout", "mekanism_energy_conversion")
                .property("outputType", "energy")
                .processor(processor)
                .build()
        );
        
        RegisterHelper.registerRecipeTypeWithLayout("mekanism", "rotary_condensentrator",
                "通用机械:回旋式气液转换器", processor, "mekanism_rotary_condensentrator");
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:electrolytic_separator", "通用机械:电解分离器")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "electrolytic_separator")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:pressurized_reaction_chamber", "通用机械:加压反应室")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "pressurized_reaction_chamber")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:isotopic_centrifuge", "通用机械:同位素离心机")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "isotopic_centrifuge")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:solar_neutron_activator", "通用机械:太阳能中子活化器")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "solar_neutron_activator")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:antiprotonic_nucleosynthesizer", "通用机械:反质子核合成器")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(true)
                .property("category", "mekanism")
                .property("mode", "antiprotonic_nucleosynthesizer")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:thermal_evaporation", "通用机械:热力蒸馏塔")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(true)
                .property("category", "mekanism")
                .property("mode", "thermal_evaporation")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:chemical_oxidizer", "通用机械:化学氧化机")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(true)
                .property("category", "mekanism")
                .property("mode", "chemical_oxidizer")
                .processor(processor)
                .build()
        );
    }
}
