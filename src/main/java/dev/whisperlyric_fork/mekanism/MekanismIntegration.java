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
        
        registerSimpleRecipeType(processor, "crushing", "粉碎机", "mekanism_crushing");
        registerSimpleRecipeType(processor, "enriching", "富集仓", "mekanism_enriching");
        registerSimpleRecipeType(processor, "smelting", "电力熔炼炉", "mekanism_smelting");
        registerSimpleRecipeType(processor, "combining", "融合机", "mekanism_combining");
        registerSimpleRecipeType(processor, "compressing", "锇压缩机", "mekanism_compressing");
        registerSimpleRecipeType(processor, "purifying", "提纯仓", "mekanism_purifying");
        registerSimpleRecipeType(processor, "injecting", "化学压射室", "mekanism_injecting");
        registerSimpleRecipeType(processor, "metallurgic_infusing", "冶金灌注机", "mekanism_metallurgic_infusing");
        registerSimpleRecipeType(processor, "sawing", "精密锯木机", "mekanism_sawing");
        registerSimpleRecipeType(processor, "chemical_infusing", "化学灌注器", "mekanism_chemical_infusing");
        registerSimpleRecipeType(processor, "crystallizing", "化学结晶器", "mekanism_chemical_crystallizer");
        registerSimpleRecipeType(processor, "dissolution", "化学溶解室", "mekanism_dissolution");
        registerSimpleRecipeType(processor, "evaporating", "热力蒸馏塔", "mekanism_evaporating");
        registerSimpleRecipeType(processor, "nucleosynthesizing", "反质子核合成器", "mekanism_nucleosynthesizing");
        registerSimpleRecipeType(processor, "centrifuging", "同位素离心机", "mekanism_centrifuging");
        registerSimpleRecipeType(processor, "activating", "太阳能中子活化器", "mekanism_activating");
        registerSimpleRecipeType(processor, "oxidizing", "化学氧化机", "mekanism_chemical_oxidizer");
        registerSimpleRecipeType(processor, "separating", "电解分离器", "mekanism_electrolytic_separator");
        registerSimpleRecipeType(processor, "washing", "化学清洗机", "mekanism_washing");
        registerSimpleRecipeType(processor, "reaction", "加压反应室", "mekanism_reaction");
        registerSimpleRecipeType(processor, "painting", "上色机", "mekanism_painting");
        registerSimpleRecipeType(processor, "pigment_mixing", "颜料混合器", "mekanism_pigment_mixing");
        registerSimpleRecipeType(processor, "pigment_extracting", "颜料提取器", "mekanism_pigment_extracting");
        registerSimpleRecipeType(processor, "gas_conversion", "物品到气体", "mekanism_gas_conversion", 1, 1);
        registerSimpleRecipeType(processor, "infusion_conversion", "物品到灌注类型", "mekanism_infusion_conversion", 1, 1);
        
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
        
        RegisterHelper.registerRecipeTypeWithLayout("mekanism", "rotary",
                "通用机械:回旋式气液转换器", processor, "mekanism_rotary_condensentrator");
    }
    
    private static void registerSimpleRecipeType(MekanismProcessor processor, String type, String displayName, String layoutId) {
        registerSimpleRecipeType(processor, type, displayName, layoutId, 3, 3);
    }
    
    private static void registerSimpleRecipeType(MekanismProcessor processor, String type, String displayName, String layoutId, int gridWidth, int gridHeight) {
        DynamicRecipeTypeConfig.registerRecipeType(
            new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder("mekanism:" + type, "通用机械:" + displayName)
                .modId("mekanism")
                .gridSize(gridWidth, gridHeight)
                .supportsFillMode(false)
                .property("category", "mekanism")
                .property("mode", type)
                .property("layout", layoutId)
                .processor(processor)
                .build()
        );
    }
}
