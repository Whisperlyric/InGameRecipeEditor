package dev.whisperlyric_fork.mekanism;

import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig;
import com.wzz.registerhelper.gui.recipe.layout.LayoutManager;
import com.wzz.registerhelper.util.RegisterHelper;
import dev.whisperlyric_fork.mekanism.layout.ChemicalCrystallizerLayout;
import dev.whisperlyric_fork.mekanism.layout.EnergyConversionLayout;
import dev.whisperlyric_fork.mekanism.layout.RotaryCondensentratorLayout;
import dev.whisperlyric_fork.mekanism.layout.CrushingLayout;
import dev.whisperlyric_fork.mekanism.layout.EnrichingLayout;
import dev.whisperlyric_fork.mekanism.layout.SmeltingLayout;
import dev.whisperlyric_fork.mekanism.layout.CombiningLayout;
import dev.whisperlyric_fork.mekanism.layout.CompressingLayout;
import dev.whisperlyric_fork.mekanism.layout.PurifyingLayout;
import dev.whisperlyric_fork.mekanism.layout.InjectingLayout;
import dev.whisperlyric_fork.mekanism.layout.MetallurgicInfusingLayout;
import dev.whisperlyric_fork.mekanism.layout.SawingLayout;
import dev.whisperlyric_fork.mekanism.layout.ChemicalInfusingLayout;
import dev.whisperlyric_fork.mekanism.layout.DissolutionLayout;
import dev.whisperlyric_fork.mekanism.layout.EvaporatingLayout;
import dev.whisperlyric_fork.mekanism.layout.NucleosynthesizingLayout;
import dev.whisperlyric_fork.mekanism.layout.CentrifugingLayout;
import dev.whisperlyric_fork.mekanism.layout.ActivatingLayout;
import dev.whisperlyric_fork.mekanism.layout.ReactionLayout;
import dev.whisperlyric_fork.mekanism.layout.ChemicalOxidizerLayout;
import dev.whisperlyric_fork.mekanism.layout.ElectrolyticSeparatorLayout;
import dev.whisperlyric_fork.mekanism.layout.ChemicalWasherLayout;
import dev.whisperlyric_fork.mekanism.layout.PaintingLayout;
import dev.whisperlyric_fork.mekanism.layout.PigmentMixingLayout;
import dev.whisperlyric_fork.mekanism.layout.PigmentExtractingLayout;
import dev.whisperlyric_fork.mekanism.layout.GasConversionLayout;
import dev.whisperlyric_fork.mekanism.layout.InfusionConversionLayout;
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
        
        LayoutManager.registerLayout("mekanism_crushing", new CrushingLayout());
        LayoutManager.registerLayout("mekanism_enriching", new EnrichingLayout());
        LayoutManager.registerLayout("mekanism_smelting", new SmeltingLayout());
        LayoutManager.registerLayout("mekanism_combining", new CombiningLayout());
        LayoutManager.registerLayout("mekanism_compressing", new CompressingLayout());
        LayoutManager.registerLayout("mekanism_purifying", new PurifyingLayout());
        LayoutManager.registerLayout("mekanism_injecting", new InjectingLayout());
        LayoutManager.registerLayout("mekanism_metallurgic_infusing", new MetallurgicInfusingLayout());
        LayoutManager.registerLayout("mekanism_sawing", new SawingLayout());
        LayoutManager.registerLayout("mekanism_chemical_infusing", new ChemicalInfusingLayout());
        LayoutManager.registerLayout("mekanism_dissolution", new DissolutionLayout());
        LayoutManager.registerLayout("mekanism_evaporating", new EvaporatingLayout());
        LayoutManager.registerLayout("mekanism_nucleosynthesizing", new NucleosynthesizingLayout());
        LayoutManager.registerLayout("mekanism_centrifuging", new CentrifugingLayout());
        LayoutManager.registerLayout("mekanism_activating", new ActivatingLayout());
        LayoutManager.registerLayout("mekanism_reaction", new ReactionLayout());
        LayoutManager.registerLayout("mekanism_chemical_oxidizer", new ChemicalOxidizerLayout());
        LayoutManager.registerLayout("mekanism_electrolytic_separator", new ElectrolyticSeparatorLayout());
        LayoutManager.registerLayout("mekanism_washing", new ChemicalWasherLayout());
        LayoutManager.registerLayout("mekanism_painting", new PaintingLayout());
        LayoutManager.registerLayout("mekanism_pigment_mixing", new PigmentMixingLayout());
        LayoutManager.registerLayout("mekanism_pigment_extracting", new PigmentExtractingLayout());
        LayoutManager.registerLayout("mekanism_gas_conversion", new GasConversionLayout());
        LayoutManager.registerLayout("mekanism_infusion_conversion", new InfusionConversionLayout());
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
                .property("layout", "mekanism_crushing")
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
                .property("layout", "mekanism_enriching")
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
                .property("layout", "mekanism_smelting")
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
                .property("layout", "mekanism_combining")
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
                .property("layout", "mekanism_compressing")
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
                .property("layout", "mekanism_purifying")
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
                .property("layout", "mekanism_injecting")
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
                .property("layout", "mekanism_metallurgic_infusing")
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
                .property("layout", "mekanism_sawing")
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
                .property("layout", "mekanism_chemical_infusing")
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
                .property("layout", "mekanism_dissolution")
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
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:evaporating", "通用机械:蒸馏塔")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "evaporating")
                .property("layout", "mekanism_evaporating")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:nucleosynthesizing", "通用机械:反质子核合成器")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "antiprotonic_nucleosynthesizer")
                .property("layout", "mekanism_nucleosynthesizing")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:centrifuging", "通用机械:同位素离心机")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "centrifuging")
                .property("layout", "mekanism_centrifuging")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:activating", "通用机械:太阳能中子活化器")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "activating")
                .property("layout", "mekanism_activating")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:oxidizing", "通用机械:化学氧化机")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "chemical_oxidizer")
                .property("layout", "mekanism_chemical_oxidizer")
                .processor(processor)
                .build()
        );
        
        RegisterHelper.registerRecipeTypeWithLayout("mekanism", "rotary",
                "通用机械:回旋式气液转换器", processor, "mekanism_rotary_condensentrator");
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:separating", "通用机械:电解分离器")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "separating")
                .property("layout", "mekanism_electrolytic_separator")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:washing", "通用机械:化学清洗机")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "washing")
                .property("layout", "mekanism_washing")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:reaction", "通用机械:加压反应室")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "reaction")
                .property("layout", "mekanism_reaction")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:painting", "通用机械:上色机")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "painting")
                .property("layout", "mekanism_painting")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:pigment_mixing", "通用机械:颜料混合器")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "pigment_mixing")
                .property("layout", "mekanism_pigment_mixing")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:pigment_extracting", "通用机械:颜料提取器")
                .modId("mekanism")
                .gridSize(3, 3)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "pigment_extracting")
                .property("layout", "mekanism_pigment_extracting")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:gas_conversion", "通用机械:物品到气体")
                .modId("mekanism")
                .gridSize(1, 1)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "gas_conversion")
                .property("layout", "mekanism_gas_conversion")
                .processor(processor)
                .build()
        );
        
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:infusion_conversion", "通用机械:物品到灌注类型")
                .modId("mekanism")
                .gridSize(1, 1)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", "infusion_conversion")
                .property("layout", "mekanism_infusion_conversion")
                .processor(processor)
                .build()
        );
    }
}
