package dev.whisperlyric_fork;

import dev.whisperlyric_fork.mekanism.MekanismIntegration;
import dev.whisperlyric_fork.create.CreateIntegration;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = "registerhelper", bus = Mod.EventBusSubscriber.Bus.MOD)
public class PlaceholderInit {
    
    private static final DeferredRegister<Item> ITEMS = 
        DeferredRegister.create(ForgeRegistries.ITEMS, "registerhelper");
    
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "registerhelper");
    
    public static final RegistryObject<Item> PLACEHOLDER_0 = 
        ITEMS.register("placeholder_0", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLACEHOLDER_1 = 
        ITEMS.register("placeholder_1", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLACEHOLDER_2 = 
        ITEMS.register("placeholder_2", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLACEHOLDER_3 = 
        ITEMS.register("placeholder_3", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLACEHOLDER_4 = 
        ITEMS.register("placeholder_4", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLACEHOLDER_5 = 
        ITEMS.register("placeholder_5", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLACEHOLDER_6 = 
        ITEMS.register("placeholder_6", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLACEHOLDER_7 = 
        ITEMS.register("placeholder_7", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLACEHOLDER_8 = 
        ITEMS.register("placeholder_8", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLACEHOLDER_9 = 
        ITEMS.register("placeholder_9", () -> new Item(new Item.Properties()));
    
    public static final RegistryObject<CreativeModeTab> PLACEHOLDER_TAB =
        CREATIVE_MODE_TABS.register("placeholder_items", () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack(PLACEHOLDER_0.get()))
            .title(Component.translatable("itemGroup.registerhelper.placeholder_items"))
            .displayItems((pParameters, pOutput) -> {
                pOutput.accept(PLACEHOLDER_0.get());
                pOutput.accept(PLACEHOLDER_1.get());
                pOutput.accept(PLACEHOLDER_2.get());
                pOutput.accept(PLACEHOLDER_3.get());
                pOutput.accept(PLACEHOLDER_4.get());
                pOutput.accept(PLACEHOLDER_5.get());
                pOutput.accept(PLACEHOLDER_6.get());
                pOutput.accept(PLACEHOLDER_7.get());
                pOutput.accept(PLACEHOLDER_8.get());
                pOutput.accept(PLACEHOLDER_9.get());
            }).build());
    
    static {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        
        try {
            MekanismIntegration.init();
        } catch (Throwable e) {
            System.err.println("Failed to initialize Mekanism integration: " + e.getMessage());
        }
        
        try {
            CreateIntegration.init();
        } catch (Throwable e) {
            System.err.println("Failed to initialize Create integration: " + e.getMessage());
        }
    }
}
